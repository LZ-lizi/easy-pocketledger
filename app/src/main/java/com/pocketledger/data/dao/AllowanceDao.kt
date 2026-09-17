package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.AllowanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowanceDao {

    @Query("SELECT * FROM allowance WHERE ledgerId = :ledgerId AND periodKey = :periodKey LIMIT 1")
    fun observeForPeriod(ledgerId: Long, periodKey: String): Flow<AllowanceEntity?>

    /**
     * The allowance in force for a month.
     *
     * Months the user never configured inherit the most recent earlier setting, so
     * the home card keeps working without a monthly chore.
     */
    @Query(
        """
        SELECT * FROM allowance
        WHERE ledgerId = :ledgerId AND periodKey <= :periodKey
        ORDER BY periodKey DESC LIMIT 1
        """
    )
    suspend fun effectiveFor(ledgerId: Long, periodKey: String): AllowanceEntity?

    /** Reactive form of [effectiveFor], so the home card updates when the setting changes. */
    @Query(
        """
        SELECT * FROM allowance
        WHERE ledgerId = :ledgerId AND periodKey <= :periodKey
        ORDER BY periodKey DESC LIMIT 1
        """
    )
    fun observeEffectiveFor(ledgerId: Long, periodKey: String): Flow<AllowanceEntity?>

    @Query("SELECT * FROM allowance WHERE ledgerId = :ledgerId ORDER BY periodKey DESC")
    fun observeHistory(ledgerId: Long): Flow<List<AllowanceEntity>>

    @Query("SELECT * FROM allowance WHERE ledgerId = :ledgerId AND periodKey = :periodKey LIMIT 1")
    suspend fun byPeriod(ledgerId: Long, periodKey: String): AllowanceEntity?

    /** The unique `(ledgerId, periodKey)` index makes REPLACE an upsert. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(allowance: AllowanceEntity): Long

    @Query("DELETE FROM allowance WHERE ledgerId = :ledgerId AND periodKey = :periodKey")
    suspend fun deletePeriod(ledgerId: Long, periodKey: String)
}
