package com.pocketledger.domain

/**
 * Money helpers.
 *
 * Every amount in the app is a `Long` count of **cents**. Nothing here converts to
 * or from a floating-point type: a `Double` yuan value cannot represent 0.1
 * exactly, and a ledger that drifts by a fen is a ledger nobody trusts.
 */
object Money {

    /** `124050` -> `"1,240.50"`, `-50` -> `"-0.50"`. */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = if (cents == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(cents)
        val yuan = abs / 100
        val fen = abs % 100
        val grouped = yuan.toString()
            .reversed()
            .chunked(3)
            .joinToString(",")
            .reversed()
        return buildString {
            if (negative) append('-')
            append(grouped)
            append('.')
            append(fen.toString().padStart(2, '0'))
        }
    }

    /** `124050` -> `"¥1,240.50"`. */
    fun formatWithSymbol(cents: Long): String = "¥" + format(cents)

    /** Signed display for a ledger row: expenses get a leading minus. */
    fun formatSigned(cents: Long, negative: Boolean): String =
        (if (negative) "-¥" else "+¥") + format(if (cents < 0) -cents else cents)

    /** Drops the decimals when an amount is a whole yuan, for tight layouts. */
    fun formatCompact(cents: Long): String =
        if (cents % 100 == 0L) format(cents).removeSuffix(".00") else format(cents)

    /**
     * Shortest honest form of an amount, for calendar cells roughly 40dp wide.
     *
     * Yuan only, because a fen is unreadable at that size and the goal is telling a
     * 8 元 day from an 80 元 one, not exact bookkeeping -- tapping the day shows the
     * real figures. Ten thousand yuan and up switch to 万 so the text can never
     * overflow the cell.
     */
    fun formatTiny(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        if (abs == 0L) return "0"
        val yuan = abs / 100
        return when {
            yuan == 0L -> "<1"
            yuan < 10_000L -> yuan.toString()
            else -> {
                val tenths = yuan / 1_000L          // tenths of 万
                val prefix = if (cents < 0) "-" else ""
                if (tenths % 10L == 0L) "$prefix${tenths / 10}万" else "$prefix${tenths / 10}.${tenths % 10}万"
            }
        }
    }

    /**
     * Parses keypad input such as `"12"`, `"12."`, `"12.3"`, `"12.34"`.
     *
     * Returns null for anything that is not a well-formed amount, so callers can
     * keep the Save button disabled rather than storing a guess.
     */
    fun parseYuanToCents(input: String): Long? {
        // Tolerate pasted amounts: "¥1,240.50" must parse exactly like "1240.50".
        val text = input.trim().removePrefix("¥").replace(",", "").trim()
        if (text.isEmpty() || text == ".") return null
        val parts = text.split('.')
        if (parts.size > 2) return null
        val yuanPart = parts[0].ifEmpty { "0" }
        val fenPart = if (parts.size == 2) parts[1] else ""
        if (yuanPart.any { !it.isDigit() }) return null
        if (fenPart.any { !it.isDigit() }) return null
        if (fenPart.length > 2) return null
        val yuan = yuanPart.toLongOrNull() ?: return null
        val fen = when (fenPart.length) {
            0 -> 0L
            1 -> fenPart.toLong() * 10
            else -> fenPart.toLong()
        }
        return yuan * 100 + fen
    }
}
