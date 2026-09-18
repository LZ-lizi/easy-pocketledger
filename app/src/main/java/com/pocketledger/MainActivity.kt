package com.pocketledger

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pocketledger.ui.navigation.AppShell
import com.pocketledger.ui.theme.PocketLedgerTheme

class MainActivity : ComponentActivity() {

    /**
     * Set when the home-screen widget asked for the keypad.
     *
     * Held as Compose state rather than a plain field so a later `onNewIntent`
     * delivery recomposes the shell instead of being silently dropped.
     */
    private var startEntry by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        startEntry = intent?.getBooleanExtra(EXTRA_START_ENTRY, false) == true
        setContent {
            PocketLedgerTheme {
                NotificationPermissionRequest()
                AppShell(
                    startEntry = startEntry,
                    onStartEntryHandled = { startEntry = false },
                )
            }
        }
    }

    /** The activity is `singleTop`, so a second widget tap arrives here, not in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_START_ENTRY, false)) {
            startEntry = true
        }
    }

    companion object {
        /** Widget extra: open the keypad directly instead of the ledger list. */
        const val EXTRA_START_ENTRY = "com.pocketledger.extra.START_ENTRY"
    }
}

/**
 * Asks for the notification permission on API 33+.
 *
 * A refusal is not fatal -- the app works fully without notifications -- and nothing
 * is remembered about the answer, so a later launch simply asks again rather than
 * silently giving up on ever warning about an overspent budget.
 */
@Composable
private fun NotificationPermissionRequest() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Either way the app keeps working. */ },
    )
    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
