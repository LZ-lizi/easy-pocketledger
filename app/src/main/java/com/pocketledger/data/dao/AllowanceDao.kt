package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketledger.data.entity.AllowanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowanceDao {

    @Query("SELECT * FROM allowance WHERE periodKey = :periodKey LIMIT 1")
    fun observeForPeriod(periodKey: String): Flow<AllowanceEntity?>

    /**
     * The allowance in force for a month.
     *
     * Months the user never configured inherit the most recent earlier setting,
     * so the home card keeps working without a monthly chore.
     */
    @Query("SELECT * FROM allowance WHERE periodKey <= :periodKey ORDER BY periodKey DESC LIMIT 1")
    suspend fun effectiveFor(periodKey: String): AllowanceEntity?

    /** Reactive form of [effectiveFor], so the home card updates when the setting changes. */
    @Query("SELECT * FROM allowance WHERE periodKey <= :periodKey ORDER BY periodKey DESC LIMIT 1")
    fun observeEffectiveFor(periodKey: String): Flow<AllowanceEntity?>

    @Query("SELECT * FROM allowance ORDER BY periodKey DESC")
    fun observeHistory(): Flow<List<AllowanceEntity>>

    @Query("SELECT * FROM allowance WHERE periodKey = :periodKey LIMIT 1")
    suspend fun byPeriod(periodKey: String): AllowanceEntity?

    /** The unique index on `periodKey` makes REPLACE an upsert. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allowance: AllowanceEntity): Long

    @Query("DELETE FROM allowance WHERE periodKey = :periodKey")
    suspend fun deletePeriod(periodKey: String)
}
