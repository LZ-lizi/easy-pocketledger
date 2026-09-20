package com.pocketledger.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.dao.TxnRow
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.di.AppContainer
import com.pocketledger.domain.AppBackup
import com.pocketledger.domain.CsvExport
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.ExportRow
import com.pocketledger.domain.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which rows the CSV covers.
 *
 * The ledger choice belongs here and only here: a CSV is a spreadsheet of transactions, so
 * "which book" is a real question with a real answer. A backup is a copy of the
 * application, and asking which part of it to copy would be asking the user to describe a
 * state they never wanted.
 */
sealed interface CsvTarget {
    /** Every active ledger, with the 账本 column filled in. */
    data object AllLedgers : CsvTarget

    data class One(val ledgerId: Long, val name: String) : CsvTarget
}

/** What to include in a CSV export. */
enum class ExportScope(val label: String, val detail: String) {
    ALL("全部记录", "所选账本的所有流水"),
    THIS_MONTH("本月", "只导出本月"),
}

/** One entry in the CSV target picker. */
data class LedgerChoice(val id: Long, val name: String)

data class ExportUiState(
    val totalCount: Int = 0,
    val monthCount: Int = 0,
    val ledgerName: String = "",
    val ledgers: List<LedgerChoice> = emptyList(),
    val target: CsvTarget = CsvTarget.AllLedgers,
    val targetPickerVisible: Boolean = false,
    val loaded: Boolean = false,
    /** Non-null once the CSV is built and waiting for a destination. */
    val pending: PendingExport? = null,
    val lastResult: String? = null,
    // ------------------------------------------------------------- application backup
    /** Non-null once the backup file is built and waiting for a destination. */
    val pendingBackup: PendingExport? = null,
    val backupResult: String? = null,
    /** Parsed backup awaiting the user's confirmation before it replaces everything. */
    val pendingRestore: AppBackup.File? = null,
    val restoring: Boolean = false,
    val restoreResult: String? = null,
) {
    val hasData: Boolean get() = totalCount > 0

    /** The name shown on the picker field. */
    val targetLabel: String
        get() = when (val current = target) {
            CsvTarget.AllLedgers -> "全部账本"
            is CsvTarget.One -> current.name
        }

    /** True when the file will carry several ledgers, so the 账本 column appears. */
    val spansLedgers: Boolean get() = target is CsvTarget.AllLedgers && ledgers.size > 1
}

/** A built file plus the name to suggest for it. */
data class PendingExport(
    val fileName: String,
    val content: String,
)

/**
 * Builds the export files.
 *
 * Contents are produced *before* the file picker opens: the system picker only hands back
 * a destination, and generating the content afterwards would mean the user could pick a
 * location for something that then failed to build.
 */
class ExportViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val ledgers = repository.activeLedgers().map { LedgerChoice(it.id, it.name) }
            val selected = repository.selectedLedgerId.value
            val current = ledgers.firstOrNull { it.id == selected }
            _uiState.update {
                it.copy(
                    ledgers = ledgers,
                    ledgerName = current?.name.orEmpty(),
                    // Defaults to the ledger the user is looking at; 全部账本 is one tap
                    // away in the picker.
                    target = current?.let { choice -> CsvTarget.One(choice.id, choice.name) }
                        ?: CsvTarget.AllLedgers,
                    loaded = true,
                )
            }
            refreshCounts()
        }
    }

    /** Both counts are recomputed whenever the target changes, so they always agree. */
    private suspend fun refreshCounts() {
        val state = _uiState.value
        val ids = when (val target = state.target) {
            CsvTarget.AllLedgers -> state.ledgers.map { it.id }
            is CsvTarget.One -> listOf(target.ledgerId)
        }
        val monthKey = DateKeys.monthKey(LocalDate.now())
        val rows = repository.rowsForExport(ids).map { it.row }
        _uiState.update {
            it.copy(
                totalCount = rows.size,
                monthCount = rows.count { row -> row.localDateKey.startsWith(monthKey) },
            )
        }
    }

    fun openTargetPicker() {
        _uiState.update { it.copy(targetPickerVisible = true) }
    }

    fun dismissTargetPicker() {
        _uiState.update { it.copy(targetPickerVisible = false) }
    }

    fun chooseAllLedgers() {
        _uiState.update { it.copy(target = CsvTarget.AllLedgers, targetPickerVisible = false) }
        viewModelScope.launch { refreshCounts() }
    }

    fun chooseLedger(choice: LedgerChoice) {
        _uiState.update {
            it.copy(target = CsvTarget.One(choice.id, choice.name), targetPickerVisible = false)
        }
        viewModelScope.launch { refreshCounts() }
    }

    fun prepare(scope: ExportScope) {
        viewModelScope.launch {
            val state = _uiState.value
            val ids = when (val target = state.target) {
                CsvTarget.AllLedgers -> state.ledgers.map { it.id }
                is CsvTarget.One -> listOf(target.ledgerId)
            }
            val names = state.ledgers.associate { it.id to it.name }
            val spansLedgers = ids.size > 1
            val monthKey = DateKeys.monthKey(LocalDate.now())
            val categories = repository.categoriesSnapshot().associateBy { it.id }
            val content = CsvExport.build(
                repository.rowsForExport(ids)
                    .filter { tagged ->
                        scope == ExportScope.ALL ||
                            tagged.row.localDateKey.startsWith(monthKey)
                    }
                    .map { tagged ->
                        tagged.row.toExportRow(
                            categories = categories,
                            // Only filled in when the file covers more than one book.
                            ledgerName = if (spansLedgers) {
                                names[tagged.ledgerId].orEmpty()
                            } else {
                                ""
                            },
                        )
                    }
            )
            _uiState.update {
                it.copy(
                    pending = PendingExport(
                        fileName = CsvExport.fileName(
                            appName = container.appName,
                            ledgerName = it.targetLabel,
                            dateKey = DateKeys.dateKey(LocalDate.now()),
                        ),
                        content = content,
                    )
                )
            }
        }
    }

    fun onWriteFinished(success: Boolean) {
        _uiState.update {
            it.copy(
                pending = null,
                lastResult = if (success) "已导出" else "导出失败，请换一个位置重试",
            )
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(lastResult = null) }
    }

    // --------------------------------------------------------------- app backup

    /**
     * Serialises the whole app into a file.
     *
     * No scope to choose: a backup is the application, and the only decision the user
     * should have to make is where to put it.
     *
     * Runs off the main thread: the dump walks every row of every table through the
     * cursor, and on a ledger with a year of imported bills that is not a frame's worth
     * of work.
     */
    fun prepareBackup() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                container.backupService.export(
                    schemaVersion = container.schemaVersion,
                    appVersion = container.appVersion,
                )
            }
            _uiState.update {
                it.copy(
                    pendingBackup = PendingExport(
                        fileName = AppBackup.fileName(
                            appName = container.appName,
                            dateKey = DateKeys.dateKey(LocalDate.now()),
                        ),
                        content = AppBackup.encode(file),
                    )
                )
            }
        }
    }

    fun onBackupWriteFinished(success: Boolean) {
        _uiState.update {
            it.copy(
                pendingBackup = null,
                backupResult = if (success) "备份已导出" else "备份导出失败，请换一个位置重试",
            )
        }
    }

    fun dismissBackupResult() {
        _uiState.update { it.copy(backupResult = null) }
    }

    /**
     * Reads a picked file and holds it until the user confirms.
     *
     * Nothing is written yet on purpose: the confirm step is the only thing standing
     * between a mis-tap in the file picker and losing the live ledger, so the file is
     * parsed and described first, and applied only from [confirmRestore].
     */
    fun stageRestore(text: String) {
        val parsed = runCatching { AppBackup.decode(text) }.getOrElse { error ->
            _uiState.update {
                it.copy(restoreResult = error.message ?: "备份文件不可读。", pendingRestore = null)
            }
            return
        }
        _uiState.update { it.copy(pendingRestore = parsed, restoreResult = null) }
    }

    fun onRestoreReadFailed() {
        _uiState.update { it.copy(restoreResult = "读不到这个文件，请重试。") }
    }

    fun dismissRestore() {
        _uiState.update { it.copy(pendingRestore = null) }
    }

    fun dismissRestoreResult() {
        _uiState.update { it.copy(restoreResult = null) }
    }

    fun confirmRestore() {
        val file = _uiState.value.pendingRestore ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(pendingRestore = null, restoring = true) }
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    container.backupService.restore(file, container.schemaVersion)
                    container.afterRestore()
                }
            }
            _uiState.update {
                it.copy(
                    restoring = false,
                    restoreResult = outcome.fold(
                        onSuccess = { "已恢复：${file.rowCount} 条数据" },
                        onFailure = { error -> error.message ?: "恢复失败，数据没有被改动。" },
                    ),
                )
            }
        }
    }

    companion object {

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Flattens one row for the spreadsheet.
         *
         * Money is written as a plain decimal string with no currency symbol, so a
         * spreadsheet reads the column as numbers rather than text.
         */
        fun TxnRow.toExportRow(
            categories: Map<Long, CategoryEntity>,
            ledgerName: String = "",
        ): ExportRow {
            val mainName = mainCategoryId?.let { categories[it]?.name }.orEmpty()
            val time = Instant.ofEpochMilli(happenedAt)
                .atZone(ZoneId.systemDefault())
                .format(TIME_FORMAT)
            return ExportRow(
                dateKey = localDateKey,
                time = time,
                type = typeLabel(type),
                mainCategory = mainName,
                category = categoryName.orEmpty(),
                amountYuan = Money.format(amountCents).replace(",", ""),
                account = accountName,
                toAccount = toAccountName.orEmpty(),
                merchant = merchant.orEmpty(),
                note = note.orEmpty(),
                excluded = if (isExcludedFromStats) "是" else "否",
                source = sourceLabel(source),
                ledger = ledgerName,
            )
        }

        private fun typeLabel(type: String): String = when (type) {
            "INCOME" -> "收入"
            "TRANSFER" -> "转账"
            else -> "支出"
        }

        private fun sourceLabel(source: String): String = when (source) {
            TxnSource.TEMPLATE.name -> "模板"
            TxnSource.IMPORT_ALIPAY.name -> "支付宝导入"
            TxnSource.IMPORT_WECHAT.name -> "微信导入"
            TxnSource.INSTALLMENT.name -> "月付"
            else -> "手动录入"
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                ExportViewModel(app.container)
            }
        }
    }
}
