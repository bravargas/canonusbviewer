package com.brainer.canonusbviewer.model

import android.net.Uri

/**
 * A UI-ready model representing a photo, combining MediaStore data and its rating.
 *
 * @param mediaStoreId The ID of the photo in the MediaStore.
 * @param uri The content URI of the photo.
 * @param dateAdded The timestamp when the photo was added to the MediaStore.
 * @param rating The user-assigned rating (0-5).
 */
data class Photo(
    val mediaStoreId: Long,
    val uri: Uri,
    val dateAdded: Long,
    val rating: Int
)
