package com.steve.junker.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_rounds")
data class GameRoundEntity(
    @PrimaryKey val id: String,
    val dateLong: Long,
    val courseName: String,
    val playerNamesWithDots: String,
    val scoreSummary: String,
    val rawScoresJson: String // Holds the complete serialized scoring matrix state
)