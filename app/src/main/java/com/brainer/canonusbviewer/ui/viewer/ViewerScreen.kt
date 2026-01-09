package com.brainer.canonusbviewer.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.brainer.canonusbviewer.model.Photo
import com.brainer.canonusbviewer.model.ZoomState
import com.brainer.canonusbviewer.ui.viewer.components.RatingBar
import com.brainer.canonusbviewer.ui.viewer.components.ThumbnailRow
import com.brainer.canonusbviewer.ui.viewer.components.ZoomableImage
import kotlinx.coroutines.delay

@Composable
fun ViewerScreen(
    status: String,
    photos: List<Photo>,
    pagerState: PagerState,
    zoomStates: List<ZoomState>,
    overlayVisible: Boolean,
    minRatingFilter: Int,
    onZoomStateChange: (Int, ZoomState) -> Unit,
    onToggleOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectIndex: (Int) -> Unit,
    onSetRating: (Long, Int) -> Unit,
    onSetMinRatingFilter: (Int) -> Unit
) {
    var lastUserActivity by remember { mutableStateOf(System.currentTimeMillis()) }

    fun bumpUserActivity() {
        lastUserActivity = System.currentTimeMillis()
    }

    LaunchedEffect(overlayVisible, lastUserActivity) {
        if (!overlayVisible) return@LaunchedEffect
        delay(2500L)

        val idleFor = System.currentTimeMillis() - lastUserActivity
        if (overlayVisible && idleFor >= 2500L) {
            onHideOverlay()
        }
    }

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
                    onSingleTap = { onToggleOverlay() },
                    onUserInteraction = { bumpUserActivity() }
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
                    text = if (current != null) "File: ${current.uri.lastPathSegment}" else "File: (none)",
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
                if (current != null) {
                    RatingBar(
                        rating = current.rating,
                        onRatingChange = { rating ->
                            bumpUserActivity()
                            onSetRating(current.mediaStoreId, rating)
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { bumpUserActivity(); onOpenSettings() }) { Text("Settings") }
                    Button(onClick = { 
                        bumpUserActivity()
                        onSetMinRatingFilter(if (minRatingFilter == 0) 1 else 0) 
                    }) {
                        Text(if (minRatingFilter == 0) "Filter > 0" else "All")
                    }
                }
            }

            if (photos.isNotEmpty()) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    ThumbnailRow(
                        photos = photos,
                        currentIndex = pagerState.currentPage,
                        onSelectIndex = {
                            bumpUserActivity()
                            onSelectIndex(it)
                        }
                    )
                }
            }
        }
    }
}
