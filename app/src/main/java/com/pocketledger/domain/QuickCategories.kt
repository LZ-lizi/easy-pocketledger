package com.pocketledger.domain

import com.pocketledger.data.entity.CategoryEntity

/**
 * Which categories the keypad shows before 「更多」 when the user has not chosen.
 *
 * This used to be "the first twelve of the usage ordering", which read as sensible and
 * was in fact arbitrary. `Presets` gives a leaf its index *within its group* as
 * `sortOrder`, so every 大类's first item ties at 0 -- and the twelve that came out were
 * one per 大类: 早餐, 公共交通, 日用品, 房租, 话费, 教材, 校园卡充值, 游戏, 门诊, 红包,
 * 手续费, 办公用品. A brand-new ledger therefore opened with 手续费 and 门诊 on the first
 * screen while 午餐, 晚餐, 零食 and 外卖 were all behind 「更多」. It went unnoticed on a
 * used ledger because recent entries are sorted to the front and papered over it.
 *
 * Naming the twelve explicitly decouples the defaults from however the tree happens to be
 * seeded or reordered. Names rather than ids because presets are re-seeded per ledger and
 * ids are only meaningful inside one; a user who renames a category simply drops out of
 * the preferred set and the fallback fills the gap.
 */
object QuickCategories {

    /** How many cells the grid shows before 「更多」. Four columns times three rows. */
    const val COUNT = 12

    /**
     * The twelve a student most likely reaches for: every meal, the two ways of getting
     * around, and the recurring fixed costs.
     */
    val NAMES = listOf(
        "早餐", "午餐", "晚餐", "外卖", "零食", "饮料",
        "公共交通", "打车", "日用品", "房租", "话费", "教材",
    )

    private val PREFERRED = NAMES.toSet()

    /**
     * Picks the defaults out of [order], keeping that order.
     *
     * Three tiers, and the order they are taken in matters:
     *
     * 1. **Anything the user has actually recorded against.** A category someone reaches
     *    for belongs on the first screen whatever any list says -- on a real ledger
     *    「共享单车」 sat on the first row until the curated names were introduced and
     *    pushed it off, even though it was the most recent thing recorded.
     * 2. The curated [NAMES], in the order given, so the grid is sensible on a ledger
     *    with no history.
     * 3. Everything else, to keep the count at [COUNT] on a custom or trimmed tree.
     *
     * `order` arrives most-recently-used first, so within each tier the familiar recency
     * ordering still holds.
     */
    fun select(
        order: List<CategoryEntity>,
        recentIds: Collection<Long> = emptyList(),
    ): List<CategoryEntity> =
        select(order, CategoryEntity::name, CategoryEntity::id, recentIds.toSet())

    fun <T> select(
        order: List<T>,
        nameOf: (T) -> String,
        idOf: (T) -> Long,
        recentIds: Set<Long>,
    ): List<T> {
        val (used, unused) = order.partition { idOf(it) in recentIds }
        val (preferred, rest) = unused.partition { nameOf(it) in PREFERRED }
        return (used + preferred + rest).take(COUNT)
    }

    /**
     * Lays the chosen cells out in the category tree's own order.
     *
     * Membership and position are separate decisions, and [select] only answers the first.
     * *Which* twelve appear should follow the user's habits. *Where* they appear should
     * not: the grid used to be drawn in the same recency order that picked it, so every
     * entry reshuffled the cells and the muscle memory of "晚餐 is the third one" was
     * wrong by the next tap. Sorting by (大类, item) also puts every 餐饮 item on one line
     * and every 交通 item on the next, so a row reads as a group instead of as a list of
     * unrelated things -- which is what 「同一类型横行相邻」 asks for.
     *
     * `all` supplies the 大类 ordering, because a leaf only knows its parent's id. A
     * category whose parent is missing (a leaf whose 大类 was deleted) sorts last rather
     * than being dropped: the grid still has to show it.
     */
    fun arrange(items: List<CategoryEntity>, all: List<CategoryEntity>): List<CategoryEntity> {
        val parentOrder = all.filter { it.parentId == null }.associate { it.id to it.sortOrder }
        return items.sortedWith(
            compareBy(
                { it.parentId?.let(parentOrder::get) ?: Int.MAX_VALUE },
                { it.sortOrder },
                { it.id },
            )
        )
    }
}
