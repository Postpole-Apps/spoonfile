package com.postpole.spoonfile.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class RecipeOrigin { MANUAL, WEB, OCR, PAPRIKA, PASTE }

enum class RecipeSort { RECENT, AZ, RATING, LAST_COOKED }

@Entity(
    tableName = "recipes",
    indices = [Index(value = ["uid"], unique = true), Index(value = ["sourceUrl"])],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val title: String,
    val description: String? = null,
    val servingsText: String? = null,
    val servings: Double? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val notes: String? = null,
    val nutritionText: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val imageFile: String? = null,
    val rating: Int = 0,
    val favorite: Boolean = false,
    val origin: RecipeOrigin = RecipeOrigin.MANUAL,
    val createdAt: Long,
    val updatedAt: Long,
    /** Epoch ms of the last finished Cook mode ("Done" on the last step); null if never cooked. */
    val lastCookedAt: Long? = null,
)
