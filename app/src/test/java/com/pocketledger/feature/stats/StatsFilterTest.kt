package com.pocketledger.feature.stats

import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the statistics filter button counts as "on".
 *
 * The button's colour is the only signal that the numbers on the page have been narrowed,
 * and it is easy to get wrong in a way nobody notices: tinting it while everything is
 * selected claims a filter that is not there, and leaving it muted after a narrowing hides
 * the reason the totals look small.
 */
class StatsFilterTest {

    private fun option(id: Long, name: String) = CategoryEntity(
        id = id,
        name = name,
        kind = CategoryKind.EXPENSE,
        parentId = null,
        sortOrder = id.toInt(),
    )

    private val options = listOf(option(1L, "餐饮"), option(2L, "交通"), option(3L, "购物"))

    private fun state(selected: Set<Long>) =
        StatsUiState(filterOptions = options, filterCategoryIds = selected)

    @Test
    fun `everything selected is not a filter`() {
        assertFalse(state(setOf(1L, 2L, 3L)).filterActive)
    }

    @Test
    fun `a subset is a filter`() {
        assertTrue(state(setOf(1L, 2L)).filterActive)
        assertTrue(state(setOf(3L)).filterActive)
    }

    @Test
    fun `selecting nothing is still a narrowing, and stays marked as one`() {
        // Empty means "count no categories" -- an empty page on purpose. Treating it as
        // "no filter" would draw the button muted while the totals sat at zero.
        assertTrue(state(emptySet()).filterActive)
    }

    @Test
    fun `before the categories have loaded there is no filter to report`() {
        assertFalse(StatsUiState().filterActive)
        assertFalse(state(setOf(1L)).copy(filterOptions = emptyList()).filterActive)
    }
}
