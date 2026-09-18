package com.pocketledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.LedgerType
import com.pocketledger.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LedgerSettingsUiState(
    val ledgers: List<LedgerEntity> = emptyList(),
    val selectedId: Long? = null,
    val editorVisible: Boolean = false,
    /** Null when creating. */
    val editorTarget: LedgerEntity? = null,
)

/**
 * Distinct name from `TermSettingsViewModel`'s editor state on purpose: private
 * top-level classes still occupy the JVM package namespace, so two files in this
 * package cannot both declare `EditorState`.
 */
private data class LedgerEditorState(val target: LedgerEntity?)

/**
 * Manage and switch ledgers.
 *
 * Switching is a single repository write; every screen observes the selection, so
 * nothing here has to notify anyone.
 */
class LedgerSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val editor = MutableStateFlow<LedgerEditorState?>(null)

    val uiState: StateFlow<LedgerSettingsUiState> = combine(
        repository.observeLedgers(),
        repository.selectedLedgerId,
        editor,
    ) { ledgers, selectedId, editorState ->
        LedgerSettingsUiState(
            ledgers = ledgers,
            selectedId = selectedId,
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerSettingsUiState(),
    )

    fun select(id: Long) {
        repository.selectLedger(id)
    }

    fun createLedger() {
        editor.value = LedgerEditorState(null)
    }

    fun edit(ledger: LedgerEntity) {
        editor.value = LedgerEditorState(ledger)
    }

    fun dismissEditor() {
        editor.value = null
    }

    /**
     * Creating a ledger also seeds its categories and accounts, and switches to it --
     * landing in an empty ledger you then have to find is a worse first impression.
     */
    fun save(ledger: LedgerEntity) {
        viewModelScope.launch {
            if (ledger.id == 0L) {
                container.createLedger(ledger.name, ledger.type)
            } else {
                repository.updateLedger(ledger)
            }
            editor.value = null
        }
    }

    fun toggleArchive(ledger: LedgerEntity) {
        viewModelScope.launch {
            repository.setLedgerArchived(ledger.id, !ledger.isArchived)
            editor.value = null
        }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                LedgerSettingsViewModel(app.container)
            }
        }
    }
}

/** Shown next to each ledger type in the picker. */
fun ledgerTypeLabel(type: LedgerType): String = when (type) {
    LedgerType.BUDGET -> "预算模式"
    LedgerType.ACCUMULATE -> "累计模式"
}
