package com.brainer.canonusbviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brainer.canonusbviewer.model.MediaPhoto
import com.brainer.canonusbviewer.model.ZoomState
import com.brainer.canonusbviewer.ui.viewer.components.ThumbnailRow
import com.brainer.canonusbviewer.ui.viewer.components.ZoomableImage

@Composable
fun ViewerScreen(
    status: String,
    photos: List<MediaPhoto>,
    pagerState: PagerState,
    thumbnailListState: LazyListState,
    zoomStates: List<ZoomState>,
    overlayVisible: Boolean,
    onZoomStateChange: (Int, ZoomState) -> Unit,
    onToggleOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectIndex: (Int) -> Unit
) {
    val currentZoom = zoomStates.getOrNull(pagerState.currentPage) ?: ZoomState()
    val pagerScrollEnabled = currentZoom.scale <= 1.001f

    Box(Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos yet.\nOpen Canon Camera Connect and take a photo.")
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = pagerScrollEnabled
            ) { page ->
                ZoomableImage(
                    model = photos[page].uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    zoomState = zoomStates[page],
                    onZoomStateChange = { onZoomStateChange(page, it) },
                    onSingleTap = { onToggleOverlay() }
                )
            }
        }

        if (overlayVisible) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(12.dp)
                    .align(Alignment.TopCenter)
            ) {
                val current = photos.getOrNull(pagerState.currentPage)
                Text(
                    text = if (current != null) "File: ${current.displayName}" else "File: (none)",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = status,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onOpenSettings) { Text("Settings") }
            }

            if (photos.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    ThumbnailRow(
                        photos = photos,
                        currentIndex = pagerState.currentPage,
                        listState = thumbnailListState,
                        onSelectIndex = onSelectIndex
                    )
                }
            }
        }
    }
}
