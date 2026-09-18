package com.pocketledger.feature.entry

import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the entry keypad shows before 「更多」.
 *
 * These pin down a bug that was reported as "累计模式帐本的类目收纳方式和预算模式不一样":
 * the pinned ids are category *rows*, so a set saved in one ledger named rows that did
 * not exist in another, and filtering by it there left the keypad with no categories at
 * all. The fix was to key the preference per ledger; the guard that makes it impossible
 * to see an empty grid again is asserted here.
 */
class EntryCategoryVisibilityTest {

    private fun leaf(id: Long, name: String, kind: CategoryKind = CategoryKind.EXPENSE, parent: Long = 100) =
        CategoryEntity(
            id = id,
            name = name,
            kind = kind,
            parentId = if (kind == CategoryKind.INCOME) null else parent,
            sortOrder = id.toInt(),
        )

    /** A 大类 plus [count] leaves under it, which is the shape the presets use. */
    private fun expenseLeaves(count: Int): List<CategoryEntity> =
        listOf(
            CategoryEntity(id = 100, name = "餐饮", kind = CategoryKind.EXPENSE, parentId = null)
        ) + (1..count).map { leaf(it.toLong(), "类别$it") }

    private fun incomeItems(): List<CategoryEntity> = listOf(
        leaf(901, "生活费", CategoryKind.INCOME),
        leaf(902, "兼职/实习", CategoryKind.INCOME),
        leaf(903, "奖学金/助学金", CategoryKind.INCOME),
        leaf(904, "红包/亲友转账", CategoryKind.INCOME),
        leaf(905, "二手变卖", CategoryKind.INCOME),
        leaf(906, "报销", CategoryKind.INCOME),
    )

    private fun expenseState(
        categories: List<CategoryEntity> = expenseLeaves(20),
        pinned: Set<Long> = emptySet(),
    ) = EntryUiState(mode = EntryMode.EXPENSE, allCategories = categories, pinnedCategoryIds = pinned)

    private fun incomeState(pinned: Set<Long> = emptySet()) = EntryUiState(
        mode = EntryMode.INCOME,
        allCategories = incomeItems() + expenseLeaves(20),
        pinnedCategoryIds = pinned,
    )

    @Test
    fun `expense grid is capped when nothing is pinned`() {
        val state = expenseState()
        assertEquals(EntryUiState.DEFAULT_PRIMARY_CATEGORIES, state.visibleCategories.size)
        assertTrue(state.hasHiddenCategories)
    }

    @Test
    fun `a pinned set from another ledger cannot empty the expense grid`() {
        // Ids 5001..5012 belong to some other ledger: none of them is in this one.
        val state = expenseState(pinned = (5001L..5012L).toSet())
        assertEquals(
            "a stale pin set must fall back to the defaults, not show nothing",
            EntryUiState.DEFAULT_PRIMARY_CATEGORIES,
            state.visibleCategories.size,
        )
    }

    @Test
    fun `a pinned set that matches this ledger is honoured exactly`() {
        val chosen = setOf(7L, 3L, 11L)
        val state = expenseState(pinned = chosen)
        assertEquals(chosen, state.visibleCategories.map { it.id }.toSet())
        assertTrue(state.hasHiddenCategories)
    }

    @Test
    fun `pinning every category leaves nothing behind 更多`() {
        val every = expenseLeaves(20).filter { it.parentId != null }.map { it.id }.toSet()
        val state = expenseState(pinned = every)
        assertEquals(20, state.visibleCategories.size)
        assertFalse(state.hasHiddenCategories)
    }

    @Test
    fun `income is never collapsed, whatever the pinned set says`() {
        // A pin set naming expense rows must not shrink the income grid either.
        assertEquals(6, incomeState(pinned = setOf(1L, 2L, 3L)).visibleCategories.size)
        assertEquals(6, incomeState().visibleCategories.size)
        assertFalse(incomeState().hasHiddenCategories)
    }

    @Test
    fun `income categories are never mixed into the expense grid`() {
        assertTrue(expenseState().visibleCategories.all { it.kind == CategoryKind.EXPENSE })
        assertTrue(incomeState().visibleCategories.all { it.kind == CategoryKind.INCOME })
    }

    @Test
    fun `the 更多 menu groups the whole list by 大类`() {
        val groups = expenseState().categoryGroups
        assertTrue(groups.isNotEmpty())
        assertEquals(20, groups.sumOf { it.items.size })
        assertTrue(groups.all { it.items.isNotEmpty() })
    }
}
