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

    /** Main categories first, then their children, each block in user order. */
    @Query(
        """
        SELECT * FROM category
        WHERE deletedAt IS NULL
        ORDER BY kind ASC, parentId IS NOT NULL ASC, sortOrder ASC, id ASC
        """
    )
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE deletedAt IS NULL AND kind = :kind
        ORDER BY parentId IS NOT NULL ASC, sortOrder ASC, id ASC
        """
    )
    fun observeByKind(kind: CategoryKind): Flow<List<CategoryEntity>>

    @Query(
        """
        SELECT * FROM category
        WHERE deletedAt IS NULL AND parentId IS NULL AND kind = :kind
        ORDER BY sortOrder ASC, id ASC
        """
    )
    suspend fun mainCategories(kind: CategoryKind): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE deletedAt IS NULL")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM category WHERE ledgerId = :ledgerId AND deletedAt IS NULL")
    suspend fun countForLedger(ledgerId: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    /**
     * Re-parenting a category is how the user fixes a debatable classification
     * (say 数码 / 电子 belongs under 日常生活 after all). History follows the
     * category, so no transaction rows need touching.
     */
    @Query("UPDATE category SET parentId = :parentId, updatedAt = :now WHERE id = :id")
    suspend fun moveTo(id: Long, parentId: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE category SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long = System.currentTimeMillis())

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM category WHERE kind = :kind AND parentId IS :parentId")
    suspend fun maxSortOrder(kind: CategoryKind, parentId: Long?): Int
}
