package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /**
     * Real accounts only.
     *
     * `isHidden = 0` here as well as in [observeActive]: a hidden account is an
     * implementation detail of 累计模式 ledgers and must never surface on any screen,
     * not just in pickers.
     */
    @Query(
        """
        SELECT * FROM account
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND isHidden = 0
        ORDER BY isArchived ASC, sortOrder ASC, id ASC
        """
    )
    fun observeAll(ledgerId: Long): Flow<List<AccountEntity>>

    /**
     * Accounts the user may pick from.
     *
     * `isHidden = 0` is what makes a 累计模式 ledger show no accounts: it still owns
     * one so transactions have somewhere to point, but that account never surfaces.
     */
    @Query(
        """
        SELECT * FROM account
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL
          AND isArchived = 0 AND isHidden = 0
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeActive(ledgerId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    /** One-shot read for form screens that need the list once, not a subscription. */
    @Query(
        """
        SELECT * FROM account
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND isHidden = 0
        ORDER BY isArchived ASC, sortOrder ASC, id ASC
        """
    )
    suspend fun all(ledgerId: Long): List<AccountEntity>

    @Query("SELECT COUNT(*) FROM account WHERE ledgerId = :ledgerId AND deletedAt IS NULL")
    suspend fun count(ledgerId: Long): Int

    /** Unscoped on purpose: used once at startup to detect pre-ledger data. */
    @Query("SELECT COUNT(*) FROM account WHERE deletedAt IS NULL")
    suspend fun countAll(): Int

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
        WHERE a.ledgerId = :ledgerId AND a.deletedAt IS NULL
        """
    )
    fun observeBalances(ledgerId: Long): Flow<List<AccountBalance>>

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
