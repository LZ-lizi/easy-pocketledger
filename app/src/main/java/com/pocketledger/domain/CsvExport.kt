package com.pocketledger.domain

/**
 * One transaction flattened for export.
 *
 * Deliberately plain strings rather than the entity: an export is read by a
 * spreadsheet, so the shape here is the spreadsheet's, not the database's.
 *
 * [ledger] is only filled in when the export covers more than one ledger; a single-ledger
 * file keeps the column layout it has always had, so old spreadsheets and the import side
 * are unaffected.
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
    val ledger: String = "",
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
        "账户", "转入账户", "交易对象", "备注", "不计收支", "来源",
    )

    /** Prepended when the file covers several ledgers, so each row can be attributed. */
    const val LEDGER_HEADER = "账本"

    /**
     * A UTF-8 byte-order mark.
     *
     * Without it Excel on Windows reads the file as the local ANSI codepage and every
     * Chinese column turns to mojibake -- the single most common complaint about CSV
     * exports in this part of the world.
     */
    const val BOM = "\uFEFF"

    private const val NEWLINE = "\r\n"

    /**
     * Builds the file.
     *
     * The 账本 column appears only when at least one row carries a ledger name -- i.e. when
     * the user exported more than one ledger. Deciding it from the rows rather than a
     * separate flag means the two can never disagree.
     */
    fun build(rows: List<ExportRow>): String {
        val withLedger = rows.any { it.ledger.isNotBlank() }
        return buildString {
            append(BOM)
            val headers = if (withLedger) listOf(LEDGER_HEADER) + HEADERS else HEADERS
            append(headers.joinToString(",") { escape(it) })
            append(NEWLINE)
            rows.forEach { row ->
                val fields = listOf(
                    row.dateKey, row.time, row.type, row.mainCategory, row.category,
                    row.amountYuan, row.account, row.toAccount, row.merchant, row.note,
                    row.excluded, row.source,
                )
                val line = if (withLedger) listOf(row.ledger) + fields else fields
                append(line.joinToString(",") { escape(it) })
                append(NEWLINE)
            }
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

    /** Default file name, e.g. `随心记账-我的账本-20260916.csv`. */
    fun fileName(appName: String, ledgerName: String, dateKey: String): String {
        val safeLedger = ledgerName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "账本" }
        return "$appName-$safeLedger-${dateKey.replace("-", "")}.csv"
    }}
