package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.LedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledger WHERE deletedAt IS NULL ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<LedgerEntity>>

    @Query("SELECT * FROM ledger WHERE deletedAt IS NULL ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    suspend fun all(): List<LedgerEntity>

    @Query("SELECT * FROM ledger WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun active(): List<LedgerEntity>

    @Query("SELECT * FROM ledger WHERE id = :id")
    suspend fun byId(id: Long): LedgerEntity?

    /** Zero means the database has never been used and onboarding should run. */
    @Query("SELECT COUNT(*) FROM ledger WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM ledger")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(ledger: LedgerEntity): Long

    @Update
    suspend fun update(ledger: LedgerEntity)

    @Query("UPDATE ledger SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE ledger SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())
}
