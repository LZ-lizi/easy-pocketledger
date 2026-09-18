package com.pocketledger.domain

/**
 * One transaction flattened for export.
 *
 * Deliberately plain strings rather than the entity: an export is read by a
 * spreadsheet, so the shape here is the spreadsheet's, not the database's.
 */
data class ExportRow(
    val dateKey: String,
    val time: String,
    val type: String,
    val mainCategory: String,
    val category: String,
    val amountYuan: String,
    val account: String,
    val toAccount: String,
    val merchant: String,
    val note: String,
    val excluded: String,
    val source: String,
)

/**
 * CSV writer for the ledger.
 *
 * Kept pure so the escaping rules are testable: a merchant called `星巴克, 国贸店`
 * or a note containing a quote is completely ordinary, and getting it wrong produces
 * a file that silently shifts every later column.
 */
object CsvExport {

    val HEADERS = listOf(
        "日期", "时间", "类型", "大类", "分类", "金额",
        "账户", "转入账户", "商家", "备注", "不计收支", "来源",
    )

    /**
     * A UTF-8 byte-order mark.
     *
     * Without it Excel on Windows reads the file as the local ANSI codepage and every
     * Chinese column turns to mojibake -- the single most common complaint about CSV
     * exports in this part of the world.
     */
    const val BOM = "\uFEFF"

    private const val NEWLINE = "\r\n"

    fun build(rows: List<ExportRow>): String = buildString {
        append(BOM)
        append(HEADERS.joinToString(",") { escape(it) })
        append(NEWLINE)
        rows.forEach { row ->
            append(
                listOf(
                    row.dateKey, row.time, row.type, row.mainCategory, row.category,
                    row.amountYuan, row.account, row.toAccount, row.merchant, row.note,
                    row.excluded, row.source,
                ).joinToString(",") { escape(it) }
            )
            append(NEWLINE)
        }
    }

    /**
     * Quotes a field only when it needs it.
     *
     * Quoting everything would be simpler but makes the file harder to eyeball, and
     * some importers treat an always-quoted numeric column as text.
     */
    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return '"' + value.replace("\"", "\"\"") + '"'
    }

    /** Default file name, e.g. `记账本-我的账本-20260916.csv`. */
    fun fileName(appName: String, ledgerName: String, dateKey: String): String {
        val safeLedger = ledgerName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "账本" }
        return "$appName-$safeLedger-${dateKey.replace("-", "")}.csv"
    }
}
