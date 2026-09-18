package com.pocketledger.feature.installments

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
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.di.AppContainer
import com.pocketledger.domain.InstallmentSchedule
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A plan plus everything the list needs to describe its progress. */
data class InstallmentPlanRow(
    val plan: InstallmentPlanEntity,
    val paidPeriods: Int,
    val paidCents: Long,
    val nextDueDateKey: String?,
    val finalDueDateKey: String?,
) {
    val remainingCents: Long get() = (plan.totalAmountCents - paidCents).coerceAtLeast(0L)

    val progress: Float
        get() = if (plan.totalAmountCents <= 0L) {
            0f
        } else {
            (paidCents.toDouble() / plan.totalAmountCents.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

data class InstallmentSettingsUiState(
    val plans: List<InstallmentPlanRow> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val editorVisible: Boolean = false,
    /** Null when creating. */
    val editorTarget: InstallmentPlanEntity? = null,
)

private data class InstallmentEditorState(val target: InstallmentPlanEntity?)

/**
 * Manages 月付 / 白条 plans.
 *
 * The list shows what has actually been charged, not what the calendar says should
 * have been: if a charge failed to generate, the progress must say so rather than
 * quietly agreeing with the schedule.
 */
class InstallmentSettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository
    private val editor = MutableStateFlow<InstallmentEditorState?>(null)

    val uiState: StateFlow<InstallmentSettingsUiState> = combine(
        repository.observeInstallmentPlans(),
        repository.observePaidSummaries(),
        repository.observeAccounts(),
        repository.observeCategories(CategoryKind.EXPENSE),
        editor,
    ) { plans, summaries, accounts, categories, editorState ->
        val paidByPlan = summaries.associateBy { it.planId }
        val today = LocalDate.now()
        InstallmentSettingsUiState(
            plans = plans.map { plan ->
                val paid = paidByPlan[plan.id]
                val elapsed = InstallmentSchedule.elapsedPeriods(
                    startDateKey = plan.startDateKey,
                    repayDay = plan.repayDay,
                    periodCount = plan.periodCount,
                    today = today,
                )
                InstallmentPlanRow(
                    plan = plan,
                    paidPeriods = paid?.paidCount ?: 0,
                    paidCents = paid?.paidCents ?: 0L,
                    // The next unpaid period, or null once the schedule is exhausted.
                    nextDueDateKey = (elapsed + 1)
                        .takeIf { it <= plan.periodCount }
                        ?.let { InstallmentSchedule.dueDate(plan.startDateKey, plan.repayDay, it) }
                        ?.toString(),
                    finalDueDateKey = InstallmentSchedule
                        .finalDueDate(plan.startDateKey, plan.repayDay, plan.periodCount)
                        ?.toString(),
                )
            },
            accounts = accounts,
            categories = categories,
            editorVisible = editorState != null,
            editorTarget = editorState?.target,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InstallmentSettingsUiState(),
    )

    fun create() {
        editor.value = InstallmentEditorState(null)
    }

    fun edit(plan: InstallmentPlanEntity) {
        editor.value = InstallmentEditorState(plan)
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun save(plan: InstallmentPlanEntity) {
        viewModelScope.launch {
            if (plan.id == 0L) {
                repository.addInstallmentPlan(plan)
            } else {
                repository.updateInstallmentPlan(plan)
            }
            editor.value = null
            // A back-dated plan can already have periods due; generate them now so the
            // user sees the effect immediately instead of on the next cold start.
            container.runInstallments()
        }
    }

    /** Terminating stops future charges; the periods already posted stay put. */
    fun setActive(plan: InstallmentPlanEntity, active: Boolean) {
        viewModelScope.launch {
            repository.setInstallmentPlanActive(plan.id, active)
            editor.value = null
        }
    }

    companion object {

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
                InstallmentSettingsViewModel(app.container)
            }
        }
    }
}
