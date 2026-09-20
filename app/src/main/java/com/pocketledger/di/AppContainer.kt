package com.pocketledger.di

import android.content.Context
import com.pocketledger.R
import com.pocketledger.data.BudgetAlertChecker
import com.pocketledger.data.DataRepair
import com.pocketledger.data.InstallmentRunner
import com.pocketledger.data.LedgerDatabase
import com.pocketledger.data.backup.BackupService
import com.pocketledger.data.prefs.AppPreferences
import com.pocketledger.notify.BudgetNotifier
import com.pocketledger.widget.refreshLedgerWidgets
import com.pocketledger.data.Presets
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.repo.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Hand-rolled dependency container, deliberately in place of Hilt.
 *
 * The app has one database, one repository and a handful of view models; a
 * second annotation processor would add build-time version coupling for very
 * little. View models get what they need through an explicit factory, which is
 * also easier to read than a generated graph at this size.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Schema version the database is built at; a backup must match it to be restorable. */
    val schemaVersion: Int = LedgerDatabase.SCHEMA_VERSION

    /** Installed version name, recorded in a backup so a file can be traced to a build. */
    val appVersion: String by lazy {
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    /**
     * The name shown on the launcher.
     *
     * Read from resources rather than written out again at each use, so an exported file
     * name (`随心记账-我的账本-20260916.csv`) follows a rename instead of contradicting it.
     */
    val appName: String by lazy { appContext.getString(R.string.app_name) }

    /**
     * Application-scoped and never cancelled: the only work started here is
     * one-shot and idempotent (adopting pre-ledger data).
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: LedgerDatabase by lazy { LedgerDatabase.build(appContext) }

    val repository: LedgerRepository by lazy {
        val db = database
        LedgerRepository(
            ledgerDao = db.ledgerDao(),
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            txnDao = db.txnDao(),
            allowanceDao = db.allowanceDao(),
            budgetDao = db.budgetDao(),
            tagDao = db.tagDao(),
            termDao = db.termDao(),
            installmentDao = db.installmentDao(),
            importDao = db.importDao(),
        )
    }

    /**
     * True once startup work finished. The shell waits for this so an upgraded
     * install never flashes the onboarding screen before its ledger is adopted.
     */
    private val _startupComplete = MutableStateFlow(false)
    val startupComplete: StateFlow<Boolean> = _startupComplete.asStateFlow()

    /** Materialises due instalments; also called again after a plan is created. */
    suspend fun runInstallments(): Int = installmentRunner.run()

    /** Re-renders any placed home-screen widgets after the data they show changed. */
    suspend fun refreshWidgets() {
        refreshLedgerWidgets(appContext)
    }

    /** Catch-up for instalment plans that fell due while the app was closed. */
    private val installmentRunner: InstallmentRunner by lazy { InstallmentRunner(repository) }

    private val preferences: AppPreferences by lazy { AppPreferences(appContext) }

    /** App-level settings (pinned categories, fired budget alerts). */
    val appPreferences: AppPreferences get() = preferences

    /** Whole-application backup and restore; see [BackupService]. */
    val backupService: BackupService by lazy { BackupService(database, preferences) }

    /**
     * Puts the app back in a consistent state after a restore replaced everything.
     *
     * The selection is re-pointed first, because the ledger it named may not exist any
     * more -- and if it does not, every scoped query in the app is watching a ledger that
     * is gone and the whole UI reads as empty until the next launch.
     */
    suspend fun afterRestore() {
        repository.reselectLedger()
        runCatching { installmentRunner.run() }
        refreshWidgets()
    }

    private val budgetNotifier: BudgetNotifier by lazy { BudgetNotifier(appContext) }

    /** One-off data fix-ups that a schema migration cannot express. */
    private val dataRepair: DataRepair by lazy {
        DataRepair(
            accountDao = database.accountDao(),
            termDao = database.termDao(),
            ledgerDao = database.ledgerDao(),
            preferences = preferences,
        )
    }

    private val budgetAlertChecker: BudgetAlertChecker by lazy {
        BudgetAlertChecker(repository, preferences, budgetNotifier)
    }

    /** Posts any budget warning that has not been reported yet for this month. */
    suspend fun checkBudgetAlerts(): Int = budgetAlertChecker.check()

    init {
        appScope.launch {
            adoptPreLedgerDataIfNeeded()
            // After adoption, so a ledger seeded for pre-ledger data is repaired too.
            runCatching { dataRepair.run() }
            selectInitialLedger()
            // Instalments are caught up before startup is declared complete, so the
            // first frame already includes any charge that came due while closed.
            runCatching { installmentRunner.run() }
            runCatching { budgetAlertChecker.check() }
            refreshWidgets()
            _startupComplete.value = true
        }
    }

    /**
     * Picks the ledger the app opens on.
     *
     * With no ledger at all the selection stays null, which is the signal for
     * onboarding to take over.
     */
    private suspend fun selectInitialLedger() {
        val repo = repository
        if (repo.selectedLedgerId.value != null) return
        repo.activeLedgers().firstOrNull()?.let { repo.selectLedger(it.id) }
    }

    /**
     * Creates a ledger and gives it its starting categories and accounts.
     *
     * Seeding lives here rather than in the repository because it spans two DAOs and
     * the preset definitions; the repository stays a plain data gateway.
     */
    suspend fun createLedger(name: String, type: LedgerType): Long {
        val repo = repository
        val id = repo.addLedger(
            LedgerEntity(
                name = name,
                type = type,
                sortOrder = repo.ledgerMaxSortOrder() + 1,
            )
        )
        val ledger = repo.ledger(id)
        if (ledger != null) Presets.seedLedger(database, ledger)
        if (repo.selectedLedgerId.value == null) repo.selectLedger(id)
        return id
    }

    /**
     * Gives data from before the ledger feature a ledger to live in.
     *
     * The v1 -> v2 migration scoped every existing row to `ledgerId = 1` but could not
     * invent a ledger row, since that is data rather than schema. The first ledger in
     * an empty table takes id 1, so creating it here reconnects that data with no
     * user-visible step -- someone upgrading should not be asked to name a ledger
     * they never knew they had.
     *
     * A genuinely fresh install has no data at all and is deliberately skipped, so
     * onboarding runs instead.
     */
    private suspend fun adoptPreLedgerDataIfNeeded() {
        val db = database
        if (db.ledgerDao().count() > 0) return
        val hasLegacyData = db.txnDao().countAll() > 0 || db.accountDao().countAll() > 0
        if (!hasLegacyData) return

        val ledgerId = db.ledgerDao().insert(
            LedgerEntity(id = 1, name = Presets.DEFAULT_LEDGER_NAME, type = LedgerType.BUDGET)
        )
        db.ledgerDao().byId(ledgerId)?.let { Presets.seedLedger(db, it) }
    }
}
