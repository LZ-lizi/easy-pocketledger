package com.pocketledger.feature.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pocketledger.LedgerApp
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.InstallmentKind
import com.pocketledger.data.entity.InstallmentPlanEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.entity.TimeMode
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.di.AppContainer
import com.pocketledger.domain.DateKeys
import com.pocketledger.domain.InstallmentSchedule
import com.pocketledger.domain.KeypadInput
import com.pocketledger.domain.Money
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the keypad is currently recording.
 *
 * 月付 is here rather than in settings because a recurring payment is something you
 * decide to start while looking at a bill, not while browsing configuration.
 */
enum class EntryMode(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    TRANSFER("转账"),
    MONTHLY("月付"),
}

/**
 * One 大类 and the items drawn under it in the 「更多」 menu.
 *
 * Carries the parent's own name, icon and colour so the heading is recognisable at a
 * glance -- the same icon the category grid used to file it under.
 */
data class CategoryGroup(
    val key: String,
    val label: String,
    val iconKey: String,
    val colorArgb: Int,
    val items: List<CategoryEntity>,
)

/** Income items have no parent, so their single group needs its own accent. */
private const val INCOME_GROUP_COLOR = 0xFF12A150.toInt()

data class EntryUiState(
    val mode: EntryMode = EntryMode.EXPENSE,
    /** Raw keypad text such as `"12.3"`; parsed only when needed. */
    val amountInput: String = "",
    /** Transfer fee, only meaningful for a transfer. */
    val feeInput: String = "",
    val allCategories: List<CategoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    /**
     * The account a 累计模式 ledger keeps hidden.
     *
     * It is never offered in a picker -- showing it would contradict the whole point of
     * that ledger type -- but an entry still has to attach to something. Without this
     * fallback every entry in such a ledger was blocked with Save permanently disabled.
     */
    val hiddenAccountId: Long? = null,
    val selectedCategoryId: Long? = null,
    val selectedAccountId: Long? = null,
    /** Destination account; transfers only. */
    val selectedToAccountId: Long? = null,
    val note: String = "",
    val merchant: String = "",
    val dateKey: String = DateKeys.dateKey(LocalDate.now()),
    /**
     * Null means "use the current time", which is what a new entry almost always
     * wants. A time is only displayed and stored once the user changes it.
     */
    val customTime: LocalTime? = null,
    val recentCategoryIds: List<Long> = emptyList(),
    /** Categories pinned to the first screen of the grid. */
    val pinnedCategoryIds: Set<Long> = emptySet(),
    // ---------------------------------------------------------------- 月付 plan
    val planName: String = "",
    val planPeriods: String = "12",
    val planRepayDay: String = "",
    val planFeeInput: String = "",
) {
    val isTransfer: Boolean get() = mode == EntryMode.TRANSFER
    val isMonthly: Boolean get() = mode == EntryMode.MONTHLY

    val type: TxnType
        get() = when (mode) {
            EntryMode.INCOME -> TxnType.INCOME
            EntryMode.TRANSFER -> TxnType.TRANSFER
            else -> TxnType.EXPENSE
        }

    val hasCustomTime: Boolean get() = customTime != null

    /**
     * The account a saved entry attaches to.
     *
     * The picked account when there is one, otherwise the ledger's hidden account --
     * the only way a 累计模式 ledger can record anything.
     */
    val effectiveAccountId: Long? get() = selectedAccountId ?: hiddenAccountId

    /** True when the ledger records to an account the user never picks. */
    val accountIsImplicit: Boolean get() = accounts.isEmpty() && hiddenAccountId != null

    val kind: CategoryKind
        get() = if (mode == EntryMode.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

    /** Every item the keypad could offer, newest-used first. */
    private val orderedCategories: List<CategoryEntity>
        get() {
            val visible = when {
                isTransfer -> emptyList()
                kind == CategoryKind.INCOME -> allCategories.filter { it.kind == kind }
                else -> allCategories.filter { it.kind == kind && it.parentId != null }
            }
            val recent = recentCategoryIds.withIndex().associate { it.value to it.index }
            return visible.sortedWith(
                compareBy({ recent[it.id] ?: Int.MAX_VALUE }, { it.sortOrder })
            )
        }

    /**
     * The grid actually drawn.
     *
     * Always the most-used items for expenses, never everything: the outermost screen is
     * for the handful of categories that carry most entries, and the rest live one tap
     * away behind 「更多」. With no explicit pinning the first
     * [DEFAULT_PRIMARY_CATEGORIES] of the usage ordering stand in, which is the same
     * promise the pinned set makes.
     *
     * Income is never collapsed. There are six income items in the presets, they fit on
     * one screen, and hiding the one you want behind a second tap buys nothing -- the
     * 「更多」 menu exists for the long tail of *spending* categories.
     *
     * A pinned set that resolves to no rows -- every pinned category deleted, or (as
     * used to happen) the ids coming from a different ledger -- falls back to the
     * defaults instead of producing an unusable empty grid.
     */
    val visibleCategories: List<CategoryEntity>
        get() {
            val all = orderedCategories
            if (kind == CategoryKind.INCOME) return all
            if (pinnedCategoryIds.isEmpty()) return all.take(DEFAULT_PRIMARY_CATEGORIES)
            val pinned = all.filter { it.id in pinnedCategoryIds }
            return pinned.ifEmpty { all.take(DEFAULT_PRIMARY_CATEGORIES) }
        }

    /** True when some categories are being held back behind 「更多」. */
    val hasHiddenCategories: Boolean
        get() = categoryGroups.isNotEmpty() && visibleCategories.size < orderedCategories.size

    /**
     * [visibleCategories]' full list, bucketed by 大类, for the 「更多」 menu.
     *
     * Finding a rarely used item is a matter of remembering which 大类 owns it -- 打印
     * lives under 学习 -- so items sharing a parent are kept together under that
     * heading instead of being listed in one flat run. Groups keep the usage order of
     * their first item, so the menu opens on whatever is used most.
     */
    val categoryGroups: List<CategoryGroup>
        get() {
            val items = orderedCategories
            if (items.isEmpty()) return emptyList()
            // Income categories are all roots, so a single heading is the honest grouping.
            if (kind == CategoryKind.INCOME) {
                return listOf(CategoryGroup("income", "收入", "income", INCOME_GROUP_COLOR, items))
            }
            val byId = allCategories.associateBy { it.id }
            return items
                .groupBy { it.parentId }
                .mapNotNull { (parentId, children) ->
                    val parent = parentId?.let { byId[it] } ?: return@mapNotNull null
                    CategoryGroup(
                        key = "parent-${parent.id}",
                        label = parent.name,
                        iconKey = parent.iconKey,
                        colorArgb = parent.colorArgb,
                        items = children,
                    )
                }
        }

    val amountCents: Long get() = Money.parseYuanToCents(amountInput) ?: 0L

    val feeCents: Long get() = Money.parseYuanToCents(feeInput) ?: 0L

    val planPeriodsValue: Int? get() = planPeriods.toIntOrNull()?.takeIf { it in 1..120 }

    val planRepayDayValue: Int? get() = planRepayDay.toIntOrNull()?.takeIf { it in 1..31 }

    /** Live preview so the per-instalment figure is never a surprise. */
    val planPerPeriodCents: Long
        get() {
            val periods = planPeriodsValue ?: return 0L
            return InstallmentSchedule.amountForPeriod(amountCents, periods, 1)
        }

    val canSave: Boolean
        get() = when (mode) {
            // Money moving between two of your own accounts is not spending, so no
            // category is required -- but it must actually move somewhere else. A
            // hidden fallback account cannot satisfy this: a transfer needs two real,
            // pickable accounts, which a 累计模式 ledger does not have.
            EntryMode.TRANSFER -> amountCents > 0L &&
                selectedAccountId != null &&
                selectedToAccountId != null &&
                selectedAccountId != selectedToAccountId

            EntryMode.MONTHLY -> amountCents > 0L &&
                effectiveAccountId != null &&
                planName.isNotBlank() &&
                planPeriodsValue != null &&
                planRepayDayValue != null

            else -> amountCents > 0L &&
                effectiveAccountId != null &&
                selectedCategoryId != null
        }

    /** True when the entry may be recorded at a time, not just on a day. */
    val timeMode: TimeMode
        get() = when {
            // A time the user actually chose is worth showing plainly.
            customTime != null -> TimeMode.EXPLICIT
            // Moving the day without touching the clock means the clock reading is just
            // "whenever this was typed", so the detail view hides it rather than
            // claiming the entry happened at 14:37 on a day it did not.
            dateKey != DateKeys.dateKey(LocalDate.now()) -> TimeMode.HIDDEN
            else -> TimeMode.AUTO
        }

    /** Accounts offered as the destination, i.e. everything except the source. */
    val destinationAccounts: List<AccountEntity>
        get() = accounts.filter { it.id != selectedAccountId }

    companion object {
        /** Three rows of the four-column grid. */
        const val DEFAULT_PRIMARY_CATEGORIES = 12
    }
}

/**
 * Backs the keypad entry screen.
 *
 * Saving closes the screen: leaving it with the amount cleared and a lingering
 * "已记一笔" leaves it ambiguous whether the entry actually stuck.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository: LedgerRepository = container.repository

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllCategories().collect { categories ->
                _uiState.update { it.copy(allCategories = categories) }
            }
        }
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                _uiState.update { state ->
                    val accountId = state.selectedAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } }
                        ?: accounts.firstOrNull()?.id
                    val toAccountId = state.selectedToAccountId
                        ?.takeIf { id -> accounts.any { it.id == id } && id != accountId }
                        ?: accounts.firstOrNull { it.id != accountId }?.id
                    state.copy(
                        accounts = accounts,
                        selectedAccountId = accountId,
                        selectedToAccountId = toAccountId,
                        planRepayDay = state.planRepayDay.ifBlank {
                            LocalDate.now().dayOfMonth.toString()
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.observeHiddenAccounts().collect { hidden ->
                _uiState.update { it.copy(hiddenAccountId = hidden.firstOrNull()?.id) }
            }
        }
        viewModelScope.launch {
            val recent = repository.recentCategoryIds()
            _uiState.update { it.copy(recentCategoryIds = recent) }
        }
        viewModelScope.launch {
            // Pinned ids are per ledger, so the preference has to be re-read whenever
            // the ledger changes -- a set from another ledger names rows that do not
            // exist here.
            repository.selectedLedgerId
                .filterNotNull()
                .flatMapLatest { ledgerId -> container.appPreferences.pinnedCategoryIds(ledgerId) }
                .collect { pinned ->
                    _uiState.update { it.copy(pinnedCategoryIds = pinned) }
                }
        }
    }

    // ------------------------------------------------------------------ editing

    fun setMode(mode: EntryMode) {
        _uiState.update { state ->
            if (state.mode == mode) return@update state
            state.copy(
                mode = mode,
                selectedCategoryId = null,
                feeInput = if (mode == EntryMode.TRANSFER) state.feeInput else "",
                planFeeInput = if (mode == EntryMode.MONTHLY) state.planFeeInput else "",
            )
        }
    }

    fun pressKey(key: String) {
        _uiState.update { state ->
            val next = if (key == ".") {
                KeypadInput.appendDecimalPoint(state.amountInput)
            } else {
                KeypadInput.appendDigit(state.amountInput, key)
            }
            state.copy(amountInput = next)
        }
    }

    fun backspace() {
        _uiState.update { it.copy(amountInput = it.amountInput.dropLast(1)) }
    }

    fun clearAmount() {
        _uiState.update { it.copy(amountInput = "") }
    }

    fun selectCategory(id: Long) {
        _uiState.update { it.copy(selectedCategoryId = id) }
    }

    fun selectAccount(id: Long) {
        _uiState.update { state ->
            // Picking the destination as the source swaps them rather than blocking.
            val toId = if (state.selectedToAccountId == id) state.selectedAccountId else state.selectedToAccountId
            state.copy(selectedAccountId = id, selectedToAccountId = toId)
        }
    }

    fun selectToAccount(id: Long) {
        _uiState.update { it.copy(selectedToAccountId = id) }
    }

    fun setFee(value: String) {
        _uiState.update { it.copy(feeInput = value) }
    }

    fun setPlanName(value: String) {
        _uiState.update { it.copy(planName = value) }
    }

    fun setPlanPeriods(value: String) {
        _uiState.update { it.copy(planPeriods = value.filter(Char::isDigit).take(3)) }
    }

    fun setPlanRepayDay(value: String) {
        _uiState.update { it.copy(planRepayDay = value.filter(Char::isDigit).take(2)) }
    }

    fun setPlanFee(value: String) {
        _uiState.update { it.copy(planFeeInput = value) }
    }

    fun setNote(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun setMerchant(value: String) {
        _uiState.update { it.copy(merchant = value) }
    }

    fun setDate(dateKey: String) {
        _uiState.update { it.copy(dateKey = dateKey) }
    }

    fun shiftDate(days: Long) {
        _uiState.update { state ->
            val next = runCatching { LocalDate.parse(state.dateKey).plusDays(days) }
                .getOrElse { LocalDate.now() }
            state.copy(dateKey = next.toString())
        }
    }

    /** Setting a time also marks the entry as deliberately timed. */
    fun setTime(hour: Int, minute: Int) {
        _uiState.update {
            it.copy(customTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
        }
    }

    /** Drops back to "use the current time" and hides the time again. */
    fun useCurrentTime() {
        _uiState.update { it.copy(customTime = null) }
    }

    // -------------------------------------------------------------------- saving

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        val cents = Money.parseYuanToCents(state.amountInput) ?: return
        val accountId = state.effectiveAccountId ?: return

        viewModelScope.launch {
            if (state.isMonthly) {
                saveMonthlyPlan(state, cents, accountId)
            } else {
                repository.addTransaction(
                    TxnEntity(
                        type = state.type,
                        amountCents = cents,
                        accountId = accountId,
                        toAccountId = if (state.isTransfer) state.selectedToAccountId else null,
                        feeCents = if (state.isTransfer && state.feeCents > 0L) state.feeCents else null,
                        categoryId = if (state.isTransfer) null else state.selectedCategoryId,
                        merchant = if (state.isTransfer) null else state.merchant.trim().ifBlank { null },
                        note = state.note.trim().ifBlank { null },
                        happenedAt = epochMillisFor(state.dateKey, state.customTime),
                        localDateKey = state.dateKey,
                        source = TxnSource.MANUAL,
                        timeMode = state.timeMode,
                    )
                )
            }
            onSaved()
        }
    }

    private suspend fun saveMonthlyPlan(state: EntryUiState, totalCents: Long, accountId: Long) {
        val periods = state.planPeriodsValue ?: return
        val repayDay = state.planRepayDayValue ?: return
        val fee = Money.parseYuanToCents(state.planFeeInput) ?: 0L
        repository.addInstallmentPlan(
            InstallmentPlanEntity(
                name = state.planName.trim(),
                kind = InstallmentKind.MONTHLY,
                totalAmountCents = totalCents,
                periodCount = periods,
                perPeriodCents = InstallmentSchedule.amountForPeriod(totalCents, periods, 1),
                repayDay = repayDay,
                startDateKey = state.dateKey,
                accountId = accountId,
                categoryId = state.selectedCategoryId,
                feeCents = if (fee > 0L) fee else null,
                note = state.note.trim().ifBlank { null },
            )
        )
        // A plan starting in the past may already owe instalments; generate them now.
        container.runInstallments()
    }

    companion object {

        /** The chosen day at the chosen time, or at the current time when none was set. */
        fun epochMillisFor(dateKey: String, time: LocalTime?): Long {
            val date = runCatching { LocalDate.parse(dateKey) }.getOrElse { LocalDate.now() }
            return date.atTime(time ?: LocalTime.now())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                EntryViewModel(app.container)
            }
        }
    }
}
