package com.pocketledger.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Schema v1.
 *
 * Conventions that hold across every table:
 * - Money is `Long` **cents**, never a floating-point type.
 * - Time is stored twice: `happenedAt` (epoch millis, for ordering) and
 *   `localDateKey` (`YYYY-MM-DD`, for day grouping and indexes). Storing the key
 *   avoids recomputing the local date in every query and sidesteps timezone drift.
 * - Mutable rows carry `deletedAt` for soft deletion so undo / import rollback /
 *   backup merge stay possible.
 * - Enums are stored by name; Room converts them.
 */

@Entity(
    tableName = "account",
    indices = [Index("sortOrder"), Index("isArchived")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

/**
 * Two levels only in the shipped presets: `parentId == null` is a **main
 * category** (日常生活 / 娱乐开销), and pointing at one makes a concrete item such
 * as 食堂 / 外卖. The model itself allows deeper nesting.
 *
 * Treating a main category as an ordinary row is what lets budgets, filters and
 * statistics aggregate by it with no extra concept or enum.
 */
@Entity(
    tableName = "category",
    indices = [Index("parentId"), Index(value = ["kind", "parentId", "sortOrder"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: CategoryKind,
    val parentId: Long? = null,
    val iconKey: String = "more",
    val colorArgb: Int = 0,
    val sortOrder: Int = 0,
    /**
     * Stable identity for the two shipped main categories, e.g. `"daily"` /
     * `"leisure"`. The display name is user-editable, so the 日常 vs 娱乐 split
     * cannot key off it, and sort order is reorderable too.
     */
    val systemKey: String? = null,
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
        Index("happenedAt"),
        Index("localDateKey"),
        Index("accountId"),
        Index("toAccountId"),
        Index("categoryId"),
        Index("type"),
        Index("importBatchId"),
        Index(value = ["externalNo"], unique = false),
        Index(value = ["dedupeHash"], unique = false),
    ],
)
data class TxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "tag",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    indices = [Index(value = ["periodType", "periodKey", "scope", "categoryId"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    indices = [Index(value = ["periodKey"], unique = true)],
)
data class AllowanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodKey: String,
    val amountCents: Long,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/** A named date range such as 「2026 秋季学期」, used by the statistics time filter. */
@Entity(tableName = "term")
data class TermEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDateKey: String,
    val endDateKey: String,
    /** The term pre-selected on the statistics page. */
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 愿望清单: park a purchase, decide later. */
@Entity(tableName = "wish")
data class WishEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
@Entity(tableName = "saving_goal")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
@Entity(tableName = "template")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    indices = [Index(value = ["keyword"], unique = true)],
)
data class ImportRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val source: TxnSource? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Groups one import so the whole batch can be rolled back in a single action. */
@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: TxnSource,
    val fileName: String? = null,
    val txnCount: Int = 0,
    val skippedCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis(),
)
