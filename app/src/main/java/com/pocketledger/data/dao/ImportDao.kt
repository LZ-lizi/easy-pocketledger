package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketledger.data.entity.ImportBatchEntity
import com.pocketledger.data.entity.ImportRuleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Import bookkeeping: which batches were imported and what the user taught the matcher.
 *
 * Both tables are ledger-scoped like everything else, so a merge between two students'
 * books cannot cross-contaminate their category rules.
 */
@Dao
interface ImportDao {

    @Insert
    suspend fun insertBatch(batch: ImportBatchEntity): Long

    @Query("SELECT * FROM import_batch WHERE ledgerId = :ledgerId ORDER BY importedAt DESC")
    fun observeBatches(ledgerId: Long): Flow<List<ImportBatchEntity>>

    /**
     * Removes the batch *record* only.
     *
     * The transactions it produced are soft-deleted separately, through
     * `txnDao.softDeleteBatch`: undoing an import has to leave the rows recoverable,
     * but a batch marker that outlived its rows would make the ledger claim an import
     * that is no longer there.
     */
    @Query("DELETE FROM import_batch WHERE id = :id")
    suspend fun deleteBatch(id: Long)

    @Query("SELECT * FROM import_rule WHERE ledgerId = :ledgerId")
    suspend fun rules(ledgerId: Long): List<ImportRuleEntity>

    /**
     * Learns (or re-learns) one keyword.
     *
     * REPLACE rather than ignore: correcting 美团 from 日用品 to 外卖 must overwrite the
     * earlier rule, which is the only way a wrong guess can be permanently fixed.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRules(rules: List<ImportRuleEntity>)

    @Query("DELETE FROM import_rule WHERE ledgerId = :ledgerId AND keyword = :keyword")
    suspend fun deleteRule(ledgerId: Long, keyword: String)
}
