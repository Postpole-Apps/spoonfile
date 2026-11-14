package com.postpole.spoonfile.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class RecipeOrigin { MANUAL, WEB, OCR, PAPRIKA, PASTE }

enum class RecipeSort { RECENT, AZ, RATING }

@Entity(
    tableName = "recipes",
    indices = [Index(value = ["uid"], unique = true), Index(value = ["sourceUrl"])],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val servingsText: String? = null,
    /** Parsed number used for scaling; null disables scaling. */
    val servings: Double? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val notes: String? = null,
    val nutritionText: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    /** Relative file name inside filesDir/images/. */
    val imageFile: String? = null,
    val rating: Int = 0,
    val favorite: Boolean = false,
    val origin: RecipeOrigin = RecipeOrigin.MANUAL,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Total time for display; falls back to prep + cook. */
    val displayTotalMinutes: Int?
        get() = totalMinutes ?: if (prepMinutes != null || cookMinutes != null) {
            (prepMinutes ?: 0) + (cookMinutes ?: 0)
        } else {
            null
        }
}
