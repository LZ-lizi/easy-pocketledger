package com.pocketledger.data

import com.pocketledger.data.dao.AccountDao
import com.pocketledger.data.dao.LedgerDao
import com.pocketledger.data.dao.TermDao
import com.pocketledger.data.prefs.AppPreferences

/**
 * One-off repairs for data that a schema migration cannot express.
 *
 * A migration rebuilds tables; it cannot tell an untouched preset row from one the
 * user has since edited. Repairs belong here instead, where they can match on values
 * and be skipped once they have run.
 *
 * Each repair is guarded by a marker in [AppPreferences] so it runs at most once,
 * even when it found nothing to change. That guard matters: these match on a preset
 * *name*, and without it an account the user deliberately called 支付宝 a month from
 * now would be renamed behind their back.
 *
 * The orphan re-homing is the one deliberate exception -- see [rehomeOrphanTerms].
 */
class DataRepair(
    private val accountDao: AccountDao,
    private val termDao: TermDao,
    private val ledgerDao: LedgerDao,
    private val preferences: AppPreferences,
) {

    /** Executes every repair not yet recorded; returns the ids of the ones that ran. */
    suspend fun run(): List<String> = buildList {
        if (renameLegacyAlipay()) add(KEY_ALIPAY)
        if (rehomeOrphanTerms()) add(KEY_ORPHAN_TERMS)
    }

    /**
     * 支付宝 -> 支付宝余额.
     *
     * The preset account tracks only the balance held inside Alipay -- not 余额宝 and
     * not a linked card -- so the old name promised more than it measured. Only the
     * exact legacy name is touched; anything the user renamed is left alone.
     */
    private suspend fun renameLegacyAlipay(): Boolean {
        if (preferences.repairDone(KEY_ALIPAY)) return false
        accountDao.renameAllNamed(Presets.ALIPAY_LEGACY_NAME, Presets.ALIPAY_NAME)
        // Marked done even with no matching row: a fresh install already ships the new
        // name, and re-checking on every launch would eventually catch a user's own.
        preferences.markRepairDone(KEY_ALIPAY)
        return true
    }

    /**
     * Gives back 学期 rows that point at no ledger.
     *
     * Deliberately **not** guarded by a run-once marker, unlike every other repair here.
     * Those match on values and would be wrong to repeat; this one can only ever move a
     * row that no ledger can display, is idempotent, and costs a single `COUNT` on a table
     * with a handful of rows. Running it once and never again would mean a term stranded
     * by a later bug stays invisible forever -- which is exactly the failure being fixed.
     *
     * Rows are re-homed onto the first ledger rather than deleted: the dates are still the
     * user's, and a 学期 that shows up in the wrong ledger is a one-tap fix, while one that
     * was deleted is not.
     */
    private suspend fun rehomeOrphanTerms(): Boolean {
        if (termDao.countOrphans() == 0) return false
        val target = ledgerDao.active().firstOrNull()?.id ?: ledgerDao.all().firstOrNull()?.id
        if (target == null) return false
        termDao.rehomeOrphans(target)
        return true
    }

    companion object {
        const val KEY_ALIPAY = "rename_alipay_balance"
        const val KEY_ORPHAN_TERMS = "rehome_orphan_terms"
    }
}
