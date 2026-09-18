package com.pocketledger.domain

import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid's default twelve.
 *
 * A real device check found the old rule -- "first twelve of the usage ordering" -- put
 * 手续费, 门诊, 办公用品 and 校园卡充值 on the first screen of every new ledger while
 * 午餐, 晚餐, 零食 and 外卖 sat behind 「更多」. The cause was `sortOrder` being a leaf's
 * index *within its group*, so every 大类's first item tied and the grid took one from
 * each. These tests pin the named list so a future reseeding or reordering cannot quietly
 * reintroduce it.
 */
class QuickCategoriesTest {

    private var nextId = 1L

    private fun leaf(name: String, sortOrder: Int) = CategoryEntity(
        id = nextId++,
        name = name,
        kind = CategoryKind.EXPENSE,
        parentId = 1L,
        sortOrder = sortOrder,
    )

    /**
     * The shipped tree, in the shape `Presets` seeds it.
     *
     * A leaf's `sortOrder` is its index *within its group*, which is the whole bug: every
     * 大类's first item ties at 0, so ordering by `sortOrder` alone yields one item per
     * 大类 rather than anything to do with how often it is used.
     */
    private val groups = listOf(
        "餐饮" to listOf("早餐", "午餐", "晚餐", "外卖", "零食", "饮料", "咖啡/茶饮", "聚餐"),
        "交通" to listOf("公共交通", "打车", "加油", "停车", "过路费", "火车/高铁", "飞机", "共享单车"),
        "购物" to listOf("日用品", "服饰", "数码", "美妆", "家居", "家电", "母婴"),
        "居住" to listOf("房租", "房贷", "物业", "水费", "电费", "燃气费", "取暖费", "宽带"),
        "通讯" to listOf("话费", "流量", "会员订阅"),
        "学习" to listOf("教材", "打印", "文具", "培训", "考试报名"),
        "校园" to listOf("校园卡充值", "社团", "班费"),
        "娱乐" to listOf("游戏", "影视", "演出", "旅行", "运动健身", "兴趣"),
        "医疗" to listOf("门诊", "药品", "体检", "住院", "牙科"),
        "人情" to listOf("红包", "礼物", "请客", "孝敬长辈"),
        "金融" to listOf("手续费", "利息", "保险", "税费"),
        "工作" to listOf("办公用品", "差旅", "快递"),
        "宠物" to listOf("宠物粮食", "宠物医疗", "宠物用品"),
        "其他" to listOf("其他支出"),
    )

    /** Insertion order: every group's leaves together, as the DAO returns them by rowid. */
    private val asSeedOrder: List<CategoryEntity> = groups.flatMap { (_, items) ->
        items.mapIndexed { index, name -> leaf(name, index) }
    }

    /** The trap: `ORDER BY sortOrder` with every 大类's first item tied at 0. */
    private val asPerGroupSortOrder: List<CategoryEntity> = asSeedOrder.sortedBy { it.sortOrder }

    @Test
    fun `the defaults are the everyday ones a student actually uses`() {
        val names = QuickCategories.select(asPerGroupSortOrder).map { it.name }.toSet()
        for (expected in listOf("早餐", "午餐", "晚餐", "外卖", "零食", "公共交通", "打车", "日用品")) {
            assertTrue("$expected should be on the first screen", expected in names)
        }
    }

    @Test
    fun `the rare categories are not among the defaults`() {
        val names = QuickCategories.select(asPerGroupSortOrder).map { it.name }.toSet()
        for (unexpected in listOf("手续费", "门诊", "办公用品", "校园卡充值", "考试报名", "房贷")) {
            assertFalse("$unexpected should be behind 更多", unexpected in names)
        }
    }

    @Test
    fun `it returns exactly twelve`() {
        assertEquals(QuickCategories.COUNT, QuickCategories.select(asSeedOrder).size)
        assertEquals(12, QuickCategories.COUNT)
    }

    @Test
    fun `the order it is given is preserved, so recent items still float up`() {
        // The keypad is a recency order; picking the defaults must not reshuffle it.
        val recent = listOf("外卖", "打车", "零食")
        val ordered = (recent + asSeedOrder.map { it.name }).distinct()
            .mapIndexed { index, name -> leaf(name, index) }
        val picked = QuickCategories.select(ordered).map { it.name }
        assertEquals(recent, picked.take(3))
    }

    @Test
    fun `a category the user actually uses beats the curated list`() {
        // Found on a real ledger: 共享单车 was the most recent entry recorded, and naming
        // the defaults explicitly pushed it off the first screen because 共享单车 is not
        // one of the twelve names. Use wins.
        val bike = asSeedOrder.first { it.name == "共享单车" }
        val ordered = listOf(bike) + asSeedOrder.filter { it.id != bike.id }
        val picked = QuickCategories.select(ordered, recentIds = listOf(bike.id))
        assertEquals("共享单车", picked.first().name)
        assertTrue(picked.size == QuickCategories.COUNT)
    }

    @Test
    fun `a used-up grid does not fall short when every recent item is off-list`() {
        val odd = listOf("过路费", "取暖费", "母婴").mapIndexed { index, name ->
            asSeedOrder.first { it.name == name }.copy(sortOrder = index)
        }
        val ordered = odd + asSeedOrder.filter { it.name !in setOf("过路费", "取暖费", "母婴") }
        val picked = QuickCategories.select(ordered, recentIds = odd.map { it.id })
        assertEquals(listOf("过路费", "取暖费", "母婴"), picked.take(3).map { it.name })
        assertEquals(QuickCategories.COUNT, picked.size)
    }

    @Test
    fun `a ledger missing the preferred names still fills the grid`() {
        // Renamed or hand-built trees must not produce a short grid.
        val only = listOf(leaf("手续费", 0), leaf("门诊", 1), leaf("办公用品", 2))
        assertEquals(3, QuickCategories.select(only).size)
    }

    @Test
    fun `the seeded per-group order really is the trap it is claimed to be`() {
        // Guards the fixture: if Presets ever gives leaves a global sortOrder, this
        // fixture stops reproducing the bug and the tests above would go on passing for
        // the wrong reason.
        val naiveFirstTwelve = asPerGroupSortOrder.take(12).map { it.name }
        assertTrue("手续费" in naiveFirstTwelve)
        assertFalse(naiveFirstTwelve.contains("午餐"))
        assertEquals(
            listOf(
                "早餐", "公共交通", "日用品", "房租", "话费", "教材",
                "校园卡充值", "游戏", "门诊", "红包", "手续费", "办公用品",
            ),
            naiveFirstTwelve,
        )
    }
}
