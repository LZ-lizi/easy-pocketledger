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
    /**
     * The ledger these terms belong to.
     *
     * Shown in the empty state because terms are **per ledger**, and an empty list on a
     * ledger that never had one is indistinguishable from having lost them. That
     * ambiguity is exactly how it was reported: "updating the app lost my 学期 data",
     * when the list was simply being looked at from another ledger.
     */
    val ledgerName: String = "",
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
        repository.observeSelectedLedger(),
    ) { terms, editorState, ledger ->
        TermSettingsUiState(
            terms = terms.map { TermRow(it, dayCount(it)) },
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
            suggestedName = suggestedTermName(LocalDate.now()),
            ledgerName = ledger?.name.orEmpty(),
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

        /**
         * The boundary the defaults hang off: 3月1日 for 春季, 9月1日 for 秋季.
         *
         * A new term starts on the boundary that has most recently passed, so opening the
         * editor in late September offers the autumn term that has just begun rather than
         * today's date. In January the most recent boundary is still September's, which is
         * right -- the autumn term is the one in progress.
         */
        fun defaultStart(today: LocalDate): LocalDate = when {
            !today.isBefore(LocalDate.of(today.year, 9, 1)) -> LocalDate.of(today.year, 9, 1)
            !today.isBefore(LocalDate.of(today.year, 3, 1)) -> LocalDate.of(today.year, 3, 1)
            else -> LocalDate.of(today.year - 1, 9, 1)
        }

        fun defaultStartDate(today: LocalDate): String = defaultStart(today).toString()

        /**
         * 「2026 秋季学期」 / 「2027 春季学期」, taken from the same boundary as the start
         * date so the name and the dates can never describe different terms.
         *
         * There is deliberately no 秋季/春季 switch: the season is not a preference, it is
         * what the date already says, and offering the choice would only add a way to get
         * it wrong.
         */
        fun suggestedTermName(today: LocalDate): String {
            val start = defaultStart(today)
            val season = if (start.monthValue >= 7) "秋季学期" else "春季学期"
            return "${start.year} $season"
        }

        /** A term runs one semester: four months from its start, both ends inclusive. */
        fun defaultEndDate(startKey: String): String {
            val start = runCatching { LocalDate.parse(startKey) }.getOrNull() ?: LocalDate.now()
            return start.plusMonths(4).minusDays(1).toString()
        }

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
