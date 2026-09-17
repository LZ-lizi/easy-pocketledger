package com.pocketledger.di

import android.content.Context
import com.pocketledger.data.LedgerDatabase
import com.pocketledger.data.Presets
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.repo.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            tagDao = db.tagDao(),
            termDao = db.termDao(),
            installmentDao = db.installmentDao(),
        )
    }

    init {
        appScope.launch { adoptPreLedgerDataIfNeeded() }
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
        val hasLegacyData = db.txnDao().countAll() > 0 || db.accountDao().count() > 0
        if (!hasLegacyData) return

        val ledgerId = db.ledgerDao().insert(
            LedgerEntity(id = 1, name = Presets.DEFAULT_LEDGER_NAME, type = LedgerType.BUDGET)
        )
        db.ledgerDao().byId(ledgerId)?.let { Presets.seedLedger(db, it) }
    }
}
