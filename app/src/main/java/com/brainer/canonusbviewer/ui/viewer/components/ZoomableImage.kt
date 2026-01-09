package com.brainer.canonusbviewer.ui.viewer.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.brainer.canonusbviewer.model.ZoomState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    zoomState: ZoomState,
    onZoomStateChange: (ZoomState) -> Unit,
    onSingleTap: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim = remember(model) { Animatable(zoomState.scale) }
    val offsetXAnim = remember(model) { Animatable(zoomState.offset.x) }
    val offsetYAnim = remember(model) { Animatable(zoomState.offset.y) }

    fun clampOffset(s: Float, o: Offset): Offset {
        if (containerSize.width == 0 || containerSize.height == 0) return Offset.Zero
        if (s <= 1f) return Offset.Zero

        val maxX = (containerSize.width * (s - 1f)) / 2f
        val maxY = (containerSize.height * (s - 1f)) / 2f

        return Offset(
            o.x.coerceIn(-maxX, maxX),
            o.y.coerceIn(-maxY, maxY)
        )
    }

    suspend fun animateTo(targetScale: Float, targetOffset: Offset) {
        coroutineScope {
            launch { scaleAnim.animateTo(targetScale, tween(220)) }
            launch { offsetXAnim.animateTo(targetOffset.x, tween(220)) }
            launch { offsetYAnim.animateTo(targetOffset.y, tween(220)) }
        }
        onZoomStateChange(ZoomState(scaleAnim.value, Offset(offsetXAnim.value, offsetYAnim.value)))
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .onSizeChanged { containerSize = it }
            .pointerInput(zoomState) { // Re-create gesture detector when zoomState changes
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tap ->
                        val targetScale = if (zoomState.scale > 1.05f) 1f else 2f // Use tolerance
                        if (targetScale == 1f) {
                            scope.launch { animateTo(1f, Offset.Zero) }
                        } else {
                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            val tapFromCenter = tap - center
                            val scaleRatio = targetScale / zoomState.scale
                            val newOffset = zoomState.offset - tapFromCenter * (scaleRatio - 1f)
                            scope.launch { animateTo(targetScale, clampOffset(targetScale, newOffset)) }
                        }
                    }
                )
            }
            .pointerInput(zoomState.scale > 1.05f) { // Attach/detach drag gesture
                 if (zoomState.scale > 1.05f) {
                    detectDragGestures {
                        change, dragAmount ->
                            change.consume()
                            val newOffset = zoomState.offset + dragAmount
                            onZoomStateChange(zoomState.copy(offset = clampOffset(zoomState.scale, newOffset)))
                    }
                 }
            }
            .graphicsLayer {
                scaleX = zoomState.scale
                scaleY = zoomState.scale
                translationX = zoomState.offset.x
                translationY = zoomState.offset.y
            }
    )
}
