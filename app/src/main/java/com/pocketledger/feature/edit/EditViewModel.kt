package com.pocketledger.feature.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.entity.TimeMode
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.KeypadInput
import com.pocketledger.domain.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class EditUiState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val id: Long = 0L,
    val type: TxnType = TxnType.EXPENSE,
    val amountInput: String = "",
    val feeInput: String = "",
    val allCategories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedMainCategoryId: Long? = null,
    val selectedCategoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val merchant: String = "",
    val note: String = "",
    val dateKey: String = DateKeys.dateKey(LocalDate.now()),
    /**
     * Wall-clock time, or null while the entry keeps whatever it already had.
     *
     * Null is the meaningful default: an edit that only renames a category must not
     * silently restamp when the entry happened.
     */
    val time: LocalTime? = null,
    /** The stored timestamp, kept so an untouched time survives the edit. */
    val storedMillis: Long = 0L,
    /** Date the entry was stored with, to tell a real move from a re-save. */
    val storedDateKey: String = "",
    /** The stored [TimeMode], preserved when neither date nor time is touched. */
    val storedTimeMode: TimeMode = TimeMode.AUTO,
    val isExcludedFromStats: Boolean = false,
) {
    val isTransfer: Boolean get() = type == TxnType.TRANSFER

    /**
     * What the detail view should show for the clock after this edit.
     *
     * Choosing a time is an explicit statement; moving only the day leaves the stored
     * clock reading as a leftover that the detail view hides; touching neither keeps
     * the entry's existing behaviour.
     */
    val timeMode: TimeMode
        get() = when {
            time != null -> TimeMode.EXPLICIT
            dateKey != storedDateKey -> TimeMode.HIDDEN
            else -> storedTimeMode
        }

    /** The exact timestamp to store. */
    val happenedAtMillis: Long
        get() = time?.let { DateKeys.atTime(dateKey, it) }
            ?: DateKeys.withTimeOfDay(dateKey, storedMillis)

    val kind: CategoryKind
        get() = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    val mainCategories: List<CategoryEntity>
        get() = if (isTransfer) {
            emptyList()
        } else {
            allCategories.filter { it.kind == kind && it.parentId == null }.sortedBy { it.sortOrder }
        }

    /** Income categories are roots; expense items are children of the selected main. */
    val visibleCategories: List<CategoryEntity>
        get() = when {
            isTransfer -> emptyList()
            kind == CategoryKind.INCOME -> mainCategories
            else -> {
                val parent = selectedMainCategoryId ?: mainCategories.firstOrNull()?.id
                allCategories.filter { it.kind == kind && it.parentId == parent }
                    .sortedBy { it.sortOrder }
            }
        }

    val destinationAccounts: List<AccountEntity>
        get() = accounts.filter { it.id != accountId }

    val amountCents: Long get() = Money.parseYuanToCents(amountInput) ?: 0L

    val feeCents: Long get() = Money.parseYuanToCents(feeInput) ?: 0L

    val canSave: Boolean
        get() = when (type) {
            TxnType.TRANSFER -> amountCents > 0L &&
                accountId != null &&
                toAccountId != null &&
                accountId != toAccountId

            else -> amountCents > 0L && accountId != null && selectedCategoryId != null
        }

    /** A transfer has no meaningful 收入/支出 split, so the exclusion toggle hides. */
    val canExcludeFromStats: Boolean get() = !isTransfer
}

/**
 * Backs the form-style edit screen.
 *
 * Editing is a form rather than the keypad because correcting a mistake means
 * reviewing every field at once; the keypad optimises for speed, which is the
 * opposite need.
 */
class EditViewModel(
    private val repository: LedgerRepository,
    private val transactionId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState(id = transactionId))
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val txn = repository.transaction(transactionId)
            if (txn == null) {
                _uiState.update { it.copy(loaded = true, missing = true) }
                return@launch
            }
            val accounts = repository.accountsSnapshot()
            val categories = repository.categoriesSnapshot()
            val parentId = categories.firstOrNull { it.id == txn.categoryId }?.parentId
            _uiState.update {
                it.copy(
                    loaded = true,
                    type = txn.type,
                    amountInput = Money.format(txn.amountCents).replace(",", ""),
                    feeInput = txn.feeCents?.let { fee -> Money.format(fee).replace(",", "") }.orEmpty(),
                    allCategories = categories,
                    accounts = accounts,
                    selectedMainCategoryId = parentId
                        ?: txn.categoryId?.takeIf { id -> categories.any { c -> c.id == id && c.parentId == null } },
                    selectedCategoryId = txn.categoryId,
                    accountId = txn.accountId,
                    toAccountId = txn.toAccountId,
                    merchant = txn.merchant.orEmpty(),
                    note = txn.note.orEmpty(),
                    dateKey = txn.localDateKey,
                    storedMillis = txn.happenedAt,
                    storedDateKey = txn.localDateKey,
                    storedTimeMode = txn.timeMode,
                    isExcludedFromStats = txn.isExcludedFromStats,
                )
            }
        }
    }

    // ------------------------------------------------------------------ editing

    fun setType(type: TxnType) {
        _uiState.update { state ->
            if (state.type == type) return@update state
            val mains = state.allCategories.filter {
                it.kind == kindOf(type) && it.parentId == null
            }
            state.copy(
                type = type,
                selectedCategoryId = null,
                selectedMainCategoryId = mains.firstOrNull()?.id,
                feeInput = if (type == TxnType.TRANSFER) state.feeInput else "",
            )
        }
    }

    fun setAmount(value: String) {
        _uiState.update { it.copy(amountInput = KeypadInput.sanitizeAmount(value)) }
    }

    fun setFee(value: String) {
        _uiState.update { it.copy(feeInput = KeypadInput.sanitizeAmount(value)) }
    }

    fun selectMainCategory(id: Long) {
        _uiState.update { it.copy(selectedMainCategoryId = id, selectedCategoryId = null) }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(selectedCategoryId = id) }
    }

    fun selectAccount(id: Long) {
        _uiState.update { state ->
            val toId = if (state.toAccountId == id) state.accountId else state.toAccountId
            state.copy(accountId = id, toAccountId = toId)
        }
    }

    fun selectToAccount(id: Long) {
        _uiState.update { it.copy(toAccountId = id) }
    }

    fun setMerchant(value: String) {
        _uiState.update { it.copy(merchant = value) }
    }

    fun setNote(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun setExcludedFromStats(excluded: Boolean) {
        _uiState.update { it.copy(isExcludedFromStats = excluded) }
    }

    /** Moves the calendar day, leaving the clock reading as it was. */
    fun setDate(dateKey: String) {
        _uiState.update { it.copy(dateKey = dateKey) }
    }

    /** Records a clock time the user chose, which the detail view then shows plainly. */
    fun setTime(hour: Int, minute: Int) {
        _uiState.update {
            it.copy(time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        }
    }

    /** Drops back to the entry's stored time. */
    fun clearTime() {
        _uiState.update { it.copy(time = null) }
    }

    // ---------------------------------------------------------------- persistence

    fun save(onDone: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        val cents = Money.parseYuanToCents(state.amountInput) ?: return
        val accountId = state.accountId ?: return

        viewModelScope.launch {
            val original = repository.transaction(transactionId) ?: return@launch
            repository.updateTransaction(
                original.copy(
                    type = state.type,
                    amountCents = cents,
                    accountId = accountId,
                    toAccountId = if (state.isTransfer) state.toAccountId else null,
                    feeCents = if (state.isTransfer && state.feeCents > 0L) state.feeCents else null,
                    categoryId = if (state.isTransfer) null else state.selectedCategoryId,
                    merchant = if (state.isTransfer) null else state.merchant.trim().ifBlank { null },
                    note = state.note.trim().ifBlank { null },
                    localDateKey = state.dateKey,
                    happenedAt = state.happenedAtMillis,
                    timeMode = state.timeMode,
                    isExcludedFromStats = if (state.isTransfer) false else state.isExcludedFromStats,
                )
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteTransaction(transactionId)
            onDone()
        }
    }

    private fun kindOf(type: TxnType): CategoryKind =
        if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    companion object {

        fun factory(transactionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                EditViewModel(app.container.repository, transactionId)
            }
        }
    }
}
