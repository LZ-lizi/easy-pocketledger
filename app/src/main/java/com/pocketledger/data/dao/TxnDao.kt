package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.TxnEntity
import kotlinx.coroutines.flow.Flow

/**
 * Every read is scoped to one ledger. Nothing in this app is global: two ledgers
 * must never contribute to the same total.
 */
@Dao
interface TxnDao {

    // ------------------------------------------------------------------ reads

    @Query(
        """
        SELECT * FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL
          AND localDateKey BETWEEN :startKey AND :endKey
        ORDER BY happenedAt DESC, id DESC
        """
    )
    fun observeRange(ledgerId: Long, startKey: String, endKey: String): Flow<List<TxnEntity>>

    /**
     * Ledger rows joined with their category and accounts so the list needs no
     * follow-up lookups (the classic N+1 on a scrolling list).
     *
     * [accountFilter] serves the account-detail screen: `null` means "every
     * account", otherwise the row must touch that account on either side, so a
     * transfer appears in the ledger of both accounts it connects.
     */
    @Query(
        """
        SELECT t.id AS id,
               t.type AS type,
               t.amountCents AS amountCents,
               t.feeCents AS feeCents,
               t.categoryId AS categoryId,
               c.name AS categoryName,
               c.iconKey AS categoryIconKey,
               c.colorArgb AS categoryColorArgb,
               c.parentId AS mainCategoryId,
               t.accountId AS accountId,
               a.name AS accountName,
               t.toAccountId AS toAccountId,
               ta.name AS toAccountName,
               t.merchant AS merchant,
               t.note AS note,
               t.happenedAt AS happenedAt,
               t.localDateKey AS localDateKey,
               t.source AS source,
               t.isExcludedFromStats AS isExcludedFromStats,
               t.importBatchId AS importBatchId
        FROM txn t
        LEFT JOIN category c ON c.id = t.categoryId
        LEFT JOIN account a ON a.id = t.accountId
        LEFT JOIN account ta ON ta.id = t.toAccountId
        WHERE t.ledgerId = :ledgerId AND t.deletedAt IS NULL
          AND t.localDateKey BETWEEN :startKey AND :endKey
          AND (:accountFilter IS NULL
               OR t.accountId = :accountFilter
               OR t.toAccountId = :accountFilter)
        ORDER BY t.happenedAt DESC, t.id DESC
        """
    )
    fun observeRows(
        ledgerId: Long,
        startKey: String,
        endKey: String,
        accountFilter: Long?,
    ): Flow<List<TxnRow>>

    /**
     * Range totals for the period header.
     *
     * A transfer's fee is a real cost, so it counts as expense; the transferred
     * principal itself is neither income nor expense.
     *
     * [mainCategoryId] filters to one 大类: a leaf matches through `c.parentId` and a
     * top-level row through `c.id`, so one parameter covers both.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE
                                WHEN t.type = 'EXPENSE' THEN t.amountCents
                                WHEN t.type = 'TRANSFER' THEN COALESCE(t.feeCents, 0)
                                ELSE 0
                            END), 0) AS expenseCents
        FROM txn t
        LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.ledgerId = :ledgerId AND t.deletedAt IS NULL AND t.isExcludedFromStats = 0
          AND t.localDateKey BETWEEN :startKey AND :endKey
          AND (:mainCategoryId IS NULL
               OR c.id = :mainCategoryId
               OR c.parentId = :mainCategoryId)
        """
    )
    fun observeTotals(
        ledgerId: Long,
        startKey: String,
        endKey: String,
        mainCategoryId: Long?,
    ): Flow<PeriodTotals>

    /**
     * Expense rolled up to top-level categories.
     *
     * `COALESCE(c.parentId, c.id)` maps a leaf onto its 大类 and leaves a root
     * category as itself, so one query serves both the 小类 and 大类 views.
     */
    @Query(
        """
        SELECT COALESCE(c.parentId, c.id) AS mainCategoryId,
               SUM(t.amountCents) AS totalCents
        FROM txn t
        JOIN category c ON c.id = t.categoryId
        WHERE t.ledgerId = :ledgerId AND t.deletedAt IS NULL
          AND t.type = 'EXPENSE'
          AND t.isExcludedFromStats = 0
          AND t.localDateKey BETWEEN :startKey AND :endKey
          AND (:mainCategoryId IS NULL
               OR c.id = :mainCategoryId
               OR c.parentId = :mainCategoryId)
        GROUP BY COALESCE(c.parentId, c.id)
        """
    )
    fun observeMainCategoryTotals(
        ledgerId: Long,
        startKey: String,
        endKey: String,
        mainCategoryId: Long?,
    ): Flow<List<MainCategoryTotal>>

    @Query(
        """
        SELECT t.categoryId AS categoryId,
               SUM(t.amountCents) AS totalCents
        FROM txn t
        LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.ledgerId = :ledgerId AND t.deletedAt IS NULL
          AND t.type = 'EXPENSE'
          AND t.isExcludedFromStats = 0
          AND t.categoryId IS NOT NULL
          AND t.localDateKey BETWEEN :startKey AND :endKey
          AND (:mainCategoryId IS NULL
               OR c.id = :mainCategoryId
               OR c.parentId = :mainCategoryId)
        GROUP BY t.categoryId
        ORDER BY totalCents DESC
        """
    )
    fun observeCategoryTotals(
        ledgerId: Long,
        startKey: String,
        endKey: String,
        mainCategoryId: Long?,
    ): Flow<List<CategoryTotal>>

    /** Per-day net for the calendar view; a month of rows, never the whole table. */
    @Query(
        """
        SELECT localDateKey AS localDateKey,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents
        FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND isExcludedFromStats = 0
          AND localDateKey BETWEEN :startKey AND :endKey
        GROUP BY localDateKey
        """
    )
    fun observeDayTotals(ledgerId: Long, startKey: String, endKey: String): Flow<List<DayTotal>>

    @Query(
        """
        SELECT substr(t.localDateKey, 1, 7) AS monthKey,
               COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amountCents ELSE 0 END), 0) AS expenseCents
        FROM txn t
        LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.ledgerId = :ledgerId AND t.deletedAt IS NULL AND t.isExcludedFromStats = 0
          AND t.localDateKey BETWEEN :startKey AND :endKey
          AND (:mainCategoryId IS NULL
               OR c.id = :mainCategoryId
               OR c.parentId = :mainCategoryId)
        GROUP BY monthKey
        ORDER BY monthKey ASC
        """
    )
    fun observeMonthTotals(
        ledgerId: Long,
        startKey: String,
        endKey: String,
        mainCategoryId: Long?,
    ): Flow<List<MonthTotal>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query(
        """
        SELECT DISTINCT merchant FROM txn
        WHERE ledgerId = :ledgerId AND merchant IS NOT NULL AND merchant != ''
          AND deletedAt IS NULL
        ORDER BY happenedAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentMerchants(ledgerId: Long, limit: Int): List<String>

    /** "Recently used" ordering is what keeps a flat category grid fast to tap. */
    @Query(
        """
        SELECT categoryId FROM txn
        WHERE ledgerId = :ledgerId AND categoryId IS NOT NULL AND deletedAt IS NULL
        GROUP BY categoryId
        ORDER BY MAX(happenedAt) DESC
        LIMIT :limit
        """
    )
    suspend fun recentCategoryIds(ledgerId: Long, limit: Int): List<Long>

    // ------------------------------------------------------------- dedupe / import

    @Query(
        """
        SELECT COUNT(*) FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND externalNo = :externalNo
        """
    )
    suspend fun countByExternalNo(ledgerId: Long, externalNo: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND dedupeHash = :hash
        """
    )
    suspend fun countByDedupeHash(ledgerId: Long, hash: String): Int

    @Query(
        """
        SELECT DISTINCT dedupeHash FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND dedupeHash IS NOT NULL
        """
    )
    suspend fun allDedupeHashes(ledgerId: Long): List<String>

    /**
     * The bill transaction numbers already stored in this ledger.
     *
     * Read as a set alongside [allDedupeHashes]. A bill's own 交易单号 identifies the
     * transaction, so it survives changes to how a note or a timestamp is derived from
     * the file -- changes that would otherwise make rows imported by an earlier version
     * unrecognisable and re-import them as duplicates.
     */
    @Query(
        """
        SELECT DISTINCT externalNo FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND externalNo IS NOT NULL
        """
    )
    suspend fun allExternalNos(ledgerId: Long): List<String>

    /** Existing plan periods in this ledger, so the catch-up can skip them cheaply. */
    @Query(
        """
        SELECT planId || ':' || planPeriodIndex FROM txn
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL
          AND planId IS NOT NULL AND planPeriodIndex IS NOT NULL
        """
    )
    suspend fun generatedPlanKeys(ledgerId: Long): List<String>

    // ----------------------------------------------------------------- writes

    @Insert
    suspend fun insert(txn: TxnEntity): Long

    @Insert
    suspend fun insertAll(txns: List<TxnEntity>): List<Long>

    @Update
    suspend fun update(txn: TxnEntity)

    @Query("UPDATE txn SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE txn SET deletedAt = :now, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteMany(ids: List<Long>, now: Long = System.currentTimeMillis())

    /** Rolls back an entire import in one statement. */
    @Query("UPDATE txn SET deletedAt = :now, updatedAt = :now WHERE importBatchId = :batchId")
    suspend fun softDeleteBatch(batchId: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM txn WHERE ledgerId = :ledgerId AND deletedAt IS NULL")
    suspend fun count(ledgerId: Long): Int

    /** Unscoped on purpose: used once at startup to detect pre-ledger data. */
    @Query("SELECT COUNT(*) FROM txn WHERE deletedAt IS NULL")
    suspend fun countAll(): Int
}
