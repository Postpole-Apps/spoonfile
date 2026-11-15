package com.postpole.spoonfile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityScalerTest {
    private fun s(line: String, factor: Double) = QuantityScaler.scale(line, factor)

    @Test fun integer_doubles() = assertEquals("4 eggs", s("2 eggs", 2.0))

    @Test fun integer_halves_toFraction() = assertEquals("1/2 cup milk", s("1 cup milk", 0.5))

    @Test fun decimal_scales() = assertEquals("3 cups flour", s("1.5 cups flour", 2.0))

    @Test fun decimal_roundsToTwoPlaces() = assertEquals("1.21 kg rice", s("1.1 kg rice", 1.1))

    @Test fun simpleFraction_doubles() = assertEquals("1 tsp salt", s("1/2 tsp salt", 2.0))

    @Test fun simpleFraction_halves() = assertEquals("1/4 tsp salt", s("1/2 tsp salt", 0.5))

    @Test fun mixedNumber_doubles() = assertEquals("3 cups sugar", s("1 1/2 cups sugar", 2.0))

    @Test fun mixedNumber_triples() = assertEquals("4 1/2 cups sugar", s("1 1/2 cups sugar", 3.0))

    @Test fun unicodeHalf() = assertEquals("1 cup oats", s("½ cup oats", 2.0))

    @Test fun unicodeQuarterAndThreeQuarters() {
        assertEquals("1/2 tsp", s("¼ tsp", 2.0))
        assertEquals("1 1/2 tsp", s("¾ tsp", 2.0))
    }

    @Test fun unicodeThirds() {
        assertEquals("2/3 cup", s("⅓ cup", 2.0))
        assertEquals("1 1/3 cup", s("⅔ cup", 2.0))
    }

    @Test fun unicodeEighth() = assertEquals("1/4 tsp", s("⅛ tsp", 2.0))

    @Test fun wholePlusUnicode() = assertEquals("3 cups", s("1½ cups", 2.0))

    @Test fun wholePlusSpacedUnicode() = assertEquals("3 cups", s("1 ½ cups", 2.0))

    @Test fun range_scalesBothEnds() = assertEquals("4-6 cloves garlic", s("2-3 cloves garlic", 2.0))

    @Test fun range_enDashAndTo() {
        assertEquals("4–6 g", s("2–3 g", 2.0))
        assertEquals("4 to 6 g", s("2 to 3 g", 2.0))
    }

    @Test fun nonNumeric_unchanged() = assertEquals("Salt to taste", s("Salt to taste", 3.0))

    @Test fun onlyLeadingQuantityScaled() = assertEquals("4 (14 oz) cans beans", s("2 (14 oz) cans beans", 2.0))

    @Test fun trailingNumbersUntouched() = assertEquals("2 cup water at 350 degrees", s("1 cup water at 350 degrees", 2.0))

    @Test fun factorOne_returnsLineUnchanged() = assertEquals("½ cup oats", s("½ cup oats", 1.0))

    @Test fun preservesLeadingWhitespace() = assertEquals("  4 eggs", s("  2 eggs", 2.0))

    @Test fun unitAttached() = assertEquals("500g flour", s("250g flour", 2.0))

    @Test fun emptyLine_unchanged() = assertEquals("", s("", 2.0))
}
