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
        startEntry = consumeStartEntry()
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
        startEntry = consumeStartEntry()
    }

    /**
     * Reads the widget's "open the keypad" extra and removes it.
     *
     * Removing it is the whole point. The extra describes one tap, but it lives on the
     * Intent, which outlives this call -- so leaving it in place meant every activity
     * recreation re-read it as true and opened the keypad again with nobody having
     * touched anything. Rotating the phone, or the system restoring the activity after
     * reclaiming memory, was enough to trigger it, and once the widget had been tapped
     * once the Intent kept the extra for the life of the task.
     *
     * Consuming it here makes the extra mean "this launch came from a widget tap" rather
     * than "a widget tap has happened at some point".
     */
    private fun consumeStartEntry(): Boolean {
        val wanted = intent?.getBooleanExtra(EXTRA_START_ENTRY, false) == true
        if (wanted) intent?.removeExtra(EXTRA_START_ENTRY)
        return wanted
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
