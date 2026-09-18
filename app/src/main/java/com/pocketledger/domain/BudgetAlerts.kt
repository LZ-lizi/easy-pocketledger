package com.pocketledger.domain

/**
 * How a budget is doing.
 *
 * [level] is the highest threshold crossed: 0 below 80%, 80 at the warning, 100 once
 * the cap is reached or passed. Keeping it on the model means the UI and the
 * notifier agree on what "over" means instead of each deciding for itself.
 */
data class BudgetProgress(
    /** 0 for the overall cap, otherwise the category being capped. */
    val categoryId: Long,
    val name: String,
    val limitCents: Long,
    val spentCents: Long,
) {
    val remainingCents: Long get() = limitCents - spentCents

    val isOver: Boolean get() = spentCents > limitCents

    val ratio: Float
        get() = if (limitCents <= 0L) {
            0f
        } else {
            (spentCents.toDouble() / limitCents.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val level: Int get() = BudgetAlerts.levelFor(spentCents, limitCents)

    /** Stable identity of one threshold crossing, used to avoid repeat notifications. */
    fun alertKey(periodKey: String, level: Int): String = "$periodKey:$categoryId:$level"
}

/** One threshold crossing worth telling the user about. */
data class BudgetAlert(
    val periodKey: String,
    val categoryId: Long,
    val name: String,
    val level: Int,
    val spentCents: Long,
    val limitCents: Long,
) {
    val key: String get() = "$periodKey:$categoryId:$level"
}

/**
 * Turns budget progress into notifications.
 *
 * Pure so the thresholds are testable, and idempotent by construction: a crossing is
 * only reported when its [BudgetAlert.key] has not already fired for that period, so
 * reopening the app does not re-notify about a budget that was blown days ago.
 */
object BudgetAlerts {

    const val WARNING_LEVEL = 80
    const val OVER_LEVEL = 100

    fun levelFor(spentCents: Long, limitCents: Long): Int = when {
        limitCents <= 0L -> 0
        spentCents > limitCents -> OVER_LEVEL
        spentCents * 100 >= limitCents * WARNING_LEVEL -> WARNING_LEVEL
        else -> 0
    }

    /**
     * Crossings not yet reported.
     *
     * A budget already past 100% still reports its 80% warning if that one never
     * fired, so a first launch after a big purchase tells the whole story instead of
     * only the latest state.
     */
    fun pending(
        periodKey: String,
        progress: List<BudgetProgress>,
        alreadyFired: Set<String>,
    ): List<BudgetAlert> {
        val alerts = mutableListOf<BudgetAlert>()
        for (item in progress) {
            val level = item.level
            if (level == 0) continue
            val levels = if (level >= OVER_LEVEL) {
                listOf(WARNING_LEVEL, OVER_LEVEL)
            } else {
                listOf(WARNING_LEVEL)
            }
            for (candidate in levels) {
                val key = item.alertKey(periodKey, candidate)
                if (key in alreadyFired) continue
                alerts += BudgetAlert(
                    periodKey = periodKey,
                    categoryId = item.categoryId,
                    name = item.name,
                    level = candidate,
                    spentCents = item.spentCents,
                    limitCents = item.limitCents,
                )
            }
        }
        return alerts
    }
}
