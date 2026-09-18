package com.pocketledger.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.ImportBatchEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.entity.TimeMode
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.CategoryMatcher
import com.pocketledger.domain.CsvImport
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.ImportFormat
import com.pocketledger.domain.ImportPreview
import com.pocketledger.domain.ImportRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

/** Where the import flow currently is. */
enum class ImportStage {
    /** Nothing read yet. */
    PICK,

    /** A file is parsed and waiting for review. */
    REVIEW,
}

/** One parsed row plus everything the review list needs to draw it. */
data class ImportRowView(
    val row: ImportRow,
    val categoryId: Long?,
    val categoryName: String?,
    /**
     * Already present in this ledger.
     *
     * Kept in the list rather than dropped: a user who sees "12 rows, 12 skipped" needs
     * to know *which* rows were skipped, and a silently shorter list reads as a bug.
     */
    val alreadyImported: Boolean,
    val include: Boolean,
) {
    val isIncome: Boolean get() = row.type == TxnType.INCOME
}

data class ImportBatchView(
    val batch: ImportBatchEntity,
    val sourceLabel: String,
)

data class ImportUiState(
    val stage: ImportStage = ImportStage.PICK,
    val fileName: String? = null,
    val format: ImportFormat? = null,
    val rows: List<ImportRowView> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val accountId: Long? = null,
    val skippedCount: Int = 0,
    val skippedReasons: List<String> = emptyList(),
    val batches: List<ImportBatchView> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    /**
     * Keyword rules learned from earlier imports, kept for the length of one review.
     *
     * Needed when a row's direction is flipped: the categories of the other kind have to
     * be matched again, and doing it with the same rules the first pass used is what
     * keeps a flipped 生活费 row filed as income instead of falling back to 未分类.
     */
    val importRules: Map<String, Long> = emptyMap(),
) {
    val duplicateCount: Int get() = rows.count { it.alreadyImported }

    /** Rows whose direction had to be inferred rather than read from the file. */
    val inferredCount: Int get() = rows.count { it.row.typeInferred }

    val selected: List<ImportRowView> get() = rows.filter { it.include }

    val selectedTotalCents: Long get() = selected.sumOf { it.row.amountCents }

    val selectedIncomeCount: Int get() = selected.count { it.isIncome }

    val selectedExpenseCount: Int get() = selected.count { !it.isIncome }

    val canImport: Boolean get() = !busy && accountId != null && selected.isNotEmpty()

    /** True when every row is excluded because the file is already in the ledger. */
    val allDuplicates: Boolean get() = rows.isNotEmpty() && duplicateCount == rows.size
}

/**
 * Backs the CSV import flow.
 *
 * Parsing happens off the main thread and the result is reviewed before anything is
 * written: an importer that writes first and asks later is how a ledger ends up with
 * three hundred uncategorised rows and no way to tell which came from where.
 *
 * Nothing is committed until 「导入」, and the whole batch can be undone afterwards.
 */
class ImportViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeImportBatches().collect { batches ->
                _uiState.update { state ->
                    state.copy(batches = batches.map { ImportBatchView(it, sourceLabel(it.source)) })
                }
            }
        }
        viewModelScope.launch {
            val accounts = repository.accountsSnapshot()
            val categories = repository.categoriesSnapshot()
            _uiState.update { state ->
                state.copy(
                    accounts = accounts,
                    categories = categories,
                    accountId = state.accountId ?: accounts.firstOrNull()?.id,
                )
            }
        }
    }

    /**
     * Reads a picked file.
     *
     * The bytes arrive already read rather than as a URI: the content resolver is an
     * Android dependency, and keeping it out of here is what lets the parser and this
     * flow's decisions be tested without a device.
     */
    fun onFilePicked(fileName: String, bytes: ByteArray) {
        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            // One entry point for both shapes: the importer decides from the bytes, so
            // the picker cannot hand it something it mishandles.
            val preview = withContext(Dispatchers.Default) { CsvImport.parseFile(fileName, bytes) }
            val existing = repository.existingDedupeHashes()
            val rules = repository.importRules()
            val categories = repository.categoriesSnapshot()
            val accounts = repository.accountsSnapshot()

            val views = preview.rows.map { row ->
                val duplicate = row.dedupeHash in existing
                val categoryId = if (duplicate) null else CategoryMatcher.match(row, categories, rules)
                ImportRowView(
                    row = row,
                    categoryId = categoryId,
                    categoryName = categories.firstOrNull { it.id == categoryId }?.name,
                    alreadyImported = duplicate,
                    // Duplicates start unchecked so a second import of an overlapping
                    // file is one tap, while still being visible and re-includable.
                    include = !duplicate,
                )
            }

            _uiState.update { state ->
                state.copy(
                    busy = false,
                    stage = ImportStage.REVIEW,
                    fileName = fileName,
                    format = preview.format,
                    rows = views,
                    categories = categories,
                    accounts = accounts,
                    accountId = state.accountId ?: accounts.firstOrNull()?.id,
                    skippedCount = preview.skippedCount,
                    skippedReasons = preview.skippedReasons,
                    importRules = rules,
                    message = if (preview.isEmpty) {
                        "没有读到可导入的记录，确认这是微信或支付宝导出的账单（CSV 或 xlsx）。"
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun toggleRow(index: Int) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.mapIndexed { i, view ->
                    if (i == index) view.copy(include = !view.include) else view
                }
            )
        }
    }

    /** Select-all / clear-all, skipping duplicates when selecting. */
    fun setAllIncluded(include: Boolean) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { view ->
                    view.copy(include = include && !view.alreadyImported)
                }
            )
        }
    }

    fun setRowCategory(index: Int, categoryId: Long?) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.mapIndexed { i, view ->
                    if (i != index) {
                        view
                    } else {
                        view.copy(
                            categoryId = categoryId,
                            categoryName = state.categories.firstOrNull { it.id == categoryId }?.name,
                        )
                    }
                }
            )
        }
    }

    /**
     * Flips one row between 支出 and 收入.
     *
     * The category is matched again rather than cleared: the two kinds have separate
     * category sets, so the old one cannot survive, but the new one can usually be
     * guessed -- 生活费 is still 生活费 on the other side of a wrong inference.
     */
    fun flipRowType(index: Int) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.mapIndexed { i, view ->
                    if (i != index) view else view.copyWith(state, view.row.flipped())
                }
            )
        }
    }

    /**
     * Sets every row's direction at once.
     *
     * Offered only when some direction was inferred: a bank statement that uses the
     * opposite sign convention would otherwise need one tap per row.
     */
    fun setAllTypes(income: Boolean) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { view ->
                    if (view.isIncome == income) view else view.copyWith(state, view.row.flipped())
                }
            )
        }
    }

    /** Replaces a view's direction and re-matches a category for the new kind. */
    private fun ImportRowView.copyWith(state: ImportUiState, row: ImportRow): ImportRowView {
        val categoryId = CategoryMatcher.match(row, state.categories, state.importRules)
        return copy(
            row = row,
            categoryId = categoryId,
            categoryName = state.categories.firstOrNull { it.id == categoryId }?.name,
        )
    }

    fun setAccount(id: Long) {
        _uiState.update { it.copy(accountId = id) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Clears the reviewed file so another can be picked. */
    fun reset() {
        _uiState.update {
            it.copy(
                stage = ImportStage.PICK,
                fileName = null,
                format = null,
                rows = emptyList(),
                skippedCount = 0,
                skippedReasons = emptyList(),
                importRules = emptyMap(),
                message = null,
            )
        }
    }

    /** Writes the reviewed rows as one batch. */
    fun commitImport() {
        val state = _uiState.value
        val accountId = state.accountId ?: return
        val selected = state.selected
        if (selected.isEmpty() || state.busy) return

        _uiState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val format = state.format ?: ImportFormat.GENERIC
            val source = format.toTxnSource()

            // The batch row is written first so its id can stamp every transaction. If
            // the insert below fails, the worst case is an empty batch record, which
            // the list shows as a zero-row import rather than losing anything.
            val batchId = repository.addImportBatch(
                ImportBatchEntity(
                    source = source,
                    fileName = state.fileName,
                    txnCount = selected.size,
                    skippedCount = state.skippedCount + state.duplicateCount,
                )
            )

            repository.addImportedTransactions(
                batchId,
                selected.map { view -> view.toEntity(accountId, source) },
            )

            // Only decisions that produced a row are learned; remembering a category
            // for a row the user excluded would teach the matcher from a rejection.
            repository.rememberImportRules(
                selected.mapNotNull { view ->
                    val keyword = CategoryMatcher.keywordFor(view.row)
                    val categoryId = view.categoryId
                    if (keyword != null && categoryId != null) keyword to categoryId else null
                }.toMap()
            )

            _uiState.update {
                it.copy(
                    busy = false,
                    stage = ImportStage.PICK,
                    fileName = null,
                    format = null,
                    rows = emptyList(),
                    skippedCount = 0,
                    skippedReasons = emptyList(),
                    importRules = emptyMap(),
                    message = "已导入 ${selected.size} 条记录，可在下方撤销。",
                )
            }
        }
    }

    /** Reverses one whole import. */
    fun undoImport(batchId: Long) {
        viewModelScope.launch {
            repository.undoImport(batchId)
            _uiState.update { it.copy(message = "已撤销这次导入的记录。") }
        }
    }

    private fun ImportRowView.toEntity(accountId: Long, source: TxnSource): TxnEntity {        val time = row.time
        return TxnEntity(
            type = row.type,
            amountCents = row.amountCents,
            accountId = accountId,
            categoryId = categoryId,
            merchant = row.merchant,
            note = row.note,
            happenedAt = DateKeys.atTime(row.dateKey, time ?: IMPORT_DEFAULT_TIME),
            localDateKey = row.dateKey,
            // A bill states the moment the money moved, so the clock is a fact rather
            // than a default. Without one there is nothing honest to show, so the
            // detail view hides the time instead of inventing noon.
            timeMode = if (time != null) TimeMode.EXPLICIT else TimeMode.HIDDEN,
            source = source,
            externalNo = row.externalNo,
            dedupeHash = row.dedupeHash,
        )
    }

    companion object {

        /**
         * Timestamp used for bill rows that carry only a date.
         *
         * Midday keeps such rows in a stable place within their day: midnight would
         * sort them before everything, and "now" would move them around on every
         * re-import.
         */
        private val IMPORT_DEFAULT_TIME: LocalTime = LocalTime.NOON

        fun sourceLabel(source: TxnSource): String = when (source) {
            TxnSource.MANUAL -> "手动录入"
            TxnSource.TEMPLATE -> "模板"
            TxnSource.IMPORT_ALIPAY -> "支付宝账单"
            TxnSource.IMPORT_WECHAT -> "微信账单"
            TxnSource.IMPORT_CSV -> "CSV 文件"
            TxnSource.INSTALLMENT -> "月付"
        }

        private fun ImportFormat.toTxnSource(): TxnSource = when (this) {
            ImportFormat.WECHAT -> TxnSource.IMPORT_WECHAT
            ImportFormat.ALIPAY -> TxnSource.IMPORT_ALIPAY
            ImportFormat.LEDGER, ImportFormat.GENERIC -> TxnSource.IMPORT_CSV
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                ImportViewModel(app.container.repository)
            }
        }
    }
}
