package com.pocketledger.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.CategoryKind
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** Top-level categories first, then their children, each block in user order. */
    @Query(
        """
        SELECT * FROM category
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL
        ORDER BY kind ASC, parentId IS NOT NULL ASC, sortOrder ASC, id ASC
        """
    )
    fun observeAll(ledgerId: Long): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL AND kind = :kind
        ORDER BY parentId IS NOT NULL ASC, sortOrder ASC, id ASC
        """
    )
    fun observeByKind(ledgerId: Long, kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE ledgerId = :ledgerId AND deletedAt IS NULL
          AND parentId IS NULL AND kind = :kind
        ORDER BY sortOrder ASC, id ASC
        """
    )
    suspend fun topLevel(ledgerId: Long, kind: CategoryKind): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE ledgerId = :ledgerId AND deletedAt IS NULL")
    suspend fun all(ledgerId: Long): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM category WHERE ledgerId = :ledgerId AND deletedAt IS NULL")
    suspend fun count(ledgerId: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    /**
     * Re-parenting a category is how a debatable classification gets fixed. History
     * follows the category, so no transaction rows need touching.
     */
    @Query("UPDATE category SET parentId = :parentId, updatedAt = :now WHERE id = :id")
    suspend fun moveTo(id: Long, parentId: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE category SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT COALESCE(MAX(sortOrder), 0) FROM category
        WHERE ledgerId = :ledgerId AND kind = :kind AND parentId IS :parentId
        """
    )
    suspend fun maxSortOrder(ledgerId: Long, kind: CategoryKind, parentId: Long?): Int
}
