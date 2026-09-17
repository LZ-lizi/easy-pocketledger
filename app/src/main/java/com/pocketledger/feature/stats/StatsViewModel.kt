package com.pocketledger.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.Presets
import com.pocketledger.data.dao.MonthTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.DateKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** One row of the spending ranking. */
data class CategoryRank(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val totalCents: Long,
    /** Share of the month's total expense, 0f..1f. */
    val share: Float,
)

data class StatsUiState(
    val monthKey: String = DateKeys.monthKey(LocalDate.now()),
    val monthLabel: String = DateKeys.monthLabel(DateKeys.monthKey(LocalDate.now())),
    val totals: PeriodTotals = PeriodTotals(0, 0),
    val dailyCents: Long = 0,
    val leisureCents: Long = 0,
    val topCategories: List<CategoryRank> = emptyList(),
    /** Oldest to newest, for the trend chart. */
    val months: List<MonthTotal> = emptyList(),
)

private const val TREND_MONTHS = 6
private const val RANKING_SIZE = 8

/**
 * Statistics for one month: totals, the 日常 / 娱乐 split, a six-month trend and a
 * category ranking.
 *
 * Every figure comes from a SQL aggregate. Nothing loads the ledger into memory,
 * which is what keeps this screen fast as the database grows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val monthKey = MutableStateFlow(DateKeys.monthKey(LocalDate.now()))

    val uiState: StateFlow<StatsUiState> = monthKey.flatMapLatest { key ->
        val (monthStart, monthEnd) = DateKeys.monthRange(key)
        val trendStart = DateKeys.parseMonthKey(key).minusMonths((TREND_MONTHS - 1).toLong())
            .atDay(1).toString()

        combine(
            repository.observeTotals(key),
            repository.observeMainCategoryTotals(key),
            repository.observeCategories(CategoryKind.EXPENSE),
            combine(
                repository.observeCategoryTotals(monthStart, monthEnd),
                repository.observeMonthTotals(trendStart, monthEnd),
            ) { categoryTotals, months -> categoryTotals to months },
        ) { totals, mainTotals, categories, extra ->
            val (categoryTotals, months) = extra
            val (daily, leisure) = splitMainTotals(mainTotals, categories)
            StatsUiState(
                monthKey = key,
                monthLabel = DateKeys.monthLabel(key),
                totals = totals,
                dailyCents = daily,
                leisureCents = leisure,
                topCategories = buildRanking(categoryTotals, categories, totals.expenseCents),
                months = months,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun previousMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).minusMonths(1).toString()
    }

    fun nextMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).plusMonths(1).toString()
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                StatsViewModel(app.container.repository)
            }
        }
    }
}

private fun splitMainTotals(
    totals: List<com.pocketledger.data.dao.MainCategoryTotal>,
    categories: List<CategoryEntity>,
): Pair<Long, Long> {
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
    return daily to leisure
}

private fun buildRanking(
    totals: List<com.pocketledger.data.dao.CategoryTotal>,
    categories: List<CategoryEntity>,
    monthExpenseCents: Long,
): List<CategoryRank> {
    if (monthExpenseCents <= 0L) return emptyList()
    val byId = categories.associateBy { it.id }
    return totals
        .mapNotNull { total ->
            val category = byId[total.categoryId] ?: return@mapNotNull null
            CategoryRank(
                id = category.id,
                name = category.name,
                colorArgb = category.colorArgb,
                totalCents = total.totalCents,
                share = (total.totalCents.toDouble() / monthExpenseCents.toDouble()).toFloat(),
            )
        }
        .sortedByDescending { it.totalCents }
        .take(RANKING_SIZE)
}
