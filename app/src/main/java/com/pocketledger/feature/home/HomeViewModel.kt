package com.pocketledger.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.AllowanceCalculator
import com.pocketledger.domain.AllowanceSnapshot
import com.pocketledger.domain.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** One day's worth of ledger rows, with that day's net totals for the header. */
data class DayGroup(
    val dateKey: String,
    val rows: List<TxnRow>,
    val incomeCents: Long,
    val expenseCents: Long,
)

/** The ledger list, or the month grid with the list filtered to the chosen day. */
enum class HomeViewMode { LIST, CALENDAR }

data class HomeUiState(
    val monthKey: String,
    val monthLabel: String,
    val isCurrentMonth: Boolean,
    /** Null only until the first emission arrives. */
    val allowance: AllowanceSnapshot?,
    val totals: PeriodTotals,
    val dayGroups: List<DayGroup>,
    val dayTotals: Map<String, DayTotal>,
    val viewMode: HomeViewMode = HomeViewMode.LIST,
    val selectedDateKey: String? = null,
    val allowanceDialogVisible: Boolean = false,
    /** 累计模式 ledgers show running totals instead of an allowance. */
    val ledgerType: LedgerType = LedgerType.BUDGET,
    val ledgerName: String = "",
    val selectedLedgerId: Long? = null,
    /** Offered by the top-right switcher. */
    val ledgers: List<LedgerEntity> = emptyList(),
    val ledgerSwitcherVisible: Boolean = false,
) {
    val isLoading: Boolean get() = allowance == null

    /**
     * The list follows the calendar selection, so tapping a day narrows both the
     * grid highlight and the rows below it without a second source of truth.
     */
    val visibleDayGroups: List<DayGroup>
        get() = selectedDateKey?.let { key -> dayGroups.filter { it.dateKey == key } } ?: dayGroups

    val isEmpty: Boolean get() = visibleDayGroups.isEmpty()
}

/**
 * Backs the home screen: the allowance card plus the month's ledger list.
 *
 * Month navigation is a [MutableStateFlow] feeding `flatMapLatest`, so switching
 * months swaps every underlying query at once instead of each flow being filtered
 * independently in the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val monthKey = MutableStateFlow(DateKeys.monthKey(LocalDate.now()))
    private val allowanceDialog = MutableStateFlow(false)
    private val viewMode = MutableStateFlow(HomeViewMode.LIST)
    private val selectedDateKey = MutableStateFlow<String?>(null)
    private val ledgerSwitcher = MutableStateFlow(false)

    /** Folds the three small view flags so `combine` stays within its typed overloads. */
    private data class ViewState(
        val dialogVisible: Boolean,
        val viewMode: HomeViewMode,
        val selectedDateKey: String?,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        monthKey.flatMapLatest { key -> monthStream(key) },
        combine(allowanceDialog, viewMode, selectedDateKey) { dialog, mode, selected ->
            ViewState(dialog, mode, selected)
        },
        repository.observeLedgers(),
        repository.selectedLedgerId,
        ledgerSwitcher,
    ) { state, view, ledgers, selectedId, switcherVisible ->
        val current = ledgers.firstOrNull { it.id == selectedId }
        state.copy(
            allowanceDialogVisible = view.dialogVisible,
            viewMode = view.viewMode,
            selectedDateKey = view.selectedDateKey,
            // A ledger deleted out from under the screen falls back to 预算模式, which
            // is the more informative layout rather than the emptier one.
            ledgerType = current?.type ?: LedgerType.BUDGET,
            ledgerName = current?.name.orEmpty(),
            selectedLedgerId = selectedId,
            ledgers = ledgers,
            ledgerSwitcherVisible = switcherVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyState(),
    )

    private fun monthStream(key: String) = combine(
        repository.observeAllowance(key),
        repository.observeTotals(key),
        repository.observeRows(key),
        repository.observeDayTotals(key),
    ) { allowanceEntity, totals, rows, dayTotals ->
        val today = LocalDate.now()
        HomeUiState(
            monthKey = key,
            monthLabel = DateKeys.monthLabel(key),
            isCurrentMonth = key == DateKeys.monthKey(today),
            allowance = AllowanceCalculator.compute(
                periodKey = key,
                budgetCents = allowanceEntity?.amountCents,
                spentCents = totals.expenseCents,
                today = today,
            ),
            totals = totals,
            dayGroups = groupByDay(rows),
            dayTotals = dayTotals.associateBy { it.localDateKey },
        )
    }

    fun previousMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).minusMonths(1).toString()
        selectedDateKey.value = null
    }

    fun nextMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).plusMonths(1).toString()
        selectedDateKey.value = null
    }

    fun goToCurrentMonth() {
        monthKey.value = DateKeys.monthKey(LocalDate.now())
        selectedDateKey.value = null
    }

    fun showMonth(key: String) {
        monthKey.value = key
        selectedDateKey.value = null
    }

    fun toggleViewMode() {
        viewMode.value = when (viewMode.value) {
            HomeViewMode.LIST -> HomeViewMode.CALENDAR
            HomeViewMode.CALENDAR -> HomeViewMode.LIST
        }
        if (viewMode.value == HomeViewMode.LIST) selectedDateKey.value = null
    }

    fun selectDay(dateKey: String?) {
        selectedDateKey.value = dateKey
    }

    fun openLedgerSwitcher() {
        ledgerSwitcher.value = true
    }

    fun dismissLedgerSwitcher() {
        ledgerSwitcher.value = false
    }

    /** Switching is a single repository write; every screen follows it. */
    fun selectLedger(id: Long) {
        repository.selectLedger(id)
    }

    fun openAllowanceDialog() {
        allowanceDialog.value = true
    }

    fun dismissAllowanceDialog() {
        allowanceDialog.value = false
    }

    fun saveAllowance(cents: Long) {
        val key = monthKey.value
        viewModelScope.launch {
            repository.setAllowance(key, cents)
            allowanceDialog.value = false
        }
    }

    private fun emptyState() = HomeUiState(
        monthKey = monthKey.value,
        monthLabel = DateKeys.monthLabel(monthKey.value),
        isCurrentMonth = true,
        allowance = null,
        totals = PeriodTotals(0, 0),
        dayGroups = emptyList(),
        dayTotals = emptyMap(),
        viewMode = viewMode.value,
        selectedDateKey = selectedDateKey.value,
    )

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                HomeViewModel(app.container.repository)
            }
        }
    }
}

/** Rows arrive already sorted newest-first, so grouping preserves that order. */
private fun groupByDay(rows: List<TxnRow>): List<DayGroup> =
    rows.groupBy { it.localDateKey }
        .entries
        .sortedByDescending { it.key }
        .map { (dateKey, dayRows) ->
            DayGroup(
                dateKey = dateKey,
                rows = dayRows,
                incomeCents = dayRows.filter { it.type == TxnType.INCOME.name }.sumOf { it.amountCents },
                expenseCents = dayRows.filter { it.type == TxnType.EXPENSE.name }.sumOf { it.amountCents },
            )
        }
