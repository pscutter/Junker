package com.steve.junker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRoundDao {
    @Query("SELECT * FROM game_rounds ORDER BY dateLong DESC")
    fun getAllSavedRounds(): Flow<List<GameRoundEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRound(round: GameRoundEntity)

    @androidx.room.Delete
    suspend fun deleteRound(round: GameRoundEntity)
}