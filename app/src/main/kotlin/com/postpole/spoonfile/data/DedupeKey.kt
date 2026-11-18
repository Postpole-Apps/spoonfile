package com.postpole.spoonfile.data

/** Identity fields of a saved recipe, used to flag duplicates during a bulk import. */
data class DedupeKey(
    val uid: String,
    val title: String,
    val sourceName: String?,
    val sourceUrl: String?,
)
