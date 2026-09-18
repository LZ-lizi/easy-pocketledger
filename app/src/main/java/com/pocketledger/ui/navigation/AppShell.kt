package com.pocketledger.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketledger.feature.accounts.AccountsScreen
import com.pocketledger.feature.accounts.AccountsViewModel
import com.pocketledger.feature.budget.BudgetSettingsScreen
import com.pocketledger.feature.budget.BudgetViewModel
import com.pocketledger.feature.categories.CategorySettingsScreen
import com.pocketledger.feature.categories.CategorySettingsViewModel
import com.pocketledger.feature.detail.DetailScreen
import com.pocketledger.feature.detail.DetailViewModel
import com.pocketledger.feature.edit.EditScreen
import com.pocketledger.feature.edit.EditViewModel
import com.pocketledger.feature.entry.EntryScreen
import com.pocketledger.feature.entry.EntryViewModel
import com.pocketledger.feature.home.HomeScreen
import com.pocketledger.feature.home.HomeViewModel
import com.pocketledger.feature.installments.InstallmentSettingsScreen
import com.pocketledger.feature.installments.InstallmentSettingsViewModel
import com.pocketledger.feature.onboarding.OnboardingScreen
import com.pocketledger.feature.onboarding.OnboardingViewModel
import com.pocketledger.feature.settings.AboutScreen
import com.pocketledger.feature.settings.LedgerSettingsScreen
import com.pocketledger.feature.settings.LedgerSettingsViewModel
import com.pocketledger.feature.settings.SettingsScreen
import com.pocketledger.feature.settings.TermSettingsScreen
import com.pocketledger.feature.settings.TermSettingsViewModel
import com.pocketledger.feature.stats.StatsScreen
import com.pocketledger.feature.stats.StatsViewModel
import com.pocketledger.ui.components.LedgerIcon
import com.pocketledger.ui.components.LedgerIconView
import com.pocketledger.di.rememberAppContainer

/** Route names. Moving to type-safe routes is a later, purely mechanical change. */
object Routes {
    const val LEDGER = "ledger"
    const val STATS = "stats"
    const val ACCOUNTS = "accounts"
    const val SETTINGS = "settings"
    const val ENTRY = "entry"
    const val EDIT = "edit"
    const val EDIT_ARG = "txnId"
    const val DETAIL = "detail"
    const val DETAIL_ARG = "txnId"
    const val TERMS = "terms"
    const val LEDGERS = "ledgers"
    const val CATEGORIES = "categories"
    const val INSTALLMENTS = "installments"
    const val BUDGET = "budget"
    const val ABOUT = "about"

    fun edit(transactionId: Long): String = "$EDIT/$transactionId"

    fun detail(transactionId: Long): String = "$DETAIL/$transactionId"
}

private class TabItem(val route: String, val label: String, val icon: LedgerIcon)

private val TABS = listOf(
    TabItem(Routes.LEDGER, "明细", LedgerIcon.LIST),
    TabItem(Routes.STATS, "统计", LedgerIcon.CHART),
    TabItem(Routes.ACCOUNTS, "账户", LedgerIcon.WALLET),
    TabItem(Routes.SETTINGS, "设置", LedgerIcon.SETTINGS),
)

private val EDIT_ROUTE = "${Routes.EDIT}/{${Routes.EDIT_ARG}}"
private val DETAIL_ROUTE = "${Routes.DETAIL}/{${Routes.DETAIL_ARG}}"

/** Routes that take the whole window, with no bottom bar and no FAB. */
private val FULL_SCREEN_ROUTES = setOf(Routes.ENTRY, EDIT_ROUTE, DETAIL_ROUTE)

/**
 * The application shell.
 *
 * Three states, in order: waiting for startup work, onboarding when no ledger
 * exists yet, and the tabs. Startup must be awaited first so an upgraded install
 * never flashes onboarding before its existing data is adopted into a ledger.
 */
@Composable
fun AppShell() {
    val container = rememberAppContainer()
    val startupComplete by container.startupComplete.collectAsStateWithLifecycle()
    val ledgerId by container.repository.selectedLedgerId.collectAsStateWithLifecycle()

    // There is no server to push a warning, so the budget check runs whenever the app
    // comes back to the foreground: that is the moment the user is about to look.
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(lifecycleOwner, ledgerId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { container.checkBudgetAlerts() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when {
        !startupComplete -> StartupPlaceholder()

        ledgerId == null -> {
            val viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)
            OnboardingScreen(viewModel = viewModel)
        }

        else -> LedgerNavHost()
    }
}

/** An empty, correctly coloured frame while startup finishes. */
@Composable
private fun StartupPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    )
}

/**
 * The four tabs plus the full-window routes.
 *
 * The bottom bar and the FAB are hoisted above the `NavHost` so the entry and edit
 * screens can take the whole window -- a keypad that shares space with a
 * navigation bar is a keypad that gets mis-tapped.
 */
@Composable
private fun LedgerNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isOverlay = currentRoute in FULL_SCREEN_ROUTES

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isOverlay) {
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
                HomeScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenTransaction = { id -> navController.navigate(Routes.detail(id)) },
                )
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
                SettingsScreen(
                    contentPadding = padding,
                    onOpenLedgers = { navController.navigate(Routes.LEDGERS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenInstallments = { navController.navigate(Routes.INSTALLMENTS) },
                    onOpenBudget = { navController.navigate(Routes.BUDGET) },
                    onOpenTerms = { navController.navigate(Routes.TERMS) },
                    onOpenAbout = { navController.navigate(Routes.ABOUT) },
                )
            }

            composable(Routes.LEDGERS) {
                val viewModel: LedgerSettingsViewModel =
                    viewModel(factory = LedgerSettingsViewModel.Factory)
                LedgerSettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.CATEGORIES) {
                val viewModel: CategorySettingsViewModel =
                    viewModel(factory = CategorySettingsViewModel.Factory)
                CategorySettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.INSTALLMENTS) {
                val viewModel: InstallmentSettingsViewModel =
                    viewModel(factory = InstallmentSettingsViewModel.Factory)
                InstallmentSettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.BUDGET) {
                val viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory)
                BudgetSettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.TERMS) {
                val viewModel: TermSettingsViewModel =
                    viewModel(factory = TermSettingsViewModel.Factory)
                TermSettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ENTRY) {
                val viewModel: EntryViewModel = viewModel(factory = EntryViewModel.Factory)
                EntryScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() },
                    modifier = Modifier.padding(padding),
                )
            }

            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(navArgument(Routes.DETAIL_ARG) { type = NavType.LongType }),
            ) { entry ->
                val transactionId = entry.arguments?.getLong(Routes.DETAIL_ARG) ?: 0L
                val viewModel: DetailViewModel =
                    viewModel(factory = DetailViewModel.factory(transactionId))
                DetailScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Routes.edit(id)) },
                )
            }

            composable(
                route = EDIT_ROUTE,
                arguments = listOf(navArgument(Routes.EDIT_ARG) { type = NavType.LongType }),
            ) { entry ->
                val transactionId = entry.arguments?.getLong(Routes.EDIT_ARG) ?: 0L
                val viewModel: EditViewModel =
                    viewModel(factory = EditViewModel.factory(transactionId))
                EditScreen(
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
