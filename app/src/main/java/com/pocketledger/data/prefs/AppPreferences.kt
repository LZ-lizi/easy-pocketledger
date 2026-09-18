package com.pocketledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Small app-level settings that are not ledger data.
 *
 * Deliberately separate from the database: these are bookkeeping for the app itself
 * (which notification already went out), not something worth a table, a schema
 * version or a migration.
 */
class AppPreferences(private val context: Context) {

    /**
     * Budget thresholds already reported, keyed `periodKey:categoryId:level`.
     *
     * Kept so reopening the app does not re-notify about a budget that was blown
     * days ago -- a notification that repeats is a notification that gets ignored.
     */
    private val firedBudgetAlerts = stringSetPreferencesKey("budget_alerts_fired")

    suspend fun firedAlerts(): Set<String> =
        context.settingsStore.data.first()[firedBudgetAlerts] ?: emptySet()

    suspend fun markAlertsFired(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.settingsStore.edit { prefs ->
            prefs[firedBudgetAlerts] = (prefs[firedBudgetAlerts] ?: emptySet()) + keys
        }
    }

    /** Drops entries from months that can no longer fire, so the set does not grow forever. */
    suspend fun pruneAlertsExcept(periodKeys: Set<String>) {
        context.settingsStore.edit { prefs ->
            val current = prefs[firedBudgetAlerts] ?: return@edit
            prefs[firedBudgetAlerts] = current.filterTo(mutableSetOf()) { key ->
                key.substringBefore(':') in periodKeys
            }
        }
    }

    /**
     * Categories shown on the first screen of the entry keypad.
     *
     * Empty means "not customised yet", which the keypad reads as "use the defaults"
     * rather than "show nothing" -- so a new install is immediately usable and a user
     * who deliberately unpins everything still sees something.
     */
    private val pinnedCategories = stringSetPreferencesKey("pinned_category_ids")

    fun pinnedCategoryIds(): Flow<Set<Long>> =
        context.settingsStore.data.map { prefs ->
            (prefs[pinnedCategories] ?: emptySet())
                .mapNotNull { it.toLongOrNull() }
                .toSet()
        }

    suspend fun setPinnedCategoryIds(ids: Set<Long>) {
        context.settingsStore.edit { prefs ->
            prefs[pinnedCategories] = ids.map(Long::toString).toSet()
        }
    }

    /**
     * Idempotence markers for one-off data repairs (see `DataRepair`).
     *
     * A repair that matches on preset *values* must not re-run: renaming every account
     * still called 支付宝 is right exactly once, but would also catch an account the
     * user deliberately gave that name a month later.
     */
    private val completedRepairs = stringSetPreferencesKey("data_repairs_done")

    suspend fun repairDone(id: String): Boolean =
        id in (context.settingsStore.data.first()[completedRepairs] ?: emptySet())

    suspend fun markRepairDone(id: String) {
        context.settingsStore.edit { prefs ->
            prefs[completedRepairs] = (prefs[completedRepairs] ?: emptySet()) + id
        }
    }
}
