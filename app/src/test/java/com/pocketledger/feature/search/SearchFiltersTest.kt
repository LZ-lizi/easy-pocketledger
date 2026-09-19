package com.pocketledger.feature.search

import com.pocketledger.data.entity.TxnType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search screen's filter state.
 *
 * `isEmpty` decides whether the summary line reads "全部 N 条" or "筛选出 N 条", and
 * whether the 清空条件 action is offered at all -- so it has to mean "nothing is
 * narrowing the ledger down", not "the user has typed something".
 */
class SearchFiltersTest {

    @Test
    fun `an untouched filter set is empty`() {
        val filters = SearchFilters()
        assertTrue(filters.isEmpty)
        assertEquals(0, filters.activeCount)
    }

    @Test
    fun `whitespace alone is not a keyword`() {
        // Otherwise tapping the field and typing a space would claim the list is filtered.
        assertTrue(SearchFilters(keyword = "   ").isEmpty)
        assertTrue(SearchFilters(keyword = "").isEmpty)
        assertFalse(SearchFilters(keyword = "外卖").isEmpty)
    }

    @Test
    fun `a date range counts as one filter however many ends are set`() {
        assertEquals(1, SearchFilters(startKey = "2026-09-01").activeCount)
        assertEquals(1, SearchFilters(endKey = "2026-09-30").activeCount)
        assertEquals(1, SearchFilters(startKey = "2026-09-01", endKey = "2026-09-30").activeCount)
    }

    @Test
    fun `an amount range counts as one filter however many ends are set`() {
        assertEquals(1, SearchFilters(minCents = 1000).activeCount)
        assertEquals(1, SearchFilters(minCents = 1000, maxCents = 5000).activeCount)
    }

    @Test
    fun `every distinct filter is counted separately`() {
        val filters = SearchFilters(
            keyword = "美团",
            startKey = "2026-09-01",
            endKey = "2026-09-30",
            type = TxnType.EXPENSE,
            accountId = 2L,
            categoryId = 7L,
            minCents = 1000,
            maxCents = 9000,
        )
        assertFalse(filters.isEmpty)
        assertEquals(6, filters.activeCount)
    }

    @Test
    fun `a single toggle makes the set non-empty`() {
        assertFalse(SearchFilters(type = TxnType.INCOME).isEmpty)
        assertFalse(SearchFilters(accountId = 1L).isEmpty)
        assertFalse(SearchFilters(categoryId = 3L).isEmpty)
    }
}
