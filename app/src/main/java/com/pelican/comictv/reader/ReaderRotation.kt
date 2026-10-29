package com.pelican.comictv.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

internal data class ReaderSurfaceGeometry(val width: Int, val height: Int, val x: Int, val y: Int)

internal fun readerSurfaceGeometry(width: Int, height: Int, rotated: Boolean): ReaderSurfaceGeometry =
    if (rotated) ReaderSurfaceGeometry(height, width, (width - height) / 2, (height - width) / 2)
    else ReaderSurfaceGeometry(width, height, 0, 0)

/** Rotation is visual only: the remote keeps the normal portrait command order. */
@Suppress("UNUSED_PARAMETER")
internal fun readerLogicalDirection(direction: ToolDirection, rotated: Boolean): ToolDirection = direction

internal val LocalReaderRotated = staticCompositionLocalOf { false }

@Suppress("UNUSED_PARAMETER")
internal fun readerLogicalKey(key: Key, rotated: Boolean): Key = key

/** Measure the reader in its real logical viewport before rotating; never scale a landscape bitmap. */
@Composable internal fun ReaderSurface(rotated: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalReaderRotated provides rotated) {
        Layout(modifier = modifier.fillMaxSize(), content = { Box(Modifier.fillMaxSize()) { content() } }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val geometry = readerSurfaceGeometry(width, height, rotated)
            val reader = measurables.single().measure(Constraints.fixed(geometry.width, geometry.height))
            layout(width, height) {
                reader.placeWithLayer(geometry.x, geometry.y) { rotationZ = if (rotated) 90f else 0f }
            }
        }
    }
}
