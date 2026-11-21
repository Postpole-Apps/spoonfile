package com.postpole.spoonfile.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        StepEntity::class,
        CategoryEntity::class,
        RecipeCategoryCrossRef::class,
        ShoppingItemEntity::class,
        MealPlanEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mealPlanDao(): MealPlanDao
}

/** v1 -> v2: adds the meal plan table only; recipes, shopping items and categories are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meal_plan_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, `recipeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plan_entries_date` ON `meal_plan_entries` (`date`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_meal_plan_entries_recipeId` ON `meal_plan_entries` (`recipeId`)")
    }
}
