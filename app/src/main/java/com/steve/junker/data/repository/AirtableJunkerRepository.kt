package com.steve.junker.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Course
import com.steve.junker.data.db.GameRoundEntity
import com.steve.junker.data.api.*

class AirtableJunkerRepository(
    private val context: Context? = null,
    private val apiService: AirtableApiService = AirtableApiService.create()
) : JunkerRepository {

    private val authHeader = "Bearer ${AirtableConfig.PERSONAL_ACCESS_TOKEN}"
    private val gson = Gson()
    private val prefs = context?.getSharedPreferences("junker_airtable_cache", Context.MODE_PRIVATE)

    private fun loadCachedPlayers(): List<Player> {
        val json = prefs?.getString("cached_players", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Player>>() {}.type
            gson.fromJson<List<Player>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedPlayers(players: List<Player>) {
        try {
            val json = gson.toJson(players)
            prefs?.edit()?.putString("cached_players", json)?.apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun loadCachedCourses(): List<Course> {
        val json = prefs?.getString("cached_courses", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Course>>() {}.type
            gson.fromJson<List<Course>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedCourses(courses: List<Course>) {
        try {
            val json = gson.toJson(courses)
            prefs?.edit()?.putString("cached_courses", json)?.apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun loadCachedRounds(): List<GameRoundEntity> {
        val json = prefs?.getString("cached_rounds", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<GameRoundEntity>>() {}.type
            gson.fromJson<List<GameRoundEntity>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCachedRounds(rounds: List<GameRoundEntity>) {
        try {
            val json = gson.toJson(rounds)
            prefs?.edit()?.putString("cached_rounds", json)?.apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    private val _playersFlow = MutableStateFlow<List<Player>>(loadCachedPlayers())
    private val _coursesFlow = MutableStateFlow<List<Course>>(loadCachedCourses())
    private val _roundsFlow = MutableStateFlow<List<GameRoundEntity>>(loadCachedRounds())

    init {
        CoroutineScope(Dispatchers.IO).launch {
            refreshPlayers()
            refreshCourses()
            refreshRounds()
        }
    }

    private suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 500,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                handleException(e)
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block()
    }

    override fun getAllPlayers(): Flow<List<Player>> = _playersFlow

    override suspend fun refreshPlayers() {
        withContext(Dispatchers.IO) {
            try {
                retryWithBackoff(times = 3, initialDelayMs = 500) {
                    var currentOffset: String? = null
                    val allRecords = mutableListOf<AirtableRecord<PlayerFields>>()
                    do {
                        val response = apiService.getPlayers(authHeader, offset = currentOffset)
                        allRecords.addAll(response.records)
                        currentOffset = response.offset
                    } while (!currentOffset.isNullOrBlank())

                    val list = allRecords.mapNotNull { record ->
                        val fields = record.fields
                        val id = fields.id
                        val name = fields.name
                        if (id != null && name != null) {
                            Player(id, name, fields.handicap ?: 0)
                        } else {
                            null
                        }
                    }
                    _playersFlow.value = list
                    saveCachedPlayers(list)
                }
            } catch (e: Exception) {
                handleException(e)
            }
        }
    }

    override suspend fun savePlayer(player: Player) {
        // Optimistically update local flow and cache immediately
        val currentList = _playersFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == player.id }
        if (index >= 0) {
            currentList[index] = player
        } else {
            currentList.add(player)
        }
        _playersFlow.value = currentList
        saveCachedPlayers(currentList)

        try {
            val fields = PlayerFields(player.id, player.name, player.handicap)
            val record = AirtableRecord(fields = fields)
            val request = AirtableUpsertRequest(
                performUpsert = AirtablePerformUpsert(fieldsToMergeOn = listOf("id")),
                records = listOf(record)
            )
            apiService.upsertPlayers(authHeader, request)
            refreshPlayers()
        } catch (e: Exception) {
            handleException(e)
        }
    }

    override suspend fun deletePlayer(player: Player) {
        // Optimistically update local flow and cache immediately
        val currentList = _playersFlow.value.filter { it.id != player.id }
        _playersFlow.value = currentList
        saveCachedPlayers(currentList)

        try {
            val response = apiService.getPlayers(authHeader, "{id}='${player.id}'")
            val record = response.records.firstOrNull()
            if (record != null && record.id != null) {
                apiService.deletePlayer(authHeader, record.id)
            }
            refreshPlayers()
        } catch (e: Exception) {
            handleException(e)
        }
    }

    override fun getAllCourses(): Flow<List<Course>> = _coursesFlow

    override suspend fun refreshCourses() {
        withContext(Dispatchers.IO) {
            try {
                retryWithBackoff(times = 3, initialDelayMs = 500) {
                    var currentOffset: String? = null
                    val allRecords = mutableListOf<AirtableRecord<CourseFields>>()
                    do {
                        val response = apiService.getCourses(authHeader, offset = currentOffset)
                        allRecords.addAll(response.records)
                        currentOffset = response.offset
                    } while (!currentOffset.isNullOrBlank())

                    val list = allRecords.mapNotNull { record ->
                        val fields = record.fields
                        val id = fields.id
                        val name = fields.name
                        if (id != null && name != null) {
                            val handicapsStr = fields.holeHandicaps ?: ""
                            val parsStr = fields.holePars ?: ""
                            val handicaps = if (handicapsStr.isBlank()) emptyList() else handicapsStr.split(",").map { it.toInt() }
                            val pars = if (parsStr.isBlank()) List(18) { 4 } else parsStr.split(",").map { it.toInt() }
                            Course(id, name, handicaps, pars)
                        } else {
                            null
                        }
                    }
                    _coursesFlow.value = list
                    saveCachedCourses(list)
                }
            } catch (e: Exception) {
                handleException(e)
            }
        }
    }

    override suspend fun saveCourse(course: Course) {
        val currentList = _coursesFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == course.id }
        if (index >= 0) {
            currentList[index] = course
        } else {
            currentList.add(course)
        }
        _coursesFlow.value = currentList
        saveCachedCourses(currentList)

        try {
            val handicapsString = course.holeHandicaps.joinToString(",")
            val parsString = course.holePars.joinToString(",")
            val fields = CourseFields(course.id, course.name, handicapsString, parsString)
            val record = AirtableRecord(fields = fields)
            val request = AirtableUpsertRequest(
                performUpsert = AirtablePerformUpsert(fieldsToMergeOn = listOf("id")),
                records = listOf(record)
            )
            apiService.upsertCourses(authHeader, request)
            refreshCourses()
        } catch (e: Exception) {
            handleException(e)
        }
    }

    override suspend fun deleteCourse(course: Course) {
        val currentList = _coursesFlow.value.filter { it.id != course.id }
        _coursesFlow.value = currentList
        saveCachedCourses(currentList)

        try {
            val response = apiService.getCourses(authHeader, "{id}='${course.id}'")
            val record = response.records.firstOrNull()
            if (record != null && record.id != null) {
                apiService.deleteCourse(authHeader, record.id)
            }
            refreshCourses()
        } catch (e: Exception) {
            handleException(e)
        }
    }

    override fun getAllRounds(): Flow<List<GameRoundEntity>> = _roundsFlow

    override suspend fun refreshRounds() {
        withContext(Dispatchers.IO) {
            try {
                retryWithBackoff(times = 3, initialDelayMs = 500) {
                    var currentOffset: String? = null
                    val allRecords = mutableListOf<AirtableRecord<GameRoundFields>>()
                    do {
                        val response = apiService.getRounds(authHeader, offset = currentOffset)
                        allRecords.addAll(response.records)
                        currentOffset = response.offset
                    } while (!currentOffset.isNullOrBlank())

                    val list = allRecords.mapNotNull { record ->
                        val fields = record.fields
                        val id = fields.id
                        if (id != null) {
                            GameRoundEntity(
                                id = id,
                                dateLong = fields.dateLong ?: 0L,
                                courseName = fields.courseName ?: "",
                                playerNamesWithDots = fields.playersNamesWithDots ?: "",
                                scoreSummary = fields.scoreSummary ?: "",
                                rawScoresJson = fields.rawScoresJson ?: ""
                            )
                        } else {
                            null
                        }
                    }.sortedByDescending { it.dateLong }
                    _roundsFlow.value = list
                    saveCachedRounds(list)
                }
            } catch (e: Exception) {
                handleException(e)
            }
        }
    }

    override suspend fun saveRound(round: GameRoundEntity) {
        val currentList = _roundsFlow.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == round.id }
        if (index >= 0) {
            currentList[index] = round
        } else {
            currentList.add(0, round)
        }
        _roundsFlow.value = currentList
        saveCachedRounds(currentList)

        try {
            val fields = GameRoundFields(
                id = round.id,
                dateLong = round.dateLong,
                courseName = round.courseName,
                playersNamesWithDots = round.playerNamesWithDots,
                scoreSummary = round.scoreSummary,
                rawScoresJson = round.rawScoresJson
            )
            val record = AirtableRecord(fields = fields)
            val request = AirtableUpsertRequest(
                performUpsert = AirtablePerformUpsert(fieldsToMergeOn = listOf("id")),
                records = listOf(record)
            )
            apiService.upsertRounds(authHeader, request)
            refreshRounds()
        } catch (e: Exception) {
            handleException(e)
        }
    }

    override suspend fun deleteRound(round: GameRoundEntity) {
        val currentList = _roundsFlow.value.filter { it.id != round.id }
        _roundsFlow.value = currentList
        saveCachedRounds(currentList)

        try {
            val response = apiService.getRounds(authHeader, "{id}='${round.id}'")
            val record = response.records.firstOrNull()
            if (record != null && record.id != null) {
                apiService.deleteRound(authHeader, record.id)
            }
            refreshRounds()
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun handleException(e: Exception) {
        if (e is retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            android.util.Log.e("AirtableRepo", "HTTP Error ${e.code()}: $errorBody", e)
        } else {
            android.util.Log.e("AirtableRepo", "Airtable error: ${e.message}", e)
        }
    }
}
