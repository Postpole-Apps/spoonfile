package com.postpole.spoonfile.ui.recipe

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.spoonfile.R
import com.postpole.spoonfile.ui.theme.SpoonfilePolygons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decodes [file] downsampled to roughly [maxEdgePx]; null while loading or when there is no photo. */
@Composable
private fun rememberPhotoBitmap(file: File?, maxEdgePx: Int): androidx.compose.ui.graphics.ImageBitmap? {
    val state = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, maxEdgePx) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                var sample = 1
                var edge = maxOf(bounds.outWidth, bounds.outHeight)
                while (edge / 2 >= maxEdgePx) {
                    sample *= 2
                    edge /= 2
                }
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.asImageBitmap()
            }
        }
    }
    return state.value
}

/** A recipe photo clipped to [shape]; shows a skillet placeholder when there is no photo. */
@Composable
fun RecipePhoto(
    file: File?,
    shape: Shape,
    modifier: Modifier = Modifier,
    maxEdgePx: Int = 1024,
) {
    val bitmap = rememberPhotoBitmap(file, maxEdgePx)
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_skillet),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** 0-5 star row. [onChange] null makes it read-only. Values are always kept within 0..5. */
@Composable
fun RatingStars(rating: Int, modifier: Modifier = Modifier, starSize: Dp = 20.dp, onChange: ((Int) -> Unit)? = null) {
    val value = rating.coerceIn(0, 5)
    Row(modifier) {
        for (i in 1..5) {
            val tint = if (i <= value) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
            val starModifier = if (onChange == null) {
                Modifier.size(starSize)
            } else {
                Modifier.size(maxOf(starSize, 40.dp)).clickable(role = Role.Button) {
                    onChange(if (i == value) 0 else i)
                }
            }
            Box(starModifier, contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = tint, modifier = Modifier.size(starSize))
            }
        }
    }
}

/** Scalloped step number. */
@Composable
fun StepBadge(
    number: Int,
    size: Dp,
    containerColor: Color,
    contentColor: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(size).clip(SpoonfilePolygons.stepNumber).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = textStyle, color = contentColor)
    }
}
