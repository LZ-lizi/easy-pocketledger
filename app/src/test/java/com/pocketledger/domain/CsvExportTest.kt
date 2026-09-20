package com.pocketledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CSV escaping.
 *
 * A merchant called `星巴克, 国贸店` or a note containing a quote is completely
 * ordinary, and getting the escaping wrong shifts every later column in the file --
 * a corruption that looks like a data problem rather than an export bug.
 */
class CsvExportTest {

    private fun row(
        merchant: String = "食堂",
        note: String = "",
        amount: String = "12.50",
        ledger: String = "",
    ) = ExportRow(
        dateKey = "2026-09-16",
        time = "14:32",
        type = "支出",
        mainCategory = "餐饮",
        category = "午餐",
        amountYuan = amount,
        account = "支付宝",
        toAccount = "",
        merchant = merchant,
        note = note,
        excluded = "否",
        source = "手动录入",
        ledger = ledger,
    )

    @Test
    fun `plain fields are not quoted`() {
        assertEquals("食堂", CsvExport.escape("食堂"))
        assertEquals("12.50", CsvExport.escape("12.50"))
        assertEquals("", CsvExport.escape(""))
    }

    @Test
    fun `a comma forces quoting so it cannot split the row`() {
        assertEquals("\"星巴克, 国贸店\"", CsvExport.escape("星巴克, 国贸店"))
    }

    @Test
    fun `a quote is doubled and the field quoted`() {
        assertEquals("\"他说\"\"好\"\"\"", CsvExport.escape("他说\"好\""))
    }

    @Test
    fun `newlines are quoted so a note cannot break the row`() {
        assertEquals("\"第一行\n第二行\"", CsvExport.escape("第一行\n第二行"))
    }

    /** Without a BOM, Excel on Windows renders every Chinese column as mojibake. */
    @Test
    fun `output starts with a byte order mark`() {
        assertTrue(CsvExport.build(listOf(row())).startsWith(CsvExport.BOM))
    }

    @Test
    fun `header is the expected column order`() {
        val lines = CsvExport.build(emptyList()).removePrefix(CsvExport.BOM).trim().split("\r\n")
        assertEquals(1, lines.size)
        assertEquals(CsvExport.HEADERS.joinToString(","), lines.first())
    }

    @Test
    fun `every row contributes exactly one line`() {
        val csv = CsvExport.build(listOf(row(), row(), row()))
        val lines = csv.removePrefix(CsvExport.BOM).trim().split("\r\n")
        assertEquals(1 + 3, lines.size)
    }

    @Test
    fun `a comma inside a field keeps the column count stable`() {
        val csv = CsvExport.build(listOf(row(merchant = "星巴克, 国贸店", note = "拿铁, 大杯")))
        val bodyLine = csv.removePrefix(CsvExport.BOM).trim().split("\r\n")[1]

        // Naive splitting would see extra commas; a real parser must not.
        assertEquals(CsvExport.HEADERS.size, splitCsvLine(bodyLine).size)
    }

    @Test
    fun `a quoted field survives a parse round trip`() {
        val original = "他说\"好\", 然后就走了"
        val csv = CsvExport.build(listOf(row(note = original)))
        val bodyLine = csv.removePrefix(CsvExport.BOM).trim().split("\r\n")[1]
        val fields = splitCsvLine(bodyLine)
        assertEquals(original, fields[9])   // 备注 column
    }

    @Test
    fun `file names drop characters a filesystem would reject`() {
        val name = CsvExport.fileName("随心记账", "我的/账本:2026", "2026-09-16")
        assertFalse(name.contains("/"))
        assertFalse(name.contains(":"))
        assertTrue(name.endsWith(".csv"))
    }

    /**
     * A single-ledger export keeps the layout it has always had.
     *
     * The import side matches columns by header name, and existing spreadsheets have these
     * twelve columns; adding a 账本 column to every file would be a silent change to both.
     */
    @Test
    fun `one ledger means no ledger column`() {
        val lines = CsvExport.build(listOf(row())).removePrefix(CsvExport.BOM).trim().split("\r\n")
        assertEquals(CsvExport.HEADERS.joinToString(","), lines.first())
        assertEquals(CsvExport.HEADERS.size, splitCsvLine(lines[1]).size)
    }

    /** Two books in one file are indistinguishable without saying which row is whose. */
    @Test
    fun `several ledgers add a ledger column, first`() {
        val csv = CsvExport.build(listOf(row(ledger = "我的账本"), row(ledger = "游戏")))
        val lines = csv.removePrefix(CsvExport.BOM).trim().split("\r\n")
        assertEquals(
            (listOf(CsvExport.LEDGER_HEADER) + CsvExport.HEADERS).joinToString(","),
            lines.first(),
        )
        val first = splitCsvLine(lines[1])
        assertEquals(CsvExport.HEADERS.size + 1, first.size)
        assertEquals("我的账本", first[0])
        assertEquals("食堂", first[1 + 8])   // 交易对象 moved one column right
        assertEquals("游戏", splitCsvLine(lines[2])[0])
    }

    @Test
    fun `a row with no ledger name does not add the column on its own`() {
        // Deciding the shape from the rows means a mixed list cannot half-add a column.
        val csv = CsvExport.build(listOf(row(ledger = "我的账本"), row()))
        assertTrue(csv.removePrefix(CsvExport.BOM).startsWith(CsvExport.LEDGER_HEADER))
    }

    @Test
    fun `an empty ledger name still yields a usable file name`() {
        assertEquals("随心记账-账本-20260916.csv", CsvExport.fileName("随心记账", "", "2026-09-16"))
    }

    /** Minimal RFC 4180 reader, used only to prove the writer is reversible. */
    private fun splitCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes && char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }

                else -> current.append(char)
            }
            index++
        }
        fields += current.toString()
        return fields
    }
}
