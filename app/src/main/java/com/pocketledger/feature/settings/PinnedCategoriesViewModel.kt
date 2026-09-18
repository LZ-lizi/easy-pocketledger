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
import com.pocketledger.feature.entry.EntryUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) {
    val defaultCount: Int get() = EntryUiState.DEFAULT_PRIMARY_CATEGORIES
}

/**
 * Chooses which categories the entry keypad shows before 「更多」.
 *
 * An empty pinned set means "not customised", which the keypad reads as "use the
 * defaults". That is deliberately different from "pinned nothing": someone who wants
 * a shorter grid unpins items and should still end up with a usable one, not an empty
 * one they cannot fix from here.
 */
class PinnedCategoriesViewModel(
    private val repository: LedgerRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val pinnedIds = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<PinnedCategoriesUiState> = combine(
        repository.observeCategories(CategoryKind.EXPENSE),
        pinnedIds,
    ) { categories, pinned ->
        val resolved = pinned.ifEmpty { defaultPinned(categories) }
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PinnedCategoriesUiState(),
    )

    init {
        viewModelScope.launch {
            preferences.pinnedCategoryIds().collect { pinnedIds.value = it }
        }
    }

    fun toggle(category: CategoryEntity) {
        viewModelScope.launch {
            // Materialise the defaults first: toggling while "not customised" must
            // start from what the user can actually see, not from an empty set.
            val current = pinnedIds.value.ifEmpty {
                defaultPinned(repository.categoriesSnapshot())
            }
            val next = if (category.id in current) current - category.id else current + category.id
            preferences.setPinnedCategoryIds(next)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch { preferences.setPinnedCategoryIds(emptySet()) }
    }

    companion object {

        /** The first N leaves, which is what an uncustomised grid shows. */
        fun defaultPinned(categories: List<CategoryEntity>): Set<Long> =
            categories
                .filter { it.kind == CategoryKind.EXPENSE && it.parentId != null }
                .sortedBy { it.sortOrder }
                .take(EntryUiState.DEFAULT_PRIMARY_CATEGORIES)
                .map { it.id }
                .toSet()

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                PinnedCategoriesViewModel(app.container.repository, app.container.appPreferences)
            }
        }
    }
}
