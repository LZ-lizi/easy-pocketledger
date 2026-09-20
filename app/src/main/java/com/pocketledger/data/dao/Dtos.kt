package com.pocketledger.data.dao

/** Income and expense totals for a date range, in cents. */
data class PeriodTotals(
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
}

/**
 * An account's derived balance. Amounts live in `txn`; nothing caches a balance,
 * so this always reflects the true sum.
 */
data class AccountBalance(
    val accountId: Long,
    val balanceCents: Long,
)

/** Total spent under one category (or one main category when [categoryId] is a parent). */
data class CategoryTotal(
    val categoryId: Long,
    val totalCents: Long,
)

/** Spend rolled up to a main category: [mainCategoryId] is either a parent id or a root id. */
data class MainCategoryTotal(
    val mainCategoryId: Long,
    val totalCents: Long,
)

/** Spend on one calendar day, used by the calendar view. */
data class DayTotal(
    val localDateKey: String,
    val incomeCents: Long,
    val expenseCents: Long,
)

/** Row shape for the ledger list, joined so the UI needs no follow-up lookups. */
data class TxnRow(
    val id: Long,
    val type: String,
    val amountCents: Long,
    val feeCents: Long?,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorArgb: Int?,
    val mainCategoryId: Long?,
    val accountId: Long,
    val accountName: String,
    val toAccountId: Long?,
    val toAccountName: String?,
    val merchant: String?,
    val note: String?,
    val happenedAt: Long,
    val localDateKey: String,
    val source: String,
    val isExcludedFromStats: Boolean,
    val importBatchId: Long?,
)
