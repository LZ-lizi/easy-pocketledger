package com.pocketledger.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.entity.TimeMode
import com.pocketledger.data.repo.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DetailUiState(
    val id: Long = 0L,
    val missing: Boolean = false,
    val type: TxnType = TxnType.EXPENSE,
    val amountCents: Long = 0L,
    val feeCents: Long? = null,
    val title: String = "",
    val iconKey: String = "more",
    val categoryName: String? = null,
    val accountName: String = "",
    val toAccountName: String? = null,
    val merchant: String? = null,
    val note: String? = null,
    val dateLabel: String = "",
    /**
     * Clock reading, or null when the entry's time carries no meaning.
     *
     * Null covers "the user moved this to another day without touching the clock":
     * showing the moment the row happened to be typed would be a claim about when the
     * money moved that the user never made.
     */
    val timeLabel: String? = null,
    /** True while the time is only "when this was recorded", so it is drawn muted. */
    val timeIsApproximate: Boolean = true,
    val isExcludedFromStats: Boolean = false,
    val sourceLabel: String = "",
)

/**
 * Loads one entry for the read-only detail view.
 *
 * Names are resolved from snapshots rather than kept in a denormalised row: the
 * detail screen shows a single record, so two extra reads cost nothing and always
 * reflect a rename.
 */
class DetailViewModel(
    private val repository: LedgerRepository,
    private val transactionId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState(id = transactionId))
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val txn = repository.transaction(transactionId)
            if (txn == null) {
                _uiState.update { it.copy(missing = true) }
                return@launch
            }
            val categories = repository.categoriesSnapshot().associateBy { it.id }
            val accounts = repository.accountsSnapshot().associateBy { it.id }
            val category = txn.categoryId?.let { categories[it] }

            val title = when (txn.type) {
                TxnType.TRANSFER -> txn.note?.takeIf { it.isNotBlank() } ?: "账户互转"
                else -> category?.name ?: "未分类"
            }
            val iconKey = when (txn.type) {
                TxnType.TRANSFER -> "finance"
                TxnType.INCOME -> "income"
                else -> category?.iconKey ?: "more"
            }

            val stamp = Instant.ofEpochMilli(txn.happenedAt).atZone(ZoneId.systemDefault())

            _uiState.update {
                it.copy(
                    missing = false,
                    type = txn.type,
                    amountCents = txn.amountCents,
                    feeCents = txn.feeCents,
                    title = title,
                    iconKey = iconKey,
                    categoryName = category?.name,
                    accountName = accounts[txn.accountId]?.name.orEmpty(),
                    toAccountName = txn.toAccountId?.let { id -> accounts[id]?.name },
                    merchant = txn.merchant,
                    note = txn.note,
                    dateLabel = stamp.format(DATE_FORMAT),
                    timeLabel = if (txn.timeMode == TimeMode.HIDDEN) {
                        null
                    } else {
                        stamp.format(CLOCK_FORMAT)
                    },
                    timeIsApproximate = txn.timeMode == TimeMode.AUTO,
                    isExcludedFromStats = txn.isExcludedFromStats,
                    sourceLabel = sourceLabel(txn.source),
                )
            }
        }
    }

    companion object {

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月d日 EEE")
        private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

        private fun sourceLabel(source: TxnSource): String = when (source) {
            TxnSource.MANUAL -> "手动录入"
            TxnSource.TEMPLATE -> "模板"
            TxnSource.IMPORT_ALIPAY -> "支付宝导入"
            TxnSource.IMPORT_WECHAT -> "微信导入"
            TxnSource.IMPORT_CSV -> "CSV 导入"
            TxnSource.INSTALLMENT -> "月付自动生成"
        }

        fun factory(transactionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                DetailViewModel(app.container.repository, transactionId)
            }
        }
    }
}
