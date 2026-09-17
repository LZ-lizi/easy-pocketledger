package com.pocketledger.data.repo

import com.pocketledger.data.dao.AccountBalance
import com.pocketledger.data.dao.AccountDao
import com.pocketledger.data.dao.AllowanceDao
import com.pocketledger.data.dao.CategoryDao
import com.pocketledger.data.dao.CategoryTotal
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.data.dao.MainCategoryTotal
import com.pocketledger.data.dao.MonthTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.dao.TagDao
import com.pocketledger.data.dao.TxnDao
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AllowanceEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.domain.DateKeys
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point to the data layer.
 *
 * Keeping one facade (rather than a repository per table) suits an app this size:
 * almost every screen needs transactions *plus* their categories and accounts, so
 * finer-grained repositories would just re-expose the same DAOs to every caller.
 * It can be split later without touching the UI, which only ever sees the model
 * types returned here.
 */
class LedgerRepository(
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val txnDao: TxnDao,
    private val allowanceDao: AllowanceDao,
    private val tagDao: TagDao,
) {

    // ------------------------------------------------------------------ accounts

    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeActive()

    fun observeAllAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeBalances(): Flow<List<AccountBalance>> = accountDao.observeBalances()

    suspend fun account(id: Long): AccountEntity? = accountDao.byId(id)

    // ---------------------------------------------------------------- categories

    fun observeCategories(kind: CategoryKind): Flow<List<CategoryEntity>> =
        categoryDao.observeByKind(kind)

    /**
     * Every category in one flow.
     *
     * The entry screen switches between expense and income in place, so observing
     * both kinds once avoids tearing down and rebuilding a query on every toggle.
     */
    fun observeAllCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    suspend fun categories(kind: CategoryKind): List<CategoryEntity> = categoryDao.all()
        .filter { it.kind == kind }

    suspend fun maxCategorySortOrder(kind: CategoryKind, parentId: Long?): Int =
        categoryDao.maxSortOrder(kind, parentId)

    suspend fun addCategory(category: CategoryEntity): Long = categoryDao.insert(category)

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    /** Re-parenting is how a debatable classification gets fixed; history follows. */
    suspend fun moveCategory(id: Long, newParentId: Long?) = categoryDao.moveTo(id, newParentId)

    suspend fun deleteCategory(id: Long) = categoryDao.softDelete(id)

    // -------------------------------------------------------------- transactions

    fun observeRows(monthKey: String): Flow<List<TxnRow>> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return txnDao.observeRows(start, end)
    }

    fun observeRows(startDateKey: String, endDateKey: String): Flow<List<TxnRow>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeRows(start, end)
    }

    fun observeTotals(monthKey: String): Flow<PeriodTotals> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return txnDao.observeTotals(start, end)
    }

    fun observeTotals(startDateKey: String, endDateKey: String): Flow<PeriodTotals> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeTotals(start, end)
    }

    fun observeMainCategoryTotals(monthKey: String): Flow<List<MainCategoryTotal>> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return txnDao.observeMainCategoryTotals(start, end)
    }

    fun observeMainCategoryTotals(startDateKey: String, endDateKey: String): Flow<List<MainCategoryTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeMainCategoryTotals(start, end)
    }

    fun observeCategoryTotals(startDateKey: String, endDateKey: String): Flow<List<CategoryTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeCategoryTotals(start, end)
    }

    fun observeDayTotals(monthKey: String): Flow<List<DayTotal>> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return txnDao.observeDayTotals(start, end)
    }

    fun observeMonthTotals(startDateKey: String, endDateKey: String): Flow<List<MonthTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeMonthTotals(start, end)
    }

    suspend fun transaction(id: Long): TxnEntity? = txnDao.byId(id)

    suspend fun addTransaction(txn: TxnEntity): Long = txnDao.insert(txn)

    suspend fun addTransactions(txns: List<TxnEntity>): List<Long> = txnDao.insertAll(txns)

    suspend fun updateTransaction(txn: TxnEntity) {
        txnDao.update(txn.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(id: Long) = txnDao.softDelete(id)

    suspend fun deleteTransactions(ids: List<Long>) = txnDao.softDeleteMany(ids)

    suspend fun recentMerchants(limit: Int = 40): List<String> = txnDao.recentMerchants(limit)

    /**
     * Recently used categories, which is what keeps a flat two-level grid quick to
     * tap: the four or five categories someone actually uses sit within reach.
     */
    suspend fun recentCategoryIds(limit: Int = 12): List<Long> = txnDao.recentCategoryIds(limit)

    suspend fun transactionCount(): Int = txnDao.count()

    suspend fun existingDedupeHashes(): Set<String> = txnDao.allDedupeHashes().toSet()

    suspend fun countByExternalNo(externalNo: String): Int = txnDao.countByExternalNo(externalNo)

    suspend fun countByDedupeHash(hash: String): Int = txnDao.countByDedupeHash(hash)

    suspend fun deleteImportBatch(batchId: Long) = txnDao.softDeleteBatch(batchId)

    // ----------------------------------------------------------------- allowance

    fun observeAllowance(monthKey: String): Flow<AllowanceEntity?> =
        allowanceDao.observeEffectiveFor(monthKey)

    fun observeAllowanceHistory(): Flow<List<AllowanceEntity>> = allowanceDao.observeHistory()

    suspend fun allowanceFor(monthKey: String): AllowanceEntity? = allowanceDao.effectiveFor(monthKey)

    suspend fun setAllowance(monthKey: String, amountCents: Long, note: String? = null) {
        allowanceDao.upsert(
            AllowanceEntity(periodKey = monthKey, amountCents = amountCents, note = note)
        )
    }

    suspend fun clearAllowance(monthKey: String) = allowanceDao.deletePeriod(monthKey)

    // ----------------------------------------------------------------------- tags

    fun observeTags() = tagDao.observeAll()

    suspend fun tagsFor(txnId: Long) = tagDao.tagsFor(txnId)
}
