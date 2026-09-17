package com.pocketledger.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pocketledger.feature.accounts.AccountsScreen
import com.pocketledger.feature.accounts.AccountsViewModel
import com.pocketledger.feature.entry.EntryScreen
import com.pocketledger.feature.entry.EntryViewModel
import com.pocketledger.feature.home.HomeScreen
import com.pocketledger.feature.home.HomeViewModel
import com.pocketledger.feature.settings.SettingsScreen
import com.pocketledger.feature.stats.StatsScreen
import com.pocketledger.feature.stats.StatsViewModel
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView

/** Route names. Moving to type-safe routes is a later, purely mechanical change. */
object Routes {
    const val LEDGER = "ledger"
    const val STATS = "stats"
    const val ACCOUNTS = "accounts"
    const val SETTINGS = "settings"
    const val ENTRY = "entry"
}

private class TabItem(val route: String, val label: String, val icon: LedgerIcon)

private val TABS = listOf(
    TabItem(Routes.LEDGER, "明细", LedgerIcon.LIST),
    TabItem(Routes.STATS, "统计", LedgerIcon.CHART),
    TabItem(Routes.ACCOUNTS, "账户", LedgerIcon.WALLET),
    TabItem(Routes.SETTINGS, "我的", LedgerIcon.PERSON),
)

/**
 * The application shell: four tabs plus the keypad entry route.
 *
 * The bottom bar and the FAB are hoisted above the `NavHost` so the entry screen
 * can hide both and take the whole window -- a keypad that shares space with a
 * navigation bar is a keypad that gets mis-tapped.
 */
@Composable
fun AppShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isEntry = currentRoute == Routes.ENTRY

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isEntry) {
                LedgerBottomBar(currentRoute = currentRoute) { target ->
                    navController.navigate(target) {
                        // Keep each tab's own state and avoid stacking duplicates.
                        popUpTo(Routes.LEDGER) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.LEDGER) {
                FloatingActionButton(
                    onClick = { navController.navigate(Routes.ENTRY) },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    LedgerIconView(
                        icon = LedgerIcon.PLUS,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        size = 26.dp,
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LEDGER,
        ) {
            composable(Routes.LEDGER) {
                val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
                HomeScreen(viewModel = viewModel, contentPadding = padding)
            }

            composable(Routes.STATS) {
                val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
                StatsScreen(viewModel = viewModel, contentPadding = padding)
            }

            composable(Routes.ACCOUNTS) {
                val viewModel: AccountsViewModel = viewModel(factory = AccountsViewModel.Factory)
                AccountsScreen(viewModel = viewModel, contentPadding = padding)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(contentPadding = padding)
            }

            composable(Routes.ENTRY) {
                val viewModel: EntryViewModel = viewModel(factory = EntryViewModel.Factory)
                EntryScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun LedgerBottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(tab.route) },
                icon = {
                    LedgerIconView(
                        icon = tab.icon,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                label = {
                    Text(text = tab.label, style = MaterialTheme.typography.labelSmall)
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
