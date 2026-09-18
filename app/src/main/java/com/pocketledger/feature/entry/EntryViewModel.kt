package com.pocketledger.feature.entry

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
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.data.entity.TxnType
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
import java.time.ZoneId

data class EntryUiState(
    val type: TxnType = TxnType.EXPENSE,
    /** Raw keypad text such as `"12.3"`; parsed only when needed. */
    val amountInput: String = "",
    /** Transfer fee, only meaningful when [type] is TRANSFER. */
    val feeInput: String = "",
    val allCategories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    /** Destination account; TRANSFER only. */
    val selectedToAccountId: Long? = null,
    val note: String = "",
    val merchant: String = "",
    val dateKey: String = DateKeys.dateKey(LocalDate.now()),
    /**
     * Null means "use the current time", which is what a new entry almost always
     * wants. A time is only displayed and stored once the user changes it, so the
     * field stays out of the way of the common case.
     */
    val customTime: LocalTime? = null,
    val recentCategoryIds: List<Long> = emptyList(),
    /** Drives the brief "已记一笔" confirmation; cleared on the next input. */
    val justSaved: Boolean = false,
) {
    val isTransfer: Boolean get() = type == TxnType.TRANSFER

    val hasCustomTime: Boolean get() = customTime != null

    val kind: CategoryKind
        get() = if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    /**
     * The items the keypad offers.
     *
     * Expense shows **every leaf in one grid** rather than a 大类 tab plus that
     * group's leaves: the two-step picker spent a tap on a choice the person does
     * not actually care about, and the 大类 still exists for statistics, which is
     * where grouping earns its keep.
     */
    val visibleCategories: List<CategoryEntity>
        get() = when {
            isTransfer -> emptyList()
            kind == CategoryKind.INCOME -> allCategories.filter { it.kind == kind }
            else -> allCategories.filter { it.kind == kind && it.parentId != null }
        }.sortedBy { it.sortOrder }

    /** Recently used items float to the front; a flat grid is slow to scan otherwise. */
    val orderedCategories: List<CategoryEntity>
        get() {
            val recent = recentCategoryIds.withIndex().associate { it.value to it.index }
            return visibleCategories.sortedWith(
                compareBy({ recent[it.id] ?: Int.MAX_VALUE }, { it.sortOrder })
            )
        }

    val amountCents: Long get() = Money.parseYuanToCents(amountInput) ?: 0L

    val feeCents: Long get() = Money.parseYuanToCents(feeInput) ?: 0L

    val canSave: Boolean
        get() = when (type) {
            // Money moving between two of your own accounts is not spending, so no
            // category is required -- but it must actually move somewhere else.
            TxnType.TRANSFER -> amountCents > 0L &&
                selectedAccountId != null &&
                selectedToAccountId != null &&
                selectedAccountId != selectedToAccountId

            else -> amountCents > 0L && selectedAccountId != null && selectedCategoryId != null
        }

    /** Accounts offered as the destination, i.e. everything except the source. */
    val destinationAccounts: List<AccountEntity>
        get() = accounts.filter { it.id != selectedAccountId }
}

/**
 * Backs the keypad entry screen.
 *
 * The success path is tuned for repetition: after saving, the amount and note
 * clear but the category and account stay, so a second similar entry is two taps.
 */
class EntryViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllCategories().collect { categories ->
                _uiState.update { it.copy(allCategories = categories) }
            }
        }
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    val accountId = state.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id
                    val toAccountId = state.selectedToAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } && id != accountId }
                        ?: accounts.firstOrNull { it.id != accountId }?.id
                    state.copy(
                        accounts = accounts,
                        selectedAccountId = accountId,
                        selectedToAccountId = toAccountId,
                    )
                }
            }
        }
        viewModelScope.launch {
            val recent = repository.recentCategoryIds()
            _uiState.update { it.copy(recentCategoryIds = recent) }
        }
    }

    // ------------------------------------------------------------------ editing

    /**
     * Switches among expense, income and transfer.
     *
     * The category selection is dropped because the trees share no ids; the amount
     * is deliberately kept, since a mistyped type is the common case.
     */
    fun setType(type: TxnType) {
        _uiState.update { state ->
            if (state.type == type) return@update state
            state.copy(
                type = type,
                selectedCategoryId = null,
                feeInput = if (type == TxnType.TRANSFER) state.feeInput else "",
                justSaved = false,
            )
        }
    }

    fun pressKey(key: String) {
        _uiState.update { state ->
            val next = if (key == ".") {
                KeypadInput.appendDecimalPoint(state.amountInput)
            } else {
                KeypadInput.appendDigit(state.amountInput, key)
            }
            state.copy(amountInput = next, justSaved = false)
        }
    }

    fun backspace() {
        _uiState.update { state ->
            state.copy(amountInput = state.amountInput.dropLast(1), justSaved = false)
        }
    }

    fun clearAmount() {
        _uiState.update { it.copy(amountInput = "", justSaved = false) }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(selectedCategoryId = id, justSaved = false) }
    }

    fun selectAccount(id: Long) {
        _uiState.update { state ->
            // Picking the destination as the source swaps them rather than blocking.
            val toId = if (state.selectedToAccountId == id) state.selectedAccountId else state.selectedToAccountId
            state.copy(selectedAccountId = id, selectedToAccountId = toId, justSaved = false)
        }
    }

    fun selectToAccount(id: Long) {
        _uiState.update { it.copy(selectedToAccountId = id, justSaved = false) }
    }

    fun setFee(value: String) {
        _uiState.update { it.copy(feeInput = value, justSaved = false) }
    }

    fun setNote(value: String) {
        _uiState.update { it.copy(note = value, justSaved = false) }
    }

    fun setMerchant(value: String) {
        _uiState.update { it.copy(merchant = value, justSaved = false) }
    }

    fun setDate(dateKey: String) {
        _uiState.update { it.copy(dateKey = dateKey, justSaved = false) }
    }

    fun shiftDate(days: Long) {
        _uiState.update { state ->
            val next = runCatching { LocalDate.parse(state.dateKey).plusDays(days) }
                .getOrElse { LocalDate.now() }
            state.copy(dateKey = next.toString(), justSaved = false)
        }
    }

    /** Setting a time also marks the entry as deliberately timed. */
    fun setTime(hour: Int, minute: Int) {
        _uiState.update {
            it.copy(
                customTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                justSaved = false,
            )
        }
    }

    /** Drops back to "use the current time" and hides the time again. */
    fun useCurrentTime() {
        _uiState.update { it.copy(customTime = null, justSaved = false) }
    }

    // -------------------------------------------------------------------- saving

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val cents = Money.parseYuanToCents(state.amountInput) ?: return
        val accountId = state.selectedAccountId ?: return
        val fee = Money.parseYuanToCents(state.feeInput) ?: 0L

        viewModelScope.launch {
            repository.addTransaction(
                TxnEntity(
                    type = state.type,
                    amountCents = cents,
                    accountId = accountId,
                    toAccountId = if (state.isTransfer) state.selectedToAccountId else null,
                    feeCents = if (state.isTransfer && fee > 0L) fee else null,
                    categoryId = if (state.isTransfer) null else state.selectedCategoryId,
                    merchant = if (state.isTransfer) null else state.merchant.trim().ifBlank { null },
                    note = state.note.trim().ifBlank { null },
                    happenedAt = epochMillisFor(state.dateKey, state.customTime),
                    localDateKey = state.dateKey,
                    source = TxnSource.MANUAL,
                )
            )
            // Keep category and account: the next entry is usually a sibling of this one.
            _uiState.update {
                it.copy(
                    amountInput = "",
                    feeInput = "",
                    note = "",
                    merchant = "",
                    justSaved = true,
                )
            }
        }
    }

    private fun kindOf(type: TxnType): CategoryKind =
        if (type == TxnType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    companion object {

        /** The chosen day at the chosen time, or at the current time when none was set. */
        fun epochMillisFor(dateKey: String, time: LocalTime?): Long {
            val date = runCatching { LocalDate.parse(dateKey) }.getOrElse { LocalDate.now() }
            return date.atTime(time ?: LocalTime.now())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                EntryViewModel(app.container.repository)
            }
        }
    }
}
