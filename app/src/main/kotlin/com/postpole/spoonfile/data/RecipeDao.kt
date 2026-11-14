package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {

    @Query(
        """
        SELECT * FROM recipes
        WHERE (title LIKE '%' || :query || '%'
               OR EXISTS (SELECT 1 FROM recipe_ingredients
                          WHERE recipeId = recipes.id AND text LIKE '%' || :query || '%'))
          AND (:categoryId IS NULL OR EXISTS (SELECT 1 FROM recipe_categories
                                              WHERE recipeId = recipes.id AND categoryId = :categoryId))
          AND (:favoritesOnly = 0 OR favorite = 1)
        ORDER BY
          CASE WHEN :sort = 'AZ' THEN title END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'RATING' THEN rating END DESC,
          updatedAt DESC, id DESC
        """,
    )
    abstract fun observeList(
        query: String,
        categoryId: Long?,
        favoritesOnly: Boolean,
        sort: RecipeSort,
    ): Flow<List<RecipeEntity>>

    @Query("SELECT COUNT(*) FROM recipes")
    abstract fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes")
    abstract suspend fun countNow(): Int

    @Query("SELECT * FROM recipes WHERE uid = :uid LIMIT 1")
    abstract suspend fun getByUid(uid: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE sourceUrl = :url LIMIT 1")
    abstract suspend fun findBySourceUrl(url: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract suspend fun getById(id: Long): RecipeEntity?

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract fun observeDetails(id: Long): Flow<RecipeWithDetails?>

    @Query("DELETE FROM recipes WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Insert
    protected abstract suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    protected abstract suspend fun updateRecipe(recipe: RecipeEntity)

    @Insert
    protected abstract suspend fun insertIngredients(items: List<IngredientEntity>)

    @Insert
    protected abstract suspend fun insertSteps(items: List<StepEntity>)

    @Insert
    protected abstract suspend fun insertCrossRefs(items: List<RecipeCategoryCrossRef>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteIngredients(recipeId: Long)

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteSteps(recipeId: Long)

    @Query("DELETE FROM recipe_categories WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteCrossRefs(recipeId: Long)

    /** Inserts (id == 0) or updates the recipe and replaces all children, in one transaction. Returns the recipe id. */
    @Transaction
    open suspend fun upsertWithChildren(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        steps: List<StepEntity>,
        categoryIds: List<Long>,
    ): Long {
        val id: Long
        if (recipe.id == 0L) {
            id = insertRecipe(recipe)
        } else {
            id = recipe.id
            updateRecipe(recipe)
            deleteIngredients(id)
            deleteSteps(id)
            deleteCrossRefs(id)
        }
        insertIngredients(ingredients.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
        insertSteps(steps.mapIndexed { i, it -> it.copy(id = 0, recipeId = id, position = i) })
        insertCrossRefs(categoryIds.distinct().map { RecipeCategoryCrossRef(id, it) })
        return id
    }
}
