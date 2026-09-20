package com.pocketledger.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The keypad grid's arithmetic, in particular the cell reserved for 「更多」.
 *
 * A real phone found the failure this pins down: the account chips sat in a row that began
 * at the same y as the third category row, and with twelve shortcuts plus 「更多」 the grid
 * needed a fourth row that the slot could not hold. The extra cell was laid out past the
 * bottom of the grid, so **nothing beyond the twelve quick categories could be reached at
 * all** -- and the screen looked fine, because a cell that is not drawn leaves no gap.
 */
class CategoryGridLayoutTest {

    @Test
    fun `the phone that exposed the bug fits three rows`() {
        // 1080x2400 device: 223dp of slot, 65dp cell + 2dp spacing.
        assertEquals(3, CategoryGridLayout.rowsThatFit(223f, 67f))
    }

    @Test
    fun `twelve shortcuts plus 更多 come to eleven shortcuts and a 更多`() {
        val rows = CategoryGridLayout.rows(categoryCount = 12, hasMore = true, fitRows = 3)
        assertEquals(3, rows)
        assertEquals(11, CategoryGridLayout.visibleCount(12, hasMore = true, rows = rows))
    }

    @Test
    fun `without the reservation the grid would need a fourth row`() {
        // The shape of the original bug, stated as arithmetic: 12 categories need 3 rows,
        // but 12 categories *and* 更多 need 4 -- one more than fits.
        assertEquals(3, CategoryGridLayout.rowsNeeded(categoryCount = 12, hasMore = false))
        assertEquals(4, CategoryGridLayout.rowsNeeded(categoryCount = 12, hasMore = true))
    }

    @Test
    fun `a short list keeps its own size`() {
        // Six income items: two rows drawn, no padding rows, no reserved cell.
        val rows = CategoryGridLayout.rows(categoryCount = 6, hasMore = false, fitRows = 3)
        assertEquals(2, rows)
        assertEquals(6, CategoryGridLayout.visibleCount(6, hasMore = false, rows = rows))
    }

    @Test
    fun `a tall screen still reserves exactly one cell`() {
        val rows = CategoryGridLayout.rows(categoryCount = 12, hasMore = true, fitRows = 6)
        assertEquals(4, rows)
        assertEquals(12, CategoryGridLayout.visibleCount(12, hasMore = true, rows = rows))
    }

    @Test
    fun `a grid with nothing to hold back never shows fewer than asked`() {
        assertEquals(3, CategoryGridLayout.visibleCount(3, hasMore = false, rows = 1))
    }

    @Test
    fun `more is never left without a cell, even on a one-row screen`() {
        // One row is four cells; 更多 takes one of them, so three categories fit beside it
        // rather than four categories and an invisible 更多.
        val rows = CategoryGridLayout.rows(categoryCount = 9, hasMore = true, fitRows = 1)
        assertEquals(1, rows)
        assertEquals(3, CategoryGridLayout.visibleCount(9, hasMore = true, rows = rows))
    }

    @Test
    fun `zero available height still yields one row rather than none`() {
        assertEquals(1, CategoryGridLayout.rowsThatFit(0f, 67f))
        assertEquals(1, CategoryGridLayout.rowsThatFit(-5f, 67f))
    }
}
