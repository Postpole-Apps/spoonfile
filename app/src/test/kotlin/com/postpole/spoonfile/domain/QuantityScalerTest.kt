package com.postpole.spoonfile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityScalerTest {
    private fun s(line: String, f: Double) = QuantityScaler.scale(line, f)

    @Test fun integer_doubles() = assertEquals("4 eggs", s("2 eggs", 2.0))
    @Test fun integer_halves_toFraction() = assertEquals("1/2 cup milk", s("1 cup milk", 0.5))
    @Test fun decimal_scales() = assertEquals("3 cups flour", s("1.5 cups flour", 2.0))
    @Test fun decimal_staysDecimal() = assertEquals("0.75 cup sugar", s("1.5 cup sugar", 0.5))
    @Test fun fraction_scales() = assertEquals("1/2 tsp salt", s("1/4 tsp salt", 2.0))
    @Test fun fraction_toMixed() = assertEquals("1 1/2 tsp salt", s("3/4 tsp salt", 2.0))
    @Test fun mixed_scales() = assertEquals("3 cups flour", s("1 1/2 cups flour", 2.0))
    @Test fun mixed_triple() = assertEquals("4 1/2 cups flour", s("1 1/2 cups flour", 3.0))
    @Test fun unicodeHalf() = assertEquals("1 cup rice", s("½ cup rice", 2.0))
    @Test fun unicodeQuarter() = assertEquals("1/2 cup rice", s("¼ cup rice", 2.0))
    @Test fun unicodeThreeQuarters() = assertEquals("1 1/2 tbsp oil", s("¾ tbsp oil", 2.0))
    @Test fun unicodeThird() = assertEquals("2/3 cup", s("⅓ cup", 2.0))
    @Test fun unicodeTwoThirds() = assertEquals("1 1/3 cups", s("⅔ cups", 2.0))
    @Test fun unicodeEighth() = assertEquals("1/4 tsp", s("⅛ tsp", 2.0))
    @Test fun integerPlusUnicode() = assertEquals("3 cups", s("1½ cups", 2.0))
    @Test fun range_scalesBothEnds() = assertEquals("4-6 tbsp butter", s("2-3 tbsp butter", 2.0))
    @Test fun range_enDash() = assertEquals("4–6 cloves", s("2–3 cloves", 2.0))
    @Test fun range_to() = assertEquals("4 to 6 cloves", s("2 to 3 cloves", 2.0))
    @Test fun nonNumeric_unchanged() = assertEquals("Salt to taste", s("Salt to taste", 2.0))
    @Test fun onlyLeadingQuantity() =
        assertEquals("4 cups water (about 2 cups broth)", s("2 cups water (about 2 cups broth)", 2.0))
    @Test fun factorOne_unchanged() = assertEquals("1 1/2 cups", s("1 1/2 cups", 1.0))
    @Test fun leadingWhitespacePreserved() = assertEquals("  4 eggs", s("  2 eggs", 2.0))
    @Test fun unitsNotConverted() = assertEquals("4 tbsp butter", s("2 tbsp butter", 2.0))
    @Test fun zeroDenominator_unchanged() = assertEquals("1/0 cup", s("1/0 cup", 2.0))
    @Test fun factor_fromServings() {
        assertEquals(1.5, QuantityScaler.factor(4.0, 6.0), 1e-9)
        assertEquals(1.0, QuantityScaler.factor(null, 6.0), 1e-9)
    }
}
