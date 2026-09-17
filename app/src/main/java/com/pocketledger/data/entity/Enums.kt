package com.pocketledger.data.entity

/**
 * What a ledger tracks.
 *
 * The two modes exist because "how much have I spent this month against my
 * allowance" and "how much have I put aside in total" are different questions with
 * different screens; forcing one ledger shape onto both would make each mediocre.
 *
 * - [BUDGET] 预算模式: accounts, balances, monthly allowance and budgets all apply.
 * - [ACCUMULATE] 累计模式: a running total only. Accounts are neither shown nor
 *   pickable -- every entry lands in one internal account so the balance maths
 *   still works, but the user never sees it.
 */
enum class LedgerType {
    BUDGET,
    ACCUMULATE,
}

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
    /** Produced by an installment plan falling due. */
    INSTALLMENT,
}

enum class BudgetPeriodType {
    MONTH,
    YEAR,
}

/**
 * A budget without a category is the overall cap; a budget whose `categoryId`
 * points at a top-level category is that category's cap.
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

/**
 * 月付 / 白条.
 *
 * Both are "pay a fixed amount on a fixed day, N times"; they differ only in how
 * the user thinks about them, so they share one schedule engine.
 */
enum class InstallmentKind {
    /** 月付: a subscription-like monthly payment. */
    MONTHLY,

    /** 白条: an instalment loan split over N periods. */
    CREDIT,
}
