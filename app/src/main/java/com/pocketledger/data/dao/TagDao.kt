package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pocketledger.data.entity.TagEntity
import com.pocketledger.data.entity.TxnTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tag ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag ORDER BY name ASC")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT t.* FROM tag t JOIN txn_tag x ON x.tagId = t.id WHERE x.txnId = :txnId ORDER BY t.name")
    suspend fun tagsFor(txnId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(refs: List<TxnTagCrossRef>)

    @Query("DELETE FROM txn_tag WHERE txnId = :txnId")
    suspend fun unlinkAll(txnId: Long)

    @Query("DELETE FROM txn_tag WHERE txnId = :txnId AND tagId NOT IN (:tagIds)")
    suspend fun unlinkExcept(txnId: Long, tagIds: List<Long>)
}
