package com.pocketledger.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.repo.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One 大类 with the 小类 filed under it. */
data class CategoryGroup(
    val parent: CategoryEntity,
    val children: List<CategoryEntity>,
)

data class CategorySettingsUiState(
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val groups: List<CategoryGroup> = emptyList(),
    /** Income has no second level, so its rows are shown flat. */
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val editorVisible: Boolean = false,
    /** Null when creating. */
    val editorTarget: CategoryEntity? = null,
    /** Pre-selected parent for a new child; null means a new top-level category. */
    val editorParentId: Long? = null,
    val parentOptions: List<CategoryEntity> = emptyList(),
)

private data class EditorState(
    val target: CategoryEntity?,
    val parentId: Long?,
)

/**
 * Category management.
 *
 * Both the shipped presets and user-created rows are editable and deletable -- the
 * presets are a starting point, not a fixed taxonomy. Deleting is always a soft
 * delete, so transactions that referenced the category keep resolving to a name.
 */
class CategorySettingsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val kind = MutableStateFlow(CategoryKind.EXPENSE)
    private val editor = MutableStateFlow<EditorState?>(null)

    val uiState: StateFlow<CategorySettingsUiState> = combine(
        repository.observeAllCategories(),
        kind,
        editor,
    ) { categories, currentKind, editorState ->
        val visible = categories.filter { !it.isArchived }
        val expenseRoots = visible
            .filter { it.kind == CategoryKind.EXPENSE && it.parentId == null }
            .sortedBy { it.sortOrder }
        val groups = expenseRoots.map { root ->
            CategoryGroup(
                parent = root,
                children = visible
                    .filter { it.parentId == root.id }
                    .sortedBy { it.sortOrder },
            )
        }
        CategorySettingsUiState(
            kind = currentKind,
            groups = groups,
            incomeCategories = visible
                .filter { it.kind == CategoryKind.INCOME }
                .sortedBy { it.sortOrder },
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
            editorParentId = editorState?.parentId,
            parentOptions = expenseRoots,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategorySettingsUiState(),
    )

    fun setKind(value: CategoryKind) {
        kind.value = value
    }

    fun createTopLevel() {
        editor.value = EditorState(target = null, parentId = null)
    }

    fun createChild(parentId: Long) {
        editor.value = EditorState(target = null, parentId = parentId)
    }

    fun edit(category: CategoryEntity) {
        editor.value = EditorState(target = category, parentId = category.parentId)
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun save(category: CategoryEntity) {
        viewModelScope.launch {
            if (category.id == 0L) {
                val order = repository.maxCategorySortOrder(category.kind, category.parentId) + 1
                repository.addCategory(category.copy(sortOrder = order))
            } else {
                repository.updateCategory(category)
            }
            editor.value = null
        }
    }

    /**
     * Moving a leaf between 大类 rewrites one column.
     *
     * This is the escape hatch for a debatable default such as 数码 sitting under
     * 购物: history follows the category, so nothing else needs touching.
     */
    fun move(category: CategoryEntity, newParentId: Long?) {
        viewModelScope.launch { repository.moveCategory(category.id, newParentId) }
    }

    /**
     * Deleting a 大类 takes its 小类 with it.
     *
     * Leaving orphans behind would put a leaf in no group at all, which the
     * management screen cannot render and the entry grid cannot show.
     */
    fun delete(category: CategoryEntity) {
        viewModelScope.launch {
            val children = uiState.value.groups
                .firstOrNull { it.parent.id == category.id }
                ?.children
                .orEmpty()
            children.forEach { repository.deleteCategory(it.id) }
            repository.deleteCategory(category.id)
        }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                CategorySettingsViewModel(app.container.repository)
            }
        }
    }
}
