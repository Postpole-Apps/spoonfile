package com.postpole.spoonfile.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** M3 shape roles. extraLarge: cards, sheet top, dialog, search bar, banners. large: connected-list outer corners. */
val SpoonfileShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp), // text field
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Connected groups: outer 20-28 dp, inner 6-8 dp, 2-3 dp gaps. */
val ConnectedOuterCorner = 24.dp
val ConnectedInnerCorner = 8.dp
val ConnectedGap = 3.dp

val FabShape: Shape = RoundedCornerShape(22.dp) // 72 dp FAB
val ExtendedFabShape: Shape = RoundedCornerShape(20.dp) // "Cook mode", 64 dp tall
val PillShape: Shape = CircleShape // floating toolbar, buttons, chips

/** Recipe photo arch, for a 328x112 dp image. */
val RecipePhotoShape: Shape = RoundedCornerShape(topStart = 91.dp, topEnd = 91.dp, bottomEnd = 21.dp, bottomStart = 21.dp)

/** Expressive polygon shapes (RoundedPolygon.toShape()). */
object SpoonfilePolygons {
    /** Category tiles (96 dp), in display order. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val categoryTiles: List<Shape>
        @androidx.compose.runtime.Composable get() = listOf(
            MaterialShapes.Cookie9Sided.toShape(),
            MaterialShapes.Clover4Leaf.toShape(),
            MaterialShapes.Flower.toShape(),
            MaterialShapes.Cookie6Sided.toShape(),
            MaterialShapes.Cookie12Sided.toShape(),
            MaterialShapes.Circle.toShape(),
        )

    /** Step number: 36 dp on detail, 120 dp in cook mode. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val stepNumber: Shape
        @androidx.compose.runtime.Composable get() = MaterialShapes.Cookie9Sided.toShape()

    /** Recipe thumbnails, 44-56 dp. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val thumbnail: Shape
        @androidx.compose.runtime.Composable get() = MaterialShapes.Cookie12Sided.toShape()
}
