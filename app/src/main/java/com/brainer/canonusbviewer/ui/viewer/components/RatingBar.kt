package com.brainer.canonusbviewer.ui.viewer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun RatingBar(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        (1..5).forEach { star ->
            val isSelected = star <= rating
            Icon(
                imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (isSelected) "Selected star" else "Unselected star",
                tint = if (isSelected) Color.Yellow else Color.White,
                modifier = Modifier.clickable { onRatingChange(if (rating == star) 0 else star) }
            )
        }
    }
}
