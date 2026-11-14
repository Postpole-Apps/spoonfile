package com.postpole.spoonfile.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<StepEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = RecipeCategoryCrossRef::class,
            parentColumn = "recipeId",
            entityColumn = "categoryId",
        ),
    )
    val categories: List<CategoryEntity>,
) {
    /** Relations come back unordered; this sorts children by position and categories by order/name. */
    fun ordered(): RecipeWithDetails = copy(
        ingredients = ingredients.sortedBy { it.position },
        steps = steps.sortedBy { it.position },
        categories = categories.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() })),
    )
}

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    val recipeCount: Int,
)
