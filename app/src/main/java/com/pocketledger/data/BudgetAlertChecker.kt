package com.pocketledger.data

import com.pocketledger.data.prefs.AppPreferences
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.BudgetAlerts
import com.pocketledger.domain.BudgetProgress
import com.pocketledger.domain.DateKeys
import com.pocketledger.notify.BudgetNotifier
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Decides whether any budget has crossed a threshold and tells the user once.
 *
 * Runs on a plain foreground check rather than a scheduled job: there is no server
 * to push anything, and "the alert appears the next time you open the app" is both
 * simpler and honest about when the app actually knows something changed.
 *
 * Only the selected ledger is checked. Alerting about a ledger the user is not
 * looking at would be noise, and the figures would have no context.
 */
class BudgetAlertChecker(
    private val repository: LedgerRepository,
    private val preferences: AppPreferences,
    private val notifier: BudgetNotifier,
) {

    /** @return how many notifications were posted. */
    suspend fun check(today: LocalDate = LocalDate.now()): Int {
        val ledgerId = repository.selectedLedgerId.value ?: return 0
        val periodKey = DateKeys.monthKey(today)
        val (start, end) = DateKeys.monthRange(periodKey)

        val budgets = repository.observeBudgets(periodKey).first()
        // The overall cap lives in the allowance, so a month whose only limit came from
        // the 明细页 card has no budget row at all -- reading budgets alone would have
        // silently skipped the alert for it.
        val allowance = repository.observeAllowance(periodKey).first()
        val totalLimitCents = allowance?.amountCents
            ?: budgets.firstOrNull { it.categoryId == 0L }?.amountCents
            ?: 0L
        val categoryBudgets = budgets.filter { it.categoryId != 0L }
        if (totalLimitCents <= 0L && categoryBudgets.isEmpty()) return 0

        val totals = repository.observeTotals(periodKey).first()
        val mainTotals = repository.observeMainCategoryTotals(periodKey).first()
        val categoryTotals = repository.observeCategoryTotals(start, end).first()

        val mainSpent = mainTotals.associate { it.mainCategoryId to it.totalCents }
        val leafSpent = categoryTotals.associate { it.categoryId to it.totalCents }

        val progress = buildList {
            if (totalLimitCents > 0L) {
                add(
                    BudgetProgress(
                        categoryId = 0L,
                        name = "总预算",
                        limitCents = totalLimitCents,
                        spentCents = totals.expenseCents,
                    )
                )
            }
            categoryBudgets.forEach { budget ->
                add(
                    BudgetProgress(
                        categoryId = budget.categoryId,
                        name = budget.categoryName ?: "分类预算",
                        limitCents = budget.amountCents,
                        spentCents = leafSpent[budget.categoryId]
                            ?: mainSpent[budget.categoryId]
                            ?: 0L,
                    )
                )
            }
        }

        val alreadyFired = preferences.firedAlerts()
        val pending = BudgetAlerts.pending(periodKey, progress, alreadyFired)
        if (pending.isEmpty()) {
            // Keep the store from growing a key per month forever.
            preferences.pruneAlertsExcept(setOf(periodKey))
            return 0
        }

        var posted = 0
        for (alert in pending) {
            // Marked only once the notification actually went out. Recording it as
            // fired while permission is missing would silently swallow the alert for
            // the rest of the month; leaving it pending costs nothing, because an
            // unposted notification is invisible either way.
            if (notifier.post(alert)) {
                posted++
                preferences.markAlertsFired(listOf(alert.key))
            }
        }
        preferences.pruneAlertsExcept(setOf(periodKey))
        return posted
    }
}
