package com.pocketledger.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

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
}
