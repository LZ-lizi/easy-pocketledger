package com.pocketledger.di

import android.content.Context
import com.pocketledger.data.LedgerDatabase
import com.pocketledger.data.Presets
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
     * one-shot and idempotent (seeding).
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: LedgerDatabase by lazy { LedgerDatabase.build(appContext) }

    val repository: LedgerRepository by lazy {
        val db = database
        LedgerRepository(
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            txnDao = db.txnDao(),
            allowanceDao = db.allowanceDao(),
            tagDao = db.tagDao(),
        )
    }

    init {
        // Presets land on a background thread; the UI observes Room flows, so it
        // simply fills in a moment later rather than blocking the first frame.
        appScope.launch { Presets.seedIfEmpty(database) }
    }
}
