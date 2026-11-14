package com.postpole.spoonfile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** extraLarge (cards, sheet top, dialog, search bar, banners) = 28 dp; large = 24 dp. */
val SpoonfileShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // text field
    large = RoundedCornerShape(24.dp), // connected-list outer corners, ingredient box
    extraLarge = RoundedCornerShape(28.dp),
)

object SpoonfileShapeTokens {
    val ConnectedOuter = RoundedCornerShape(24.dp) // 20-28 dp
    val ConnectedInner = RoundedCornerShape(8.dp) // 6-8 dp, 2-3 dp gaps
    val Fab = RoundedCornerShape(22.dp) // 72 dp FAB
    val ExtendedFab = RoundedCornerShape(20.dp) // 64 dp tall
    val Pill = CircleShape // floating toolbar, buttons, chips
    val TextField = RoundedCornerShape(12.dp)

    /** Recipe photo arch for a 328x112 dp image. */
    val PhotoArch = RoundedCornerShape(topStart = 91.dp, topEnd = 91.dp, bottomEnd = 21.dp, bottomStart = 21.dp)
}

/** Expressive polygon shapes. Category tiles (96 dp) use [categoryTiles] in order. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object SpoonfilePolygons {
    /** Step number: 36 dp on detail, 120 dp in cook mode. */
    @Composable
    fun stepNumber(): Shape = MaterialShapes.Cookie9Sided.toShape()

    /** Recipe thumbnails, 44-56 dp. */
    @Composable
    fun thumbnail(): Shape = MaterialShapes.Cookie12Sided.toShape()

    /** Category tiles (96 dp), in order. */
    @Composable
    fun categoryTiles(): List<Shape> = listOf(
        MaterialShapes.Cookie9Sided.toShape(),
        MaterialShapes.Clover4Leaf.toShape(),
        MaterialShapes.Flower.toShape(),
        MaterialShapes.Cookie6Sided.toShape(),
        MaterialShapes.Cookie12Sided.toShape(),
        MaterialShapes.Circle.toShape(),
    )
}
