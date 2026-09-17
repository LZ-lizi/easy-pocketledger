package com.pocketledger.data

import androidx.room.TypeConverter
import com.pocketledger.data.entity.AccountType
import com.pocketledger.data.entity.BudgetPeriodType
import com.pocketledger.data.entity.BudgetScope
import com.pocketledger.data.entity.CategoryKind
import com.pocketledger.data.entity.GoalStatus
import com.pocketledger.data.entity.TxnSource
import com.pocketledger.data.entity.TxnType
import com.pocketledger.data.entity.WishStatus

/**
 * Enums are stored as their `name`, never their ordinal.
 *
 * Room converts enums on its own, but declaring the converters makes the on-disk
 * format explicit and lets raw SQL compare against readable literals such as
 * `type = 'EXPENSE'` without depending on an undocumented default. Names also
 * survive enum reordering, which ordinals would not.
 */
class Converters {

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromCategoryKind(value: CategoryKind): String = value.name

    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun fromTxnType(value: TxnType): String = value.name

    @TypeConverter
    fun toTxnType(value: String): TxnType = TxnType.valueOf(value)

    @TypeConverter
    fun fromTxnSource(value: TxnSource): String = value.name

    @TypeConverter
    fun toTxnSource(value: String): TxnSource = TxnSource.valueOf(value)

    @TypeConverter
    fun fromTxnSourceOrNull(value: TxnSource?): String? = value?.name

    @TypeConverter
    fun toTxnSourceOrNull(value: String?): TxnSource? = value?.let(TxnSource::valueOf)

    @TypeConverter
    fun fromBudgetPeriodType(value: BudgetPeriodType): String = value.name

    @TypeConverter
    fun toBudgetPeriodType(value: String): BudgetPeriodType = BudgetPeriodType.valueOf(value)

    @TypeConverter
    fun fromBudgetScope(value: BudgetScope): String = value.name

    @TypeConverter
    fun toBudgetScope(value: String): BudgetScope = BudgetScope.valueOf(value)

    @TypeConverter
    fun fromWishStatus(value: WishStatus): String = value.name

    @TypeConverter
    fun toWishStatus(value: String): WishStatus = WishStatus.valueOf(value)

    @TypeConverter
    fun fromGoalStatus(value: GoalStatus): String = value.name

    @TypeConverter
    fun toGoalStatus(value: String): GoalStatus = GoalStatus.valueOf(value)
}
