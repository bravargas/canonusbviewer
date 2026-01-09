package com.brainer.canonusbviewer.model

import android.net.Uri

/**
 * Represents a photo retrieved from the MediaStore.
 *
 * @param uri The content URI of the photo.
 * @param dateAdded The timestamp when the photo was added to the MediaStore.
 */
data class MediaPhoto(
    val uri: Uri,
    val dateAdded: Long
)
