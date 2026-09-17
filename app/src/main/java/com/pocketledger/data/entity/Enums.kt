package com.pocketledger.data.entity

/** How an account holds money. Drives the balance sign and the account-page grouping. */
enum class AccountType {
    CASH,
    BANK_CARD,
    CREDIT_CARD,
    ALIPAY,
    WECHAT,
    PREPAID,
    OTHER,
}

/** The two halves of the ledger. Income and expense categories never mix. */
enum class CategoryKind {
    EXPENSE,
    INCOME,
}

/**
 * One row type covers all three movements.
 *
 * - [EXPENSE]: [TxnEntity.accountId] is the paying account.
 * - [INCOME]:  [TxnEntity.accountId] is the receiving account.
 * - [TRANSFER]: [TxnEntity.accountId] is the source, [TxnEntity.toAccountId] the target,
 *   and [TxnEntity.feeCents] (if any) leaves the source as an extra cost.
 */
enum class TxnType {
    EXPENSE,
    INCOME,
    TRANSFER,
}

/** Provenance, so imported rows can be filtered and a whole batch undone. */
enum class TxnSource {
    MANUAL,
    TEMPLATE,
    IMPORT_ALIPAY,
    IMPORT_WECHAT,
}

enum class BudgetPeriodType {
    MONTH,
    YEAR,
}

/**
 * A budget without a category is the overall cap; a budget whose `categoryId`
 * points at a *main* category is the 日常 / 娱乐 cap. One enum covers all three
 * levels because a main category is just an ordinary category row.
 */
enum class BudgetScope {
    TOTAL,
    BY_CATEGORY,
}

enum class WishStatus {
    WISHING,
    BOUGHT,
    DROPPED,
}

enum class GoalStatus {
    ACTIVE,
    DONE,
    ARCHIVED,
}
