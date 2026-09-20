package com.pocketledger.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The application-backup file format.
 *
 * A CSV export answers "what did I spend"; this answers "put my app back exactly as it
 * was" -- every table, every soft-deleted row, every ledger, and the app-level settings
 * that live outside the database. Those are two different jobs and mixing them into one
 * file would make both worse: a spreadsheet cannot carry a schema, and a restore that
 * quietly reinterprets columns as text loses the money.
 *
 * **Structure, not a schema.** Rows are stored as arrays of values against a column list
 * taken at export time, and the table list is discovered from the database rather than
 * written down here. A hand-written list of fields per entity would be a second copy of
 * the schema, and the failure mode of the two drifting apart is a restore that silently
 * *drops* whatever was added since -- the worst possible bug for a backup, and one nobody
 * would notice until they needed it. Serialising the tables as they are means a backup
 * taken by this version carries whatever it actually had.
 *
 * This module is pure: it turns a [File] into text and back, with no Android or database
 * dependency, so the format is verifiable by unit tests.
 */
object AppBackup {

    /** Marks the file so a JSON file that merely happens to parse is still rejected. */
    const val FORMAT = "pocketledger-backup"

    /** Format revision, independent of the database schema version. */
    const val VERSION = 1

    /** Thrown for anything wrong with a file the user picked; the message is shown as-is. */
    class FormatException(message: String) : Exception(message)

    /** One table: its columns, then one value list per row. */
    data class Table(
        val columns: List<String>,
        val rows: List<List<JsonElement>>,
    )

    /**
     * A whole backup.
     *
     * [omittedTables] names tables a single-ledger export could not attribute to a ledger.
     * It is recorded rather than silently dropped so a restore can say what it is missing
     * instead of the user discovering it months later.
     */
    data class File(
        val version: Int,
        val schemaVersion: Int,
        val createdAt: Long,
        val appVersion: String,
        val scope: String,
        val ledgerName: String?,
        val tables: Map<String, Table>,
        val omittedTables: List<String> = emptyList(),
        val preferences: Map<String, Set<String>> = emptyMap(),
    ) {
        val rowCount: Int get() = tables.values.sumOf { it.rows.size }
    }

    private val json = Json {
        prettyPrint = true
        // Keeps every key present, which makes two backups of the same data diff cleanly.
        encodeDefaults = true
    }

    fun encode(file: File): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", FORMAT)
            put("version", file.version)
            put("schemaVersion", file.schemaVersion)
            put("createdAt", file.createdAt)
            put("appVersion", file.appVersion)
            put("scope", file.scope)
            file.ledgerName?.let { put("ledgerName", it) }
            putJsonObject("tables") {
                file.tables.forEach { (name, table) ->
                    putJsonObject(name) {
                        putJsonArray("columns") { table.columns.forEach { add(it) } }
                        putJsonArray("rows") {
                            table.rows.forEach { row -> add(JsonArray(row)) }
                        }
                    }
                }
            }
            if (file.omittedTables.isNotEmpty()) {
                putJsonArray("omittedTables") { file.omittedTables.forEach { add(it) } }
            }
            putJsonObject("preferences") {
                file.preferences.forEach { (key, values) ->
                    putJsonArray(key) { values.sorted().forEach { add(it) } }
                }
            }
        },
    )

    /**
     * Reads a file back.
     *
     * Every failure raises [FormatException] with a sentence a user can act on. The point
     * is that a restore replaces everything the app holds, so it must be *sure* the file is
     * one of ours before it starts: an exception here costs a retry, whereas accepting a
     * partial file costs the data it was meant to protect.
     */
    fun decode(text: String): File {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
            throw FormatException("这个文件不是记账本备份。")
        }
        if (root.str("format") != FORMAT) throw FormatException("这个文件不是记账本备份。")

        val version = (root.long("version")
            ?: throw FormatException("备份文件缺少版本号，无法确认它来自哪个版本。")).toInt()
        if (version > VERSION) {
            throw FormatException("备份来自更新的记账本（格式 v$version），当前版本读不了。")
        }

        val tables = root["tables"]?.takeIf { it is JsonObject }?.jsonObject
            ?: throw FormatException("备份文件里没有任何数据表。")

        return File(
            version = version,
            schemaVersion = root.long("schemaVersion")?.toInt() ?: 0,
            createdAt = root.long("createdAt") ?: 0L,
            appVersion = root.str("appVersion").orEmpty(),
            scope = root.str("scope").orEmpty(),
            ledgerName = root.str("ledgerName"),
            tables = tables.mapValues { (name, element) -> decodeTable(name, element) },
            omittedTables = (root["omittedTables"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                .orEmpty(),
            preferences = (root["preferences"] as? JsonObject)
                ?.mapValues { (_, element) ->
                    (element as? JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.toSet()
                        .orEmpty()
                }
                .orEmpty(),
        )
    }

    private fun decodeTable(name: String, element: JsonElement): Table {
        val table = element as? JsonObject
            ?: throw FormatException("备份里的表「$name」格式不对。")
        val columns = (table["columns"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: throw FormatException("备份里的表「$name」缺少列定义。")
        val rows = (table["rows"] as? JsonArray)
            ?.map { row ->
                row.jsonArray.map { cell -> cell }
                    .also {
                        if (it.size != columns.size) {
                            throw FormatException("备份里的表「$name」有一行与列数不符。")
                        }
                    }
            }
            .orEmpty()
        return Table(columns, rows)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? JsonPrimitive)?.longOrNull
}
