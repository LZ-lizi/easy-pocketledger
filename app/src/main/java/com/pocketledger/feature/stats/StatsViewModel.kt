package com.pocketledger.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.dao.CategoryTotal
import com.pocketledger.data.dao.MonthTotal
import com.pocketledger.data.dao.PeriodTotals
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.TermEntity
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

/**
 * The statistical window.
 *
 * Deliberately a small set of presets rather than a free date-range picker: the
 * three questions actually asked of a ledger are "this month", "this term" and
 * "how has this year gone", and a picker answers none of them faster.
 */
enum class StatsRangeMode(val label: String) {
    MONTH("本月"),
    LAST_30_DAYS("近30天"),
    YEAR("今年"),
    TERM("学期"),
}

/** One row of the spending ranking, also used to build the donut's wedges. */
data class CategoryRank(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val totalCents: Long,
    /** Share of the period's total expense, 0f..1f. */
    val share: Float,
)

/**
 * Whether the donut shows leaf categories or their 大类.
 *
 * Both answer real questions -- "what exactly did I buy" versus "which area of life
 * took the money" -- so it is a toggle rather than a fixed choice.
 */
enum class PieLevel(val label: String) {
    SMALL("小类"),
    LARGE("大类"),
}

data class StatsUiState(
    val mode: StatsRangeMode = StatsRangeMode.MONTH,
    val monthKey: String = DateKeys.monthKey(LocalDate.now()),
    val rangeLabel: String = DateKeys.monthLabel(DateKeys.monthKey(LocalDate.now())),
    val rangeStartKey: String = "",
    val rangeEndKey: String = "",
    val terms: List<TermEntity> = emptyList(),
    val selectedTermId: Long? = null,
    val totals: PeriodTotals = PeriodTotals(0, 0),
    val topCategories: List<CategoryRank> = emptyList(),
    /** Top wedges plus an aggregated 「其他」; what the donut draws. */
    val donutSlices: List<CategoryRank> = emptyList(),
    val months: List<MonthTotal> = emptyList(),
    val pieLevel: PieLevel = PieLevel.SMALL,
    /** Top-level categories offered as a filter. */
    val filterOptions: List<CategoryEntity> = emptyList(),
    /** 大类 the page is narrowed to. Empty means every category. */
    val filterCategoryIds: Set<Long> = emptySet(),
) {
    val hasExpense: Boolean get() = totals.expenseCents > 0L

    /**
     * True when the category filter is doing something.
     *
     * The button that opens the filter is tinted from this: at its default (everything
     * selected) it is not a filter at all, and drawing it in the accent colour would claim
     * the page is narrowed when it is not.
     */
    val filterActive: Boolean
        get() = filterOptions.isNotEmpty() && filterCategoryIds.size != filterOptions.size

    /** True when 学期 is selected but no term has been defined yet. */
    val needsTermSetup: Boolean
        get() = mode == StatsRangeMode.TERM && terms.isEmpty()
}

internal data class StatsSelection(
    val mode: StatsRangeMode,
    val monthKey: String,
    val termId: Long?,
    val terms: List<TermEntity>,
    val pieLevel: PieLevel,
    /**
     * The category filter as the query needs it: `null` for "no filter", a set (possibly
     * empty) for an actual narrowing. The resolved "everything selected" case collapses
     * back to null here so the common visit runs the cheapest query.
     */
    val filterCategoryIds: Set<Long>?,
)

/** The user-controlled inputs, folded together before being paired with the term list. */
private data class SelectionKey(
    val mode: StatsRangeMode,
    val monthKey: String,
    val termId: Long?,
    val pieLevel: PieLevel,
    /** Null while the user has never opened the filter; see [StatsViewModel]. */
    val filterCategoryIds: Set<Long>?,
)

private const val TREND_MONTHS = 6
private const val RANKING_SIZE = 8
private const val DONUT_SLICES = 6

/**
 * Statistics for the selected window: totals, the 日常 / 娱乐 split, a category
 * donut, a six-month trend and a ranking.
 *
 * Every figure comes from a SQL aggregate; nothing loads the ledger into memory,
 * which is what keeps this screen fast as the database grows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val mode = MutableStateFlow(StatsRangeMode.MONTH)
    private val monthKey = MutableStateFlow(DateKeys.monthKey(LocalDate.now()))
    private val selectedTermId = MutableStateFlow<Long?>(null)
    private val pieLevel = MutableStateFlow(PieLevel.SMALL)

    /**
     * Null means "not customised yet", which resolves to *every* 大类 once the category
     * list is known.
     *
     * Defaulting to the full set rather than to an empty selection matters: an empty set
     * is a legitimate state here ("nothing selected", which is what you get by unticking
     * everything) and it must not silently mean "no filter" too.
     */
    private val filterCategoryIds = MutableStateFlow<Set<Long>?>(null)

    // Five selection inputs plus the term list would exceed combine's typed overloads,
    // so the selection is folded together first and then paired with the terms.
    val uiState: StateFlow<StatsUiState> = combine(
        combine(mode, monthKey, selectedTermId, pieLevel, filterCategoryIds) { m, k, t, pie, filter ->
            SelectionKey(m, k, t, pie, filter)
        },
        repository.observeTerms(),
        repository.observeCategories(CategoryKind.EXPENSE),
    ) { key, terms, categories ->
        val allMain = categories.filter { it.parentId == null }.map { it.id }.toSet()
        // A saved selection that names rows this ledger no longer has (or a first load
        // where nothing is known yet) counts as "not customised".
        val chosen = key.filterCategoryIds?.takeIf { it.all(allMain::contains) }
        StatsSelection(
            mode = key.mode,
            monthKey = key.monthKey,
            termId = key.termId,
            terms = terms,
            pieLevel = key.pieLevel,
            // Selecting every 大类 is the default, and passing it to SQL would filter for
            // no reason; selecting *none* is a real narrowing and must be kept as one.
            filterCategoryIds = chosen?.takeIf { allMain.isNotEmpty() && it.size != allMain.size },
        )
    }.flatMapLatest { selection ->
        val today = LocalDate.now()
        val (startKey, endKey, label) = resolveRange(selection, today)
        // The trend always ends with the selected window, so the chart stays in context.
        val trendStart = DateKeys.parseMonthKey(endKey.take(7))
            .minusMonths((TREND_MONTHS - 1).toLong())
            .atDay(1)
            .toString()
        val activeFilter = selection.filterCategoryIds

        combine(
            repository.observeTotals(startKey, endKey, activeFilter),
            repository.observeCategoryTotals(startKey, endKey, activeFilter),
            repository.observeMainCategoryTotals(startKey, endKey, activeFilter),
            repository.observeMonthTotals(trendStart, endKey, activeFilter),
            repository.observeCategories(CategoryKind.EXPENSE),
        ) { totals, categoryTotals, mainTotals, months, categories ->
            // The 大类 view reuses the leaf ranking machinery by projecting the
            // roll-up onto the same shape.
            val source = if (selection.pieLevel == PieLevel.LARGE) {
                mainTotals.map { CategoryTotal(it.mainCategoryId, it.totalCents) }
            } else {
                categoryTotals
            }
            val ranking = buildRanking(source, categories, totals.expenseCents, RANKING_SIZE)
            val options = categories.filter { it.parentId == null }.sortedBy { it.sortOrder }
            StatsUiState(
                mode = selection.mode,
                monthKey = selection.monthKey,
                rangeLabel = label,
                rangeStartKey = startKey,
                rangeEndKey = endKey,
                terms = selection.terms,
                selectedTermId = selection.termId ?: selection.terms.firstOrNull()?.id,
                totals = totals,
                topCategories = ranking,
                donutSlices = collapseTail(ranking, totals.expenseCents),
                months = months,
                pieLevel = selection.pieLevel,
                filterOptions = options,
                // The dialog always shows a concrete set of tick boxes; "no filter" is
                // drawn as everything being ticked.
                filterCategoryIds = selection.filterCategoryIds ?: options.map { it.id }.toSet(),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    /** Selecting 学期 with no term defined yet falls back to the month window. */
    fun setMode(next: StatsRangeMode) {
        mode.value = next
    }

    fun selectTerm(id: Long) {
        selectedTermId.value = id
    }

    fun setPieLevel(level: PieLevel) {
        pieLevel.value = level
    }

    /** Ticking every box means "no filter" again, so [StatsUiState.filterActive] clears. */
    fun setFilter(categoryIds: Set<Long>) {
        val known = uiState.value.filterOptions.map { it.id }.toSet()
        filterCategoryIds.value = categoryIds.intersect(known)
    }

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

/** Inclusive `start..end` date keys plus a human label for the chosen window. */
internal fun resolveRange(
    selection: StatsSelection,
    today: LocalDate,
): Triple<String, String, String> = when (selection.mode) {
    StatsRangeMode.MONTH -> {
        val (start, end) = DateKeys.monthRange(selection.monthKey)
        Triple(start, end, DateKeys.monthLabel(selection.monthKey))
    }

    StatsRangeMode.LAST_30_DAYS -> Triple(
        today.minusDays(29).toString(),
        today.toString(),
        "近 30 天",
    )

    StatsRangeMode.YEAR -> {
        val (start, end) = DateKeys.yearRange(today.year)
        Triple(start, end, "${today.year} 年")
    }

    StatsRangeMode.TERM -> {
        val term = selection.terms.firstOrNull { it.id == selection.termId }
            ?: selection.terms.firstOrNull()
        if (term == null) {
            // Falls back to the month window so the screen is never blank just
            // because no term has been defined yet.
            val (start, end) = DateKeys.monthRange(selection.monthKey)
            Triple(start, end, DateKeys.monthLabel(selection.monthKey))
        } else {
            Triple(term.startDateKey, term.endDateKey, term.name)
        }
    }
}

internal fun buildRanking(
    totals: List<CategoryTotal>,
    categories: List<CategoryEntity>,
    periodExpenseCents: Long,
    limit: Int,
): List<CategoryRank> {
    if (periodExpenseCents <= 0L) return emptyList()
    val byId = categories.associateBy { it.id }
    return totals
        .mapNotNull { total ->
            val category = byId[total.categoryId] ?: return@mapNotNull null
            CategoryRank(
                id = category.id,
                name = category.name,
                colorArgb = category.colorArgb,
                totalCents = total.totalCents,
                share = (total.totalCents.toDouble() / periodExpenseCents.toDouble()).toFloat(),
            )
        }
        .sortedByDescending { it.totalCents }
        .take(limit)
}

/**
 * Folds everything past the nth category into a single 「其他」 wedge.
 *
 * A donut with twenty hairlines communicates nothing; six named wedges plus a
 * remainder keeps every slice legible.
 */
internal fun collapseTail(ranking: List<CategoryRank>, periodExpenseCents: Long): List<CategoryRank> {
    if (ranking.size <= DONUT_SLICES) return ranking
    val head = ranking.take(DONUT_SLICES - 1)
    val tailTotal = ranking.drop(DONUT_SLICES - 1).sumOf { it.totalCents }
    if (tailTotal <= 0L) return head
    return head + CategoryRank(
        id = -1L,
        name = "其他",
        colorArgb = 0xFF94A3B8.toInt(),
        totalCents = tailTotal,
        share = if (periodExpenseCents <= 0L) {
            0f
        } else {
            (tailTotal.toDouble() / periodExpenseCents.toDouble()).toFloat()
        },
    )
}
