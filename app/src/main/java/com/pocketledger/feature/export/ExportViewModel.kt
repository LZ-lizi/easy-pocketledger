package com.pocketledger.feature.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.backup.BackupScope
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

/** What to include in a CSV export. */
enum class ExportScope(val label: String, val detail: String) {
    ALL("全部记录", "当前账本的所有流水"),
    THIS_MONTH("本月", "只导出本月"),
}

data class ExportUiState(
    val totalCount: Int = 0,
    val monthCount: Int = 0,
    val ledgerName: String = "",
    val ledgerCount: Int = 0,
    val loaded: Boolean = false,
    /** Non-null once the CSV is built and waiting for a destination. */
    val pending: PendingExport? = null,
    val lastResult: String? = null,
    // ------------------------------------------------------------- application backup
    val backupScope: BackupScope = BackupScope.ALL,
    /** Non-null once the backup JSON is built and waiting for a destination. */
    val pendingBackup: PendingExport? = null,
    val backupResult: String? = null,
    /** Parsed backup awaiting the user's confirmation before it replaces everything. */
    val pendingRestore: AppBackup.File? = null,
    val restoring: Boolean = false,
    val restoreResult: String? = null,
) {
    val hasData: Boolean get() = totalCount > 0
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
            val rows = repository.allRowsForExport()
            val monthKey = DateKeys.monthKey(LocalDate.now())
            val ledgers = repository.activeLedgers()
            val selected = repository.selectedLedgerId.value
            _uiState.update {
                it.copy(
                    totalCount = rows.size,
                    monthCount = rows.count { row -> row.localDateKey.startsWith(monthKey) },
                    ledgerName = selected?.let { id -> ledgers.firstOrNull { l -> l.id == id }?.name }
                        .orEmpty(),
                    ledgerCount = ledgers.size,
                    loaded = true,
                )
            }
        }
    }

    fun prepare(scope: ExportScope) {
        viewModelScope.launch {
            val rows = repository.allRowsForExport()
            val categories = repository.categoriesSnapshot().associateBy { it.id }
            val monthKey = DateKeys.monthKey(LocalDate.now())
            val selected = when (scope) {
                ExportScope.ALL -> rows
                ExportScope.THIS_MONTH -> rows.filter { it.localDateKey.startsWith(monthKey) }
            }
            val content = CsvExport.build(selected.map { it.toExportRow(categories) })
            _uiState.update {
                it.copy(
                    pending = PendingExport(
                        fileName = CsvExport.fileName(
                            appName = "记账本",
                            ledgerName = it.ledgerName,
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

    fun setBackupScope(scope: BackupScope) {
        _uiState.update { it.copy(backupScope = scope) }
    }

    /**
     * Serialises the whole app into a file.
     *
     * Runs off the main thread: the dump walks every row of every table through the
     * cursor, and on a ledger with a year of imported bills that is not a frame's worth
     * of work.
     */
    fun prepareBackup() {
        val scope = _uiState.value.backupScope
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                container.backupService.export(
                    scope = scope,
                    ledgerId = repository.selectedLedgerId.value,
                    ledgerName = repository.selectedLedger()?.name,
                    schemaVersion = container.schemaVersion,
                    appVersion = container.appVersion,
                )
            }
            _uiState.update {
                it.copy(
                    pendingBackup = PendingExport(
                        fileName = backupFileName(scope, it.ledgerName, DateKeys.dateKey(LocalDate.now())),
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
                it.copy(restoreResult = error.message ?: "备份文件读不了。", pendingRestore = null)
            }
            return
        }
        _uiState.update { it.copy(pendingRestore = parsed, restoreResult = null) }
    }

    fun onRestoreReadFailed() {
        _uiState.update { it.copy(restoreResult = "读不到这个文件，请换一个再试。") }
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
                }
                container.afterRestore()
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

        /** `记账本-备份-全部账本-2026-09-18.json`, matching the CSV naming. */
        fun backupFileName(scope: BackupScope, ledgerName: String, dateKey: String): String {
            val subject = if (scope == BackupScope.LEDGER && ledgerName.isNotBlank()) {
                ledgerName
            } else {
                scope.label
            }
            return "记账本-备份-$subject-$dateKey.json"
        }

        /**
         * Flattens one row for the spreadsheet.
         *
         * Money is written as a plain decimal string with no currency symbol, so a
         * spreadsheet reads the column as numbers rather than text.
         */
        fun TxnRow.toExportRow(categories: Map<Long, CategoryEntity>): ExportRow {
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
