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

    @Test fun countable_garlicAtSixServingsOfFour_roundsToWhole() =
        assertEquals("5 garlic cloves", s("3 garlic cloves", 1.5))

    @Test fun countable_largeAmountsRoundToWhole() {
        assertEquals("8 large eggs", s("5 large eggs", 1.5))
        assertEquals("7 eggs", s("6 eggs", 1.2))
    }

    @Test fun countable_smallAmountsRoundToHalf() {
        assertEquals("1 1/2 onions", s("2 onions", 0.7))
        assertEquals("1/2 lemon", s("1 lemon", 0.4))
        assertEquals("1 1/2 eggs", s("3 eggs", 0.5))
    }

    @Test fun countable_neverRoundsToZero() = assertEquals("1/2 egg", s("1 egg", 0.1))

    @Test fun countable_rangeRoundsBothEnds() = assertEquals("5-8 sage leaves", s("3-5 sage leaves", 1.5))

    @Test fun countable_cloveBeforeIngredient() = assertEquals("5 cloves garlic", s("3 cloves garlic", 1.5))

    @Test fun measured_unitsStayExact() {
        assertEquals("4 1/2 cups flour", s("3 cups flour", 1.5))
        assertEquals("4 1/2 tbsp oil", s("3 tbsp oil", 1.5))
        assertEquals("4 1/2 Tbsp oil", s("3 Tbsp oil", 1.5))
    }

    @Test fun bracketedGrams_scaleWithMainQuantity() {
        assertEquals("4 cups flour (500 g)", s("2 cups flour (250 g)", 2.0))
        assertEquals("3 cups flour (375 g)", s("2 cups flour (250 g)", 1.5))
        assertEquals("3 cups milk (720 ml)", s("2 cups milk (480 ml)", 1.5))
        assertEquals("1 cups flour (125 g)", s("2 cups flour (250 g)", 0.5))
    }

    @Test fun bracketedGrams_smallValuesRoundToWhole() = assertEquals("3 tbsp butter (45 g)", s("2 tbsp butter (30 g)", 1.5))

    @Test fun bracketedAmount_onCountableItem() = assertEquals("5 garlic cloves (15 g)", s("3 garlic cloves (10 g)", 1.5))

    @Test fun bracketedOunces_useFractions() = assertEquals("4 cups stock (16 oz)", s("2 cups stock (8 oz)", 2.0))

    @Test fun bracketedWithoutLeadingQuantity_unchanged() = assertEquals("Butter (75 g)", s("Butter (75 g)", 2.0))

    @Test fun parenthesisAfterQuantity_keepsExactAmount() =
        assertEquals("3 (14 oz) cans beans", s("2 (14 oz) cans beans", 1.5))
}
