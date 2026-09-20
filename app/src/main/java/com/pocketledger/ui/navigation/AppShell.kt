package com.pocketledger.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.pocketledger.feature.export.ExportScreen
import com.pocketledger.feature.export.ExportViewModel
import com.pocketledger.feature.imports.ImportScreen
import com.pocketledger.feature.imports.ImportViewModel
import com.pocketledger.feature.entry.EntryScreen
import com.pocketledger.feature.entry.EntryViewModel
import com.pocketledger.feature.home.HomeScreen
import com.pocketledger.feature.home.HomeViewModel
import com.pocketledger.feature.installments.InstallmentSettingsScreen
import com.pocketledger.feature.installments.InstallmentSettingsViewModel
import com.pocketledger.feature.onboarding.OnboardingScreen
import com.pocketledger.feature.onboarding.OnboardingViewModel
import com.pocketledger.feature.search.SearchScreen
import com.pocketledger.feature.search.SearchViewModel
import com.pocketledger.feature.settings.AboutScreen
import com.pocketledger.feature.settings.FeatureGuideScreen
import com.pocketledger.feature.settings.LedgerSettingsScreen
import com.pocketledger.feature.settings.LedgerSettingsViewModel
import com.pocketledger.feature.settings.PinnedCategoriesScreen
import com.pocketledger.feature.settings.PinnedCategoriesViewModel
import com.pocketledger.feature.settings.SettingsScreen
import com.pocketledger.feature.settings.TermSettingsScreen
import com.pocketledger.feature.settings.TermSettingsViewModel
import com.pocketledger.feature.settings.TermsOfUseScreen
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
    const val BUDGET_ARG = "ledgerId"
    const val PINNED = "pinned"
    const val IMPORT = "import"
    const val EXPORT = "export"
    const val ABOUT = "about"
    const val FEATURES = "features"
    const val TERMS_OF_USE = "termsOfUse"
    const val SEARCH = "search"

    fun edit(transactionId: Long): String = "$EDIT/$transactionId"

    fun detail(transactionId: Long): String = "$DETAIL/$transactionId"

    /** Budgets belong to one ledger, so the route names it explicitly. */
    fun budget(ledgerId: Long): String = "$BUDGET/$ledgerId"
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
private val BUDGET_ROUTE = "${Routes.BUDGET}/{${Routes.BUDGET_ARG}}"

/**
 * The pages that live under the 设置 tab.
 *
 * Every one of them is reached from the settings menu, so the tab stays lit while they
 * are open. Without this the dock lost its selection the moment you opened a sub-page --
 * `currentRoute == tab.route` was false for all of them -- and the bar read as though
 * nothing was selected at all.
 */
private val SETTINGS_SUB_ROUTES = setOf(
    Routes.LEDGERS,
    Routes.CATEGORIES,
    Routes.INSTALLMENTS,
    Routes.PINNED,
    Routes.IMPORT,
    Routes.EXPORT,
    Routes.ABOUT,
    Routes.TERMS,
    Routes.FEATURES,
    Routes.TERMS_OF_USE,
    BUDGET_ROUTE,
)

/**
 * Which dock tab owns [route], or null when no tab does.
 *
 * The entry, edit and detail screens take the whole window and hide the bar, so their
 * mapping only matters while a transition is still running.
 *
 * `internal` rather than private so the mapping is covered by unit tests: it decides which
 * tab stays lit, and getting it wrong is invisible until someone notices the dock has gone
 * dark on a sub-page.
 */
internal fun owningTab(route: String?): String? = when {
    route == null -> null
    route == Routes.LEDGER -> Routes.LEDGER
    route == Routes.STATS -> Routes.STATS
    route == Routes.ACCOUNTS -> Routes.ACCOUNTS
    route == Routes.SETTINGS || route in SETTINGS_SUB_ROUTES -> Routes.SETTINGS
    route.startsWith("${Routes.DETAIL}/") || route.startsWith("${Routes.EDIT}/") -> Routes.LEDGER
    route == Routes.ENTRY || route == Routes.SEARCH -> Routes.LEDGER
    else -> null
}

/** Dock order, or -1 for a route that is not a tab. */
private fun tabIndexOf(route: String?): Int {
    val owner = owningTab(route) ?: return -1
    return TABS.indexOfFirst { it.route == owner }
}

/**
 * Which way the content should travel.
 *
 * Tapping 账户 from 统计 moves right, so the new screen comes in from the right; tapping
 * back the other way has to come in from the left. The transitions used to hardcode a
 * right-hand offset, so moving left still slid right-to-left and the motion contradicted
 * the tap.
 *
 * Going *into* a sub-page always travels forward, whichever sub-page it was opened from.
 * Comparing dock indices alone could not express that: two sub-pages of the same tab both
 * map to that tab, so 设置 → 关于 → 功能介绍 compared equal and slid in from the *left* --
 * the one direction a deeper screen must never come from. Depth is what decides inside a
 * tab, and dock position is what decides between tabs.
 *
 * `internal` so the rule is unit-tested; the animation is 150ms, which is far too short to
 * catch and check by eye on a device.
 */
internal fun isForward(from: String?, to: String?): Boolean {
    val toIsTab = TABS.any { it.route == to }
    val fromIsTab = TABS.any { it.route == from }
    return when {
        // Any sub-page or full-window page: always in from the right.
        !toIsTab -> true
        // Climbing back out to a tab: from the left, i.e. the reverse of going in.
        !fromIsTab -> false
        else -> tabIndexOf(to) > tabIndexOf(from)
    }
}

/** Milliseconds for the screen enter/exit slides. Tuned to feel immediate. */
private const val TRANSITION_MS = 150

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
fun AppShell(
    startEntry: Boolean = false,
    onStartEntryHandled: () -> Unit = {},
) {
    val container = rememberAppContainer()
    val startupComplete by container.startupComplete.collectAsStateWithLifecycle()
    val ledgerId by container.repository.selectedLedgerId.collectAsStateWithLifecycle()
    val selectedLedger by container.repository.observeSelectedLedger()
        .collectAsStateWithLifecycle(initialValue = null)

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

        else -> LedgerNavHost(
            startEntry = startEntry,
            ledgerArchived = selectedLedger?.isArchived == true,
            onStartEntryHandled = onStartEntryHandled,
        )
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
private fun LedgerNavHost(
    startEntry: Boolean,
    ledgerArchived: Boolean,
    onStartEntryHandled: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isOverlay = currentRoute in FULL_SCREEN_ROUTES

    // A home-screen widget tap asks for the keypad directly. Handled here rather than
    // in the widget so it still works when the app was already running.
    LaunchedEffect(startEntry) {
        if (startEntry) {
            navController.navigate(Routes.ENTRY)
            onStartEntryHandled()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isOverlay) {
                LedgerBottomBar(
                    selectedTab = owningTab(currentRoute),
                    onNavigate = { target ->
                        // Re-tapping the tab you are already in used to re-run the
                        // navigation and its animation, so the screen visibly twitched
                        // for a tap that changed nothing.
                        if (owningTab(currentRoute) != target) {
                            navController.navigate(target) {
                                // Keep each tab's own state and avoid stacking duplicates.
                                popUpTo(Routes.LEDGER) { saveState = true }
                                launchSingleTop = true
                                // Settings always reopens at its top level: coming back to
                                // a sub-page you left earlier is disorienting when you
                                // tapped the tab expecting the menu.
                                restoreState = target != Routes.SETTINGS
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            // An archived ledger is put away: no new entries, matching the edit screen
            // refusing to change the ones already there.
            if (currentRoute == Routes.LEDGER && !ledgerArchived) {
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
            // Short, uniform transitions whose direction follows the tab that was tapped:
            // moving right slides in from the right, moving left from the left.
            enterTransition = {
                val forward = isForward(initialState.destination.route, targetState.destination.route)
                slideInHorizontally(
                    initialOffsetX = { width -> if (forward) width / 8 else -width / 8 },
                    animationSpec = tween(TRANSITION_MS),
                ) + fadeIn(tween(TRANSITION_MS))
            },
            exitTransition = {
                val forward = isForward(initialState.destination.route, targetState.destination.route)
                slideOutHorizontally(
                    targetOffsetX = { width -> if (forward) -width / 8 else width / 8 },
                    animationSpec = tween(TRANSITION_MS),
                ) + fadeOut(tween(TRANSITION_MS))
            },
            // Back always reverses the direction of travel, so popping never slides the
            // same way as the push it is undoing.
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { width -> -width / 8 },
                    animationSpec = tween(TRANSITION_MS),
                ) + fadeIn(tween(TRANSITION_MS))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { width -> width / 8 },
                    animationSpec = tween(TRANSITION_MS),
                ) + fadeOut(tween(TRANSITION_MS))
            },
        ) {
            composable(Routes.LEDGER) {
                val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
                HomeScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onOpenTransaction = { id -> navController.navigate(Routes.detail(id)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                )
            }

            composable(Routes.SEARCH) {
                val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
                SearchScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
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
                    onOpenPinned = { navController.navigate(Routes.PINNED) },
                    onOpenImport = { navController.navigate(Routes.IMPORT) },
                    onOpenExport = { navController.navigate(Routes.EXPORT) },
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
                    onOpenBudget = { id -> navController.navigate(Routes.budget(id)) },
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

            composable(
                route = BUDGET_ROUTE,
                arguments = listOf(navArgument(Routes.BUDGET_ARG) { type = NavType.LongType }),
            ) { entry ->
                val ledgerId = entry.arguments?.getLong(Routes.BUDGET_ARG) ?: 0L
                val viewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.factory(ledgerId))
                BudgetSettingsScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PINNED) {
                val viewModel: PinnedCategoriesViewModel =
                    viewModel(factory = PinnedCategoriesViewModel.Factory)
                PinnedCategoriesScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.IMPORT) {
                val viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory)
                ImportScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.EXPORT) {
                val viewModel: ExportViewModel = viewModel(factory = ExportViewModel.Factory)
                ExportScreen(
                    viewModel = viewModel,
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                    onOpenFeatures = { navController.navigate(Routes.FEATURES) },
                    onOpenTermsOfUse = { navController.navigate(Routes.TERMS_OF_USE) },
                )
            }

            composable(Routes.FEATURES) {
                FeatureGuideScreen(
                    contentPadding = padding,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.TERMS_OF_USE) {
                TermsOfUseScreen(
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
private fun LedgerBottomBar(selectedTab: String?, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        TABS.forEach { tab ->
            val selected = selectedTab == tab.route
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
