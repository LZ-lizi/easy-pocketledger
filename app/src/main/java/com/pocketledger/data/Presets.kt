package com.pocketledger.data

import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AccountType
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType

/**
 * The shipped category tree.
 *
 * Shape: a dozen or so broad 大类, each holding concrete 小类. The entry keypad shows
 * the **leaf items only** -- a two-step main-category picker made every entry slower
 * for no gain -- while statistics group by the parent, and a leaf always inherits
 * its parent for that roll-up.
 *
 * Names stay compact and carry no spaces around slashes, because they have to fit a
 * four-column grid. Categories that belong together are merged rather than split
 * (公交地铁 and 共享单车 are both 公共交通), which keeps the grid short enough to scan
 * in one glance.
 *
 * `iconKey` selects a vector icon; nothing here relies on a category's first
 * character, which read poorly for anything starting with a common word.
 */
object Presets {

    /** Marks the ledger created for data that predates ledgers. */
    const val DEFAULT_LEDGER_NAME = "我的账本"

    private class Group(
        val name: String,
        val iconKey: String,
        val colorArgb: Int,
        val items: List<String>,
    )

    private val EXPENSE_TREE = listOf(
        Group(
            "餐饮", "food", 0xFFF97316.toInt(),
            listOf("早餐", "午餐", "晚餐", "外卖", "零食", "饮料", "咖啡/茶饮", "聚餐"),
        ),
        Group(
            "交通", "transport", 0xFF0EA5E9.toInt(),
            listOf("公共交通", "打车", "加油", "停车", "过路费", "火车/高铁", "飞机", "共享单车"),
        ),
        Group(
            "购物", "shopping", 0xFFEC4899.toInt(),
            listOf("日用品", "服饰", "数码", "美妆", "家居", "家电", "母婴"),
        ),
        Group(
            "居住", "home", 0xFF8B5CF6.toInt(),
            listOf("房租", "房贷", "物业", "水费", "电费", "燃气费", "取暖费", "宽带"),
        ),
        Group(
            "通讯", "comms", 0xFF06B6D4.toInt(),
            listOf("话费", "流量", "会员订阅"),
        ),
        Group(
            "学习", "study", 0xFF6366F1.toInt(),
            listOf("教材", "打印", "文具", "培训", "考试报名"),
        ),
        Group(
            "校园", "campus", 0xFF10B981.toInt(),
            listOf("校园卡充值", "社团", "班费"),
        ),
        Group(
            "娱乐", "fun", 0xFFFF7A45.toInt(),
            listOf("游戏", "影视", "演出", "旅行", "运动健身", "兴趣"),
        ),
        Group(
            "医疗", "medical", 0xFFEF4444.toInt(),
            listOf("门诊", "药品", "体检", "住院", "牙科"),
        ),
        Group(
            "人情", "gift", 0xFFF43F5E.toInt(),
            listOf("红包", "礼物", "请客", "孝敬长辈"),
        ),
        Group(
            "金融", "finance", 0xFF64748B.toInt(),
            listOf("手续费", "利息", "保险", "税费"),
        ),
        Group(
            "工作", "work", 0xFF0F766E.toInt(),
            listOf("办公用品", "差旅", "快递"),
        ),
        Group(
            "宠物", "pet", 0xFFA855F7.toInt(),
            listOf("宠物粮食", "宠物医疗", "宠物用品"),
        ),
        Group(
            "其他", "other", 0xFF94A3B8.toInt(),
            listOf("其他支出"),
        ),
    )

    private val INCOME_ITEMS = listOf(
        "生活费", "兼职/实习", "奖学金/助学金", "红包/亲友转账", "二手变卖", "报销",
    )

    private const val INCOME_COLOR = 0xFF12A150.toInt()

    /** The account a 累计模式 ledger hides but still needs for uniform transactions. */
    const val HIDDEN_ACCOUNT_NAME = "累计账户"

    private val BUDGET_ACCOUNTS = listOf(
        AccountEntity(name = "现金", type = AccountType.CASH, iconKey = "cash", colorArgb = 0xFF10B981.toInt(), sortOrder = 0),
        AccountEntity(name = "支付宝", type = AccountType.ALIPAY, iconKey = "alipay", colorArgb = 0xFF1677FF.toInt(), sortOrder = 1),
        AccountEntity(name = "微信零钱", type = AccountType.WECHAT, iconKey = "wechat", colorArgb = 0xFF07C160.toInt(), sortOrder = 2),
        AccountEntity(name = "储蓄卡", type = AccountType.BANK_CARD, iconKey = "bank", colorArgb = 0xFF6366F1.toInt(), sortOrder = 3),
        AccountEntity(name = "校园卡", type = AccountType.PREPAID, iconKey = "card", colorArgb = 0xFFF59E0B.toInt(), sortOrder = 4),
    )

    /**
     * Fills a brand-new ledger with its starting categories and accounts.
     *
     * Idempotent per ledger, so restoring a backup or re-entering a ledger never
     * duplicates the presets. A 累计模式 ledger gets one hidden account instead of the
     * usual set: its transactions still need an account to balance against, but the
     * user never picks one.
     */
    suspend fun seedLedger(db: LedgerDatabase, ledger: LedgerEntity) {
        val categoryDao = db.categoryDao()
        val existing = categoryDao.count(ledger.id)
        if (existing == 0) {
            seedCategories(categoryDao, ledger.id)
        }

        val accountDao = db.accountDao()
        if (accountDao.count(ledger.id) == 0) {
            when (ledger.type) {
                LedgerType.BUDGET -> accountDao.insertAll(
                    BUDGET_ACCOUNTS.mapIndexed { index, account ->
                        account.copy(ledgerId = ledger.id, sortOrder = index)
                    }
                )

                LedgerType.ACCUMULATE -> accountDao.insert(
                    AccountEntity(
                        ledgerId = ledger.id,
                        name = HIDDEN_ACCOUNT_NAME,
                        type = AccountType.OTHER,
                        iconKey = "wallet",
                        isHidden = true,
                        sortOrder = 0,
                    )
                )
            }
        }
    }

    private suspend fun seedCategories(dao: com.pocketledger.data.dao.CategoryDao, ledgerId: Long) {
        EXPENSE_TREE.forEachIndexed { groupIndex, group ->
            val parentId = dao.insert(
                CategoryEntity(
                    ledgerId = ledgerId,
                    name = group.name,
                    kind = CategoryKind.EXPENSE,
                    parentId = null,
                    iconKey = group.iconKey,
                    colorArgb = group.colorArgb,
                    sortOrder = groupIndex,
                    isSystem = true,
                )
            )
            group.items.forEachIndexed { itemIndex, itemName ->
                dao.insert(
                    CategoryEntity(
                        ledgerId = ledgerId,
                        name = itemName,
                        kind = CategoryKind.EXPENSE,
                        parentId = parentId,
                        // Leaves inherit their group's icon: a per-leaf icon set would
                        // be mostly meaningless distinctions at this size.
                        iconKey = group.iconKey,
                        colorArgb = group.colorArgb,
                        sortOrder = itemIndex,
                        isSystem = true,
                    )
                )
            }
        }

        INCOME_ITEMS.forEachIndexed { index, name ->
            dao.insert(
                CategoryEntity(
                    ledgerId = ledgerId,
                    name = name,
                    kind = CategoryKind.INCOME,
                    parentId = null,
                    iconKey = "income",
                    colorArgb = INCOME_COLOR,
                    sortOrder = index,
                    isSystem = true,
                )
            )
        }
    }
}
