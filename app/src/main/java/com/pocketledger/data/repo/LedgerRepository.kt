package com.pocketledger.data.repo

import com.pocketledger.data.dao.AccountBalance
import com.pocketledger.data.dao.AccountDao
import com.pocketledger.data.dao.AllowanceDao
import com.pocketledger.data.dao.BudgetDao
import com.pocketledger.data.dao.BudgetWithName
import com.pocketledger.data.dao.CategoryDao
import com.pocketledger.data.dao.CategoryTotal
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.data.dao.InstallmentDao
import com.pocketledger.data.dao.ImportDao
import com.pocketledger.data.dao.LedgerDao
import com.pocketledger.data.dao.MainCategoryTotal
import com.pocketledger.data.dao.MonthTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.dao.PlanPaidSummary
import com.pocketledger.data.dao.TagDao
import com.pocketledger.data.dao.TermDao
import com.pocketledger.data.dao.TxnDao
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AllowanceEntity
import com.pocketledger.data.entity.BudgetEntity
import com.pocketledger.data.entity.BudgetPeriodType
import com.pocketledger.data.entity.BudgetScope
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.ImportBatchEntity
import com.pocketledger.data.entity.ImportRuleEntity
import com.pocketledger.data.entity.InstallmentPeriodEntity
import com.pocketledger.data.entity.InstallmentPlanEntity
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.entity.TagEntity
import com.pocketledger.data.entity.TermEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.domain.DateKeys
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Wide enough to cover any date a person will enter, and still a plain string range. */
private const val WIDE_START_KEY = "0000-01-01"
private const val WIDE_END_KEY = "9999-12-31"

/** The small, read-only summary a home-screen widget renders. */
data class WidgetSnapshot(
    val ledgerName: String,
    val ledgerType: LedgerType,
    val monthExpenseCents: Long,
    /** Zero when the ledger has no allowance configured. */
    val allowanceCents: Long,
) {
    /** Null when there is nothing to measure against. */
    val remainingCents: Long? get() = allowanceCents.takeIf { it > 0L }?.minus(monthExpenseCents)

    val progress: Float
        get() = if (allowanceCents <= 0L) {
            0f
        } else {
            (monthExpenseCents.toDouble() / allowanceCents.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

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
    private val budgetDao: BudgetDao,
    private val tagDao: TagDao,
    private val termDao: TermDao,
    private val installmentDao: InstallmentDao,
    private val importDao: ImportDao,
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

    /**
     * The ledger currently being shown, reactively.
     *
     * Screens need its *type*, not just its id: a 累计模式 ledger has no accounts and
     * no allowance, so the home screen renders a different card entirely.
     */
    fun observeSelectedLedger(): Flow<LedgerEntity?> =
        selectedLedgerId.flatMapLatest { id ->
            if (id == null) flowOf(null) else ledgerDao.observeById(id)
        }

    suspend fun ledgerCount(): Int = ledgerDao.count()

    suspend fun ledgerMaxSortOrder(): Int = ledgerDao.maxSortOrder()

    /**
     * What a home-screen widget shows.
     *
     * Read with an explicit ledger rather than the selected one: a widget may be the
     * only reason the process started, so nothing has set the selection yet.
     */
    suspend fun widgetSnapshot(today: LocalDate = LocalDate.now()): WidgetSnapshot? {
        val ledger = ledgerDao.active().firstOrNull() ?: return null
        val periodKey = DateKeys.monthKey(today)
        val (start, end) = DateKeys.monthRange(periodKey)
        val totals = txnDao.observeTotals(ledger.id, start, end, null).first()
        val allowance = allowanceDao.effectiveFor(ledger.id, periodKey)
        return WidgetSnapshot(
            ledgerName = ledger.name,
            ledgerType = ledger.type,
            monthExpenseCents = totals.expenseCents,
            allowanceCents = allowance?.amountCents ?: 0L,
        )
    }

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

    /**
     * The selected ledger's hidden accounts.
     *
     * Feeds the entry keypad's fallback account: in a 累计模式 ledger nothing is
     * selectable, and without this the keypad would have no account to attach an entry
     * to and would refuse to save.
     */
    fun observeHiddenAccounts(): Flow<List<AccountEntity>> = scoped { accountDao.observeHidden(it) }

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
     * Removes an account from every picker and list.
     *
     * Soft, not hard: the transactions that referenced it stay in the ledger, so
     * deleting an account never rewrites past months of statistics.
     */
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

    /**
     * Writes into an explicitly named ledger.
     *
     * The instalment catch-up runs across every ledger at startup, so it cannot use
     * the "currently selected" ledger -- a plan in another ledger would otherwise
     * post its charge into whichever ledger happens to be open.
     */
    suspend fun addTransactionInLedger(ledgerId: Long, txn: TxnEntity): Long =
        txnDao.insert(txn.copy(ledgerId = ledgerId))

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

    /**
     * Every row in the current ledger, for export.
     *
     * Uses a deliberately wide date range rather than a new "select all" query: the
     * existing one is already indexed on `localDateKey`, and adding a second query
     * shape for one caller is how two code paths start disagreeing.
     */
    suspend fun allRowsForExport(): List<TxnRow> {
        val ledgerId = currentLedgerId.value ?: return emptyList()
        return txnDao.observeRows(ledgerId, WIDE_START_KEY, WIDE_END_KEY, null).first()
    }

    suspend fun existingDedupeHashes(): Set<String> =
        currentLedgerId.value?.let { txnDao.allDedupeHashes(it).toSet() } ?: emptySet()

    suspend fun countByExternalNo(externalNo: String): Int =
        currentLedgerId.value?.let { txnDao.countByExternalNo(it, externalNo) } ?: 0

    suspend fun countByDedupeHash(hash: String): Int =
        currentLedgerId.value?.let { txnDao.countByDedupeHash(it, hash) } ?: 0

    suspend fun generatedPlanKeys(): Set<String> =
        currentLedgerId.value?.let { txnDao.generatedPlanKeys(it).toSet() } ?: emptySet()

    suspend fun deleteImportBatch(batchId: Long) = txnDao.softDeleteBatch(batchId)

    // ---------------------------------------------------------------------- import

    fun observeImportBatches(): Flow<List<ImportBatchEntity>> =
        scoped { importDao.observeBatches(it) }

    /**
     * Keywords the user has already filed, as `keyword -> categoryId`.
     *
     * Read once per import rather than observed: the matcher runs over a whole file at
     * a time, and a mid-import change to the rules would make the preview disagree
     * with itself.
     */
    suspend fun importRules(): Map<String, Long> {
        val ledgerId = currentLedgerId.value ?: return emptyMap()
        return importDao.rules(ledgerId)
            .mapNotNull { rule -> rule.categoryId?.let { rule.keyword to it } }
            .toMap()
    }

    /** Records one import so it can be shown and undone as a unit. */
    suspend fun addImportBatch(batch: ImportBatchEntity): Long =
        importDao.insertBatch(batch.copy(ledgerId = writeLedgerId()))

    /** Writes imported rows in one call, stamped with their batch and ledger. */
    suspend fun addImportedTransactions(batchId: Long, txns: List<TxnEntity>): List<Long> {
        val ledgerId = writeLedgerId()
        return txnDao.insertAll(txns.map { it.copy(ledgerId = ledgerId, importBatchId = batchId) })
    }

    /** Remembers the category decisions made during an import. */
    suspend fun rememberImportRules(keywordToCategory: Map<String, Long>) {
        if (keywordToCategory.isEmpty()) return
        val ledgerId = writeLedgerId()
        importDao.upsertRules(
            keywordToCategory.map { (keyword, categoryId) ->
                ImportRuleEntity(ledgerId = ledgerId, keyword = keyword, categoryId = categoryId)
            }
        )
    }

    /**
     * Undoes one import.
     *
     * The transactions are soft-deleted, so a mistaken undo is recoverable, but the
     * batch record is removed outright -- a batch that still listed rows no longer in
     * the ledger would be a lie about what happened.
     */
    suspend fun undoImport(batchId: Long) {
        txnDao.softDeleteBatch(batchId)
        importDao.deleteBatch(batchId)
    }

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

    /**
     * The allowance in force for one month of one *named* ledger.
     *
     * The overall monthly cap lives here and nowhere else: it is the same number the
     * home card shows as 生活费, so the ledger-management page reads and writes it
     * through this pair rather than keeping a second copy in the budget table.
     */
    fun observeAllowanceFor(ledgerId: Long, monthKey: String): Flow<AllowanceEntity?> =
        allowanceDao.observeEffectiveFor(ledgerId, monthKey)

    /**
     * Sets one month's overall cap for a named ledger.
     *
     * A zero is **stored**, not deleted, matching what the 明细页 card does. The
     * allowance rolls forward from the most recent earlier month, so deleting this
     * month's row would simply resurrect last month's figure; an explicit zero is the
     * only way "no budget this month" can be said.
     */
    suspend fun setAllowanceFor(ledgerId: Long, monthKey: String, amountCents: Long) {
        allowanceDao.upsert(
            AllowanceEntity(
                ledgerId = ledgerId,
                periodKey = monthKey,
                amountCents = amountCents,
            )
        )
    }

    // -------------------------------------------------------------------- budgets

    /**
     * Caps for one month.
     *
     * `categoryId == 0` is the overall cap, a top-level category id is that 大类's
     * cap, and a leaf id is a single category's cap. One table covers all three
     * levels because a 大类 is an ordinary category row.
     */
    fun observeBudgets(periodKey: String): Flow<List<BudgetWithName>> =
        scoped { budgetDao.observeForPeriodWithNames(it, BudgetPeriodType.MONTH, periodKey) }

    /** Setting zero clears the cap rather than storing a meaningless zero. */
    suspend fun setBudget(periodKey: String, categoryId: Long, amountCents: Long) {
        setBudgetFor(writeLedgerId(), periodKey, categoryId, amountCents)
    }

    // ------------------------------------------------- budgets for one named ledger

    /**
     * Budget and spending aggregates for a specific ledger, regardless of which one
     * is selected.
     *
     * Budgets are configured from the ledger management page, where the whole point
     * is to set up a ledger other than the one currently open -- so those calls cannot
     * go through the "selected ledger" indirection.
     */
    fun observeBudgetsFor(ledgerId: Long, periodKey: String): Flow<List<BudgetWithName>> =
        budgetDao.observeForPeriodWithNames(ledgerId, BudgetPeriodType.MONTH, periodKey)

    fun observeTotalsFor(
        ledgerId: Long,
        startDateKey: String,
        endDateKey: String,
    ): Flow<PeriodTotals> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeTotals(ledgerId, start, end, null)
    }

    fun observeMainCategoryTotalsFor(
        ledgerId: Long,
        startDateKey: String,
        endDateKey: String,
    ): Flow<List<MainCategoryTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeMainCategoryTotals(ledgerId, start, end, null)
    }

    fun observeCategoryTotalsFor(
        ledgerId: Long,
        startDateKey: String,
        endDateKey: String,
    ): Flow<List<CategoryTotal>> {
        val (start, end) = DateKeys.range(startDateKey, endDateKey)
        return txnDao.observeCategoryTotals(ledgerId, start, end, null)
    }

    fun observeCategoriesFor(ledgerId: Long, kind: CategoryKind): Flow<List<CategoryEntity>> =
        categoryDao.observeByKind(ledgerId, kind)

    suspend fun setBudgetFor(
        ledgerId: Long,
        periodKey: String,
        categoryId: Long,
        amountCents: Long,
    ) {
        if (amountCents <= 0L) {
            budgetDao.delete(ledgerId, BudgetPeriodType.MONTH, periodKey, categoryId)
        } else {
            budgetDao.upsert(
                BudgetEntity(
                    ledgerId = ledgerId,
                    periodType = BudgetPeriodType.MONTH,
                    periodKey = periodKey,
                    scope = if (categoryId == 0L) BudgetScope.TOTAL else BudgetScope.BY_CATEGORY,
                    categoryId = categoryId,
                    amountCents = amountCents,
                )
            )
        }
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

    /** Paid progress for every plan, so the list needs one query rather than N. */
    fun observePaidSummaries(): Flow<List<PlanPaidSummary>> = installmentDao.observePaidSummaries()

    suspend fun deleteInstallmentPeriods(planId: Long) = installmentDao.deletePeriods(planId)
}
