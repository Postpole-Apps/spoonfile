package com.postpole.spoonfile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["recipeId"])],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val checked: Boolean = false,
    val recipeId: Long? = null,
    val position: Int = 0,
    val createdAt: Long,
)
