package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.TermEntity
import kotlinx.coroutines.flow.Flow

/**
 * Named date ranges such as 「2026 秋季学期」.
 *
 * A term is deliberately just a pair of dates rather than a campus calendar: the
 * app has no way to know when a given school starts, and guessing would be worse
 * than letting the user type two dates once per term.
 */
@Dao
interface TermDao {

    @Query("SELECT * FROM term ORDER BY startDateKey DESC")
    fun observeAll(): Flow<List<TermEntity>>

    @Query("SELECT * FROM term ORDER BY startDateKey DESC")
    suspend fun all(): List<TermEntity>

    @Query("SELECT * FROM term WHERE id = :id")
    suspend fun byId(id: Long): TermEntity?

    @Query("SELECT COUNT(*) FROM term")
    suspend fun count(): Int

    @Insert
    suspend fun insert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    @Query("DELETE FROM term WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE term SET isActive = 0")
    suspend fun clearActive()
}
