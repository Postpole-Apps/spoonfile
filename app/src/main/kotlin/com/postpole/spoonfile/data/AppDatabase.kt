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
        MealPlanEntryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun categoryDao(): CategoryDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun mealPlanDao(): MealPlanDao
}

/** Version 2 to 3: adds the nullable `lastCookedAt` stamp to recipes. Existing rows stay null. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recipes` ADD COLUMN `lastCookedAt` INTEGER")
    }
}

/** Version 1 to 2: adds the meal plan table only. Existing rows are untouched. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
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
