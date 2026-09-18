package com.pocketledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.prefs.AppPreferences
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.QuickCategories
import com.pocketledger.feature.entry.EntryUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PinnedItem(
    val category: CategoryEntity,
    val pinned: Boolean,
)

/** One 大类 with its leaves, each carrying whether it shows on the first screen. */
data class PinnedGroup(
    val parent: CategoryEntity,
    val children: List<PinnedItem>,
)

data class PinnedCategoriesUiState(
    val groups: List<PinnedGroup> = emptyList(),
    val pinnedCount: Int = 0,
    /** True until the user customises; the grid then falls back to its defaults. */
    val usingDefaults: Boolean = true,
    /** The ledger these pins belong to; writes are scoped to it. */
    val ledgerId: Long? = null,
) {
    val defaultCount: Int get() = EntryUiState.DEFAULT_PRIMARY_CATEGORIES
}

/**
 * Chooses which categories the entry keypad shows before 「更多」.
 *
 * Scoped to the selected ledger, matching the keypad: the pinned ids are category rows,
 * so a set chosen here only means anything in the ledger it was chosen in.
 *
 * An empty pinned set means "not customised", which the keypad reads as "use the
 * defaults". That is deliberately different from "pinned nothing": someone who wants
 * a shorter grid unpins items and should still end up with a usable one, not an empty
 * one they cannot fix from here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinnedCategoriesViewModel(
    private val repository: LedgerRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val pinnedIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Ids in the order the keypad actually offers them.
     *
     * Read once rather than observed: recency changes when an entry is saved, and
     * rescanning it while this screen is open would move rows under the user's finger.
     */
    private val recentIds = MutableStateFlow<List<Long>>(emptyList())

    val uiState: StateFlow<PinnedCategoriesUiState> = combine(
        repository.observeCategories(CategoryKind.EXPENSE),
        pinnedIds,
        recentIds,
        repository.selectedLedgerId,
    ) { categories, pinned, recent, ledgerId ->
        val ordered = orderLikeKeypad(categories, recent)
        val resolved = pinned.ifEmpty {
            QuickCategories.select(ordered, recent).map { it.id }.toSet()
        }
        val roots = categories.filter { it.parentId == null }.sortedBy { it.sortOrder }
        PinnedCategoriesUiState(
            groups = roots.map { root ->
                PinnedGroup(
                    parent = root,
                    children = categories
                        .filter { it.parentId == root.id }
                        .sortedBy { it.sortOrder }
                        .map { PinnedItem(it, it.id in resolved) },
                )
            },
            pinnedCount = resolved.size,
            usingDefaults = pinned.isEmpty(),
            ledgerId = ledgerId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PinnedCategoriesUiState(),
    )

    init {
        viewModelScope.launch {
            // Re-read whenever the ledger changes: a set from another ledger names rows
            // that are not in this one.
            repository.selectedLedgerId
                .filterNotNull()
                .flatMapLatest { ledgerId -> preferences.pinnedCategoryIds(ledgerId) }
                .collect { pinnedIds.value = it }
        }
        viewModelScope.launch {
            recentIds.value = repository.recentCategoryIds()
        }
    }

    fun toggle(category: CategoryEntity) {
        val ledgerId = uiState.value.ledgerId ?: return
        viewModelScope.launch {
            // Materialise the defaults first: toggling while "not customised" must
            // start from what the user can actually see, not from an empty set.
            val current = pinnedIds.value.ifEmpty {
                val categories = repository.categoriesSnapshot()
                QuickCategories.select(orderLikeKeypad(categories, recentIds.value), recentIds.value)
                    .map { it.id }
                    .toSet()
            }
            val next = if (category.id in current) current - category.id else current + category.id
            preferences.setPinnedCategoryIds(ledgerId, next)
        }
    }

    fun resetToDefaults() {
        val ledgerId = uiState.value.ledgerId ?: return
        viewModelScope.launch { preferences.setPinnedCategoryIds(ledgerId, emptySet()) }
    }

    companion object {

        /**
         * Expense leaves in the keypad's own order: most recently used first, then by
         * the user's ordering.
         *
         * The settings page used to order by `sortOrder` alone, so an uncustomised grid
         * showed a *different* twelve categories here than on the keypad -- the toggles
         * claimed to describe the keypad and did not.
         */
        fun orderLikeKeypad(
            categories: List<CategoryEntity>,
            recentIds: List<Long>,
        ): List<CategoryEntity> {
            val recent = recentIds.withIndex().associate { it.value to it.index }
            return categories
                .filter { it.kind == CategoryKind.EXPENSE && it.parentId != null }
                .sortedWith(compareBy({ recent[it.id] ?: Int.MAX_VALUE }, { it.sortOrder }))
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                PinnedCategoriesViewModel(app.container.repository, app.container.appPreferences)
            }
        }
    }
}
