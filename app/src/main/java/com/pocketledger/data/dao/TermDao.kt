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

    @Query("SELECT * FROM term WHERE ledgerId = :ledgerId ORDER BY startDateKey DESC")
    fun observeAll(ledgerId: Long): Flow<List<TermEntity>>

    @Query("SELECT * FROM term WHERE ledgerId = :ledgerId ORDER BY startDateKey DESC")
    suspend fun all(ledgerId: Long): List<TermEntity>

    @Query("SELECT * FROM term WHERE id = :id")
    suspend fun byId(id: Long): TermEntity?

    @Query("SELECT COUNT(*) FROM term WHERE ledgerId = :ledgerId")
    suspend fun count(ledgerId: Long): Int

    @Insert
    suspend fun insert(term: TermEntity): Long

    @Update
    suspend fun update(term: TermEntity)

    @Query("DELETE FROM term WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE term SET isActive = 0 WHERE ledgerId = :ledgerId")
    suspend fun clearActive(ledgerId: Long)

    /**
     * Terms belonging to no ledger at all.
     *
     * Reachable in practice: an editor can submit a rebuilt row that carries the
     * `ledgerId` default instead of the row's real one, and an earlier ledger deletion
     * could leave rows behind. Such a term is invisible on every screen -- the list is
     * scoped to a ledger -- so the user's honest description of it is "my 学期 data is
     * gone", while the row is still in the database.
     */
    @Query("SELECT COUNT(*) FROM term WHERE ledgerId NOT IN (SELECT id FROM ledger)")
    suspend fun countOrphans(): Int

    /** Re-homes every orphan onto [ledgerId]; see [countOrphans]. */
    @Query("UPDATE term SET ledgerId = :ledgerId WHERE ledgerId NOT IN (SELECT id FROM ledger)")
    suspend fun rehomeOrphans(ledgerId: Long): Int
}
