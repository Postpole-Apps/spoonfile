package com.postpole.spoonfile.ui.recipe

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.postpole.spoonfile.ui.theme.ConnectedGap
import com.postpole.spoonfile.ui.theme.ConnectedInnerCorner
import com.postpole.spoonfile.ui.theme.ConnectedOuterCorner
import androidx.compose.foundation.shape.RoundedCornerShape

/** A recipe photo clipped to [shape]; a tonal placeholder shows while it loads or when there is none. */
@Composable
fun RecipePhoto(
    imageFile: String?,
    load: suspend (name: String, maxEdge: Int) -> ImageBitmap?,
    shape: Shape,
    modifier: Modifier = Modifier,
    maxEdge: Int = 640,
    contentDescription: String? = null,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageFile) {
        value = imageFile?.let { load(it, maxEdge) }
    }
    Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.clip(shape)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Soft fade so scrolling content is never half-covered by a floating toolbar. Solid from 42% of its height. */
@Composable
fun BottomFade(height: Dp, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.background) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.42f to color, 1f to color)),
    )
}

/** Outer corners 24 dp, inner 8 dp; used for connected lists (Add sheet, categories). */
fun connectedShape(index: Int, count: Int): Shape {
    val top = if (index == 0) ConnectedOuterCorner else ConnectedInnerCorner
    val bottom = if (index == count - 1) ConnectedOuterCorner else ConnectedInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}

val ConnectedListGap: Dp = ConnectedGap
