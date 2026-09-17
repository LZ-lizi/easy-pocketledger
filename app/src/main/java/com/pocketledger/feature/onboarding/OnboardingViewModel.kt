package com.pocketledger.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.Presets
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val name: String = Presets.DEFAULT_LEDGER_NAME,
    val type: LedgerType = LedgerType.BUDGET,
    val creating: Boolean = false,
) {
    val canCreate: Boolean get() = name.isNotBlank() && !creating
}

/**
 * Backs first launch: pick a name and a tracking mode, then the ledger is seeded
 * with its starting categories and accounts.
 */
class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun setName(value: String) {
        _uiState.update { it.copy(name = value) }
    }

    fun setType(value: LedgerType) {
        _uiState.update { it.copy(type = value) }
    }

    fun create() {
        val state = _uiState.value
        if (!state.canCreate) return
        _uiState.update { it.copy(creating = true) }
        viewModelScope.launch {
            // Creating also selects the ledger, which is what dismisses onboarding:
            // the shell renders the tabs as soon as a ledger is selected.
            container.createLedger(state.name.trim(), state.type)
        }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                OnboardingViewModel(app.container)
            }
        }
    }
}
