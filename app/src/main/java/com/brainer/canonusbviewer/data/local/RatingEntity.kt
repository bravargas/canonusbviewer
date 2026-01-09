package com.brainer.canonusbviewer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratings")
data class RatingEntity(
    @PrimaryKey val mediaStoreId: Long,
    val rating: Int,
    val updatedAt: Long
)
