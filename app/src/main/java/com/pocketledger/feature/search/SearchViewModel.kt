package com.pocketledger.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * A filter the search can apply.
 *
 * Every field is nullable and null means "off", matching the query's own convention --
 * there is one representation of "not filtering on this" rather than one per field.
 */
data class SearchFilters(
    val keyword: String = "",
    val startKey: String? = null,
    val endKey: String? = null,
    val type: TxnType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
) {
    /** True when nothing narrows the ledger down, so the screen can say so. */
    val isEmpty: Boolean
        get() = keyword.isBlank() && startKey == null && endKey == null && type == null &&
            accountId == null && categoryId == null && minCents == null && maxCents == null

    /** How many filters are on, for the summary line. */
    val activeCount: Int
        get() = listOf(
            keyword.isNotBlank(),
            startKey != null || endKey != null,
            type != null,
            accountId != null,
            categoryId != null,
            minCents != null || maxCents != null,
        ).count { it }
}

data class SearchUiState(
    val filters: SearchFilters = SearchFilters(),
    val results: List<TxnRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    val resultCount: Int get() = results.size

    val totalCents: Long get() = results.sumOf { it.amountCents }

    /** Matches the search screen's own copy: "筛选出 N 条". */
    val hasFilters: Boolean get() = !filters.isEmpty
}

/**
 * Backs the search screen.
 *
 * Filtering happens in SQL rather than in memory: the ledger can hold every entry the
 * user has ever made, and the screen only ever shows the newest few hundred matches.
 *
 * The filters are a single [SearchFilters] value rather than one flow per field, so
 * changing two of them at once -- picking a date range, say -- re-queries once instead
 * of once per field.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val filters = MutableStateFlow(SearchFilters())

    val uiState: StateFlow<SearchUiState> = combine(
        filters.flatMapLatest { filter ->
            combine(
                repository.observeSearch(
                    startKey = filter.startKey,
                    endKey = filter.endKey,
                    type = filter.type,
                    accountId = filter.accountId,
                    categoryId = filter.categoryId,
                    minCents = filter.minCents,
                    maxCents = filter.maxCents,
                    keyword = filter.keyword,
                ),
                repository.observeAllCategories(),
                repository.observeAllAccounts(),
            ) { rows, categories, accounts -> Triple(rows, categories, accounts) }
        },
        filters,
    ) { (rows, categories, accounts), filter ->
        SearchUiState(
            filters = filter,
            results = rows,
            categories = categories,
            accounts = accounts,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun setKeyword(value: String) = filters.update { it.copy(keyword = value) }

    fun setDateRange(startKey: String?, endKey: String?) =
        filters.update { it.copy(startKey = startKey, endKey = endKey) }

    fun setType(type: TxnType?) = filters.update { it.copy(type = type) }

    fun setAccount(id: Long?) = filters.update { it.copy(accountId = id) }

    fun setCategory(id: Long?) = filters.update { it.copy(categoryId = id) }

    fun setAmountRange(minCents: Long?, maxCents: Long?) =
        filters.update { it.copy(minCents = minCents, maxCents = maxCents) }

    /** Clears the keyword but keeps the structural filters. */
    fun clearKeyword() = filters.update { it.copy(keyword = "") }

    fun clearAll() {
        filters.value = SearchFilters()
    }

    /** Filters offered as chips: the ones worth describing in words. */
    val amountBounds: Pair<Long?, Long?>
        get() = filters.value.minCents to filters.value.maxCents

    /** Parses a yuan string from the amount fields; blank means "no bound". */
    fun parseYuan(value: String): Long? =
        value.trim().takeIf { it.isNotEmpty() }?.let { Money.parseYuanToCents(it) }

    /** Expense leaves plus top-level rows, for the category picker. */
    fun categoryOptions(state: SearchUiState): List<CategoryEntity> =
        state.categories.filter { it.kind == CategoryKind.EXPENSE || it.parentId == null }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                SearchViewModel(app.container.repository)
            }
        }
    }
}
