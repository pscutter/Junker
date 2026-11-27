package com.steve.junker.data.models

enum class GameType {
    RAW_JUNKER,
    NINES
}

data class Player(val id: String, val name: String, val handicap: Int)

data class Course(val id: String, val name: String, val holeHandicaps: List<Int>, val holePars: List<Int>)

data class Team(
    val id: String,
    val name: String,
    val playerIds: List<String>
)

data class HoleScore(
    val grossScore: Int = 0,
    val netScore: Int = 0,
    val hasDrive: Boolean = false,
    val hasSandy: Boolean = false,
    val hasDoubleSandy: Boolean = false,
    val hasPoley: Boolean = false,
    val hasDoublePoley: Boolean = false,
    val hasLostBoy: Boolean = false,
    val hasJacques: Boolean = false,
    val hasCtp: Boolean = false
)

data class HoleCalculationResult(
    val basicJunkDots: Int = 0,
    val skinDotsWon: Int = 0,
    val ctpDotsWon: Int = 0
)

data class MatchCalculationOutput(
    val entityTotals: Map<String, Int>,
    val holeBreakdowns: Map<String, List<HoleCalculationResult>>
)

fun calculateJunkDots(
    scores: Map<String, List<HoleScore>>, 
    pressHoles: List<Int>, 
    course: Course?,
    teams: List<Team>?,
    useTeams: Boolean
): MatchCalculationOutput {
    
    val isTeamMode = useTeams && !teams.isNullOrEmpty()
    val entityKeys = if (isTeamMode) teams!!.map { it.id } else scores.keys
    
    val entityTotals = entityKeys.associateWith { 0 }.toMutableMap()
    val holeBreakdowns = entityKeys.associateWith { MutableList(18) { HoleCalculationResult() } }
    
    if (course == null) return MatchCalculationOutput(entityTotals, holeBreakdowns)
    
    var skinsCarryOverValue = 0
    var ctpCarryOverValue = 0
    
    fun getOwnerEntityId(playerId: String): String {
        if (!isTeamMode) return playerId
        return teams!!.find { it.playerIds.contains(playerId) }?.id ?: playerId
    }

    for (h in 1..18) {
        val multiplier = if (pressHoles.isEmpty()) 1 else {
            var m = 1
            pressHoles.sorted().forEach { pHole ->
                if (h >= pHole) m *= 2
            }
            m
        }
        val holePar = course.holePars.getOrNull(h - 1) ?: 4
        
        // Skins and CTP values are accumulated dynamically in their respective steps to support press timing changes
        
        val entityMinNets = mutableMapOf<String, Int>()
        var holeHasValidScores = false
        val ctpWinnersThisHole = mutableListOf<String>()
        
        // Track accumulated basic junk dots specifically on this hole to avoid overwrite issues
        val basicJunkThisHole = entityKeys.associateWith { 0 }.toMutableMap()
        
        scores.forEach { (playerId, playerHoleList) ->
            val hScore = playerHoleList.getOrNull(h - 1) ?: HoleScore()
            if (hScore.grossScore > 0) {
                holeHasValidScores = true
                val ownerId = getOwnerEntityId(playerId)
                
                val currentMin = entityMinNets[ownerId] ?: Int.MAX_VALUE
                if (hScore.netScore < currentMin) {
                    entityMinNets[ownerId] = hScore.netScore
                }
                
                var dotsOnHole = 0
                if (hScore.hasPoley) dotsOnHole += 1
                if (hScore.hasDoublePoley) dotsOnHole += 2
                if (hScore.hasDoubleSandy) dotsOnHole += 2
                
                val scoreRelationToPar = hScore.grossScore - holePar
                if (scoreRelationToPar == -1) dotsOnHole += 2
                else if (scoreRelationToPar <= -2) dotsOnHole += 5
                
                val madeParOrBetter = hScore.grossScore <= holePar
                if (madeParOrBetter) {
                    if (hScore.hasDrive) dotsOnHole += 1
                    if (hScore.hasSandy) dotsOnHole += 1
                    if (hScore.hasJacques) dotsOnHole += 1
                    if (hScore.hasLostBoy) dotsOnHole += 1
                    
                    if (holePar == 3 && hScore.hasCtp && !ctpWinnersThisHole.contains(ownerId)) {
                        ctpWinnersThisHole.add(ownerId)
                    }
                }
                
                // Keep a running tally of basic junk for this entity on this hole
                basicJunkThisHole[ownerId] = (basicJunkThisHole[ownerId] ?: 0) + (dotsOnHole * multiplier)
            }
        }
        
        // 1. Commit base junk values safely first
        entityKeys.forEach { entId ->
            val junkAmt = basicJunkThisHole[entId] ?: 0
            holeBreakdowns[entId]!![h - 1] = HoleCalculationResult(basicJunkDots = junkAmt)
            entityTotals[entId] = (entityTotals[entId] ?: 0) + junkAmt
        }
        
        // 2. Evaluate and apply skin rewards to the exact same cell matrix block cleanly
        val currentHoleSkinValue = 1 * multiplier
        if (holeHasValidScores && entityMinNets.isNotEmpty()) {
            val absoluteMinNet = entityMinNets.values.minOrNull() ?: Int.MAX_VALUE
            val prospectiveWinners = entityMinNets.filterValues { it == absoluteMinNet }.keys
            
            if (prospectiveWinners.size == 1) {
                val winnerId = prospectiveWinners.first()
                val totalSkinsAwarded = currentHoleSkinValue + skinsCarryOverValue
                
                val existingRes = holeBreakdowns[winnerId]!![h - 1]
                holeBreakdowns[winnerId]!![h - 1] = existingRes.copy(skinDotsWon = totalSkinsAwarded)
                entityTotals[winnerId] = (entityTotals[winnerId] ?: 0) + totalSkinsAwarded
                skinsCarryOverValue = 0 
            } else {
                skinsCarryOverValue += currentHoleSkinValue
            }
        } else {
            skinsCarryOverValue += currentHoleSkinValue
        }
        
        // 3. Evaluate and apply Par-3 Closest to Pin rewards (CTP carries over value-based skin dots when no one wins it)
        val currentCtpDotValue = 1 * multiplier
        if (holePar == 3) {
            if (ctpWinnersThisHole.size == 1) {
                val winnerId = ctpWinnersThisHole.first()
                val totalCtpAwarded = currentCtpDotValue + ctpCarryOverValue
                
                val existingRes = holeBreakdowns[winnerId]!![h - 1]
                holeBreakdowns[winnerId]!![h - 1] = existingRes.copy(ctpDotsWon = totalCtpAwarded)
                entityTotals[winnerId] = (entityTotals[winnerId] ?: 0) + totalCtpAwarded
                ctpCarryOverValue = 0
            } else {
                ctpCarryOverValue += currentCtpDotValue
            }
        }
    }
    
    // --- SIDE MATCH CALCULATIONS ---
    
    val entityFrontNets = entityKeys.associateWith { 0 }.toMutableMap()
    val entityBackNets = entityKeys.associateWith { 0 }.toMutableMap()
    
    entityKeys.forEach { entId ->
        var fNet = 0
        var bNet = 0
        for (i in 0..17) {
            val holeMembersNets = scores.filter { getOwnerEntityId(it.key) == entId }
                .mapNotNull { it.value.getOrNull(i) }
                .filter { it.grossScore > 0 }
                .map { it.netScore }
            
            if (holeMembersNets.isNotEmpty()) {
                val bestBallNet = holeMembersNets.minOrNull() ?: 0
                if (i < 9) fNet += bestBallNet else bNet += bestBallNet
            }
        }
        entityFrontNets[entId] = fNet
        entityBackNets[entId] = bNet
    }
    
    fun awardSideDot(netMap: Map<String, Int>, mult: Int) {
        if (netMap.isEmpty() || netMap.values.all { it == 0 }) return
        val minVal = netMap.values.minOrNull() ?: return
        val winners = netMap.filterValues { it == minVal }.keys
        if (winners.size == 1) {
            val w = winners.first()
            entityTotals[w] = (entityTotals[w] ?: 0) + (1 * mult)
        }
    }
    
    awardSideDot(entityFrontNets, 1)
    awardSideDot(entityBackNets, 1)
    
    val entityTotalNets = entityKeys.associateWith { (entityFrontNets[it] ?: 0) + (entityBackNets[it] ?: 0) }
    awardSideDot(entityTotalNets, 1)
    
    return MatchCalculationOutput(entityTotals, holeBreakdowns)
}

data class NinesCalculationOutput(
    val playerTotals: Map<String, Int>,
    val holePoints: Map<String, List<Int>>
)

/**
 * Calculates Nines (5-3-1 for 3 players / Sixteens 7-5-3-1 for 4 players) points per hole and 18-hole totals.
 * Evaluates each hole only when all active players have a grossScore > 0, using each player's wheeled netScore.
 */
fun calculateNinesPoints(
    scores: Map<String, List<HoleScore>>,
    activePlayers: List<Player>,
    isBlitzEnabled: Boolean
): NinesCalculationOutput {
    val playerTotals = activePlayers.associate { it.id to 0 }.toMutableMap()
    val holePoints = activePlayers.associate { it.id to MutableList(18) { 0 } }

    val baseRanks = when (activePlayers.size) {
        3 -> listOf(5, 3, 1)
        4 -> listOf(7, 5, 3, 1)
        else -> return NinesCalculationOutput(playerTotals, holePoints)
    }
    val totalHolePoints = baseRanks.sum()

    for (hIndex in 0..17) {
        val holeEntries = activePlayers.map { player ->
            val hScore = scores[player.id]?.getOrNull(hIndex)
            player.id to hScore
        }

        // Only evaluate holes where every active player has entered a valid grossScore > 0
        if (holeEntries.any { (_, hScore) -> hScore == null || hScore.grossScore <= 0 }) {
            continue
        }

        val sortedByNet = holeEntries
            .map { (playerId, hScore) -> playerId to hScore!!.netScore }
            .sortedBy { it.second }

        if (isBlitzEnabled && (sortedByNet[1].second - sortedByNet[0].second) >= 2) {
            val winnerId = sortedByNet[0].first
            holePoints[winnerId]!![hIndex] = totalHolePoints
            playerTotals[winnerId] = (playerTotals[winnerId] ?: 0) + totalHolePoints
            continue
        }

        var i = 0
        while (i < sortedByNet.size) {
            var j = i + 1
            while (j < sortedByNet.size && sortedByNet[j].second == sortedByNet[i].second) {
                j++
            }
            val pooledPoints = (i until j).sumOf { baseRanks[it] }
            val pointsEach = pooledPoints / (j - i)
            for (k in i until j) {
                val playerId = sortedByNet[k].first
                holePoints[playerId]!![hIndex] = pointsEach
                playerTotals[playerId] = (playerTotals[playerId] ?: 0) + pointsEach
            }
            i = j
        }
    }

    return NinesCalculationOutput(playerTotals, holePoints)
}