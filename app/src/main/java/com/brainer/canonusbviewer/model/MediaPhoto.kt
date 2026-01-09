package com.brainer.canonusbviewer.model

import android.net.Uri

/**
 * Represents a photo retrieved from the MediaStore.
 *
 * @param uri The content URI of the photo.
 * @param dateAdded The timestamp when the photo was added to the MediaStore.
 * @param displayName The display name of the file (e.g., "IMG_1234.JPG").
 */
data class MediaPhoto(
    val uri: Uri,
    val dateAdded: Long,
    val displayName: String
)
