package com.pocketledger.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema v2.
 *
 * Conventions that hold across every table:
 * - Money is `Long` **cents**, never a floating-point type.
 * - Rows that belong to a ledger carry `ledgerId`; **nothing is global**. Statistics,
 *   budgets, accounts and installment plans are all scoped to one ledger.
 * - Time is stored twice: `happenedAt` (epoch millis, for ordering) and
 *   `localDateKey` (`YYYY-MM-DD`, for day grouping and indexes).
 * - Mutable rows carry `deletedAt` for soft deletion so undo / import rollback /
 *   backup merge stay possible.
 * - Enums are stored by name; Room converts them.
 *
 * `ledgerId` declares `defaultValue = "1"` so the v1 -> v2 migration can add the
 * column with the same default the schema expects, instead of rebuilding every
 * table.
 */

/** An isolated set of books. Everything else in the app hangs off one of these. */
@Entity(
    tableName = "ledger",
    indices = [Index("isArchived"), Index("sortOrder")],
)
data class LedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: LedgerType,
    val iconKey: String = "book",
    val colorArgb: Int = 0xFF2F6BFF.toInt(),
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "account",
    indices = [Index("ledgerId"), Index("sortOrder"), Index("isArchived")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val type: AccountType,
    val iconKey: String = "wallet",
    val colorArgb: Int = 0xFF2F6BFF.toInt(),
    /** Opening balance; the live balance is always derived, never stored. */
    val initialBalanceCents: Long = 0,
    val creditLimitCents: Long? = null,
    /** Day of month the statement closes / is due, for credit cards. */
    val billDay: Int? = null,
    val repayDay: Int? = null,
    /** Cleared cards can be hidden from the net-worth total without being deleted. */
    val includeInTotal: Boolean = true,
    /**
     * Accounts are only offered in 预算模式 ledgers. A 累计模式 ledger still owns one
     * hidden account so transaction rows stay uniform, but it is never shown.
     *
     * The SQL default matters: the v1 -> v2 migration adds this to a populated
     * table, which only works for a NOT NULL column that has one.
     */
    @ColumnInfo(defaultValue = "0") val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

/**
 * Two levels: `parentId == null` is a **top-level category** (餐饮 / 交通 / 娱乐 ...),
 * and pointing at one makes a concrete item such as 外卖 or 公共交通.
 *
 * Top-level rows exist for grouping and for statistics, not as a first step of data
 * entry: the entry keypad shows the leaf items directly, and a leaf always inherits
 * its parent for the 大类 roll-up.
 *
 * `iconKey` selects a vector icon. Built-in rows carry a curated key; custom rows
 * fall back to a default.
 */
@Entity(
    tableName = "category",
    indices = [
        Index("ledgerId"),
        Index("parentId"),
        Index(value = ["ledgerId", "kind", "parentId", "sortOrder"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val iconKey: String = "more",
    val colorArgb: Int = 0,
    val sortOrder: Int = 0,
    /** True for the shipped presets; they may still be renamed, moved or deleted. */
    val isSystem: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "txn",
    indices = [
        Index("ledgerId"),
        Index("happenedAt"),
        Index("localDateKey"),
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
        Index("type"),
        Index("importBatchId"),
        Index(value = ["externalNo"], unique = false),
        Index(value = ["dedupeHash"], unique = false),
        Index(value = ["planId", "planPeriodIndex"], unique = false),
    ],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val type: TxnType,
    /** Always positive; the direction comes from [type]. */
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val feeCents: Long? = null,
    val categoryId: Long? = null,
    /** Merchant / counterparty, auto-filled from 支付宝/微信「交易对方」columns. */
    val merchant: String? = null,
    val note: String? = null,
    /** Epoch millis, precise to the second: entries record a real time of day. */
    val happenedAt: Long,
    val localDateKey: String,
    val source: TxnSource = TxnSource.MANUAL,
    /** Original order number from an imported bill, used for idempotent re-import. */
    val externalNo: String? = null,
    /** Fingerprint fallback for rows whose bill has no order number. */
    val dedupeHash: String? = null,
    /** 「不计收支」rows: recorded but excluded from every total. */
    val isExcludedFromStats: Boolean = false,
    val importBatchId: Long? = null,
    /** Set when this row was produced by an installment plan falling due. */
    val planId: Long? = null,
    val planPeriodIndex: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "tag",
    indices = [Index(value = ["ledgerId", "name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val colorArgb: Int = 0xFF6B7280.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "txn_tag",
    primaryKeys = ["txnId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(
            entity = TxnEntity::class,
            parentColumns = ["id"],
            childColumns = ["txnId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TxnTagCrossRef(
    val txnId: Long,
    val tagId: Long,
)

/**
 * `categoryId == 0` means "the overall cap". A sentinel rather than NULL keeps the
 * unique index meaningful (SQLite treats NULLs as distinct, which would allow
 * duplicate overall budgets).
 */
@Entity(
    tableName = "budget",
    indices = [
        Index("ledgerId"),
        Index(value = ["ledgerId", "periodType", "periodKey", "scope", "categoryId"], unique = true),
    ],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val periodType: BudgetPeriodType,
    /** `YYYY-MM` for months, `YYYY` for years. */
    val periodKey: String,
    val scope: BudgetScope,
    val categoryId: Long = 0,
    val amountCents: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Monthly living allowance, keyed `YYYY-MM`; missing months may inherit the last one. */
@Entity(
    tableName = "allowance",
    indices = [Index(value = ["ledgerId", "periodKey"], unique = true)],
)
data class AllowanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val periodKey: String,
    val amountCents: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A named date range such as 「2026 秋季学期」, used by the statistics time filter. */
@Entity(
    tableName = "term",
    indices = [Index("ledgerId")],
)
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val startDateKey: String,
    val endDateKey: String,
    /** The term pre-selected on the statistics page. */
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 愿望清单: park a purchase, decide later. */
@Entity(
    tableName = "wish",
    indices = [Index("ledgerId")],
)
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val expectedPriceCents: Long? = null,
    val note: String? = null,
    val priority: Int = 0,
    val status: WishStatus = WishStatus.WISHING,
    /** Set when the wish was fulfilled, linking to the expense it produced. */
    val boughtTxnId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val decidedAt: Long? = null,
)

/**
 * 存钱目标. Progress comes from the linked account's balance when there is one,
 * otherwise from [manualSavedCents].
 */
@Entity(
    tableName = "saving_goal",
    indices = [Index("ledgerId")],
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val targetCents: Long,
    val linkedAccountId: Long? = null,
    val manualSavedCents: Long = 0,
    val targetDateKey: String? = null,
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** One-tap entry presets shown above the keypad. */
@Entity(
    tableName = "template",
    indices = [Index("ledgerId")],
)
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val type: TxnType,
    val amountCents: Long? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val merchant: String? = null,
    val note: String? = null,
    val sortOrder: Int = 0,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Remembers "this keyword belongs in that category" so the next import is one tap. */
@Entity(
    tableName = "import_rule",
    indices = [Index(value = ["ledgerId", "keyword"], unique = true)],
)
data class ImportRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val keyword: String,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val source: TxnSource? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Groups one import so the whole batch can be rolled back in a single action. */
@Entity(
    tableName = "import_batch",
    indices = [Index("ledgerId")],
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val source: TxnSource,
    val fileName: String? = null,
    val txnCount: Int = 0,
    val skippedCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis(),
)

/**
 * 月付 / 白条: pay [perPeriodCents] on day [repayDay] of each month, [periodCount] times.
 *
 * Only the schedule lives here. Each period that falls due materialises a normal
 * transaction, so the plan contributes to the ledger's statistics and budgets the
 * same way any other spending does -- there is no parallel accounting path.
 */
@Entity(
    tableName = "installment_plan",
    indices = [Index("ledgerId"), Index("isActive"), Index("accountId")],
)
data class InstallmentPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "1") val ledgerId: Long = 1,
    val name: String,
    val kind: InstallmentKind,
    val totalAmountCents: Long,
    val periodCount: Int,
    val perPeriodCents: Long,
    /** Day of month the payment is due, 1..31; clamped to short months. */
    val repayDay: Int,
    val startDateKey: String,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * One instalment of a plan.
 *
 * The unique `(planId, periodIndex)` index is the idempotency guarantee behind
 * "同一计划同一期只生成一次": re-running the catch-up can never double-charge, even
 * if it runs twice in the same session or the app was closed across the due date.
 * [txnId] stays null until the period actually falls due.
 */
@Entity(
    tableName = "installment_period",
    indices = [
        Index(value = ["planId", "periodIndex"], unique = true),
        Index("txnId"),
        Index("dueDateKey"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = InstallmentPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class InstallmentPeriodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    /** 1-based. */
    val periodIndex: Int,
    val dueDateKey: String,
    val amountCents: Long,
    /** Set once the due date arrives and the transaction was written. */
    val txnId: Long? = null,
    val generatedAt: Long? = null,
)
