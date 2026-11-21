package com.postpole.spoonfile.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a real version-1 database from the exported `1.json` schema, fills it, then opens it with the app's
 * Room database and `MIGRATION_1_2`. Room itself validates the migrated schema against `2.json` on open, so
 * a wrong table or index definition fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(DB)
    }

    private fun schemaFile(version: Int): File {
        val dir = "schemas/com.postpole.spoonfile.data.AppDatabase/$version.json"
        return listOf(File(dir), File("app/$dir")).first { it.isFile }
    }

    private fun createVersion1Database() {
        val database = JSONObject(schemaFile(1).readText()).getJSONObject("database")
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = database.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                db.execSQL("INSERT INTO recipes (id, uid, title, rating, favorite, origin, createdAt, updatedAt) VALUES (1, 'u1', 'Soup', 0, 0, 'MANUAL', 1, 1)")
                db.execSQL("INSERT INTO recipes (id, uid, title, rating, favorite, origin, createdAt, updatedAt) VALUES (2, 'u2', 'Salad', 0, 1, 'MANUAL', 1, 1)")
                db.execSQL("INSERT INTO recipe_ingredients (recipeId, position, text, isHeader) VALUES (1, 0, '2 tomatoes', 0)")
                db.execSQL("INSERT INTO categories (id, name, sortOrder) VALUES (1, 'Dinner', 0)")
                db.execSQL("INSERT INTO recipe_categories (recipeId, categoryId) VALUES (1, 1)")
                db.execSQL("INSERT INTO shopping_items (text, checked, recipeId, position, createdAt) VALUES ('milk', 0, 1, 0, 5)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB).callback(callback).build(),
        )
        helper.writableDatabase.close()
        helper.close()
    }

    @Test
    fun migrate1To2_keepsExistingRowsAndAddsAnEmptyMealPlanTable() = runBlocking {
        createVersion1Database()

        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, DB)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        db = upgraded

        assertEquals(listOf("Soup", "Salad"), upgraded.recipeDao().allRecipes().map { it.title })
        assertEquals(1, upgraded.recipeDao().allIngredients().size)
        assertEquals(1, upgraded.categoryDao().allCategories().size)
        assertEquals(1, upgraded.recipeDao().allCrossRefs().size)
        assertEquals(listOf("milk"), upgraded.shoppingDao().getAll().map { it.text })
        assertEquals(0, upgraded.mealPlanDao().countNow())

        // The new table works, and its recipe link cascades.
        upgraded.mealPlanDao().append("2026-11-21", recipeId = 1, now = 9)
        assertEquals(1, upgraded.mealPlanDao().countNow())
        upgraded.recipeDao().delete(1)
        assertEquals(0, upgraded.mealPlanDao().countNow())
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
