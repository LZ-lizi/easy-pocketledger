package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.InstallmentPeriodEntity
import com.pocketledger.data.entity.InstallmentPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    // --------------------------------------------------------------------- plans

    @Query("SELECT * FROM installment_plan WHERE ledgerId = :ledgerId ORDER BY isActive DESC, createdAt DESC")
    fun observePlans(ledgerId: Long): Flow<List<InstallmentPlanEntity>>

    @Query("SELECT * FROM installment_plan WHERE ledgerId = :ledgerId AND isActive = 1 ORDER BY createdAt DESC")
    fun observeActivePlans(ledgerId: Long): Flow<List<InstallmentPlanEntity>>

    /** Every active plan in every ledger; the catch-up runs across all of them. */
    @Query("SELECT * FROM installment_plan WHERE isActive = 1")
    suspend fun allActivePlans(): List<InstallmentPlanEntity>

    @Query("SELECT * FROM installment_plan WHERE id = :id")
    suspend fun plan(id: Long): InstallmentPlanEntity?

    @Insert
    suspend fun insertPlan(plan: InstallmentPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: InstallmentPlanEntity)

    @Query("UPDATE installment_plan SET isActive = :active, updatedAt = :now WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM installment_plan WHERE id = :id")
    suspend fun deletePlan(id: Long)

    // ------------------------------------------------------------------- periods

    @Query("SELECT * FROM installment_period WHERE planId = :planId ORDER BY periodIndex ASC")
    fun observePeriods(planId: Long): Flow<List<InstallmentPeriodEntity>>

    @Query("SELECT * FROM installment_period WHERE planId = :planId ORDER BY periodIndex ASC")
    suspend fun periods(planId: Long): List<InstallmentPeriodEntity>

    /**
     * Creates the period row if it does not exist yet.
     *
     * IGNORE on the unique `(planId, periodIndex)` index makes this safe to call
     * repeatedly: the second attempt is a no-op rather than a duplicate charge.
     * Returns -1 when the row already existed.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeriodIfAbsent(period: InstallmentPeriodEntity): Long

    /**
     * Claims a period for a freshly written transaction.
     *
     * The `txnId IS NULL` guard is the real idempotency check: two concurrent
     * catch-ups cannot both claim the same period, because the second update
     * matches no rows and returns 0.
     */
    @Query(
        """
        UPDATE installment_period
        SET txnId = :txnId, generatedAt = :now
        WHERE id = :periodId AND txnId IS NULL
        """
    )
    suspend fun claimPeriod(
        periodId: Long,
        txnId: Long,
        now: Long = System.currentTimeMillis(),
    ): Int

    @Query("SELECT COUNT(*) FROM installment_period WHERE planId = :planId AND txnId IS NOT NULL")
    suspend fun paidPeriodCount(planId: Long): Int

    @Query("DELETE FROM installment_period WHERE planId = :planId")
    suspend fun deletePeriods(planId: Long)
}
