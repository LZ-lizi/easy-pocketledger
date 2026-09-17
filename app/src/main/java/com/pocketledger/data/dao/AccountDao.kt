package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM account WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT * FROM account
        WHERE deletedAt IS NULL AND isArchived = 0
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM account WHERE deletedAt IS NULL")
    suspend fun count(): Int

    /**
     * Derives every balance in one pass.
     *
     * Balances are never stored: a cached column would have to be invalidated on
     * every edit, import and restore, and the two would eventually disagree. At
     * personal-bookkeeping scale this aggregate is far cheaper than that risk.
     *
     * A transfer moves `amountCents` and additionally costs the source its
     * `feeCents`; a credit card simply goes negative to mean "owed".
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.initialBalanceCents
               + COALESCE((SELECT SUM(t.amountCents) FROM txn t
                           WHERE t.deletedAt IS NULL AND t.type = 'INCOME'
                             AND t.accountId = a.id), 0)
               - COALESCE((SELECT SUM(t.amountCents) FROM txn t
                           WHERE t.deletedAt IS NULL AND t.type = 'EXPENSE'
                             AND t.accountId = a.id), 0)
               + COALESCE((SELECT SUM(t.amountCents) FROM txn t
                           WHERE t.deletedAt IS NULL AND t.type = 'TRANSFER'
                             AND t.toAccountId = a.id), 0)
               - COALESCE((SELECT SUM(t.amountCents + COALESCE(t.feeCents, 0)) FROM txn t
                           WHERE t.deletedAt IS NULL AND t.type = 'TRANSFER'
                             AND t.accountId = a.id), 0)
               AS balanceCents
        FROM account a
        WHERE a.deletedAt IS NULL
        """
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE account SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE account SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())
}
