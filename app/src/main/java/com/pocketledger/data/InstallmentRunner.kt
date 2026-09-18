package com.pocketledger.data

import com.pocketledger.data.entity.InstallmentPeriodEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.repo.LedgerRepository
import com.pocketledger.domain.InstallmentSchedule
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Materialises instalments that have fallen due.
 *
 * There is no server, so nothing can charge on the due date itself; the app catches
 * up whenever it opens. That makes the catch-up the only place a charge is created,
 * which is why it is written to be runnable any number of times:
 *
 * 1. the unique `(planId, periodIndex)` index means the period row can only exist once,
 * 2. a period already backed by a transaction is skipped before anything is written,
 * 3. the claim update is guarded by `txnId IS NULL`, and if it loses the race the
 *    transaction just written is rolled back rather than left as a duplicate.
 *
 * Every generated row is an ordinary transaction in the plan's own ledger, so it
 * counts towards that ledger's statistics and budgets with no parallel accounting.
 */
class InstallmentRunner(private val repository: LedgerRepository) {

    /** Serialises runs within the process; startup and a plan edit can overlap. */
    private val mutex = Mutex()

    /** @return how many instalments were generated. */
    suspend fun run(today: LocalDate = LocalDate.now()): Int = mutex.withLock {
        var created = 0
        for (plan in repository.allActiveInstallmentPlans()) {
            // A plan with nowhere to post cannot produce a transaction.
            val accountId = plan.accountId ?: continue

            val periods = repository.installmentPeriods(plan.id)
            val generated = periods
                .filter { it.txnId != null }
                .map { it.periodIndex }
                .toSet()

            val pending = InstallmentSchedule.pendingPeriods(
                startDateKey = plan.startDateKey,
                repayDay = plan.repayDay,
                periodCount = plan.periodCount,
                totalCents = plan.totalAmountCents,
                today = today,
                alreadyGenerated = generated,
            )

            for (period in pending) {
                val periodId = periods.firstOrNull { it.periodIndex == period.periodIndex }?.id
                    ?: repository.insertInstallmentPeriodIfAbsent(
                        InstallmentPeriodEntity(
                            planId = plan.id,
                            periodIndex = period.periodIndex,
                            dueDateKey = period.dueDateKey,
                            amountCents = period.amountCents,
                        )
                    ).takeIf { it > 0L }
                    ?: continue

                // A one-off handling fee rides along with the first instalment, so the
                // plan is fully accounted for without a second schedule.
                val feeCents = if (period.periodIndex == 1) plan.feeCents ?: 0L else 0L

                val txnId = repository.addTransactionInLedger(
                    ledgerId = plan.ledgerId,
                    txn = TxnEntity(
                        type = TxnType.EXPENSE,
                        amountCents = period.amountCents + feeCents,
                        accountId = accountId,
                        categoryId = plan.categoryId,
                        note = buildString {
                            append(plan.name)
                            append(" 第 ")
                            append(period.periodIndex)
                            append('/')
                            append(plan.periodCount)
                            append(" 期")
                            if (feeCents > 0L) append("（含手续费）")
                        },
                        happenedAt = dueMillis(period.dueDateKey),
                        localDateKey = period.dueDateKey,
                        source = TxnSource.INSTALLMENT,
                        planId = plan.id,
                        planPeriodIndex = period.periodIndex,
                    ),
                )

                if (repository.claimInstallmentPeriod(periodId, txnId) == 0) {
                    // Lost the race: another run already claimed this period.
                    repository.deleteTransaction(txnId)
                } else {
                    created++
                }
            }
        }
        created
    }

    companion object {

        /** 09:00 on the due day: a plausible charge time, and never near a DST edge. */
        fun dueMillis(dateKey: String): Long =
            runCatching { LocalDate.parse(dateKey) }
                .getOrElse { LocalDate.now() }
                .atTime(9, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
    }
}
