package com.pocketledger.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats cents with grouping and two decimals`() {
        assertEquals("0.00", Money.format(0))
        assertEquals("0.05", Money.format(5))
        assertEquals("0.50", Money.format(50))
        assertEquals("1.00", Money.format(100))
        assertEquals("12.34", Money.format(1234))
        assertEquals("1,240.50", Money.format(124050))
        assertEquals("1,000,000.00", Money.format(100_000_000))
    }

    @Test
    fun `formats negatives with a leading sign`() {
        assertEquals("-0.50", Money.format(-50))
        assertEquals("-1,240.50", Money.format(-124050))
    }

    @Test
    fun `parses keypad input`() {
        assertEquals(0L, Money.parseYuanToCents("0"))
        assertEquals(1200L, Money.parseYuanToCents("12"))
        assertEquals(1200L, Money.parseYuanToCents("12."))
        assertEquals(1230L, Money.parseYuanToCents("12.3"))
        assertEquals(1234L, Money.parseYuanToCents("12.34"))
        assertEquals(50L, Money.parseYuanToCents(".5"))
    }

    @Test
    fun `parses pasted amounts with symbol and separators`() {
        assertEquals(124050L, Money.parseYuanToCents("¥1,240.50"))
        assertEquals(100000000L, Money.parseYuanToCents("1,000,000"))
        assertEquals(1234L, Money.parseYuanToCents(" 12.34 "))
    }

    @Test
    fun `rejects malformed input so save stays disabled`() {
        assertNull(Money.parseYuanToCents(""))
        assertNull(Money.parseYuanToCents("."))
        assertNull(Money.parseYuanToCents("1.2.3"))
        assertNull(Money.parseYuanToCents("12.345"))
        assertNull(Money.parseYuanToCents("abc"))
        assertNull(Money.parseYuanToCents("1a"))
        assertNull(Money.parseYuanToCents("-5"))
    }

    /**
     * The property that matters: nothing may be lost or invented by a round trip
     * through display formatting. A one-fen drift here would silently corrupt
     * every total in the app.
     */
    @Test
    fun `format and parse round trip is exact`() {
        val samples = listOf(0L, 1L, 9L, 99L, 100L, 101L, 124050L, 999_999_999L, 100_000_000L)
        for (cents in samples) {
            assertEquals(cents, Money.parseYuanToCents(Money.format(cents)))
        }
    }

    @Test
    fun `compact form drops a trailing zero fraction`() {
        assertEquals("1,240", Money.formatCompact(124000))
        assertEquals("1,240.50", Money.formatCompact(124050))
    }

    @Test
    fun `signed display marks direction`() {
        assertEquals("-¥128.00", Money.formatSigned(12800, negative = true))
        assertEquals("+¥2,500.00", Money.formatSigned(250000, negative = false))
        assertEquals("+¥128.00", Money.formatSigned(-12800, negative = false))
    }

    /**
     * Calendar cells show the real figure below a yuan.
     *
     * They used to print "<1" for anything under ¥1, which made a ¥0.08 red packet and
     * a ¥0.90 one read identically -- and the point of the cell is to say which days had
     * money move, not merely that some did.
     */
    @Test
    fun `tiny form keeps the real figure below one yuan`() {
        assertEquals("0.08", Money.formatTiny(8))
        assertEquals("0.50", Money.formatTiny(50))
        assertEquals("0.99", Money.formatTiny(99))
    }

    @Test
    fun `tiny form still rounds to whole yuan from one yuan up`() {
        assertEquals("1", Money.formatTiny(100))
        assertEquals("12", Money.formatTiny(1299))
        assertEquals("0", Money.formatTiny(0))
    }

    @Test
    fun `tiny form switches to wan for five figures`() {
        assertEquals("1万", Money.formatTiny(1_000_000))
        assertEquals("1.2万", Money.formatTiny(1_200_000))
    }
}
