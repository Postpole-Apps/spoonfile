package com.postpole.spoonfile.ui.mealplan

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.R
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `meal_plan_shopping_added` must be a plural so a count of 1 reads singular in every locale. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MealPlanShoppingAddedPluralsTest {
    private fun text(tag: String, count: Int): String {
        val base: Context = ApplicationProvider.getApplicationContext()
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config).resources
            .getQuantityString(R.plurals.meal_plan_shopping_added, count, count)
    }

    @Test fun english() {
        assertEquals("1 item added to your shopping list", text("en", 1))
        assertEquals("2 items added to your shopping list", text("en", 2))
    }

    @Test fun portugueseBrazil() {
        assertEquals("1 item adicionado à sua lista de compras", text("pt-BR", 1))
        assertEquals("2 itens adicionados à sua lista de compras", text("pt-BR", 2))
    }

    @Test fun italian() {
        assertEquals("1 elemento aggiunto alla tua lista della spesa", text("it", 1))
        assertEquals("2 elementi aggiunti alla tua lista della spesa", text("it", 2))
    }

    @Test fun german() {
        assertEquals("1 Eintrag zur Einkaufsliste hinzugefügt", text("de", 1))
        assertEquals("2 Einträge zur Einkaufsliste hinzugefügt", text("de", 2))
    }

    @Test fun spanish() {
        assertEquals("1 elemento añadido a tu lista de la compra", text("es", 1))
        assertEquals("2 elementos añadidos a tu lista de la compra", text("es", 2))
    }

    @Test fun french() {
        assertEquals("1 élément ajouté à votre liste de courses", text("fr", 1))
        assertEquals("2 éléments ajoutés à votre liste de courses", text("fr", 2))
    }
}
