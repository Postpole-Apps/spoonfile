package com.postpole.spoonfile.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun originToString(value: RecipeOrigin): String = value.name

    @TypeConverter
    fun stringToOrigin(value: String): RecipeOrigin =
        runCatching { RecipeOrigin.valueOf(value) }.getOrDefault(RecipeOrigin.MANUAL)

    @TypeConverter
    fun sortToString(value: RecipeSort): String = value.name

    @TypeConverter
    fun stringToSort(value: String): RecipeSort =
        runCatching { RecipeSort.valueOf(value) }.getOrDefault(RecipeSort.RECENT)
}
