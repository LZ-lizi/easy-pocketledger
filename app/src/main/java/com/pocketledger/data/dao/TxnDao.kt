package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.TxnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TxnDao {

    // ------------------------------------------------------------------ reads

    @Query(
        """
        SELECT * FROM txn
        WHERE deletedAt IS NULL AND localDateKey BETWEEN :startKey AND :endKey
        ORDER BY happenedAt DESC, id DESC
        """
    )
    fun observeRange(startKey: String, endKey: String): Flow<List<TxnEntity>>

    /**
     * Ledger rows joined with their category and accounts so the list needs no
     * follow-up lookups (the classic N+1 on a scrolling list).
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
        WHERE t.deletedAt IS NULL AND t.localDateKey BETWEEN :startKey AND :endKey
        ORDER BY t.happenedAt DESC, t.id DESC
        """
    )
    fun observeRows(startKey: String, endKey: String): Flow<List<TxnRow>>

    /**
     * Range totals for the month header.
     *
     * A transfer's fee is a real cost, so it counts as expense; the transferred
     * principal itself is neither income nor expense.
     */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE
                                WHEN type = 'EXPENSE' THEN amountCents
                                WHEN type = 'TRANSFER' THEN COALESCE(feeCents, 0)
                                ELSE 0
                            END), 0) AS expenseCents
        FROM txn
        WHERE deletedAt IS NULL AND isExcludedFromStats = 0
          AND localDateKey BETWEEN :startKey AND :endKey
        """
    )
    fun observeTotals(startKey: String, endKey: String): Flow<PeriodTotals>

    /**
     * Expense rolled up to main categories.
     *
     * `COALESCE(c.parentId, c.id)` maps a child category onto its parent and
     * leaves a root category as itself, so one query serves both levels.
     */
    @Query(
        """
        SELECT COALESCE(c.parentId, c.id) AS mainCategoryId,
               SUM(t.amountCents) AS totalCents
        FROM txn t
        JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL
          AND t.type = 'EXPENSE'
          AND t.isExcludedFromStats = 0
          AND t.localDateKey BETWEEN :startKey AND :endKey
        GROUP BY COALESCE(c.parentId, c.id)
        """
    )
    fun observeMainCategoryTotals(startKey: String, endKey: String): Flow<List<MainCategoryTotal>>

    @Query(
        """
        SELECT t.categoryId AS categoryId,
               SUM(t.amountCents) AS totalCents
        FROM txn t
        WHERE t.deletedAt IS NULL
          AND t.type = 'EXPENSE'
          AND t.isExcludedFromStats = 0
          AND t.categoryId IS NOT NULL
          AND t.localDateKey BETWEEN :startKey AND :endKey
        GROUP BY t.categoryId
        ORDER BY totalCents DESC
        """
    )
    fun observeCategoryTotals(startKey: String, endKey: String): Flow<List<CategoryTotal>>

    /** Per-day net for the calendar view; a month of rows, never the whole table. */
    @Query(
        """
        SELECT localDateKey AS localDateKey,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents
        FROM txn
        WHERE deletedAt IS NULL AND isExcludedFromStats = 0
          AND localDateKey BETWEEN :startKey AND :endKey
        GROUP BY localDateKey
        """
    )
    fun observeDayTotals(startKey: String, endKey: String): Flow<List<DayTotal>>

    @Query(
        """
        SELECT substr(localDateKey, 1, 7) AS monthKey,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents
        FROM txn
        WHERE deletedAt IS NULL AND isExcludedFromStats = 0
          AND localDateKey BETWEEN :startKey AND :endKey
        GROUP BY monthKey
        ORDER BY monthKey ASC
        """
    )
    fun observeMonthTotals(startKey: String, endKey: String): Flow<List<MonthTotal>>

    @Query("SELECT * FROM txn WHERE id = :id")
    suspend fun byId(id: Long): TxnEntity?

    @Query(
        """
        SELECT DISTINCT merchant FROM txn
        WHERE merchant IS NOT NULL AND merchant != '' AND deletedAt IS NULL
        ORDER BY happenedAt DESC
        LIMIT :limit
        """
    )
    suspend fun recentMerchants(limit: Int = 40): List<String>

    /** "Recently used" ordering is what keeps a flat two-level category grid fast to tap. */
    @Query(
        """
        SELECT categoryId FROM txn
        WHERE categoryId IS NOT NULL AND deletedAt IS NULL
        GROUP BY categoryId
        ORDER BY MAX(happenedAt) DESC
        LIMIT :limit
        """
    )
    suspend fun recentCategoryIds(limit: Int = 12): List<Long>

    // ------------------------------------------------------------- dedupe / import

    @Query("SELECT COUNT(*) FROM txn WHERE deletedAt IS NULL AND externalNo = :externalNo")
    suspend fun countByExternalNo(externalNo: String): Int

    @Query("SELECT COUNT(*) FROM txn WHERE deletedAt IS NULL AND dedupeHash = :hash")
    suspend fun countByDedupeHash(hash: String): Int

    @Query("SELECT DISTINCT dedupeHash FROM txn WHERE deletedAt IS NULL AND dedupeHash IS NOT NULL")
    suspend fun allDedupeHashes(): List<String>

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

    @Query("SELECT COUNT(*) FROM txn WHERE deletedAt IS NULL")
    suspend fun count(): Int
}
