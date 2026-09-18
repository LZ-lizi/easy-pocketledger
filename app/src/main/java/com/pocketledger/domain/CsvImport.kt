package com.pocketledger.domain

import com.pocketledger.data.entity.TxnType
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime

/**
 * Which app produced a bill file, decided from the file's own content.
 *
 * Each exporter lays its columns out differently and wraps them in a different
 * preamble, so "which app wrote this" is the first thing the importer has to answer --
 * there is no single CSV dialect for Chinese payment bills.
 */
enum class ImportFormat(val label: String) {
    WECHAT("微信支付账单"),
    ALIPAY("支付宝账单"),
    LEDGER("本应用导出的文件"),
    GENERIC("通用 CSV"),
}

/**
 * One row of a bill, normalised.
 *
 * Amounts are always positive with the direction in [type], matching how the ledger
 * stores them, and both a real timestamp and its `YYYY-MM-DD` key are derived so the
 * importer never has to re-parse the original text.
 */
data class ImportRow(
    val dateKey: String,
    val time: LocalTime?,
    val type: TxnType,
    val amountCents: Long,
    val merchant: String?,
    val note: String?,
    val method: String?,
    val status: String?,
    val externalNo: String?,
    /**
     * True when [type] was inferred rather than read.
     *
     * Shown in the review list so a file whose convention is the opposite of what was
     * assumed is one tap from correct, instead of being silently wrong for every row.
     */
    val typeInferred: Boolean,
    /** Row position inside the file, so a bad row can be pointed at. */
    val lineNumber: Int,
) {
    /**
     * Fingerprint used to refuse a second import of the same row.
     *
     * Bills that carry an order number use that instead; this is the fallback for the
     * ones that do not (a cash payment typed into a spreadsheet, for instance).
     */
    val dedupeHash: String get() = CsvImport.dedupeHash(this)

    /** Everything the category matcher is allowed to look at. */
    val matchText: String get() = listOfNotNull(merchant, note).joinToString(" ")

    /**
     * The same row in the other direction.
     *
     * Used when the importer had to infer the direction and got it wrong. The result is
     * no longer "inferred" -- the user has now said what it is.
     */
    fun flipped(): ImportRow = copy(
        type = if (type == TxnType.INCOME) TxnType.EXPENSE else TxnType.INCOME,
        typeInferred = false,
    )
}

/** The outcome of reading one file: what could be imported, and what could not. */
data class ImportPreview(
    val format: ImportFormat,
    val rows: List<ImportRow>,
    /** Why rows were dropped, deduplicated and capped for display. */
    val skippedReasons: List<String>,
    val skippedCount: Int,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    /** How many rows carry an inferred direction. */
    val inferredCount: Int get() = rows.count { it.typeInferred }
}

/**
 * Reads WeChat, Alipay and generic bill files, as CSV or as .xlsx.
 *
 * Deliberately pure and free of Android types: the whole point of an importer is that
 * it is fed real files full of real edge cases, and this way those edge cases are
 * unit-testable instead of only reproducible by picking a file on a phone.
 *
 * Several things about these files drive the design:
 *
 * - **Encoding is not uniform.** WeChat exports UTF-8; Alipay exports GBK. Decoding
 *   GBK bytes as UTF-8 does not always throw, so the check is on the decoded text.
 * - **The header is not the first line.** Both apps write several lines of account
 *   metadata first, so the header row has to be found rather than assumed.
 * - **Columns are renamed between releases.** Alipay's 商品 became 商品说明, WeChat's
 *   当前状态 became 交易状态 in places, so columns are matched by a list of accepted
 *   names plus a substring fallback rather than by position.
 * - **The same bill has two shapes.** WeChat can export the same statement as CSV or as
 *   .xlsx; both are read into the same table and then through the same column rules, so
 *   the two cannot disagree about what a column means.
 */
object CsvImport {

    /** Rows that do not describe a movement of money and are therefore dropped. */
    private val NON_TRANSACTION_TYPES = setOf(
        "不计收支", "其他", "转账", "/", "-", "neutral", "不计入收支",
    )

    private val EXPENSE_WORDS = setOf("支出", "支", "付款", "expense")
    private val INCOME_WORDS = setOf("收入", "收", "收款", "income")

    /**
     * Statuses that mean the money did not finally move.
     *
     * Refunds matter most: WeChat still lists a fully refunded payment as 支出 with
     * 当前状态 `已全额退款`, and importing it would overstate the month. A partial refund
     * cannot be netted out from the bill alone, so the row is left out entirely rather
     * than guessed at -- the user can add it by hand if they want it counted.
     */
    private val DEAD_STATUSES = listOf("交易关闭", "已关闭", "已撤销", "退款")

    /**
     * Decodes bill bytes.
     *
     * The BOM is stripped here as well as tolerated later: one of these three encodings
     * always leaves it glued to the first header cell, which would otherwise make the
     * first column unmatchable.
     */
    fun decode(bytes: ByteArray): String {
        val asUtf8 = String(bytes, Charsets.UTF_8)
        val text = if (asUtf8.contains('\uFFFD')) String(bytes, charset("GBK")) else asUtf8
        return text.removePrefix("\uFEFF")
    }

    /**
     * Reads a picked file of either supported shape.
     *
     * The zip signature decides, not the extension: Android's picker frequently reports
     * an .xlsx as `application/octet-stream` with a mangled name.
     */
    fun parseFile(fileName: String, bytes: ByteArray): ImportPreview {
        if (bytes.isEmpty()) {
            return ImportPreview(ImportFormat.GENERIC, emptyList(), listOf("文件是空的或读不出来"), 0)
        }
        if (XlsxReader.looksLikeZip(bytes)) {
            val table = XlsxReader.read(bytes)
            if (table.isNotEmpty() && table.any { isHeaderRow(it) }) return parseTable(table)
            // A zip that is not a workbook (or a workbook we could not open) still
            // deserves one attempt as text before being written off.
        }
        return parse(decode(bytes))
    }

    /**
     * Splits CSV text into rows of fields.
     *
     * A real reader rather than `split(",")`: merchant names such as `星巴克, 国贸店`
     * are routinely quoted, and a naive split would shift every column after them --
     * which silently corrupts the amounts, the worst possible failure for a ledger.
     * Blank lines are kept so line numbers keep matching the file.
     */
    fun readTable(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun endField() {
            fields.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(fields)
            fields = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                inQuotes -> when {
                    char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }

                    char == '"' -> inQuotes = false
                    else -> field.append(char)
                }

                char == '"' -> inQuotes = true
                char == ',' -> endField()
                char == '\r' -> {
                    // Swallow CRLF as one break; a lone CR still ends the row.
                    endRow()
                    if (index + 1 < text.length && text[index + 1] == '\n') index++
                }

                char == '\n' -> endRow()
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRow()
        return rows
    }

    /** Reads CSV text into importable rows. */
    fun parse(text: String): ImportPreview = parseTable(readTable(decodeBom(text)))

    /** Guesses the exporter from its preamble, which is more reliable than the headers. */
    fun detect(rows: List<List<String>>): ImportFormat {
        val preamble = rows.take(HEADER_SEARCH_LIMIT).joinToString(" ") { it.joinToString(" ") }
        if (preamble.contains("微信支付账单") || preamble.contains("微信昵称")) return ImportFormat.WECHAT
        // The full literal, not just "支付宝": this app's own export can legitimately
        // contain an account called 支付宝余额, and matching that would misread it.
        if (preamble.contains("支付宝交易记录明细")) return ImportFormat.ALIPAY

        val header = rows.firstOrNull { isHeaderRow(it) }?.map(::normalise) ?: return ImportFormat.GENERIC
        return when {
            header.contains("收/支") && header.any { it.contains("交易对方") } &&
                header.any { it.contains("商户单号") || it == "当前状态" } -> ImportFormat.WECHAT

            header.contains("收/支") && header.any { it.contains("商品说明") || it.contains("交易分类") } ->
                ImportFormat.ALIPAY

            header.containsAll(listOf("日期", "时间", "类型", "金额")) -> ImportFormat.LEDGER
            else -> ImportFormat.GENERIC
        }
    }

    /**
     * Reads an already-tabulated file into importable rows.
     *
     * Shared by both file shapes, so a column can only ever mean one thing.
     */
    fun parseTable(table: List<List<String>>, format: ImportFormat? = null): ImportPreview {
        val resolvedFormat = format ?: detect(table)
        val headerIndex = table.indexOfFirst { isHeaderRow(it) }
        if (headerIndex < 0) {
            return ImportPreview(resolvedFormat, emptyList(), listOf("没有找到表头行"), 0)
        }

        val columns = Columns(table[headerIndex])
        val hasDirectionColumn = columns.has(TYPE_NAMES)
        // A file with no 收/支 column falls back to the sign of the amount, but only if
        // the file actually uses signs -- otherwise a plain list of positive amounts is
        // read as all-expense, which is what a hand-written spending sheet means.
        val amountIndex = columns.indexOf(AMOUNT_NAMES)
        val signedAmounts = !hasDirectionColumn && table
            .drop(headerIndex + 1)
            .any { row -> row.getOrNull(amountIndex)?.trim()?.startsWith('-') == true }

        val rows = mutableListOf<ImportRow>()
        val reasons = mutableListOf<String>()
        var skipped = 0

        fun drop(reason: String) {
            skipped++
            if (reasons.size < MAX_REASONS && reason !in reasons) reasons.add(reason)
        }

        for (line in headerIndex + 1 until table.size) {
            val fields = table[line]
            if (fields.all { it.isBlank() }) continue

            val typeText = columns.text(fields, TYPE_NAMES)
            val normalisedType = normalise(typeText)
            if (hasDirectionColumn && normalisedType in NON_TRANSACTION_TYPES) {
                drop("「${typeText.ifBlank { "空" }}」不是收支记录")
                continue
            }

            val amountText = columns.text(fields, AMOUNT_NAMES)
            val negative = amountText.trim().let { it.startsWith('-') || it.startsWith('−') }
            val amount = Money.parseYuanToCents(cleanAmount(amountText))
            if (amount == null || amount == 0L) {
                drop("金额无法识别：${amountText.ifBlank { "空" }}")
                continue
            }

            val status = columns.text(fields, STATUS_NAMES).ifBlank { null }
            if (status != null && DEAD_STATUSES.any { status.contains(it) }) {
                drop("已失效的交易：$status")
                continue
            }

            val (dateKey, serialTime) = resolveDate(
                stamp = columns.text(fields, DATETIME_NAMES),
                dateColumn = columns.text(fields, DATE_NAMES),
            )
            if (dateKey == null) {
                drop("日期无法识别：${columns.text(fields, DATETIME_NAMES).ifBlank { "空" }}")
                continue
            }

            val known = normalisedType in EXPENSE_WORDS || normalisedType in INCOME_WORDS
            // A direction column that says something we cannot read is a row worth
            // reporting, not one worth guessing at.
            if (hasDirectionColumn && !known && normalisedType.isNotEmpty()) {
                drop("无法判断收支方向：$typeText")
                continue
            }
            val type = when {
                normalisedType in INCOME_WORDS -> TxnType.INCOME
                normalisedType in EXPENSE_WORDS -> TxnType.EXPENSE
                // A direction column that is blank on this row: bills do this for the odd
                // 其他 row, and spending is the safer reading.
                hasDirectionColumn -> TxnType.EXPENSE
                signedAmounts -> if (negative) TxnType.EXPENSE else TxnType.INCOME
                else -> TxnType.EXPENSE
            }

            rows.add(
                ImportRow(
                    dateKey = dateKey,
                    time = serialTime
                        ?: parseTime(columns.text(fields, DATETIME_NAMES))
                        ?: parseTime(columns.text(fields, TIME_NAMES)),
                    type = type,
                    amountCents = amount,
                    merchant = columns.text(fields, MERCHANT_NAMES).ifBlank { null },
                    note = mergeNote(
                        description = columns.text(fields, DESCRIPTION_NAMES),
                        remark = columns.text(fields, REMARK_NAMES),
                    ),
                    method = columns.text(fields, METHOD_NAMES).ifBlank { null },
                    status = status,
                    externalNo = columns.text(fields, EXTERNAL_NO_NAMES).ifBlank { null },
                    typeInferred = !known,
                    lineNumber = line + 1,
                )
            )
        }

        return ImportPreview(resolvedFormat, rows, reasons, skipped)
    }

    /**
     * Stable fingerprint of a row.
     *
     * Includes the time when the file provides one, because two identical 12.00
     * coffees on the same day are two real transactions and collapsing them would lose
     * money. SHA-1 rather than a plain concatenation: notes can contain the separator,
     * and a fingerprint that can be forged by content would skip real rows.
     */
    fun dedupeHash(row: ImportRow): String {
        val material = listOf(
            row.dateKey,
            row.time?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty(),
            row.type.name,
            row.amountCents.toString(),
            row.merchant.orEmpty(),
            row.note.orEmpty(),
        ).joinToString("\u0001")
        val digest = MessageDigest.getInstance("SHA-1").digest(material.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------------ internals

    private const val HEADER_SEARCH_LIMIT = 30
    private const val MAX_REASONS = 4

    private val TYPE_NAMES = listOf("收/支", "收支", "类型", "交易类型", "资金方向")
    private val AMOUNT_NAMES = listOf("金额(元)", "金额（元）", "金额", "交易金额")
    private val DATETIME_NAMES = listOf("交易时间", "交易创建时间", "付款时间", "时间")
    private val DATE_NAMES = listOf("日期", "交易日期", "记账日期")
    private val TIME_NAMES = listOf("时间", "交易时间")
    // 交易对象 is this app's own wording; 商家 and 对方 are what other files call the
    // same thing, and previously exported files keep importing.
    private val MERCHANT_NAMES = listOf("交易对方", "交易对象", "商家", "对方", "商户名称")
    private val DESCRIPTION_NAMES = listOf("商品说明", "商品", "交易说明", "摘要", "说明")
    private val REMARK_NAMES = listOf("备注", "交易备注")
    private val METHOD_NAMES = listOf("支付方式", "收/付款方式", "付款方式", "账户", "收付款方式")
    private val STATUS_NAMES = listOf("当前状态", "交易状态", "状态")
    private val EXTERNAL_NO_NAMES = listOf("交易单号", "交易订单号", "商户单号", "商家订单号", "订单号")

    /** Marks the boundary between preamble and data. */
    private val HEADER_MARKERS = listOf(
        "交易时间", "收/支", "金额", "交易对方", "商品", "日期", "类型", "分类",
    )

    private fun decodeBom(text: String): String = text.removePrefix("\uFEFF")

    private fun isHeaderRow(row: List<String>): Boolean {
        if (row.size < 3) return false
        val hits = row.count { field -> HEADER_MARKERS.any { field.contains(it) } }
        return hits >= 2
    }

    /** Case-, space- and full-width-insensitive comparison for header cells. */
    private fun normalise(value: String): String = value
        .trim()
        .replace("（", "(")
        .replace("）", ")")
        .replace(" ", "")
        .replace("\u3000", "")
        .lowercase()

    /** Resolves a value by trying exact header names first, then a substring match. */
    private class Columns(private val header: List<String>) {

        private val normalisedHeader = header.map(::normalise)

        fun has(names: List<String>): Boolean = indexOf(names) >= 0

        fun indexOf(names: List<String>): Int {
            names.forEach { name ->
                val target = normalise(name)
                val exact = normalisedHeader.indexOf(target)
                if (exact >= 0) return exact
            }
            // Release-to-release renames: 商品 -> 商品说明, 订单号 -> 交易订单号.
            names.forEach { name ->
                val target = normalise(name)
                val partial = normalisedHeader.indexOfFirst {
                    it.isNotEmpty() && (it.contains(target) || target.contains(it))
                }
                if (partial >= 0) return partial
            }
            return -1
        }

        fun text(fields: List<String>, names: List<String>): String {
            val index = indexOf(names)
            return fields.getOrNull(index)?.trim().orEmpty()
        }
    }

    /**
     * The note stored on the transaction.
     *
     * 商品 carries what was bought and 备注 carries whatever the user added at the till;
     * WeChat and Alipay write them into separate columns, and merging them here is what
     * keeps a row's meaning intact -- the same bill exported as CSV or as .xlsx must
     * produce the same note, or the two files would import as two different
     * transactions and the deduplication would not catch it.
     */
    private fun mergeNote(description: String, remark: String): String? = listOf(description, remark)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" ")
        .ifBlank { null }

    /** `¥1,240.50`, `1240.50元` and `-50.00` all have to become a plain decimal. */
    private fun cleanAmount(value: String): String = value
        .replace("¥", "")
        .replace("￥", "")
        .replace(",", "")
        .replace("元", "")
        .replace("+", "")
        .replace("-", "")
        .replace("−", "")
        .trim()

    /**
     * Finds the date (and any time carried inside it).
     *
     * Handles the three shapes these files use: a combined `2026-09-16 12:34:56`, a date
     * on its own, and — from a spreadsheet cell — an Excel serial number, whose fraction
     * is the time of day.
     */
    private fun resolveDate(stamp: String, dateColumn: String): Pair<String?, LocalTime?> {
        val text = stamp.trim()
        if (text.isNotEmpty()) {
            if (text.contains(' ')) {
                parseDateKey(text.substringBefore(' '))?.let { return it to parseTime(text) }
            }
            text.toDoubleOrNull()?.let { serial ->
                excelDate(serial)?.let { return it.toString() to excelTime(serial) }
            }
            parseDateKey(text)?.let { return it to null }
        }
        return parseDateKey(dateColumn) to null
    }

    /** Accepts `2026-09-01`, `2026/9/1` and `20260901`. */
    private fun parseDateKey(raw: String): String? {
        val text = raw.trim().replace("/", "-").replace(".", "-")
        if (text.isBlank()) return null
        if (text.length == 8 && text.all(Char::isDigit)) {
            return runCatching {
                LocalDate.of(
                    text.substring(0, 4).toInt(),
                    text.substring(4, 6).toInt(),
                    text.substring(6, 8).toInt(),
                ).toString()
            }.getOrNull()
        }
        return runCatching { LocalDate.parse(text) }.getOrNull()?.toString()
    }

    /** Accepts `12:34:56`, `12:34` and `2026-09-01 12:34`. */
    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim()
        if (text.isBlank()) return null
        val clock = text.substringAfter(' ', text).trim()
        if (!clock.contains(':')) return null
        val parts = clock.split(':')
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /**
     * Excel's 1900 date system.
     *
     * Day 60 is the 29th of February 1900, a date that does not exist -- Excel kept the
     * bug for Lotus compatibility, so everything from day 61 on is off by one and the
     * epoch differs either side of it. Only cells that plausibly *are* dates are
     * converted; anything outside the range is left alone for the text parser to reject.
     */
    private fun excelDate(serial: Double): LocalDate? {
        if (serial < 1.0 || serial > 2_958_465.0) return null
        val days = serial.toLong()
        val epoch = if (days >= 61) LocalDate.of(1899, 12, 30) else LocalDate.of(1899, 12, 31)
        return runCatching { epoch.plusDays(days) }.getOrNull()
    }

    /** The fractional part of a serial date is its time of day. */
    private fun excelTime(serial: Double): LocalTime? {
        val days = serial.toLong()
        val fraction = serial - days
        if (fraction <= 0.0) return null
        val minutes = Math.round(fraction * 24 * 60).toInt().mod(24 * 60)
        return LocalTime.of(minutes / 60, minutes % 60)
    }
}
