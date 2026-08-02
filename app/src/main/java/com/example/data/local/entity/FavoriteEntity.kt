package com.example.data.local.entity

import androidx.room.Entity

@Entity(tableName = "favorites", primaryKeys = ["userId", "psychologistId"])
data class FavoriteEntity(
    val userId: Int,
    val psychologistId: Int
)
