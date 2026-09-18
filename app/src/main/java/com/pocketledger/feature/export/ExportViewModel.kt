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
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.CsvExport
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.ExportRow
import com.pocketledger.domain.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What to include in an export. */
enum class ExportScope(val label: String, val detail: String) {
    ALL("全部记录", "当前账本的所有流水"),
    THIS_MONTH("本月", "只导出本月"),
}

data class ExportUiState(
    val totalCount: Int = 0,
    val monthCount: Int = 0,
    val ledgerName: String = "",
    val loaded: Boolean = false,
    /** Non-null once the CSV is built and waiting for a destination. */
    val pending: PendingExport? = null,
    val lastResult: String? = null,
) {
    val hasData: Boolean get() = totalCount > 0
}

/** A built CSV plus the name to suggest for it. */
data class PendingExport(
    val fileName: String,
    val content: String,
)

/**
 * Builds the export file.
 *
 * The CSV is produced before the file picker opens: the system picker only hands back
 * a destination, and generating the content afterwards would mean the user could pick
 * a location for something that then failed to build.
 */
class ExportViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val rows = repository.allRowsForExport()
            val monthKey = DateKeys.monthKey(LocalDate.now())
            val ledgerName = repository.selectedLedgerId.value
                ?.let { repository.ledger(it)?.name }
                .orEmpty()
            _uiState.update {
                it.copy(
                    totalCount = rows.size,
                    monthCount = rows.count { row -> row.localDateKey.startsWith(monthKey) },
                    ledgerName = ledgerName,
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

    companion object {

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

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
                ExportViewModel(app.container.repository)
            }
        }
    }
}
