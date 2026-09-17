package com.pocketledger.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.TermEntity
import com.pocketledger.data.repo.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TermRow(
    val term: TermEntity,
    /** Number of whole days the term spans, inclusive. */
    val dayCount: Long,
)

data class TermSettingsUiState(
    val terms: List<TermRow> = emptyList(),
    val editorVisible: Boolean = false,
    /** Null while creating a new term. */
    val editorTarget: TermEntity? = null,
    /** Pre-filled name for a new term, e.g. 「2026 秋季学期」. */
    val suggestedName: String = "",
)

private data class EditorState(val target: TermEntity?)

/**
 * Manages the named date ranges behind the statistics page's 学期 view.
 *
 * The app cannot know when a given school's term starts, so this is a plain
 * start/end pair the user sets once per term -- guessing a campus calendar would
 * be worse than asking.
 */
class TermSettingsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val editor = MutableStateFlow<EditorState?>(null)

    val uiState: StateFlow<TermSettingsUiState> = combine(
        repository.observeTerms(),
        editor,
    ) { terms, editorState ->
        TermSettingsUiState(
            terms = terms.map { TermRow(it, dayCount(it)) },
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
            suggestedName = suggestedTermName(LocalDate.now()),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TermSettingsUiState(),
    )

    fun createTerm() {
        editor.value = EditorState(null)
    }

    fun editTerm(term: TermEntity) {
        editor.value = EditorState(term)
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun saveTerm(term: TermEntity) {
        viewModelScope.launch {
            if (term.id == 0L) repository.addTerm(term) else repository.updateTerm(term)
            editor.value = null
        }
    }

    fun deleteTerm(term: TermEntity) {
        viewModelScope.launch {
            repository.deleteTerm(term.id)
            editor.value = null
        }
    }

    companion object {

        /** Days spanned inclusively, so a single-day term reports 1 rather than 0. */
        fun dayCount(term: TermEntity): Long {
            val start = runCatching { LocalDate.parse(term.startDateKey) }.getOrNull() ?: return 0L
            val end = runCatching { LocalDate.parse(term.endDateKey) }.getOrNull() ?: return 0L
            return end.toEpochDay() - start.toEpochDay() + 1
        }

        /** 「2026 秋季学期」 for a date in the second half of the year, else 春季. */
        fun suggestedTermName(today: LocalDate): String =
            if (today.monthValue >= 7) "${today.year} 秋季学期" else "${today.year} 春季学期"

        /** A term defaults to roughly one semester starting today. */
        fun defaultEndDate(today: LocalDate): String = today.plusMonths(4).minusDays(1).toString()

        fun isValidRange(startKey: String, endKey: String): Boolean {
            val start = runCatching { LocalDate.parse(startKey) }.getOrNull() ?: return false
            val end = runCatching { LocalDate.parse(endKey) }.getOrNull() ?: return false
            return !end.isBefore(start)
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                TermSettingsViewModel(app.container.repository)
            }
        }
    }
}
