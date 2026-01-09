package com.brainer.canonusbviewer.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RatingDao {

    @Query("SELECT * FROM ratings WHERE mediaStoreId IN (:ids)")
    suspend fun getRatings(ids: List<Long>): List<RatingEntity>

    @Query("SELECT * FROM ratings WHERE mediaStoreId = :id")
    suspend fun getRating(id: Long): RatingEntity?

    @Upsert
    suspend fun upsertRating(entity: RatingEntity)
}
