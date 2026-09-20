package com.pocketledger.data.backup

import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import com.pocketledger.data.LedgerDatabase
import com.pocketledger.data.prefs.AppPreferences
import com.pocketledger.domain.AppBackup
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Dumps and restores the whole application.
 *
 * Works off the database's own catalogue rather than a list of entities: the tables are
 * whatever `sqlite_master` says, and the columns are whatever a `SELECT *` returns. That
 * is deliberate -- see [AppBackup.File] -- but it also means this class never has to be
 * updated when a table or column is added, which is exactly when a backup is most likely
 * to be written wrongly.
 *
 * **There is no scope.** Every table, every ledger, every soft-deleted row: a backup that
 * covered only part of the app would restore into a state the user never had, and the
 * question "which ledger?" belongs to the CSV export, which is a spreadsheet of one book
 * rather than a copy of the application.
 *
 * A restore is likewise a **full replacement**: everything currently stored is deleted
 * first. Merging the file with what is already there would produce a third state that is
 * neither the backup nor the present, and there would be no way to describe what the user
 * ended up with.
 */
class BackupService(
    private val database: LedgerDatabase,
    private val preferences: AppPreferences,
) {

    suspend fun export(
        schemaVersion: Int,
        appVersion: String,
        now: Long = System.currentTimeMillis(),
    ): AppBackup.File {
        val db = database.openHelper.readableDatabase
        val tables = linkedMapOf<String, AppBackup.Table>()
        tableNames(db).forEach { table ->
            tables[table] = dump(db, table)
        }
        return AppBackup.File(
            version = AppBackup.VERSION,
            schemaVersion = schemaVersion,
            createdAt = now,
            appVersion = appVersion,
            tables = tables,
            preferences = preferences.snapshot(),
        )
    }

    /**
     * Replaces everything the app holds with [file].
     *
     * One transaction, with foreign-key checks deferred to the commit: the file's insert
     * order follows the database's own table order, which happens to satisfy the two real
     * foreign keys, but relying on that would make a future table's position load-bearing.
     * Deferring means the constraint still has to hold at the end -- it is checked, just
     * not at every intermediate statement.
     *
     * The schema version must match. A backup whose columns do not line up would insert
     * wrong values into right columns, and a half-restored ledger is worse than a refusal.
     */
    suspend fun restore(file: AppBackup.File, expectedSchemaVersion: Int) {
        if (file.schemaVersion != expectedSchemaVersion) {
            throw AppBackup.FormatException(
                "备份来自 v${file.schemaVersion} 的数据结构，当前是 v$expectedSchemaVersion，不能直接恢复。"
            )
        }

        val db = database.openHelper.writableDatabase
        val existing = tableNames(db)
        val missing = file.tables.keys - existing.toSet()
        if (missing.isNotEmpty()) {
            throw AppBackup.FormatException("备份里有当前版本不存在的表：${missing.joinToString()}。")
        }

        // Room's own transaction wrapper, even though every statement inside is raw SQL:
        // it is the same path a DAO write takes, so the invalidation triggers Room installs
        // are given the chance to fire and every observing screen re-queries. A bare
        // `beginTransaction` on the support database would leave the open page showing the
        // ledger that was just replaced.
        database.withTransaction {
            db.execSQL("PRAGMA defer_foreign_keys = TRUE")
            // Every table, not just the ones in the file: a backup from an older schema may
            // simply not mention a table that this install has been filling since.
            existing.forEach { db.execSQL("DELETE FROM `$it`") }
            file.tables.forEach { (table, data) -> insertAll(db, table, data) }
        }
        database.invalidationTracker.refreshVersionsAsync()

        // Settings live outside the database. Written after the transaction because a
        // failure here leaves a restored ledger with default settings -- recoverable --
        // whereas a half-written database would not be.
        preferences.replaceAll(file.preferences)
    }

    // ------------------------------------------------------------------- reading

    private fun tableNames(db: SupportSQLiteDatabase): List<String> {
        val sql = """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
              AND name NOT LIKE 'android_%'
              AND name NOT LIKE 'room_%'
            ORDER BY rowid
        """.trimIndent()
        return db.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    private fun dump(db: SupportSQLiteDatabase, table: String): AppBackup.Table =
        db.query("SELECT * FROM `$table`").use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = ArrayList<List<JsonElement>>(cursor.count)
            while (cursor.moveToNext()) {
                rows += columns.indices.map { index -> readCell(cursor, index) }
            }
            AppBackup.Table(columns, rows)
        }

    /**
     * One cell, keeping its storage class.
     *
     * Money is a `Long` of cents, so an integer read as a string would restore a column of
     * text and break every sum. Blobs are refused rather than encoded: nothing in this
     * schema stores one, and a silent skip is how a backup starts losing data.
     */
    private fun readCell(cursor: Cursor, index: Int): JsonElement = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(cursor.getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(cursor.getDouble(index))
        Cursor.FIELD_TYPE_STRING -> JsonPrimitive(cursor.getString(index))
        else -> throw AppBackup.FormatException("表里有二进制字段，备份暂不支持。")
    }

    // ------------------------------------------------------------------- writing

    private fun insertAll(db: SupportSQLiteDatabase, table: String, data: AppBackup.Table) {
        if (data.rows.isEmpty()) return
        val columns = data.columns.joinToString(", ") { "`$it`" }
        val placeholders = data.columns.joinToString(", ") { "?" }
        val statement = db.compileStatement("INSERT INTO `$table` ($columns) VALUES ($placeholders)")
        data.rows.forEach { row ->
            statement.clearBindings()
            row.forEachIndexed { index, element -> bind(statement, index + 1, element) }
            statement.executeInsert()
        }
    }

    private fun bind(statement: SupportSQLiteStatement, index: Int, element: JsonElement) {
        if (element is JsonNull) {
            statement.bindNull(index)
            return
        }
        val primitive = element as? JsonPrimitive
            ?: throw AppBackup.FormatException("备份里有一行不是简单值。")
        if (primitive.isString) {
            statement.bindString(index, primitive.content)
            return
        }
        // "5" was a Long when it was exported and "5.0" was a Double, so the textual form
        // carries the storage class back without a second type field per column.
        val asLong = primitive.content.toLongOrNull()
        if (asLong != null) {
            statement.bindLong(index, asLong)
        } else {
            statement.bindDouble(index, primitive.content.toDouble())
        }
    }
}

