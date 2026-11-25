package com.steve.junker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.steve.junker.data.api.GolfCourseApiService
import com.steve.junker.data.api.ApiSearchResponse
import com.steve.junker.data.models.Course
import com.steve.junker.data.models.HoleScore
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Team
import com.steve.junker.data.repository.JunkerRepository
import org.json.JSONArray
import org.json.JSONObject

class GameViewModel : ViewModel() {
    private var repository: JunkerRepository? = null
    private val apiService = GolfCourseApiService.create()

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleTheme() { _isDarkMode.update { !it } }

    private val _apiSearchResults = MutableStateFlow<List<ApiSearchResponse>>(emptyList())
    val apiSearchResults: StateFlow<List<ApiSearchResponse>> = _apiSearchResults.asStateFlow()

    private val _isApiLoading = MutableStateFlow(false)
    val isApiLoading: StateFlow<Boolean> = _isApiLoading.asStateFlow()

    private val _apiErrorMessage = MutableStateFlow("")
    val apiErrorMessage: StateFlow<String> = _apiErrorMessage.asStateFlow()

    fun clearApiError() { _apiErrorMessage.value = "" }

    private var currentSessionRoundId: String? = null

    fun searchRemoteCourses(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isApiLoading.value = true
            _apiErrorMessage.value = ""
            try {
                val responseEnvelope = withContext(Dispatchers.IO) {
                    apiService.searchCourses(query)
                }
                _apiSearchResults.value = responseEnvelope.courses ?: emptyList()
            } catch (e: Exception) {
                _apiErrorMessage.value = "Search Failed: Check network or API availability."
            } finally {
                _isApiLoading.value = false
            }
        }
    }

    fun fetchAndSaveRemoteCourse(courseId: Int, onComplete: (Course) -> Unit) {
        viewModelScope.launch {
            _isApiLoading.value = true
            _apiErrorMessage.value = ""
            try {
                val detailResponse = withContext(Dispatchers.IO) {
                    apiService.getCourseDetails(courseId)
                }
                
                val innerCourseData = detailResponse.course
                val maleTeeBoxes = innerCourseData?.tees?.male
                
                var targetTeeBox = maleTeeBoxes?.find { it.tee_name?.equals("Blue", ignoreCase = true) == true }
                
                if (targetTeeBox == null) {
                    targetTeeBox = maleTeeBoxes?.firstOrNull() ?: innerCourseData?.tees?.female?.firstOrNull()
                }
                
                if (targetTeeBox == null || targetTeeBox.holes.isNullOrEmpty()) {
                    _apiErrorMessage.value = "Course found, but no matching tee details are available."
                    return@launch
                }

                val handicaps = targetTeeBox.holes!!.map { it.handicap }
                val pars = targetTeeBox.holes!!.map { it.par }

                val downloadedCourse = Course(
                    id = java.util.UUID.randomUUID().toString(),
                    name = innerCourseData?.course_name ?: "Unknown Course",
                    holeHandicaps = handicaps,
                    holePars = pars
                )

                saveCourseToDb(downloadedCourse)
                onComplete(downloadedCourse)
            } catch (e: Exception) {
                _apiErrorMessage.value = "Failed to download course parameters."
            } finally {
                _isApiLoading.value = false
            }
        }
    }

    private val _useTeams = MutableStateFlow(false)
    val useTeams: StateFlow<Boolean> = _useTeams.asStateFlow()

    private val _teams = MutableStateFlow<List<Team>>(emptyList())
    val teams: StateFlow<List<Team>> = _teams.asStateFlow()

    fun setUseTeams(enabled: Boolean) { _useTeams.value = enabled }
    fun configureTeams(teamList: List<Team>) { _teams.value = teamList }

    private val _savedPlayers = MutableStateFlow<List<Player>>(emptyList())
    val savedPlayers: StateFlow<List<Player>> = _savedPlayers.asStateFlow()

    private val _savedCourses = MutableStateFlow<List<Course>>(emptyList())
    val savedCourses: StateFlow<List<Course>> = _savedCourses.asStateFlow()

    private val _activePlayers = MutableStateFlow<List<Player>>(emptyList())
    val activePlayers: StateFlow<List<Player>> = _activePlayers.asStateFlow()

    private val _selectedCourse = MutableStateFlow<Course?>(null)
    val selectedCourse: StateFlow<Course?> = _selectedCourse.asStateFlow()

    private val _currentHole = MutableStateFlow(1)
    val currentHole: StateFlow<Int> = _currentHole.asStateFlow()

    private val _pressHoles = MutableStateFlow<List<Int>>(emptyList())
    val pressHoles: StateFlow<List<Int>> = _pressHoles.asStateFlow()

    private val _scores = MutableStateFlow<Map<String, List<HoleScore>>>(emptyMap())
    val scores: StateFlow<Map<String, List<HoleScore>>> = _scores.asStateFlow()

    private val _isRoundLocked = MutableStateFlow(false)
    val isRoundLocked: StateFlow<Boolean> = _isRoundLocked.asStateFlow()

    private var currentDeviceId: String = ""
    private var activeRoundCreatorDeviceId: String? = null

    fun setCurrentDeviceId(id: String) { currentDeviceId = id }
    fun getCurrentDeviceId(): String = currentDeviceId

    private val _isPlayersLoading = MutableStateFlow(false)
    val isPlayersLoading: StateFlow<Boolean> = _isPlayersLoading.asStateFlow()

    private val _isCoursesLoading = MutableStateFlow(false)
    val isCoursesLoading: StateFlow<Boolean> = _isCoursesLoading.asStateFlow()

    fun refreshPlayers() {
        viewModelScope.launch {
            _isPlayersLoading.value = true
            try {
                repository?.refreshPlayers()
            } catch (e: Exception) {
                // repo logs error
            } finally {
                _isPlayersLoading.value = false
            }
        }
    }

    fun refreshCourses() {
        viewModelScope.launch {
            _isCoursesLoading.value = true
            try {
                repository?.refreshCourses()
            } catch (e: Exception) {
                // repo logs error
            } finally {
                _isCoursesLoading.value = false
            }
        }
    }

    fun initRepository(repo: JunkerRepository) {
        this.repository = repo
        
        viewModelScope.launch {
            repository?.getAllPlayers()?.collect { list ->
                _savedPlayers.value = list
            }
        }
        
        viewModelScope.launch {
            repository?.getAllCourses()?.collect { list ->
                _savedCourses.value = list
            }
        }

        viewModelScope.launch {
            repository?.getAllRounds()?.collect { list ->
                _savedRounds.value = list
            }
        }

        refreshPlayers()
        refreshCourses()
    }

    fun savePlayerToDb(player: Player) {
        viewModelScope.launch { repository?.savePlayer(player) }
    }

    fun deletePlayerFromDb(player: Player) {
        viewModelScope.launch { repository?.deletePlayer(player) }
    }

    fun saveCourseToDb(course: Course) {
        viewModelScope.launch { repository?.saveCourse(course) }
    }

    // FIX: Core parameters extended to safely bind team configurations on state initialization
    fun setupGame(players: List<Player>, course: Course, matchInTeamMode: Boolean = false, activeTeamsList: List<Team> = emptyList()) {
        _activePlayers.value = players
        _selectedCourse.value = course
        _pressHoles.value = emptyList()
        _currentHole.value = 1
        _useTeams.value = matchInTeamMode
        _teams.value = activeTeamsList
        _isRoundLocked.value = false
        activeRoundCreatorDeviceId = currentDeviceId
        
        currentSessionRoundId = "round_${java.util.UUID.randomUUID()}"
        
        val initialMap = players.associate { player -> player.id to List(18) { HoleScore() } }
        _scores.value = initialMap
    }

    fun setCurrentHole(hole: Int) { if (hole in 1..18) _currentHole.value = hole }
    fun callPress(holeNumber: Int) { _pressHoles.update { current -> if (current.contains(holeNumber)) current else current + holeNumber } }

    private fun computeWheeledNetScore(grossScore: Int, holeIndex: Int, playerId: String): Int {
        if (grossScore == 0) return 0
        val currentCourse = _selectedCourse.value ?: return grossScore
        val activeField = _activePlayers.value
        if (activeField.isEmpty()) return grossScore

        val lowestHandicapInField = activeField.minOf { it.handicap }
        val targetPlayer = activeField.find { it.id == playerId } ?: return grossScore
        val wheeledHandicap = targetPlayer.handicap - lowestHandicapInField

        val holeHandicapRating = currentCourse.holeHandicaps.getOrNull(holeIndex) ?: 18
        var netScore = grossScore

        if (wheeledHandicap >= holeHandicapRating) netScore -= 1
        if ((wheeledHandicap - 18) >= holeHandicapRating) netScore -= 1
        if ((wheeledHandicap - 36) >= holeHandicapRating) netScore -= 1

        return netScore
    }

    fun updateGrossScore(playerId: String, holeNumber: Int, score: Int) {
        _scores.update { currentMap ->
            val updatedMap = currentMap.toMutableMap()
            val existingIndex = holeNumber - 1
            
            val playerList = updatedMap[playerId]?.toMutableList() ?: MutableList(18) { HoleScore() }
            val currentHoleScore = playerList.getOrNull(existingIndex) ?: HoleScore()
            
            playerList[existingIndex] = currentHoleScore.copy(grossScore = score)
            updatedMap[playerId] = playerList

            updatedMap.keys.forEach { pId ->
                val list = updatedMap[pId]?.toMutableList() ?: MutableList(18) { HoleScore() }
                for (i in 0..17) {
                    val entry = list[i]
                    if (entry.grossScore > 0) {
                        val computedNet = computeWheeledNetScore(entry.grossScore, i, pId)
                        list[i] = entry.copy(netScore = computedNet)
                    }
                }
                updatedMap[pId] = list
            }
            updatedMap
        }
    }

    fun toggleJunkDot(playerId: String, holeNumber: Int, dotType: String) {
        _scores.update { currentMap ->
            val updatedMap = currentMap.toMutableMap()
            val existingIndex = holeNumber - 1

            if (dotType == "hasDrive") {
                updatedMap.keys.forEach { pId ->
                    val list = updatedMap[pId]?.toMutableList() ?: MutableList(18) { HoleScore() }
                    val hScore = list[existingIndex]
                    list[existingIndex] = hScore.copy(hasDrive = if (pId == playerId) !hScore.hasDrive else false)
                    updatedMap[pId] = list
                }
            } else if (dotType == "hasCtp") {
                updatedMap.keys.forEach { pId ->
                    val list = updatedMap[pId]?.toMutableList() ?: MutableList(18) { HoleScore() }
                    val hScore = list[existingIndex]
                    list[existingIndex] = hScore.copy(hasCtp = if (pId == playerId) !hScore.hasCtp else false)
                    updatedMap[pId] = list
                }
            } else {
                val playerList = updatedMap[playerId]?.toMutableList() ?: MutableList(18) { HoleScore() }
                val hScore = playerList[existingIndex]
                
                playerList[existingIndex] = when (dotType) {
                    "hasSandy" -> hScore.copy(hasSandy = !hScore.hasSandy)
                    "hasPoley" -> hScore.copy(hasPoley = !hScore.hasPoley)
                    "hasDoublePoley" -> hScore.copy(hasDoublePoley = !hScore.hasDoublePoley)
                    "hasLostBoy" -> hScore.copy(hasLostBoy = !hScore.hasLostBoy)
                    "hasJacques" -> hScore.copy(hasJacques = !hScore.hasJacques)
                    else -> hScore
                }
                updatedMap[playerId] = playerList
            }
            updatedMap
        }
    }

    private val _savedRounds = MutableStateFlow<List<com.steve.junker.data.db.GameRoundEntity>>(emptyList())
    val savedRounds: StateFlow<List<com.steve.junker.data.db.GameRoundEntity>> = _savedRounds.asStateFlow()

    fun archiveCurrentRound(finalDotsMap: Map<String, Int>, isLocked: Boolean = false) {
        val currentCourse = selectedCourse.value ?: return
        val currentPlayers = activePlayers.value
        val currentTeams = teams.value
        val isTeamMode = useTeams.value
        val currentScoresMap = scores.value
        
        if (currentPlayers.isEmpty()) return
        
        if (currentSessionRoundId == null) {
            currentSessionRoundId = "round_${currentCourse.name.hashCode()}_${currentPlayers.hashCode()}"
        }

        viewModelScope.launch(Dispatchers.IO) {
            val finalSummaryString = if (isTeamMode && currentTeams.isNotEmpty()) {
                currentTeams.map { team ->
                    "${team.name} (${finalDotsMap[team.id] ?: 0} dots)"
                }.joinToString("\n")
            } else {
                currentPlayers.map { p ->
                    "${p.name} (${finalDotsMap[p.id] ?: 0} dots)"
                }.joinToString(", ")
            }

            if (isLocked) {
                _isRoundLocked.value = true
            }
            val rootContainerJsonObject = JSONObject()
            rootContainerJsonObject.put("isTeamMode", isTeamMode)
            rootContainerJsonObject.put("isLocked", isLocked || _isRoundLocked.value)
            rootContainerJsonObject.put("creatorDeviceId", activeRoundCreatorDeviceId ?: currentDeviceId)
            val pressHolesArray = JSONArray()
            pressHoles.value.forEach { pressHolesArray.put(it) }
            rootContainerJsonObject.put("pressHoles", pressHolesArray)

            val scoresJsonObject = JSONObject()
            currentScoresMap.forEach { (playerId, holeList) ->
                val holeArray = JSONArray()
                holeList.forEach { score ->
                    val scoreObj = JSONObject()
                    scoreObj.put("grossScore", score.grossScore)
                    scoreObj.put("netScore", score.netScore)
                    scoreObj.put("hasDrive", score.hasDrive)
                    scoreObj.put("hasSandy", score.hasSandy)
                    scoreObj.put("hasPoley", score.hasPoley)
                    scoreObj.put("hasDoublePoley", score.hasDoublePoley)
                    scoreObj.put("hasJacques", score.hasJacques)
                    scoreObj.put("hasLostBoy", score.hasLostBoy)
                    scoreObj.put("hasCtp", score.hasCtp)
                    holeArray.put(scoreObj)
                }
                scoresJsonObject.put(playerId, holeArray)
            }
            rootContainerJsonObject.put("playerScoresMap", scoresJsonObject)

            val newRoundRecord = com.steve.junker.data.db.GameRoundEntity(
                id = currentSessionRoundId!!,
                dateLong = System.currentTimeMillis(),
                courseName = currentCourse.name,
                playerNamesWithDots = finalSummaryString,
                scoreSummary = "18-Hole Side Action Matrix Status Updated",
                rawScoresJson = rootContainerJsonObject.toString()
            )
            repository?.saveRound(newRoundRecord)
        }
    }

    fun deleteRoundFromHistory(round: com.steve.junker.data.db.GameRoundEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository?.deleteRound(round)
        }
    }
    
    fun restoreRoundFromHistory(
        roundId: String, 
        courseName: String, 
        onRestored: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedCoursesList = repository?.getAllCourses()?.first() ?: emptyList()
            val targetCourseEntity = savedCoursesList.find { it.name == courseName }
            
            val restoredCourse = targetCourseEntity ?: Course("", courseName, List(18) { 18 }, List(18) { 4 })

            val savedRoundsList = repository?.getAllRounds()?.first() ?: emptyList()
            val targetRound = savedRoundsList.find { it.id == roundId }
            
            val reconstructedPlayers = mutableListOf<Player>()
            val reconstructedTeams = mutableListOf<Team>()
            val reconstructedScores = mutableMapOf<String, List<HoleScore>>()
            var parsedAsTeams = false
            val restoredPressHoles = mutableListOf<Int>()
            var restoredIsLocked = false
            var restoredCreatorDeviceId = ""
 
            if (targetRound != null) {
                val directory = _savedPlayers.value
 
                try {
                    val rootJson = JSONObject(targetRound.rawScoresJson)
                    parsedAsTeams = rootJson.optBoolean("isTeamMode", false)
                    restoredIsLocked = rootJson.optBoolean("isLocked", false)
                    restoredCreatorDeviceId = rootJson.optString("creatorDeviceId", "")
                    if (rootJson.has("pressHoles")) {
                        val arr = rootJson.getJSONArray("pressHoles")
                        for (i in 0 until arr.length()) {
                            restoredPressHoles.add(arr.getInt(i))
                        }
                    } else if (rootJson.has("pressHole")) {
                        val v = rootJson.getInt("pressHole")
                        if (v > 0) restoredPressHoles.add(v)
                    }
                    
                    val jsonScores = if (rootJson.has("playerScoresMap")) {
                        rootJson.getJSONObject("playerScoresMap")
                    } else {
                        rootJson
                    }

                    jsonScores.keys().forEach { playerId ->
                        val jsonArray = jsonScores.getJSONArray(playerId)
                        val holeList = MutableList(18) { HoleScore() }
                        
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            holeList[i] = HoleScore(
                                grossScore = obj.optInt("grossScore", 0),
                                netScore = obj.optInt("netScore", 0),
                                hasDrive = obj.optBoolean("hasDrive", false),
                                hasSandy = obj.optBoolean("hasSandy", false),
                                hasPoley = obj.optBoolean("hasPoley", false),
                                hasDoublePoley = obj.optBoolean("hasDoublePoley", false),
                                hasJacques = obj.optBoolean("hasJacques", false),
                                hasLostBoy = obj.optBoolean("hasLostBoy", false),
                                hasCtp = obj.optBoolean("hasCtp", false)
                            )
                        }
                        reconstructedScores[playerId] = holeList
                        
                        val matchedPlayer = directory.find { it.id == playerId }
                        if (matchedPlayer != null) {
                            reconstructedPlayers.add(matchedPlayer)
                        }
                    }
                } catch (e: Exception) { }

                if (parsedAsTeams) {
                    if (reconstructedPlayers.isEmpty()) {
                        reconstructedPlayers.addAll(directory.take(4))
                    }
                    if (reconstructedPlayers.size >= 4) {
                        val t1Players = listOf(reconstructedPlayers[0], reconstructedPlayers[1])
                        val t2Players = listOf(reconstructedPlayers[2], reconstructedPlayers[3])
                        
                        reconstructedTeams.add(Team("team_1", "${t1Players[0].name} / ${t1Players[1].name}", t1Players.map { it.id }))
                        reconstructedTeams.add(Team("team_2", "${t2Players[0].name} / ${t2Players[1].name}", t2Players.map { it.id }))
                    }
                } else if (reconstructedPlayers.isEmpty()) {
                    val components = targetRound.playerNamesWithDots.split(",")
                    components.forEach { chunk ->
                        val cleanName = chunk.substringBefore(" (").trim()
                        val matchedPlayer = directory.find { it.name.equals(cleanName, ignoreCase = true) }
                        if (matchedPlayer != null && !reconstructedPlayers.any { it.id == matchedPlayer.id }) {
                            reconstructedPlayers.add(matchedPlayer)
                        }
                    }
                }
            }

            if (reconstructedPlayers.isEmpty()) {
                reconstructedPlayers.addAll(_savedPlayers.value.take(4))
            }
            if (reconstructedScores.isEmpty()) {
                reconstructedPlayers.forEach { player ->
                    reconstructedScores[player.id] = List(18) { HoleScore() }
                }
            }

            withContext(Dispatchers.Main) {
                currentSessionRoundId = roundId
                _selectedCourse.value = restoredCourse
                _useTeams.value = parsedAsTeams
                _teams.value = reconstructedTeams
                _activePlayers.value = reconstructedPlayers
                _scores.value = reconstructedScores
                _pressHoles.value = restoredPressHoles
                _currentHole.value = 1
                
                activeRoundCreatorDeviceId = if (restoredCreatorDeviceId.isNotEmpty()) restoredCreatorDeviceId else null
                val isDeviceAuthorized = restoredCreatorDeviceId.isEmpty() || restoredCreatorDeviceId == currentDeviceId
                _isRoundLocked.value = restoredIsLocked || !isDeviceAuthorized
                
                onRestored()
            }
        }
    }
    
}