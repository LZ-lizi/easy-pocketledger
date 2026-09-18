package com.pocketledger.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AccountType
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.data.repo.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountRow(
    val account: AccountEntity,
    val balanceCents: Long,
)

data class AccountsUiState(
    val accounts: List<AccountRow> = emptyList(),
    /** Everything that is not a credit card, archived accounts excluded. */
    val assetsCents: Long = 0,
    /** Credit-card debt, reported as a positive number. */
    val liabilitiesCents: Long = 0,
    val editorVisible: Boolean = false,
    /** Null while creating a new account. */
    val editorTarget: AccountEntity? = null,
    val ledgerName: String = "",
    val ledgerType: LedgerType = LedgerType.BUDGET,
    val selectedLedgerId: Long? = null,
    val ledgers: List<LedgerEntity> = emptyList(),
    val ledgerSwitcherVisible: Boolean = false,
) {
    val netWorthCents: Long get() = assetsCents - liabilitiesCents

    /** 累计模式 ledgers do not track accounts at all. */
    val accountsUnsupported: Boolean get() = ledgerType == LedgerType.ACCUMULATE
}

private data class EditorState(val target: AccountEntity?)

class AccountsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val editor = MutableStateFlow<EditorState?>(null)
    private val ledgerSwitcher = MutableStateFlow(false)

    val uiState: StateFlow<AccountsUiState> = combine(
        repository.observeAllAccounts(),
        repository.observeBalances(),
        editor,
        repository.observeLedgers(),
        combine(repository.selectedLedgerId, ledgerSwitcher) { id, visible -> id to visible },
    ) { accounts, balances, editorState, ledgers, selection ->
        val (selectedId, switcherVisible) = selection
        val current = ledgers.firstOrNull { it.id == selectedId }
        val balanceById = balances.associate { it.accountId to it.balanceCents }

        // Archived accounts stay visible (muted, sorted last) so they can be
        // restored -- hiding them would strand the un-archive action.
        val rows = accounts
            .map { AccountRow(it, balanceById[it.id] ?: it.initialBalanceCents) }
            .sortedWith(compareBy({ it.account.isArchived }, { it.account.sortOrder }))

        var assets = 0L
        var liabilities = 0L
        for (row in rows) {
            if (row.account.isArchived || !row.account.includeInTotal) continue
            if (row.account.type == AccountType.CREDIT_CARD) {
                // A credit card goes negative to mean "owed", so flip the sign.
                if (row.balanceCents < 0L) liabilities += -row.balanceCents
                else assets += row.balanceCents
            } else {
                if (row.balanceCents >= 0L) assets += row.balanceCents
                else liabilities += -row.balanceCents
            }
        }

        AccountsUiState(
            accounts = rows,
            assetsCents = assets,
            liabilitiesCents = liabilities,
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
            ledgerName = current?.name.orEmpty(),
            ledgerType = current?.type ?: LedgerType.BUDGET,
            selectedLedgerId = selectedId,
            ledgers = ledgers,
            ledgerSwitcherVisible = switcherVisible,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(),
    )

    fun createAccount() {
        editor.value = EditorState(null)
    }

    fun editAccount(account: AccountEntity) {
        editor.value = EditorState(account)
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun saveAccount(account: AccountEntity) {
        viewModelScope.launch {
            if (account.id == 0L) {
                val nextOrder = (uiState.value.accounts.maxOfOrNull { it.account.sortOrder } ?: -1) + 1
                repository.addAccount(account.copy(sortOrder = nextOrder))
            } else {
                repository.updateAccount(account)
            }
            editor.value = null
        }
    }

    fun toggleArchive(account: AccountEntity) {
        viewModelScope.launch {
            repository.setAccountArchived(account.id, !account.isArchived)
            editor.value = null
        }
    }

    fun openLedgerSwitcher() {
        ledgerSwitcher.value = true
    }

    fun dismissLedgerSwitcher() {
        ledgerSwitcher.value = false
    }

    /** Switching is a single repository write; every screen follows it. */
    fun selectLedger(id: Long) {
        repository.selectLedger(id)
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                AccountsViewModel(app.container.repository)
            }
        }
    }
}
