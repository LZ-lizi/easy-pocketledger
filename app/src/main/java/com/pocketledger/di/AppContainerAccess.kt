package com.pocketledger.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.pocketledger.LedgerApp

/**
 * The application-scoped container.
 *
 * Read from the Application rather than passed down through composition: the app
 * has exactly one, and threading it through every screen would add a parameter to
 * every signature for no benefit.
 */
@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as LedgerApp).container }
}
