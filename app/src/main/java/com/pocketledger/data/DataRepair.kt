package com.pocketledger.data

import com.pocketledger.data.dao.AccountDao
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
 */
class DataRepair(
    private val accountDao: AccountDao,
    private val preferences: AppPreferences,
) {

    /** Executes every repair not yet recorded; returns the ids of the ones that ran. */
    suspend fun run(): List<String> = buildList {
        if (renameLegacyAlipay()) add(KEY_ALIPAY)
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

    companion object {
        const val KEY_ALIPAY = "rename_alipay_balance"
    }
}
