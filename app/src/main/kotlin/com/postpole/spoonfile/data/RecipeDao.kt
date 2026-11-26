package com.postpole.spoonfile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class RecipeDao {

    @Query(
        """
        SELECT * FROM recipes
        WHERE (:query = '' OR title LIKE '%' || :query || '%' ESCAPE '\'
            OR EXISTS (SELECT 1 FROM recipe_ingredients
                       WHERE recipeId = recipes.id AND text LIKE '%' || :query || '%' ESCAPE '\'))
          AND (:categoryId IS NULL OR id IN
               (SELECT recipeId FROM recipe_categories WHERE categoryId = :categoryId))
          AND (:favoritesOnly = 0 OR favorite = 1)
        ORDER BY
          CASE WHEN :sort = 'AZ' THEN title END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'RATING' THEN rating END DESC,
          CASE WHEN :sort = 'LAST_COOKED' THEN lastCookedAt IS NULL END ASC,
          CASE WHEN :sort = 'LAST_COOKED' THEN lastCookedAt END DESC,
          updatedAt DESC
        """,
    )
    abstract fun observeList(
        query: String,
        categoryId: Long?,
        favoritesOnly: Boolean,
        sort: RecipeSort,
    ): Flow<List<RecipeEntity>>

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY id")
    abstract suspend fun allDetails(): List<RecipeWithDetails>

    @Query("SELECT * FROM recipes ORDER BY id")
    abstract suspend fun allRecipes(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients ORDER BY recipeId, position, id")
    abstract suspend fun allIngredients(): List<IngredientEntity>

    @Query("SELECT * FROM recipe_steps ORDER BY recipeId, position, id")
    abstract suspend fun allSteps(): List<StepEntity>

    @Query("SELECT * FROM recipe_categories ORDER BY recipeId, categoryId")
    abstract suspend fun allCrossRefs(): List<RecipeCategoryCrossRef>

    /** Deletes every recipe; ingredients, steps and category links cascade. */
    @Query("DELETE FROM recipes")
    abstract suspend fun deleteAllRecipes()

    @Insert
    abstract suspend fun restoreRecipes(items: List<RecipeEntity>)

    @Insert
    abstract suspend fun restoreIngredients(items: List<IngredientEntity>)

    @Insert
    abstract suspend fun restoreSteps(items: List<StepEntity>)

    @Insert
    abstract suspend fun restoreCrossRefs(items: List<RecipeCategoryCrossRef>)

    @Query("SELECT COUNT(*) FROM recipes")
    abstract fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM recipes")
    abstract suspend fun countNow(): Int

    @Query("SELECT * FROM recipes WHERE uid = :uid LIMIT 1")
    abstract suspend fun getByUid(uid: String): RecipeEntity?

    /** Just the fields the bulk import needs to spot recipes that are already saved. */
    @Query("SELECT uid, title, sourceName, sourceUrl FROM recipes")
    abstract suspend fun dedupeKeys(): List<DedupeKey>

    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract suspend fun getById(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE sourceUrl = :url LIMIT 1")
    abstract suspend fun findBySourceUrl(url: String): RecipeEntity?

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    protected abstract fun observeDetailsRaw(id: Long): Flow<RecipeWithDetails?>

    fun observeDetails(id: Long): Flow<RecipeWithDetails?> =
        observeDetailsRaw(id).map { it?.ordered() }

    /** Stamps only `lastCookedAt`; `updatedAt` is left alone so Recent keeps meaning last edited. */
    @Query("UPDATE recipes SET lastCookedAt = :nowMillis WHERE id = :id")
    abstract suspend fun markCooked(id: Long, nowMillis: Long)

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertCrossRefs(items: List<RecipeCategoryCrossRef>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :id")
    protected abstract suspend fun deleteIngredients(id: Long)

    @Query("DELETE FROM recipe_steps WHERE recipeId = :id")
    protected abstract suspend fun deleteSteps(id: Long)

    @Query("DELETE FROM recipe_categories WHERE recipeId = :id")
    protected abstract suspend fun deleteCrossRefs(id: Long)

    /**
     * Inserts or updates [recipe] (matched by id, else by uid) and replaces all of its children
     * in one transaction. Returns the recipe's row id.
     */
    @Transaction
    open suspend fun upsertWithChildren(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        steps: List<StepEntity>,
        categoryIds: List<Long>,
    ): Long {
        val clean = recipe.copy(title = recipe.title.trim())
        val existingId = if (clean.id != 0L) clean.id else (getByUid(clean.uid)?.id ?: 0L)
        val id = if (existingId != 0L) {
            updateRecipe(clean.copy(id = existingId))
            deleteIngredients(existingId)
            deleteSteps(existingId)
            deleteCrossRefs(existingId)
            existingId
        } else {
            insertRecipe(clean.copy(id = 0))
        }
        insertIngredients(
            ingredients.mapIndexed { index, item -> item.copy(id = 0, recipeId = id, position = index) },
        )
        insertSteps(steps.mapIndexed { index, item -> item.copy(id = 0, recipeId = id, position = index) })
        insertCrossRefs(categoryIds.distinct().map { RecipeCategoryCrossRef(id, it) })
        return id
    }
}
