package com.steve.junker.data.db

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.steve.junker.data.models.GameType

@Entity(tableName = "game_rounds")
data class GameRoundEntity(
    @PrimaryKey val id: String,
    val dateLong: Long,
    val courseName: String,
    val playerNamesWithDots: String,
    val scoreSummary: String,
    val rawScoresJson: String // Holds the complete serialized scoring matrix state
) {
    @Ignore
    var gameType: GameType = GameType.RAW_JUNKER

    @Ignore
    var isBlitzEnabled: Boolean = false

    @Ignore
    constructor(
        id: String,
        dateLong: Long,
        courseName: String,
        playerNamesWithDots: String,
        scoreSummary: String,
        rawScoresJson: String,
        gameType: GameType = GameType.RAW_JUNKER,
        isBlitzEnabled: Boolean = false
    ) : this(id, dateLong, courseName, playerNamesWithDots, scoreSummary, rawScoresJson) {
        this.gameType = gameType
        this.isBlitzEnabled = isBlitzEnabled
    }
}
