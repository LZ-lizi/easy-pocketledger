package com.pocketledger.domain

import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.TxnType

/**
 * Decides which category an imported row belongs to.
 *
 * Two sources, in order:
 *
 * 1. **Rules the user has already confirmed.** Every import correction is remembered as
 *    `keyword -> category`, so the second 美团 order lands in 外卖 without being asked
 *    about again. This is what makes a monthly re-import a single tap.
 * 2. **A built-in keyword table** for the merchants that dominate student spending.
 *    Only ever a starting point: a wrong guess costs one tap to fix, and fixing it
 *    teaches the rule for next time.
 *
 * Nothing here is fuzzy on purpose. A near-match that silently files 星巴克 under 交通
 * is worse than leaving the row unclassified, because an unclassified row is visible
 * and a wrong one is not.
 */
object CategoryMatcher {

    /**
     * Built-in merchant keywords, longest-first at match time.
     *
     * Keyed by the *leaf* name rather than an id: presets are re-seeded per ledger and a
     * user may have renamed a category, so names are the only stable handle the domain
     * layer has. [match] ignores any keyword whose category is not present.
     */
    private val BUILT_IN: List<Pair<String, String>> = listOf(
        "美团" to "外卖",
        "饿了么" to "外卖",
        "肯德基" to "外卖",
        "麦当劳" to "外卖",
        "瑞幸" to "咖啡/茶饮",
        "星巴克" to "咖啡/茶饮",
        "蜜雪冰城" to "饮料",
        "喜茶" to "饮料",
        "滴滴" to "打车",
        "高德打车" to "打车",
        "哈啰" to "共享单车",
        "青桔" to "共享单车",
        "美团单车" to "共享单车",
        "12306" to "火车/高铁",
        "中国铁路" to "火车/高铁",
        "地铁" to "公共交通",
        "公交" to "公共交通",
        "淘宝" to "日用品",
        "天猫" to "日用品",
        "京东" to "数码",
        "拼多多" to "日用品",
        "唯品会" to "服饰",
        "优衣库" to "服饰",
        "屈臣氏" to "美妆",
        "中国移动" to "话费",
        "中国联通" to "话费",
        "中国电信" to "话费",
        "话费" to "话费",
        "流量" to "流量",
        "电费" to "电费",
        "水费" to "水费",
        "燃气" to "燃气费",
        "物业" to "物业",
        "房租" to "房租",
        "学费" to "培训",
        "打印" to "打印",
        "图书馆" to "打印",
        "医院" to "门诊",
        "药店" to "药品",
        "大药房" to "药品",
        "电影" to "影视",
        "猫眼" to "影视",
        "腾讯视频" to "会员订阅",
        "爱奇艺" to "会员订阅",
        "网易云" to "会员订阅",
        "QQ音乐" to "会员订阅",
        "健身房" to "运动健身",
        "keep" to "运动健身",
        "快递" to "快递",
        "菜鸟" to "快递",
        "顺丰" to "快递",
        "红包" to "红包",
        "转账" to "红包/亲友转账",
        "生活费" to "生活费",
        "奖学金" to "奖学金/助学金",
        "助学金" to "奖学金/助学金",
        "兼职" to "兼职/实习",
        "实习" to "兼职/实习",
        "报销" to "报销",
    )

    /**
     * Picks the best category for one row.
     *
     * @param rules keyword -> category name, learned from the user's own corrections.
     * @param categories every category in the ledger; the matcher only ever returns one
     *   of these, and only one matching the row's own income/expense kind.
     */
    fun match(
        row: ImportRow,
        categories: List<CategoryEntity>,
        rules: Map<String, Long>,
    ): Long? {
        val kind = if (row.type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        val eligible = categories.filter { it.kind == kind }
        if (eligible.isEmpty()) return null
        val eligibleIds = eligible.mapTo(mutableSetOf()) { it.id }

        val haystack = row.matchText.lowercase()
        if (haystack.isBlank()) return null

        // Learned rules win: they encode a decision the user already made for this exact
        // merchant, which beats any guess the shipped table could make.
        rules.entries
            .filter { it.value in eligibleIds && it.key.isNotBlank() && haystack.contains(it.key.lowercase()) }
            .maxByOrNull { it.key.length }
            ?.let { return it.value }

        val byName = eligible.groupBy { it.name }
        BUILT_IN
            .filter { haystack.contains(it.first.lowercase()) }
            .sortedByDescending { it.first.length }
            .forEach { (_, categoryName) ->
                byName[categoryName]?.firstOrNull()?.let { return it.id }
            }
        return null
    }

    /**
     * The keyword a correction should be remembered under.
     *
     * The merchant is the stable part of a bill row -- the商品 text changes every time
     * -- so a rule keyed on it keeps matching. Falls back to the note when a bill has no
     * counterparty at all.
     */
    fun keywordFor(row: ImportRow): String? =
        row.merchant?.trim()?.takeIf { it.isNotBlank() && it.length <= MAX_KEYWORD_LENGTH }
            ?: row.note?.trim()?.takeIf { it.isNotBlank() && it.length <= MAX_KEYWORD_LENGTH }

    private const val MAX_KEYWORD_LENGTH = 24
}
