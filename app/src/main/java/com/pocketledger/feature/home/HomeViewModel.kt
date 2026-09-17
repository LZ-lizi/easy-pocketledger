package com.pocketledger.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.Presets
import com.pocketledger.data.dao.DayTotal
import com.pocketledger.data.dao.MainCategoryTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
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

data class HomeUiState(
    val monthKey: String,
    val monthLabel: String,
    val isCurrentMonth: Boolean,
    /** Null only until the first emission arrives. */
    val allowance: AllowanceSnapshot?,
    val totals: PeriodTotals,
    val dayGroups: List<DayGroup>,
    val dayTotals: Map<String, DayTotal>,
    val allowanceDialogVisible: Boolean = false,
) {
    val isLoading: Boolean get() = allowance == null
    val isEmpty: Boolean get() = dayGroups.isEmpty()
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

    val uiState: StateFlow<HomeUiState> = combine(
        monthKey.flatMapLatest { key -> monthStream(key) },
        allowanceDialog,
    ) { state, dialogVisible ->
        state.copy(allowanceDialogVisible = dialogVisible)
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
        combine(
            repository.observeMainCategoryTotals(key),
            repository.observeCategories(CategoryKind.EXPENSE),
        ) { totals, categories -> totals to categories },
    ) { allowanceEntity, totals, rows, dayTotals, splitInput ->
        val today = LocalDate.now()
        val split = splitMainTotals(splitInput.first, splitInput.second)
        HomeUiState(
            monthKey = key,
            monthLabel = DateKeys.monthLabel(key),
            isCurrentMonth = key == DateKeys.monthKey(today),
            allowance = AllowanceCalculator.compute(
                periodKey = key,
                budgetCents = allowanceEntity?.amountCents,
                spentCents = totals.expenseCents,
                spentOnDailyCents = split.daily,
                spentOnLeisureCents = split.leisure,
                today = today,
            ),
            totals = totals,
            dayGroups = groupByDay(rows),
            dayTotals = dayTotals.associateBy { it.localDateKey },
        )
    }

    fun previousMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).minusMonths(1).toString()
    }

    fun nextMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).plusMonths(1).toString()
    }

    fun goToCurrentMonth() {
        monthKey.value = DateKeys.monthKey(LocalDate.now())
    }

    fun showMonth(key: String) {
        monthKey.value = key
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

private class MainSplit(val daily: Long, val leisure: Long)

/**
 * Maps main-category totals onto 日常 / 娱乐.
 *
 * Matching on `systemKey` rather than the display name keeps this working after
 * the user renames or reorders the two main categories.
 */
private fun splitMainTotals(
    totals: List<MainCategoryTotal>,
    categories: List<CategoryEntity>,
): MainSplit {
    val dailyId = categories.firstOrNull { it.systemKey == Presets.KEY_DAILY }?.id
    val leisureId = categories.firstOrNull { it.systemKey == Presets.KEY_LEISURE }?.id
    var daily = 0L
    var leisure = 0L
    for (total in totals) {
        when (total.mainCategoryId) {
            dailyId -> daily += total.totalCents
            leisureId -> leisure += total.totalCents
        }
    }
    return MainSplit(daily, leisure)
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
