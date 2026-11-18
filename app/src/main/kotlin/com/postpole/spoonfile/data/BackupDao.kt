package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** Whole-table reads and writes used only by backup, restore and recipe-file export. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM recipes ORDER BY id")
    suspend fun recipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, position")
    suspend fun ingredients(): List<IngredientEntity>

    @Query("SELECT * FROM recipe_steps ORDER BY recipeId, position")
    suspend fun steps(): List<StepEntity>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun categories(): List<CategoryEntity>

    @Query("SELECT * FROM recipe_categories ORDER BY recipeId, categoryId")
    suspend fun recipeCategories(): List<RecipeCategoryCrossRef>

    @Query("SELECT * FROM shopping_items ORDER BY id")
    suspend fun shoppingItems(): List<ShoppingItemEntity>

    @Query("DELETE FROM recipes")
    suspend fun deleteRecipes()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Query("DELETE FROM shopping_items")
    suspend fun deleteShoppingItems()

    @Insert
    suspend fun insertRecipes(items: List<RecipeEntity>)

    @Insert
    suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    suspend fun insertSteps(items: List<StepEntity>)

    @Insert
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert
    suspend fun insertRecipeCategories(items: List<RecipeCategoryCrossRef>)

    @Insert
    suspend fun insertShoppingItems(items: List<ShoppingItemEntity>)
}
