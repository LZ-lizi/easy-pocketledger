package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketledger.data.entity.BudgetEntity
import com.pocketledger.data.entity.BudgetPeriodType
import kotlinx.coroutines.flow.Flow

/** A budget row joined with the name of the category it caps, if any. */
data class BudgetWithName(
    val id: Long,
    val categoryId: Long,
    val amountCents: Long,
    val categoryName: String?,
    val iconKey: String?,
    val colorArgb: Int?,
)

@Dao
interface BudgetDao {

    @Query(
        """
        SELECT * FROM budget
        WHERE ledgerId = :ledgerId AND periodType = :periodType AND periodKey = :periodKey
        """
    )
    fun observeForPeriod(
        ledgerId: Long,
        periodType: BudgetPeriodType,
        periodKey: String,
    ): Flow<List<BudgetEntity>>

    @Query(
        """
        SELECT b.id AS id,
               b.categoryId AS categoryId,
               b.amountCents AS amountCents,
               c.name AS categoryName,
               c.iconKey AS iconKey,
               c.colorArgb AS colorArgb
        FROM budget b
        LEFT JOIN category c ON c.id = b.categoryId
        WHERE b.ledgerId = :ledgerId
          AND b.periodType = :periodType
          AND b.periodKey = :periodKey
        """
    )
    fun observeForPeriodWithNames(
        ledgerId: Long,
        periodType: BudgetPeriodType,
        periodKey: String,
    ): Flow<List<BudgetWithName>>

    @Query(
        """
        SELECT * FROM budget
        WHERE ledgerId = :ledgerId AND periodType = :periodType AND periodKey = :periodKey
        """
    )
    suspend fun forPeriod(
        ledgerId: Long,
        periodType: BudgetPeriodType,
        periodKey: String,
    ): List<BudgetEntity>

    /**
     * Sets one cap.
     *
     * The unique `(ledgerId, periodType, periodKey, scope, categoryId)` index makes
     * REPLACE an upsert, so the caller never has to look up whether a budget exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long

    @Query(
        """
        DELETE FROM budget
        WHERE ledgerId = :ledgerId AND periodType = :periodType
          AND periodKey = :periodKey AND categoryId = :categoryId
        """
    )
    suspend fun delete(
        ledgerId: Long,
        periodType: BudgetPeriodType,
        periodKey: String,
        categoryId: Long,
    )
}
