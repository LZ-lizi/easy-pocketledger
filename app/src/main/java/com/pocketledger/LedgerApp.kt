package com.pocketledger

import android.app.Application
import com.pocketledger.di.AppContainer

/**
 * Process-wide entry point.
 *
 * Dependency wiring is hand-rolled (see [AppContainer]) rather than Hilt: this is
 * a single-module personal app, and dropping the second annotation processor keeps
 * the build's version coupling to a minimum.
 */
class LedgerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
