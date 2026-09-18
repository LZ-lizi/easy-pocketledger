package com.pocketledger.feature.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.dao.BudgetWithName
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.BudgetProgress
import com.pocketledger.domain.DateKeys
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One editable cap: the overall one, a 大类, or a single category. */
data class BudgetRow(
    val categoryId: Long,
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val limitCents: Long,
    val spentCents: Long,
) {
    val progress: BudgetProgress get() = BudgetProgress(categoryId, name, limitCents, spentCents)
    val isSet: Boolean get() = limitCents > 0L
}

data class BudgetUiState(
    val monthKey: String = DateKeys.monthKey(LocalDate.now()),
    val monthLabel: String = DateKeys.monthLabel(DateKeys.monthKey(LocalDate.now())),
    /** The overall cap; [BudgetRow.categoryId] is 0. */
    val total: BudgetRow? = null,
    /** Every top-level expense category, set or not. */
    val mainRows: List<BudgetRow> = emptyList(),
    /** Leaf categories that have a cap of their own. */
    val leafRows: List<BudgetRow> = emptyList(),
    val editorVisible: Boolean = false,
    val editorTarget: BudgetRow? = null,
    /** Offered when adding a leaf budget. */
    val leafOptions: List<CategoryEntity> = emptyList(),
) {
    val totalSpentCents: Long get() = total?.spentCents ?: 0L
    val totalLimitCents: Long get() = total?.limitCents ?: 0L
    val hasAnyBudget: Boolean
        get() = totalLimitCents > 0L || mainRows.any { it.isSet } || leafRows.isNotEmpty()
}

private data class BudgetEditorState(val target: BudgetRow?)

/**
 * Budgets for the current month, at all three levels.
 *
 * A 大类 is an ordinary category row, so the same table and the same query serve
 * "the whole month" (`categoryId = 0`), "all of 餐饮" (a top-level id) and "just
 * 外卖" (a leaf id) with no extra concepts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val monthKey = MutableStateFlow(DateKeys.monthKey(LocalDate.now()))
    private val editor = MutableStateFlow<BudgetEditorState?>(null)

    val uiState: StateFlow<BudgetUiState> = combine(
        monthKey.flatMapLatest { key -> monthStream(key) },
        editor,
    ) { state, editorState ->
        state.copy(
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BudgetUiState(),
    )

    private fun monthStream(key: String) = combine(
        repository.observeBudgets(key),
        repository.observeTotals(key),
        repository.observeMainCategoryTotals(key),
        repository.observeCategoryTotals(
            DateKeys.monthRange(key).first,
            DateKeys.monthRange(key).second,
        ),
        repository.observeCategories(CategoryKind.EXPENSE),
    ) { budgets, totals, mainTotals, categoryTotals, categories ->
        val limitByCategory = budgets.associate { it.categoryId to it.amountCents }
        val mainSpent = mainTotals.associate { it.mainCategoryId to it.totalCents }
        val leafSpent = categoryTotals.associate { it.categoryId to it.totalCents }
        val roots = categories.filter { it.parentId == null }.sortedBy { it.sortOrder }

        BudgetUiState(
            monthKey = key,
            monthLabel = DateKeys.monthLabel(key),
            total = BudgetRow(
                categoryId = 0L,
                name = "总预算",
                iconKey = "wallet",
                colorArgb = 0xFF2F6BFF.toInt(),
                limitCents = limitByCategory[0L] ?: 0L,
                spentCents = totals.expenseCents,
            ),
            mainRows = roots.map { root ->
                BudgetRow(
                    categoryId = root.id,
                    name = root.name,
                    iconKey = root.iconKey,
                    colorArgb = root.colorArgb,
                    limitCents = limitByCategory[root.id] ?: 0L,
                    spentCents = mainSpent[root.id] ?: 0L,
                )
            },
            leafRows = budgets
                .filter { it.categoryId != 0L }
                .mapNotNull { budget ->
                    categories.firstOrNull { it.id == budget.categoryId }?.let { category ->
                        BudgetRow(
                            categoryId = category.id,
                            name = category.name,
                            iconKey = category.iconKey,
                            colorArgb = category.colorArgb,
                            limitCents = budget.amountCents,
                            spentCents = leafSpent[category.id] ?: 0L,
                        )
                    }
                }
                .sortedByDescending { it.spentCents },
            leafOptions = categories.filter { it.parentId != null }.sortedBy { it.sortOrder },
        )
    }

    fun previousMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).minusMonths(1).toString()
    }

    fun nextMonth() {
        monthKey.value = DateKeys.parseMonthKey(monthKey.value).plusMonths(1).toString()
    }

    fun edit(row: BudgetRow) {
        editor.value = BudgetEditorState(row)
    }

    /** A leaf is not shown until it has a cap, so adding one starts from a blank row. */
    fun addLeafBudget(category: CategoryEntity) {
        editor.value = BudgetEditorState(
            BudgetRow(
                categoryId = category.id,
                name = category.name,
                iconKey = category.iconKey,
                colorArgb = category.colorArgb,
                limitCents = 0L,
                spentCents = 0L,
            )
        )
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun save(categoryId: Long, amountCents: Long) {
        val key = monthKey.value
        viewModelScope.launch {
            repository.setBudget(key, categoryId, amountCents)
            editor.value = null
        }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                BudgetViewModel(app.container.repository)
            }
        }
    }
}
