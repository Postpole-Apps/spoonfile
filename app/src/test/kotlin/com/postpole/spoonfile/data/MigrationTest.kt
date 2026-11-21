package com.postpole.spoonfile.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Builds a real version-1 database from the exported 1.json schema, then upgrades it with [MIGRATION_1_2]. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        // Nothing: each test creates the file it needs.
    }

    @After
    fun tearDown() {
        db?.close()
    }

    private fun schema(version: Int): JSONObject {
        val dir = listOf(File("schemas"), File("app/schemas")).first { it.isDirectory }
        return JSONObject(File(dir, "com.postpole.spoonfile.data.AppDatabase/$version.json").readText()).getJSONObject("database")
    }

    private fun createV1(file: File) {
        val v1 = schema(1)
        val sqlite = SQLiteDatabase.openOrCreateDatabase(file, null)
        val entities = v1.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            sqlite.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = e.optJSONArray("indices")
            for (j in 0 until (indices?.length() ?: 0)) {
                sqlite.execSQL(indices!!.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = v1.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
        sqlite.execSQL(
            "INSERT INTO recipes (id, uid, title, rating, favorite, origin, createdAt, updatedAt) " +
                "VALUES (1, 'u1', 'Soup', 3, 1, 'MANUAL', 10, 20)",
        )
        sqlite.execSQL("INSERT INTO recipe_ingredients (recipeId, position, text, isHeader) VALUES (1, 0, '2 tomatoes', 0)")
        sqlite.execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Dinner', 0)")
        sqlite.execSQL("INSERT INTO shopping_items (text, checked, recipeId, position, createdAt) VALUES ('milk', 0, 1, 0, 5)")
        sqlite.version = 1
        sqlite.close()
    }

    @Test
    fun upgrade1To2_keepsExistingDataAndAddsAnEmptyPlanTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(tmp.root, "v1.db")
        createV1(file)

        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        db = upgraded

        assertEquals(listOf("Soup"), upgraded.recipeDao().allRecipes().map { it.title })
        assertEquals(listOf("2 tomatoes"), upgraded.recipeDao().allIngredients().map { it.text })
        assertEquals(listOf("Dinner"), upgraded.categoryDao().allCategories().map { it.name })
        assertEquals(listOf("milk"), upgraded.shoppingDao().getAll().map { it.text })
        assertEquals(emptyList<MealPlanEntryEntity>(), upgraded.mealPlanDao().getAll())

        // The new table works, including the cascade to the migrated recipe.
        upgraded.mealPlanDao().insert(MealPlanEntryEntity(date = "2026-11-23", recipeId = 1, createdAt = 1))
        assertEquals(1, upgraded.mealPlanDao().getAll().size)
        upgraded.recipeDao().delete(1)
        assertTrue(upgraded.mealPlanDao().getAll().isEmpty())
    }

    @Test
    fun schema2IsExported() {
        assertEquals(2, schema(2).getInt("version"))
    }
}
