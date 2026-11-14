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
    val orderedIngredients: List<IngredientEntity> get() = ingredients.sortedBy { it.position }
    val orderedSteps: List<StepEntity> get() = steps.sortedBy { it.position }
}
