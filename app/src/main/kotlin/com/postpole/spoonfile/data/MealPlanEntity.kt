package com.postpole.spoonfile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One recipe planned on one day. [date] is a local ISO-8601 date (`yyyy-MM-dd`, no time zone). The same
 * recipe may appear on several days, or twice on one day; [position] orders a day's entries.
 */
@Entity(
    tableName = "meal_plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["date"]), Index(value = ["recipeId"])],
)
data class MealPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val recipeId: Long,
    val position: Int = 0,
    val createdAt: Long,
)

/** A plan entry together with the bits of its recipe the plan screen shows. */
data class MealPlanRow(
    val id: Long,
    val date: String,
    val recipeId: Long,
    val position: Int,
    val title: String,
    val imageFile: String?,
)
