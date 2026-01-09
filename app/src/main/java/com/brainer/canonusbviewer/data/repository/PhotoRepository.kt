package com.brainer.canonusbviewer.data.repository

import com.brainer.canonusbviewer.data.local.RatingDao
import com.brainer.canonusbviewer.data.local.RatingEntity
import com.brainer.canonusbviewer.data.mediastore.MediaStoreDataSource
import com.brainer.canonusbviewer.model.Photo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class PhotoRepository(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val ratingDao: RatingDao
) {

    // A trigger to force the photo flow to re-combine with new ratings.
    private val ratingsUpdateTrigger = MutableStateFlow(0L)

    /**
     * Provides a flow of photos, combining MediaStore data with Room ratings.
     * It automatically re-emits the list when either the MediaStore content changes
     * or when a rating is updated via [setRating].
     */
    fun getPhotos(folderFilter: String): Flow<List<Photo>> {
        return mediaStoreDataSource.getPhotoFlow(folderFilter)
            .combine(ratingsUpdateTrigger) { mediaPhotos, _ ->
                val photoIds = mediaPhotos.map { it.id }
                val ratings = ratingDao.getRatings(photoIds).associateBy { it.mediaStoreId }

                mediaPhotos.map { mediaPhoto ->
                    Photo(
                        mediaStoreId = mediaPhoto.id,
                        uri = mediaPhoto.uri,
                        dateAdded = mediaPhoto.dateAdded,
                        rating = ratings[mediaPhoto.id]?.rating ?: 0
                    )
                }
            }
    }

    /**
     * Sets or updates the rating for a given photo and triggers the flow to update.
     */
    suspend fun setRating(mediaStoreId: Long, rating: Int) {
        withContext(Dispatchers.IO) {
            val entity = RatingEntity(
                mediaStoreId = mediaStoreId,
                rating = rating,
                updatedAt = System.currentTimeMillis()
            )
            ratingDao.upsertRating(entity)
            // Trigger the flow to be re-collected with the new rating.
            ratingsUpdateTrigger.value = System.currentTimeMillis()
        }
    }
}
