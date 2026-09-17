package com.pocketledger.data

import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AccountType
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind

/**
 * The shipped classification, built around student life rather than a generic
 * household budget.
 *
 * Two flat main categories do the heavy lifting: **日常生活** (things a student
 * cannot really avoid) and **娱乐开销** (everything discretionary). Because a main
 * category is an ordinary category row, "how much did I spend on fun this month"
 * is a single GROUP BY with no extra concept.
 *
 * Item placement is opinionated but the user can move anything:
 * 数码 / 电子 and 聚会 AA / 请客 sit under 娱乐开销; 社团 / 班费 and 食堂 / 外卖 under
 * 日常生活. Re-parenting rewrites one column and history follows automatically.
 *
 * Colours are assigned by index and never reshuffled, so a category keeps its
 * colour for the life of the database.
 */
object Presets {

    const val DAILY = "日常生活"
    const val LEISURE = "娱乐开销"

    private const val COLOR_DAILY = 0xFF2F6BFF.toInt()
    private const val COLOR_LEISURE = 0xFFFF7A45.toInt()

    private val PALETTE = intArrayOf(
        0xFF2F6BFF.toInt(), 0xFF00A88F.toInt(), 0xFFFF7A45.toInt(), 0xFF8B5CF6.toInt(),
        0xFFEC4899.toInt(), 0xFFF59E0B.toInt(), 0xFF10B981.toInt(), 0xFF06B6D4.toInt(),
        0xFF6366F1.toInt(), 0xFFEF4444.toInt(), 0xFF84CC16.toInt(), 0xFF14B8A6.toInt(),
        0xFFF97316.toInt(), 0xFFA855F7.toInt(), 0xFF0EA5E9.toInt(), 0xFF64748B.toInt(),
    )

    private class Main(
        val name: String,
        val colorArgb: Int,
        val iconKey: String,
        val items: List<String>,
    )

    private val EXPENSE_TREE = listOf(
        Main(
            DAILY, COLOR_DAILY, "daily",
            listOf(
                "食堂 / 外卖",
                "校园卡充值",
                "零食饮料",
                "宿舍水电",
                "网费 / 话费",
                "洗衣 / 生活服务",
                "日用品",
                "教材 / 打印 / 文具",
                "培训 / 考试报名",
                "公交地铁",
                "打车 / 共享单车",
                "火车 / 高铁 / 飞机",
                "医疗 / 药品",
                "理发 / 洗护",
                "社团 / 班费",
                "其他日常",
            ),
        ),
        Main(
            LEISURE, COLOR_LEISURE, "leisure",
            listOf(
                "游戏 / 会员充值",
                "聚会 AA / 请客",
                "数码 / 电子",
                "服饰 / 美妆",
                "运动 / 健身",
                "旅行 / 周边游",
                "影视 / 演出 / 展览",
                "礼物 / 人情",
                "兴趣 / 书报",
                "其他娱乐",
            ),
        ),
    )

    /** Income is flat: six sources cover student life without a second level. */
    private val INCOME_ITEMS = listOf(
        "生活费",
        "兼职 / 实习",
        "奖学金 / 助学金",
        "红包 / 亲友转账",
        "二手变卖 / 其他",
        "报销",
    )

    private val ACCOUNTS = listOf(
        AccountEntity(name = "现金", type = AccountType.CASH, iconKey = "cash", colorArgb = 0xFF10B981.toInt(), sortOrder = 0),
        AccountEntity(name = "支付宝", type = AccountType.ALIPAY, iconKey = "alipay", colorArgb = 0xFF1677FF.toInt(), sortOrder = 1),
        AccountEntity(name = "微信零钱", type = AccountType.WECHAT, iconKey = "wechat", colorArgb = 0xFF07C160.toInt(), sortOrder = 2),
        AccountEntity(name = "储蓄卡", type = AccountType.BANK_CARD, iconKey = "bank", colorArgb = 0xFF6366F1.toInt(), sortOrder = 3),
        AccountEntity(name = "校园卡", type = AccountType.PREPAID, iconKey = "card", colorArgb = 0xFFF59E0B.toInt(), sortOrder = 4),
    )

    /**
     * Idempotent: only fills an empty database, so a restore or a second launch
     * never duplicates the presets.
     */
    suspend fun seedIfEmpty(db: LedgerDatabase) {
        seedCategories(db)
        seedAccounts(db)
    }

    private suspend fun seedCategories(db: LedgerDatabase) {
        val dao = db.categoryDao()
        if (dao.count() > 0) return

        var paletteCursor = 0
        EXPENSE_TREE.forEachIndexed { mainIndex, main ->
            val parentId = dao.insert(
                CategoryEntity(
                    name = main.name,
                    kind = CategoryKind.EXPENSE,
                    parentId = null,
                    iconKey = main.iconKey,
                    colorArgb = main.colorArgb,
                    sortOrder = mainIndex,
                    isSystem = true,
                )
            )
            main.items.forEachIndexed { itemIndex, itemName ->
                dao.insert(
                    CategoryEntity(
                        name = itemName,
                        kind = CategoryKind.EXPENSE,
                        parentId = parentId,
                        iconKey = main.iconKey,
                        colorArgb = PALETTE[paletteCursor++ % PALETTE.size],
                        sortOrder = itemIndex,
                        isSystem = true,
                    )
                )
            }
        }

        INCOME_ITEMS.forEachIndexed { index, name ->
            dao.insert(
                CategoryEntity(
                    name = name,
                    kind = CategoryKind.INCOME,
                    parentId = null,
                    iconKey = "income",
                    colorArgb = PALETTE[(index + 3) % PALETTE.size],
                    sortOrder = index,
                    isSystem = true,
                )
            )
        }
    }

    private suspend fun seedAccounts(db: LedgerDatabase) {
        val dao = db.accountDao()
        if (dao.count() > 0) return
        dao.insertAll(ACCOUNTS)
    }
}
