package com.brainer.canonusbviewer.ui.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.brainer.canonusbviewer.model.MediaPhoto

@Composable
fun ThumbnailRow(
    photos: List<MediaPhoto>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x88000000))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(photos, key = { _, item -> item.uri }) { index, item ->
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clickable { onSelectIndex(index) }
            ) {
                AsyncImage(
                    model = item.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (index == currentIndex) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x55FFFFFF))
                    )
                }
            }
        }
    }
}
