package com.pocketledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pocketledger.data.dao.AccountDao
import com.pocketledger.data.dao.AllowanceDao
import com.pocketledger.data.dao.BudgetDao
import com.pocketledger.data.dao.CategoryDao
import com.pocketledger.data.dao.InstallmentDao
import com.pocketledger.data.dao.ImportDao
import com.pocketledger.data.dao.LedgerDao
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
import com.pocketledger.data.entity.InstallmentPeriodEntity
import com.pocketledger.data.entity.InstallmentPlanEntity
import com.pocketledger.data.entity.LedgerEntity
import com.pocketledger.data.entity.TagEntity
import com.pocketledger.data.entity.TemplateEntity
import com.pocketledger.data.entity.TermEntity
import com.pocketledger.data.entity.TxnEntity
import com.pocketledger.data.entity.TxnTagCrossRef
import com.pocketledger.data.entity.WishEntity

/**
 * The whole schema is declared up front even though features land milestone by
 * milestone: adding tables to an already-shipped database means writing
 * migrations, and declaring them early avoids that.
 */
@Database(
    entities = [
        LedgerEntity::class,
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
        InstallmentPlanEntity::class,
        InstallmentPeriodEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LedgerDatabase : RoomDatabase() {

    abstract fun ledgerDao(): LedgerDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun txnDao(): TxnDao
    abstract fun allowanceDao(): AllowanceDao
    abstract fun budgetDao(): BudgetDao
    abstract fun tagDao(): TagDao
    abstract fun termDao(): TermDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun importDao(): ImportDao

    companion object {
        const val NAME = "ledger.db"

        /** The `version` above, readable without opening the database. */
        const val SCHEMA_VERSION = 4

        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, NAME)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
