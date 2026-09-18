package com.pocketledger.domain

import com.pocketledger.data.entity.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * CSV bill import.
 *
 * These files come from three different exporters, are encoded two different ways, and
 * start with several lines of account metadata before the header. Everything asserted
 * here is something a real bill has actually done: a byte-order mark glued to the first
 * header cell, a quoted merchant containing a comma, an amount written as `¥1,240.50`,
 * an Alipay file in GBK.
 */
class CsvImportTest {

    /** Roughly what WeChat's 账单明细 looks like: metadata, header, then rows. */
    private val wechat = """
        微信支付账单明细
        微信昵称：[张三]
        起始时间：[2026-09-01 00:00:00] 终止时间：[2026-09-30 23:59:59]
        导出类型：[全部]
        ----------------------微信支付账单明细列表--------------------
        交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
        2026-09-16 12:34:56,商户消费,美团,外卖订单,支出,¥28.50,零钱,支付成功,4200001,merchant-1,
        2026-09-16 18:02:11,商户消费,瑞幸咖啡,生椰拿铁,支出,¥19.00,零钱,支付成功,4200002,merchant-2,
        2026-09-17 09:15:00,转账,李四,,收入,¥200.00,零钱,已存入零钱,4200003,merchant-3,还钱
        2026-09-18 20:00:00,商户消费,某某店,退款订单,支出,¥50.00,零钱,已全额退款,4200004,merchant-4,
        2026-09-19 20:00:00,零钱提现,提现,,不计收支,¥100.00,零钱,提现成功,4200005,merchant-5,
    """.trimIndent()

    /** Alipay's 交易记录明细, which is what `交易分类`/`商品说明` columns come from. */
    private val alipay = """
        支付宝交易记录明细查询
        账号：[zhangsan@example.com]
        起始日期：[2026-09-01 00:00:00]    终止日期：[2026-09-30 23:59:59]
        ---------------------------------交易记录明细列表------------------------------------
        交易时间,交易分类,交易对方,对方账号,商品说明,收/支,金额,收/付款方式,交易状态,交易订单号,商家订单号,备注
        2026-09-16 08:10:00,餐饮美食,肯德基,,KFC早餐,支出,15.00,余额宝,交易成功,20260916001,,
        2026-09-16 12:00:00,交通出行,滴滴出行,,快车,支出,"1,240.50",余额,交易成功,20260916002,,
        2026-09-16 13:00:00,其他,某某商店,,"商品, 带逗号",支出,3.00,余额,交易成功,20260916003,,
        2026-09-16 14:00:00,其他,已关闭店铺,,无效订单,支出,9.00,余额,交易关闭,20260916004,,
        2026-09-20 10:00:00,转账红包,李四,,红包,收入,66.00,余额,交易成功,20260916005,,
    """.trimIndent()

    @Test
    fun `detects wechat from its preamble rather than its headers`() {
        assertEquals(ImportFormat.WECHAT, CsvImport.parse(wechat).format)
    }

    @Test
    fun `detects alipay from its preamble`() {
        assertEquals(ImportFormat.ALIPAY, CsvImport.parse(alipay).format)
    }

    @Test
    fun `reads wechat rows with their times and amounts`() {
        val preview = CsvImport.parse(wechat)

        // 已全额退款 and 不计收支 are not movements of money, so five rows yield three.
        assertEquals(3, preview.rows.size)
        val first = preview.rows.first()
        assertEquals("2026-09-16", first.dateKey)
        assertEquals(LocalTime.of(12, 34), first.time)
        assertEquals(TxnType.EXPENSE, first.type)
        assertEquals(2850L, first.amountCents)
        assertEquals("美团", first.merchant)
        assertEquals("外卖订单", first.note)
        assertEquals("4200001", first.externalNo)
    }

    @Test
    fun `treats 收入 as income and 支出 as expense`() {
        val rows = CsvImport.parse(wechat).rows
        assertEquals(TxnType.INCOME, rows.first { it.externalNo == "4200003" }.type)
        assertEquals(20_000L, rows.first { it.externalNo == "4200003" }.amountCents)
    }

    @Test
    fun `skips rows that do not move money and says why`() {
        val preview = CsvImport.parse(wechat)
        assertEquals(2, preview.skippedCount)
        assertTrue(preview.skippedReasons.any { it.contains("不计收支") })
        assertTrue(preview.skippedReasons.any { it.contains("退款") })
    }

    @Test
    fun `strips currency symbols and thousands separators from amounts`() {
        val rows = CsvImport.parse(alipay).rows
        val didi = rows.first { it.externalNo == "20260916002" }
        assertEquals(124_050L, didi.amountCents)
    }

    @Test
    fun `keeps a quoted field containing a comma intact`() {
        val rows = CsvImport.parse(alipay).rows
        val quoted = rows.first { it.externalNo == "20260916003" }
        assertEquals("商品, 带逗号", quoted.note)
        assertEquals(300L, quoted.amountCents)
    }

    @Test
    fun `drops closed trades`() {
        val preview = CsvImport.parse(alipay)
        assertTrue(preview.rows.none { it.externalNo == "20260916004" })
        assertTrue(preview.skippedReasons.any { it.contains("交易关闭") })
    }

    @Test
    fun `reads this app's own export back`() {
        val exported = CsvExport.BOM + CsvExport.build(
            listOf(
                ExportRow(
                    dateKey = "2026-09-16",
                    time = "14:32",
                    type = "支出",
                    mainCategory = "餐饮",
                    category = "午餐",
                    amountYuan = "12.50",
                    account = "支付宝余额",
                    toAccount = "",
                    merchant = "食堂",
                    note = "三楼",
                    excluded = "否",
                    source = "手动录入",
                )
            )
        ).removePrefix(CsvExport.BOM)

        val preview = CsvImport.parse(exported)
        assertEquals(ImportFormat.LEDGER, preview.format)
        assertEquals(1, preview.rows.size)
        val row = preview.rows.single()
        assertEquals("2026-09-16", row.dateKey)
        assertEquals(LocalTime.of(14, 32), row.time)
        assertEquals(1250L, row.amountCents)
        assertEquals("食堂", row.merchant)
    }

    @Test
    fun `decodes an Alipay file written in GBK`() {
        val bytes = alipay.toByteArray(charset("GBK"))
        val decoded = CsvImport.decode(bytes)
        assertTrue(decoded.contains("支付宝交易记录明细查询"))
        assertEquals(ImportFormat.ALIPAY, CsvImport.parse(decoded).format)
    }

    @Test
    fun `a byte order mark does not break the first header cell`() {
        val withBom = "\uFEFF交易时间,收/支,金额,交易对方,商品,当前状态,商户单号\n" +
            "2026-09-16 10:00:00,支出,¥5.00,便利店,矿泉水,支付成功,m-1\n"
        val preview = CsvImport.parse(withBom)
        assertEquals(1, preview.rows.size)
        assertEquals(500L, preview.rows.single().amountCents)
    }

    @Test
    fun `finds the header row even when metadata is longer than usual`() {
        val padded = buildString {
            repeat(12) { append("导出行 $it\n") }
            append("交易时间,收/支,金额,交易对方,商品,当前状态\n")
            append("2026-09-16 10:00:00,支出,¥5.00,便利店,矿泉水,支付成功\n")
        }
        assertEquals(1, CsvImport.parse(padded).rows.size)
    }

    @Test
    fun `reports a file with no header instead of importing nonsense`() {
        val preview = CsvImport.parse("这不是一个账单文件\n随便写点什么\n")
        assertTrue(preview.isEmpty)
        assertTrue(preview.skippedReasons.any { it.contains("表头") })
    }

    @Test
    fun `two identical purchases on the same day are two distinct rows`() {
        val hashA = CsvImport.parse(wechat).rows.first().dedupeHash
        val hashB = CsvImport.parse(wechat).rows.first().dedupeHash
        assertEquals(hashA, hashB)

        val other = CsvImport.parse(alipay).rows.first().dedupeHash
        assertNotEquals(hashA, other)
    }

    @Test
    fun `a row without a time still imports`() {
        val dated = "日期,时间,类型,大类,分类,金额,账户,备注\n" +
            "2026-09-16,,支出,餐饮,午餐,18.00,现金,食堂\n"
        val preview = CsvImport.parse(dated)
        val row = preview.rows.single()
        assertEquals("2026-09-16", row.dateKey)
        assertNull(row.time)
        assertEquals(1800L, row.amountCents)
    }

    @Test
    fun `a blank amount is skipped rather than imported as zero`() {
        val broken = "交易时间,收/支,金额,交易对方,商品,当前状态\n" +
            "2026-09-16 10:00:00,支出,,便利店,矿泉水,支付成功\n"
        val preview = CsvImport.parse(broken)
        assertTrue(preview.isEmpty)
        assertTrue(preview.skippedReasons.any { it.contains("金额") })
    }

    // ------------------------------------------------------------------ note merging

    @Test
    fun `商品 and 备注 are merged into one note`() {
        val preview = CsvImport.parse(alipay)
        val kfc = preview.rows.first { it.externalNo == "20260916001" }
        assertEquals("KFC早餐", kfc.note)

        val withRemark = "交易时间,收/支,金额,交易对方,商品,当前状态,备注\n" +
            "2026-09-16 10:00:00,支出,5.00,便利店,矿泉水,支付成功,楼下买的\n"
        assertEquals("矿泉水 楼下买的", CsvImport.parse(withRemark).rows.single().note)
    }

    @Test
    fun `an identical 商品 and 备注 is not repeated`() {
        val doubled = "交易时间,收/支,金额,交易对方,商品,当前状态,备注\n" +
            "2026-09-16 10:00:00,支出,5.00,便利店,矿泉水,支付成功,矿泉水\n"
        assertEquals("矿泉水", CsvImport.parse(doubled).rows.single().note)
    }

    @Test
    fun `the same bill as CSV and as xlsx produces the same fingerprint`() {
        val csv = "交易时间,收/支,金额(元),交易对方,商品,当前状态,商户单号,备注\n" +
            "2026-09-16 12:34:56,支出,¥28.50,美团,外卖订单,支付成功,m-1,少放辣\n"
        val sheet = listOf(
            listOf("交易时间", "收/支", "金额(元)", "交易对方", "商品", "当前状态", "商户单号", "备注"),
            listOf("2026-09-16 12:34:56", "支出", 28.5, "美团", "外卖订单", "支付成功", "m-1", "少放辣"),
        )

        val fromCsv = CsvImport.parse(csv).rows.single()
        val fromXlsx = CsvImport.parseFile("账单.xlsx", buildXlsx(sheet)).rows.single()

        assertEquals(fromCsv.note, fromXlsx.note)
        assertEquals(fromCsv.amountCents, fromXlsx.amountCents)
        assertEquals(fromCsv.dedupeHash, fromXlsx.dedupeHash)
    }

    // ------------------------------------------------------------------------ xlsx

    @Test
    fun `reads a WeChat xlsx export`() {
        val sheet = listOf(
            listOf("微信支付账单明细"),
            listOf("微信昵称：[张三]"),
            listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号", "商户单号", "备注"),
            listOf("2026-09-16 12:34:56", "商户消费", "美团", "外卖订单", "支出", 28.5, "零钱", "支付成功", "4200001", "m-1", ""),
            listOf("2026-09-17 09:15:00", "转账", "李四", "", "收入", 200.0, "零钱", "已存入零钱", "4200003", "m-3", "还钱"),
        )

        val preview = CsvImport.parseFile("微信支付账单.xlsx", buildXlsx(sheet))

        assertEquals(ImportFormat.WECHAT, preview.format)
        assertEquals(2, preview.rows.size)
        val first = preview.rows.first()
        assertEquals("2026-09-16", first.dateKey)
        assertEquals(LocalTime.of(12, 34), first.time)
        assertEquals(TxnType.EXPENSE, first.type)
        assertEquals(2850L, first.amountCents)
        assertEquals("美团", first.merchant)
        assertEquals("外卖订单", first.note)
        assertEquals(TxnType.INCOME, preview.rows[1].type)
    }

    @Test
    fun `reads inline strings as well as the shared string table`() {
        val sheet = listOf(
            listOf("交易时间", "收/支", "金额", "交易对方", "商品", "当前状态"),
            listOf("2026-09-16 10:00:00", "支出", 5.0, "便利店", "矿泉水", "支付成功"),
        )
        val preview = CsvImport.parseFile("账单.xlsx", buildXlsx(sheet, useSharedStrings = false))
        assertEquals(1, preview.rows.size)
        assertEquals("便利店", preview.rows.single().merchant)
        assertEquals(500L, preview.rows.single().amountCents)
    }

    @Test
    fun `places cells by their reference so skipped columns stay aligned`() {
        // Excel omits empty cells entirely, so a row can start at column C.
        val sheet = listOf(
            listOf("交易时间", "收/支", "金额", "交易对方", "商品", "当前状态"),
            listOf("2026-09-16 10:00:00", "支出", 5.0, "", "矿泉水", "支付成功"),
        )
        val preview = CsvImport.parseFile("账单.xlsx", buildXlsx(sheet))
        val row = preview.rows.single()
        assertNull(row.merchant)
        assertEquals("矿泉水", row.note)
        assertEquals(500L, row.amountCents)
    }

    @Test
    fun `reads a spreadsheet date serial and its time of day`() {
        // 46174 = 2026-06-01 in Excel's 1900 system (45658 is 2025-01-01); .5 is midday.
        val sheet = listOf(
            listOf("交易时间", "收/支", "金额", "交易对方", "商品", "当前状态"),
            listOf(46174.5, "支出", 12.0, "食堂", "午餐", "支付成功"),
        )
        val row = CsvImport.parseFile("账单.xlsx", buildXlsx(sheet)).rows.single()
        assertEquals("2026-06-01", row.dateKey)
        assertEquals(LocalTime.of(12, 0), row.time)
    }

    @Test
    fun `a zip that is not a workbook does not crash the importer`() {
        val notAWorkbook = ByteArray(64).also {
            it[0] = 0x50
            it[1] = 0x4B
        }
        val preview = CsvImport.parseFile("something.zip", notAWorkbook)
        assertTrue(preview.isEmpty)
    }

    // --------------------------------------------------- direction is never lost

    @Test
    fun `an income row keeps its direction through an xlsx round trip`() {
        val sheet = listOf(
            listOf("交易时间", "收/支", "金额", "交易对方", "商品", "当前状态"),
            listOf("2026-09-16 10:00:00", "收入", 200.0, "李四", "转账", "已收钱"),
        )
        val preview = CsvImport.parseFile("账单.xlsx", buildXlsx(sheet))
        assertEquals(TxnType.INCOME, preview.rows.single().type)
        assertEquals(0, preview.inferredCount)
    }

    @Test
    fun `a signed amount decides the direction when there is no 收 支 column`() {
        val bank = "日期,金额,摘要,交易对象\n" +
            "2026-09-16,-28.50,外卖,美团\n" +
            "2026-09-17,200.00,转入,李四\n"
        val preview = CsvImport.parse(bank)
        assertEquals(2, preview.rows.size)
        assertEquals(TxnType.EXPENSE, preview.rows[0].type)
        assertEquals(TxnType.INCOME, preview.rows[1].type)
        assertEquals(2850L, preview.rows[0].amountCents)
        assertEquals(2, preview.inferredCount)
    }

    @Test
    fun `an unsigned file with no 收 支 column is read as spending`() {
        val sheet = "日期,金额,摘要,交易对象\n" +
            "2026-09-16,28.50,外卖,美团\n" +
            "2026-09-17,15.00,午餐,食堂\n"
        val preview = CsvImport.parse(sheet)
        assertTrue(preview.rows.all { it.type == TxnType.EXPENSE })
        assertTrue(preview.rows.all { it.typeInferred })
    }

    @Test
    fun `a filled 收 支 column is never marked as inferred`() {
        assertTrue(CsvImport.parse(wechat).rows.none { it.typeInferred })
        assertTrue(CsvImport.parse(alipay).rows.none { it.typeInferred })
    }

    @Test
    fun `flipping a row changes only its direction`() {
        val row = CsvImport.parse(alipay).rows.first()
        val flipped = row.flipped()
        assertEquals(TxnType.EXPENSE, row.type)
        assertEquals(TxnType.INCOME, flipped.type)
        assertEquals(row.amountCents, flipped.amountCents)
        assertEquals(row.dateKey, flipped.dateKey)
        assertFalse(flipped.typeInferred)
        assertNotEquals(row.dedupeHash, flipped.dedupeHash)
    }

    @Test
    fun `this app's own export header is read as the counterparty`() {
        val exported = CsvExport.build(
            listOf(
                ExportRow(
                    dateKey = "2026-09-16",
                    time = "14:32",
                    type = "收入",
                    mainCategory = "",
                    category = "生活费",
                    amountYuan = "2000.00",
                    account = "储蓄卡",
                    toAccount = "",
                    merchant = "妈妈",
                    note = "九月",
                    excluded = "否",
                    source = "手动录入",
                )
            )
        ).removePrefix(CsvExport.BOM)

        val row = CsvImport.parse(exported).rows.single()
        assertEquals("妈妈", row.merchant)
        assertEquals("九月", row.note)
        assertEquals(TxnType.INCOME, row.type)
    }

    // ------------------------------------------------------------------- helpers

    /** Excel's column letters: 0 -> A, 26 -> AA. */
    private fun columnName(index: Int): String {
        var remaining = index
        val name = StringBuilder()
        while (true) {
            name.insert(0, ('A' + remaining % 26))
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return name.toString()
    }

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * Builds the smallest workbook the reader will accept.
     *
     * Written by hand rather than with a library because the point of the test is to
     * pin the reader to the real file format -- Excel writes strings through a shared
     * table, so that path has to be exercised, and inline strings have to be too
     * because not every exporter uses the table.
     */
    private fun buildXlsx(rows: List<List<Any?>>, useSharedStrings: Boolean = true): ByteArray {
        val shared = mutableListOf<String>()
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("<sheetData>")
            rows.forEachIndexed { rowIndex, row ->
                append("""<row r="${rowIndex + 1}">""")
                row.forEachIndexed { columnIndex, value ->
                    if (value == null) return@forEachIndexed
                    val reference = "${columnName(columnIndex)}${rowIndex + 1}"
                    when (value) {
                        is Number -> append("""<c r="$reference"><v>$value</v></c>""")
                        else -> {
                            val text = escapeXml(value.toString())
                            if (useSharedStrings) {
                                val id = shared.size
                                shared.add(text)
                                append("""<c r="$reference" t="s"><v>$id</v></c>""")
                            } else {
                                append("""<c r="$reference" t="inlineStr"><is><t>$text</t></is></c>""")
                            }
                        }
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zip.write(sheet.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            if (useSharedStrings) {
                val table = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                    shared.forEach { append("<si><t>${it}</t></si>") }
                    append("</sst>")
                }
                zip.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
                zip.write(table.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
