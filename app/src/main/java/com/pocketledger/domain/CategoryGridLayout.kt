package com.pocketledger.domain

import kotlin.math.floor

/**
 * How many cells the entry keypad's category grid draws, and in how many rows.
 *
 * Pure arithmetic, kept out of the composable because it has one non-obvious rule that has
 * already been got wrong once on a real phone: when some categories are held back behind
 * 「更多」, **one cell has to be reserved for it**.
 *
 * Without the reservation, twelve shortcuts plus the 「更多」 cell came to thirteen -- a
 * fourth row the fixed-height slot cannot hold. The grid was built with `rows` rows, the
 * extra cell was laid out below the visible area, and the result was that **every category
 * beyond the twelve became unreachable**, with nothing on screen to say so. Reserving the
 * cell costs one shortcut and keeps the whole tree one tap away.
 */
object CategoryGridLayout {

    /** The grid is four across. */
    const val COLUMNS = 4

    /** Whole rows that fit: a half row is a rendering defect, not a scroll hint. */
    fun rowsThatFit(availablePx: Float, pitchPx: Float): Int =
        floor(availablePx / pitchPx).toInt().coerceAtLeast(1)

    fun rowsNeeded(categoryCount: Int, hasMore: Boolean): Int =
        (categoryCount + (if (hasMore) 1 else 0) + COLUMNS - 1) / COLUMNS

    /**
     * Rows actually drawn.
     *
     * Never more than fit, and never more than are needed -- an income ledger has six items
     * and should not reserve three empty rows.
     */
    fun rows(categoryCount: Int, hasMore: Boolean, fitRows: Int): Int =
        minOf(fitRows, maxOf(rowsNeeded(categoryCount, hasMore), 1))

    /** How many category cells to draw, leaving room for 「更多」 when there is one. */
    fun visibleCount(categoryCount: Int, hasMore: Boolean, rows: Int): Int {
        val reserved = if (hasMore) 1 else 0
        return minOf(categoryCount, (rows * COLUMNS - reserved).coerceAtLeast(1))
    }
}
