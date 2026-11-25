package com.steve.junker.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Course
import com.steve.junker.data.db.PlayerDao
import com.steve.junker.data.db.CourseDao
import com.steve.junker.data.db.GameRoundDao
import com.steve.junker.data.db.PlayerEntity
import com.steve.junker.data.db.CourseEntity
import com.steve.junker.data.db.GameRoundEntity

class RoomJunkerRepository(
    private val playerDao: PlayerDao,
    private val courseDao: CourseDao,
    private val gameRoundDao: GameRoundDao
) : JunkerRepository {

    override fun getAllPlayers(): Flow<List<Player>> {
        return playerDao.getAllPlayers().map { entityList ->
            entityList.map { Player(it.id, it.name, it.handicap) }
        }
    }

    override suspend fun refreshPlayers() {}

    override suspend fun savePlayer(player: Player) {
        playerDao.insertPlayer(PlayerEntity(player.id, player.name, player.handicap))
    }

    override suspend fun deletePlayer(player: Player) {
        playerDao.deletePlayer(PlayerEntity(player.id, player.name, player.handicap))
    }

    override fun getAllCourses(): Flow<List<Course>> {
        return courseDao.getAllCourses().map { entityList ->
            entityList.map { entity ->
                val handicaps = if (entity.holeHandicaps.isBlank()) emptyList() else entity.holeHandicaps.split(",").map { it.toInt() }
                val pars = if (entity.holePars.isBlank()) List(18) { 4 } else entity.holePars.split(",").map { it.toInt() }
                Course(entity.id, entity.name, handicaps, pars)
            }
        }
    }

    override suspend fun refreshCourses() {}

    override suspend fun saveCourse(course: Course) {
        val handicapsString = course.holeHandicaps.joinToString(",")
        val parsString = course.holePars.joinToString(",")
        courseDao.insertCourse(CourseEntity(course.id, course.name, handicapsString, parsString))
    }

    override suspend fun deleteCourse(course: Course) {
        val handicapsString = course.holeHandicaps.joinToString(",")
        val parsString = course.holePars.joinToString(",")
        courseDao.deleteCourse(CourseEntity(course.id, course.name, handicapsString, parsString))
    }

    override fun getAllRounds(): Flow<List<GameRoundEntity>> {
        return gameRoundDao.getAllSavedRounds()
    }

    override suspend fun refreshRounds() {}

    override suspend fun saveRound(round: GameRoundEntity) {
        gameRoundDao.insertRound(round)
    }

    override suspend fun deleteRound(round: GameRoundEntity) {
        gameRoundDao.deleteRound(round)
    }
}
