package com.pocketledger.data.repo

import com.pocketledger.data.dao.AccountBalance
import com.pocketledger.data.dao.AccountDao
import com.pocketledger.data.dao.AllowanceDao
import com.pocketledger.data.dao.CategoryDao
import com.pocketledger.data.dao.CategoryTotal
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.data.dao.InstallmentDao
import com.pocketledger.data.dao.LedgerDao
import com.pocketledger.data.dao.MainCategoryTotal
import com.pocketledger.data.dao.MonthTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.dao.TagDao
import com.pocketledger.data.dao.TermDao
import com.pocketledger.data.dao.TxnDao
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AllowanceEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.InstallmentPeriodEntity
import com.pocketledger.data.entity.InstallmentPlanEntity
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.TagEntity
import com.pocketledger.data.entity.TermEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.domain.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

/**
 * Single entry point to the data layer.
 *
 * **Every read is scoped to the selected ledger, centrally.** Rather than threading a
 * `ledgerId` through every call site, the observe methods re-subscribe whenever
 * [selectedLedgerId] changes. That means switching a ledger is one write, no screen
 * has to remember to filter, and it is impossible for two ledgers to contribute to
 * the same total by omission.
 *
 * Writes stamp the current ledger id themselves, so callers never construct an
 * entity with the wrong one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val txnDao: TxnDao,
    private val allowanceDao: AllowanceDao,
    private val tagDao: TagDao,
    private val termDao: TermDao,
    private val installmentDao: InstallmentDao,
) {

    private val currentLedgerId = MutableStateFlow<Long?>(null)

    /** Null until onboarding has created or adopted a ledger. */
    val selectedLedgerId: StateFlow<Long?> = currentLedgerId.asStateFlow()

    fun selectLedger(id: Long) {
        currentLedgerId.value = id
    }

    /** Re-runs [block] against the current ledger, and again whenever it changes. */
    private fun <T> scoped(block: (Long) -> Flow<T>): Flow<T> =
        selectedLedgerId.filterNotNull().flatMapLatest(block)

    /**
     * The ledger to write into.
     *
     * Throws rather than falling back to some default: writing a transaction into
     * whichever ledger happens to be id 1 would be a silent, hard-to-notice
     * corruption. Onboarding gates every write path, so this is unreachable in
     * practice and loud if that invariant ever breaks.
     */
    private fun writeLedgerId(): Long =
        checkNotNull(currentLedgerId.value) { "No ledger selected; onboarding must run first" }

    // --------------------------------------------------------------------- ledgers

    fun observeLedgers(): Flow<List<LedgerEntity>> = ledgerDao.observeAll()

    suspend fun ledgers(): List<LedgerEntity> = ledgerDao.all()

    suspend fun activeLedgers(): List<LedgerEntity> = ledgerDao.active()

    suspend fun ledger(id: Long): LedgerEntity? = ledgerDao.byId(id)

    suspend fun ledgerCount(): Int = ledgerDao.count()

    suspend fun ledgerMaxSortOrder(): Int = ledgerDao.maxSortOrder()

    suspend fun addLedger(ledger: LedgerEntity): Long = ledgerDao.insert(ledger)

    suspend fun updateLedger(ledger: LedgerEntity) {
        ledgerDao.update(ledger.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setLedgerArchived(id: Long, archived: Boolean) = ledgerDao.setArchived(id, archived)

    suspend fun deleteLedger(id: Long) = ledgerDao.softDelete(id)

    // ------------------------------------------------------------------ accounts

    fun observeAccounts(): Flow<List<AccountEntity>> = scoped { accountDao.observeActive(it) }

    fun observeAllAccounts(): Flow<List<AccountEntity>> = scoped { accountDao.observeAll(it) }

    fun observeBalances(): Flow<List<AccountBalance>> = scoped { accountDao.observeBalances(it) }

    suspend fun account(id: Long): AccountEntity? = accountDao.byId(id)

    suspend fun accountsSnapshot(): List<AccountEntity> =
        currentLedgerId.value?.let { accountDao.all(it) } ?: emptyList()

    suspend fun accountCount(): Int = currentLedgerId.value?.let { accountDao.count(it) } ?: 0

    suspend fun addAccount(account: AccountEntity): Long =
        accountDao.insert(account.copy(ledgerId = writeLedgerId()))

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.update(account.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Archiving hides an account from pickers without touching its history, which
     * is the right move for a card you stopped using but still want reports for.
     */
    suspend fun setAccountArchived(id: Long, archived: Boolean) = accountDao.setArchived(id, archived)

    suspend fun deleteAccount(id: Long) = accountDao.softDelete(id)

    // ---------------------------------------------------------------- categories

    fun observeCategories(kind: CategoryKind): Flow<List<CategoryEntity>> =
        scoped { categoryDao.observeByKind(it, kind) }

    /**
     * Every category in one flow.
     *
     * The entry screen switches between expense and income in place, so observing
     * both kinds once avoids tearing down and rebuilding a query on every toggle.
     */
    fun observeAllCategories(): Flow<List<CategoryEntity>> = scoped { categoryDao.observeAll(it) }

    suspend fun categoriesSnapshot(): List<CategoryEntity> =
        currentLedgerId.value?.let { categoryDao.all(it) } ?: emptyList()

    suspend fun categoryCount(): Int = currentLedgerId.value?.let { categoryDao.count(it) } ?: 0

    suspend fun maxCategorySortOrder(kind: CategoryKind, parentId: Long?): Int =
        categoryDao.maxSortOrder(writeLedgerId(), kind, parentId)

    suspend fun addCategory(category: CategoryEntity): Long =
        categoryDao.insert(category.copy(ledgerId = writeLedgerId()))

    suspend fun updateCategory(category: CategoryEntity) = categoryDao.update(category)

    /** Re-parenting is how a debatable classification gets fixed; history follows. */
    suspend fun moveCategory(id: Long, newParentId: Long?) = categoryDao.moveTo(id, newParentId)

    suspend fun deleteCategory(id: Long) = categoryDao.softDelete(id)

    // -------------------------------------------------------------- transactions

    fun observeRows(monthKey: String, accountId: Long? = null): Flow<List<TxnRow>> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return scoped { txnDao.observeRows(it, start, end, accountId) }
    }

    fun observeRows(
        startDateKey: String,
        endDateKey: String,
        accountId: Long? = null,
    ): Flow<List<TxnRow>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return scoped { txnDao.observeRows(it, start, end, accountId) }
    }

    fun observeTotals(monthKey: String): Flow<PeriodTotals> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return scoped { txnDao.observeTotals(it, start, end, null) }
    }

    /**
     * [mainCategoryId] narrows every figure on the statistics page to one 大类; null
     * means "everything".
     */
    fun observeTotals(
        startDateKey: String,
        endDateKey: String,
        mainCategoryId: Long? = null,
    ): Flow<PeriodTotals> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return scoped { txnDao.observeTotals(it, start, end, mainCategoryId) }
    }

    fun observeMainCategoryTotals(monthKey: String): Flow<List<MainCategoryTotal>> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return scoped { txnDao.observeMainCategoryTotals(it, start, end, null) }
    }

    fun observeMainCategoryTotals(
        startDateKey: String,
        endDateKey: String,
        mainCategoryId: Long? = null,
    ): Flow<List<MainCategoryTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return scoped { txnDao.observeMainCategoryTotals(it, start, end, mainCategoryId) }
    }

    fun observeCategoryTotals(
        startDateKey: String,
        endDateKey: String,
        mainCategoryId: Long? = null,
    ): Flow<List<CategoryTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return scoped { txnDao.observeCategoryTotals(it, start, end, mainCategoryId) }
    }

    fun observeDayTotals(monthKey: String): Flow<List<DayTotal>> {
        val (start, end) = DateKeys.monthRange(monthKey)
        return scoped { txnDao.observeDayTotals(it, start, end) }
    }

    fun observeMonthTotals(
        startDateKey: String,
        endDateKey: String,
        mainCategoryId: Long? = null,
    ): Flow<List<MonthTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return scoped { txnDao.observeMonthTotals(it, start, end, mainCategoryId) }
    }

    suspend fun transaction(id: Long): TxnEntity? = txnDao.byId(id)

    suspend fun addTransaction(txn: TxnEntity): Long =
        txnDao.insert(txn.copy(ledgerId = writeLedgerId()))

    suspend fun addTransactions(txns: List<TxnEntity>): List<Long> {
        val id = writeLedgerId()
        return txnDao.insertAll(txns.map { it.copy(ledgerId = id) })
    }

    suspend fun updateTransaction(txn: TxnEntity) {
        txnDao.update(txn.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteTransaction(id: Long) = txnDao.softDelete(id)

    suspend fun deleteTransactions(ids: List<Long>) = txnDao.softDeleteMany(ids)

    suspend fun recentMerchants(limit: Int = 40): List<String> =
        currentLedgerId.value?.let { txnDao.recentMerchants(it, limit) } ?: emptyList()

    /**
     * Recently used categories, which is what keeps a flat two-level grid quick to
     * tap: the four or five categories someone actually uses sit within reach.
     */
    suspend fun recentCategoryIds(limit: Int = 12): List<Long> =
        currentLedgerId.value?.let { txnDao.recentCategoryIds(it, limit) } ?: emptyList()

    suspend fun transactionCount(): Int = currentLedgerId.value?.let { txnDao.count(it) } ?: 0

    suspend fun existingDedupeHashes(): Set<String> =
        currentLedgerId.value?.let { txnDao.allDedupeHashes(it).toSet() } ?: emptySet()

    suspend fun countByExternalNo(externalNo: String): Int =
        currentLedgerId.value?.let { txnDao.countByExternalNo(it, externalNo) } ?: 0

    suspend fun countByDedupeHash(hash: String): Int =
        currentLedgerId.value?.let { txnDao.countByDedupeHash(it, hash) } ?: 0

    suspend fun generatedPlanKeys(): Set<String> =
        currentLedgerId.value?.let { txnDao.generatedPlanKeys(it).toSet() } ?: emptySet()

    suspend fun deleteImportBatch(batchId: Long) = txnDao.softDeleteBatch(batchId)

    // ----------------------------------------------------------------- allowance

    fun observeAllowance(monthKey: String): Flow<AllowanceEntity?> =
        scoped { allowanceDao.observeEffectiveFor(it, monthKey) }

    fun observeAllowanceHistory(): Flow<List<AllowanceEntity>> =
        scoped { allowanceDao.observeHistory(it) }

    suspend fun allowanceFor(monthKey: String): AllowanceEntity? =
        currentLedgerId.value?.let { allowanceDao.effectiveFor(it, monthKey) }

    suspend fun setAllowance(monthKey: String, amountCents: Long, note: String? = null) {
        allowanceDao.upsert(
            AllowanceEntity(
                ledgerId = writeLedgerId(),
                periodKey = monthKey,
                amountCents = amountCents,
                note = note,
            )
        )
    }

    suspend fun clearAllowance(monthKey: String) {
        currentLedgerId.value?.let { allowanceDao.deletePeriod(it, monthKey) }
    }

    // ----------------------------------------------------------------------- tags

    fun observeTags(): Flow<List<TagEntity>> = scoped { tagDao.observeAll(it) }

    suspend fun tagsFor(txnId: Long) = tagDao.tagsFor(txnId)

    // ---------------------------------------------------------------------- terms

    /** Terms back the statistics page's 「学期」 time range. */
    fun observeTerms(): Flow<List<TermEntity>> = scoped { termDao.observeAll(it) }

    suspend fun terms(): List<TermEntity> =
        currentLedgerId.value?.let { termDao.all(it) } ?: emptyList()

    suspend fun term(id: Long): TermEntity? = termDao.byId(id)

    suspend fun addTerm(term: TermEntity): Long =
        termDao.insert(term.copy(ledgerId = writeLedgerId()))

    suspend fun updateTerm(term: TermEntity) = termDao.update(term)

    suspend fun deleteTerm(id: Long) = termDao.delete(id)

    /** At most one term is pre-selected, so the picker has an obvious default. */
    suspend fun setActiveTerm(id: Long) {
        val ledgerId = writeLedgerId()
        termDao.clearActive(ledgerId)
        termDao.byId(id)?.let { termDao.update(it.copy(isActive = true)) }
    }

    // --------------------------------------------------------------- installments

    fun observeInstallmentPlans(): Flow<List<InstallmentPlanEntity>> =
        scoped { installmentDao.observePlans(it) }

    fun observeActiveInstallmentPlans(): Flow<List<InstallmentPlanEntity>> =
        scoped { installmentDao.observeActivePlans(it) }

    /** Every active plan in every ledger; the due-date catch-up runs across all. */
    suspend fun allActiveInstallmentPlans(): List<InstallmentPlanEntity> =
        installmentDao.allActivePlans()

    suspend fun installmentPlan(id: Long): InstallmentPlanEntity? = installmentDao.plan(id)

    suspend fun addInstallmentPlan(plan: InstallmentPlanEntity): Long =
        installmentDao.insertPlan(plan.copy(ledgerId = writeLedgerId()))

    suspend fun updateInstallmentPlan(plan: InstallmentPlanEntity) {
        installmentDao.updatePlan(plan.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun setInstallmentPlanActive(id: Long, active: Boolean) =
        installmentDao.setActive(id, active)

    suspend fun deleteInstallmentPlan(id: Long) = installmentDao.deletePlan(id)

    fun observeInstallmentPeriods(planId: Long): Flow<List<InstallmentPeriodEntity>> =
        installmentDao.observePeriods(planId)

    suspend fun installmentPeriods(planId: Long): List<InstallmentPeriodEntity> =
        installmentDao.periods(planId)

    suspend fun insertInstallmentPeriodIfAbsent(period: InstallmentPeriodEntity): Long =
        installmentDao.insertPeriodIfAbsent(period)

    /** Returns 1 when this call claimed the period, 0 when someone already had. */
    suspend fun claimInstallmentPeriod(periodId: Long, txnId: Long): Int =
        installmentDao.claimPeriod(periodId, txnId)

    suspend fun paidInstallmentCount(planId: Long): Int = installmentDao.paidPeriodCount(planId)

    suspend fun deleteInstallmentPeriods(planId: Long) = installmentDao.deletePeriods(planId)
}
