package com.postpole.spoonfile.ui.mealplan

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.postpole.spoonfile.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `meal_plan_shopping_added` is a plural, so a count of 1 must not read "1 items" in any locale. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShoppingAddedPluralTest {
    private fun text(count: Int): String =
        ApplicationProvider.getApplicationContext<Context>().resources
            .getQuantityString(R.plurals.meal_plan_shopping_added, count, count)

    @Test @Config(qualifiers = "en")
    fun english() {
        assertEquals("Added 1 item to your shopping list", text(1))
        assertEquals("Added 2 items to your shopping list", text(2))
    }

    @Test @Config(qualifiers = "de")
    fun german() {
        assertEquals("1 Eintrag zur Einkaufsliste hinzugefügt", text(1))
        assertEquals("2 Einträge zur Einkaufsliste hinzugefügt", text(2))
    }

    @Test @Config(qualifiers = "fr")
    fun french() {
        assertEquals("1 élément ajouté à votre liste de courses", text(1))
        assertEquals("2 éléments ajoutés à votre liste de courses", text(2))
    }

    @Test @Config(qualifiers = "es")
    fun spanish() {
        assertEquals("1 elemento añadido a tu lista de la compra", text(1))
        assertEquals("2 elementos añadidos a tu lista de la compra", text(2))
    }

    @Test @Config(qualifiers = "it")
    fun italian() {
        assertEquals("1 elemento aggiunto alla tua lista della spesa", text(1))
        assertEquals("2 elementi aggiunti alla tua lista della spesa", text(2))
    }

    @Test @Config(qualifiers = "pt-rBR")
    fun portugueseBrazil() {
        assertEquals("1 item adicionado à sua lista de compras", text(1))
        assertEquals("2 itens adicionados à sua lista de compras", text(2))
    }
}
