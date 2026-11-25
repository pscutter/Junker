package com.steve.junker.data.repository

import kotlinx.coroutines.flow.Flow
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Course
import com.steve.junker.data.db.GameRoundEntity

interface JunkerRepository {
    fun getAllPlayers(): Flow<List<Player>>
    suspend fun refreshPlayers()
    suspend fun savePlayer(player: Player)
    suspend fun deletePlayer(player: Player)

    fun getAllCourses(): Flow<List<Course>>
    suspend fun refreshCourses()
    suspend fun saveCourse(course: Course)
    suspend fun deleteCourse(course: Course)

    fun getAllRounds(): Flow<List<GameRoundEntity>>
    suspend fun refreshRounds()
    suspend fun saveRound(round: GameRoundEntity)
    suspend fun deleteRound(round: GameRoundEntity)
}
