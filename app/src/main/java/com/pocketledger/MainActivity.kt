package com.pocketledger

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.pocketledger.ui.navigation.AppShell
import com.pocketledger.ui.theme.PocketLedgerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PocketLedgerTheme {
                NotificationPermissionRequest()
                AppShell()
            }
        }
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
