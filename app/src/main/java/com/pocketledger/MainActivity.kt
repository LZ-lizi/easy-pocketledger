package com.pocketledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketledger.feature.home.HomeScreen
import com.pocketledger.feature.home.HomeViewModel
import com.pocketledger.ui.theme.PocketLedgerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PocketLedgerTheme {
                LedgerRoot()
            }
        }
    }
}

/**
 * Application shell.
 *
 * Milestone 1 shows the home screen only; the bottom navigation and the keypad
 * entry route replace this scaffold as the remaining screens land.
 */
@Composable
private fun LedgerRoot() {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* keypad entry route lands next */ },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Text(text = "＋", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        HomeScreen(
            viewModel = homeViewModel,
            contentPadding = padding,
        )
    }
}
