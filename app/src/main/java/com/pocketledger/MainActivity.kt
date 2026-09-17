package com.pocketledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketledger.feature.entry.EntryScreen
import com.pocketledger.feature.entry.EntryViewModel
import com.pocketledger.feature.home.HomeScreen
import com.pocketledger.feature.home.HomeViewModel
import com.pocketledger.ui.theme.LedgerTheme
import com.pocketledger.ui.theme.PocketLedgerTheme

/** Route names. Migrating to type-safe routes is a later, purely mechanical change. */
private object Routes {
    const val HOME = "home"
    const val ENTRY = "entry"
}

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
 * Milestone 1 wires the home screen and the keypad entry route. The bottom
 * navigation bar (明细 / 统计 / 账户 / 我的) slots in here as those screens land.
 */
@Composable
private fun LedgerRoot() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { navController.navigate(Routes.ENTRY) },
                        shape = CircleShape,
                        containerColor = LedgerTheme.colors.daily,
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

        composable(Routes.ENTRY) {
            val entryViewModel: EntryViewModel = viewModel(factory = EntryViewModel.Factory)
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                EntryScreen(
                    viewModel = entryViewModel,
                    onClose = { navController.popBackStack() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
