package com.pocketledger.domain

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The backup file format.
 *
 * A backup is only ever read on the day something has already gone wrong, so the parts
 * worth testing are the refusals: a file that belongs to something else, a file from a
 * newer release, and a file whose rows do not line up with its columns must all be
 * rejected *before* anything is written.
 */
class AppBackupTest {

    private fun file(
        tables: Map<String, AppBackup.Table>,
        omitted: List<String> = emptyList(),
        preferences: Map<String, Set<String>> = emptyMap(),
        ledgerName: String? = "我的账本",
    ) = AppBackup.File(
        version = AppBackup.VERSION,
        schemaVersion = 4,
        createdAt = 1_700_000_000_000L,
        appVersion = "0.8.3",
        scope = "ALL",
        ledgerName = ledgerName,
        tables = tables,
        omittedTables = omitted,
        preferences = preferences,
    )

    private val tables = mapOf(
        "ledger" to AppBackup.Table(
            columns = listOf("id", "name", "isArchived", "deletedAt"),
            rows = listOf(
                listOf(JsonPrimitive(1L), JsonPrimitive("我的账本"), JsonPrimitive(0L), JsonNull),
            ),
        ),
        "txn" to AppBackup.Table(
            columns = listOf("id", "ledgerId", "amountCents", "merchant"),
            rows = listOf(
                listOf(JsonPrimitive(1L), JsonPrimitive(1L), JsonPrimitive(1250L), JsonPrimitive("食堂")),
                listOf(JsonPrimitive(2L), JsonPrimitive(1L), JsonPrimitive(80L), JsonNull),
            ),
        ),
        "empty_table" to AppBackup.Table(columns = listOf("id"), rows = emptyList()),
    )

    @Test
    fun `a round trip preserves every table, column and value`() {
        val original = file(tables, omitted = listOf("txn_tag"), preferences = mapOf("a" to setOf("1", "2")))
        val decoded = AppBackup.decode(AppBackup.encode(original))

        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.appVersion, decoded.appVersion)
        assertEquals(original.ledgerName, decoded.ledgerName)
        assertEquals(original.omittedTables, decoded.omittedTables)
        assertEquals(original.preferences, decoded.preferences)
        assertEquals(tables.keys, decoded.tables.keys)
        decoded.tables.forEach { (name, table) ->
            val expected = tables.getValue(name)
            assertEquals("columns of $name", expected.columns, table.columns)
            assertEquals("row count of $name", expected.rows.size, table.rows.size)
            expected.rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { cellIndex, cell ->
                    assertEquals(
                        "$name[$rowIndex][$cellIndex]",
                        cell.toString(),
                        table.rows[rowIndex][cellIndex].toString(),
                    )
                }
            }
        }
    }

    @Test
    fun `storage classes survive, so money does not come back as text`() {
        val decoded = AppBackup.decode(AppBackup.encode(file(tables)))
        val row = decoded.tables.getValue("txn").rows.first()
        assertTrue(row[2].toString() == "1250")
        // Specifically not a JSON string: an integer restored as text breaks every SUM.
        assertFalse((row[2] as JsonPrimitive).isString)
        assertNotNull(row[0])
    }

    @Test
    fun `a file that is not a backup is refused`() {
        assertRefused("""{"hello":"world"}""")
        assertRefused("not json at all")
        assertRefused("[]")
    }

    @Test
    fun `a file from a newer format version is refused rather than half-read`() {
        // Written out by hand rather than string-patched out of an encoded file: the
        // encoder pretty-prints, so a `replace` on its output would quietly match nothing
        // and the test would pass without exercising anything.
        assertRefused(
            """
            {
              "format": "pocketledger-backup",
              "version": 99,
              "schemaVersion": 4,
              "tables": {}
            }
            """.trimIndent()
        )
    }

    @Test
    fun `a row that does not match the column count is refused`() {
        assertRefused(
            """
            {
              "format": "pocketledger-backup",
              "version": 1,
              "schemaVersion": 4,
              "tables": {
                "txn": { "columns": ["id", "amountCents"], "rows": [[1]] }
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `a table named in the file but missing from the schema is caught by the caller`() {
        // `decode` keeps what the file says; refusing a table the install does not have is
        // the restore's job, because only it can see the live schema. This pins that the
        // name is preserved rather than dropped on the floor.
        val decoded = AppBackup.decode(AppBackup.encode(file(tables)))
        assertTrue("txn" in decoded.tables)
    }

    @Test
    fun `a table with no rows survives the round trip`() {
        val decoded = AppBackup.decode(AppBackup.encode(file(tables)))
        assertEquals(emptyList<List<Any>>(), decoded.tables.getValue("empty_table").rows)
        assertEquals(listOf("id"), decoded.tables.getValue("empty_table").columns)
    }

    @Test
    fun `rowCount adds up the tables`() {
        assertEquals(3, file(tables).rowCount)
    }

    private fun assertRefused(text: String) {
        try {
            AppBackup.decode(text)
            fail("expected the file to be refused")
        } catch (expected: AppBackup.FormatException) {
            assertTrue(
                "the message has to say something the user can act on",
                !expected.message.isNullOrBlank(),
            )
        }
    }
}
