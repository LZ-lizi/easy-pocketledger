package com.pocketledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pocketledger.data.dao.AccountDao
import com.pocketledger.data.dao.AllowanceDao
import com.pocketledger.data.dao.CategoryDao
import com.pocketledger.data.dao.TagDao
import com.pocketledger.data.dao.TermDao
import com.pocketledger.data.dao.TxnDao
import com.pocketledger.data.entity.AccountEntity
import com.pocketledger.data.entity.AllowanceEntity
import com.pocketledger.data.entity.BudgetEntity
import com.pocketledger.data.entity.CategoryEntity
import com.pocketledger.data.entity.GoalEntity
import com.pocketledger.data.entity.ImportBatchEntity
import com.pocketledger.data.entity.ImportRuleEntity
import com.pocketledger.data.entity.TagEntity
import com.pocketledger.data.entity.TemplateEntity
import com.pocketledger.data.entity.TermEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.data.entity.TxnTagCrossRef
import com.pocketledger.data.entity.WishEntity

/**
 * The whole schema is declared in v1 even though features land milestone by
 * milestone. Adding tables to an already-shipped database means writing
 * migrations; declaring them up front costs nothing and avoids that.
 */
@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TxnEntity::class,
        TagEntity::class,
        TxnTagCrossRef::class,
        BudgetEntity::class,
        AllowanceEntity::class,
        TermEntity::class,
        WishEntity::class,
        GoalEntity::class,
        TemplateEntity::class,
        ImportRuleEntity::class,
        ImportBatchEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun allowanceDao(): AllowanceDao
    abstract fun tagDao(): TagDao
    abstract fun termDao(): TermDao

    companion object {
        const val NAME = "ledger.db"

        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, NAME)
                // Foreign keys back the tag join table; SQLite needs them switched on.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
