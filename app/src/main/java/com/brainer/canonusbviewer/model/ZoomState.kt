package com.brainer.canonusbviewer.model

import androidx.compose.ui.geometry.Offset

/**
 * Represents the state of a zoomable image.
 *
 * @param scale The current zoom level.
 * @param offset The current pan offset.
 */
data class ZoomState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
)
