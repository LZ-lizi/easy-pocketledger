package com.pocketledger.feature.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AccountType
import com.pocketledger.data.repo.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccountRow(
    val account: AccountEntity,
    val balanceCents: Long,
)

data class AccountsUiState(
    val accounts: List<AccountRow> = emptyList(),
    /** Everything that is not a credit card. */
    val assetsCents: Long = 0,
    /** Credit-card debt, reported as a positive number. */
    val liabilitiesCents: Long = 0,
) {
    val netWorthCents: Long get() = assetsCents - liabilitiesCents
}

class AccountsViewModel(repository: LedgerRepository) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        repository.observeAllAccounts(),
        repository.observeBalances(),
    ) { accounts, balances ->
        val balanceById = balances.associate { it.accountId to it.balanceCents }
        val rows = accounts
            .filter { !it.isArchived }
            .map { AccountRow(it, balanceById[it.id] ?: it.initialBalanceCents) }
            .sortedBy { it.account.sortOrder }

        var assets = 0L
        var liabilities = 0L
        for (row in rows) {
            if (!row.account.includeInTotal) continue
            if (row.account.type == AccountType.CREDIT_CARD) {
                // A credit card goes negative to mean "owed", so flip the sign.
                if (row.balanceCents < 0L) liabilities += -row.balanceCents
                else assets += row.balanceCents
            } else {
                if (row.balanceCents >= 0L) assets += row.balanceCents else liabilities += -row.balanceCents
            }
        }
        AccountsUiState(accounts = rows, assetsCents = assets, liabilitiesCents = liabilities)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(),
    )

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                AccountsViewModel(app.container.repository)
            }
        }
    }
}
