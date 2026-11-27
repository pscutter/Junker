package com.steve.junker.voice

import com.steve.junker.data.models.Player
import java.util.Locale

fun parseGolfTranscript(
    transcript: String,
    currentHole: Int,
    activePlayers: List<Player>
): VoiceParseResult {
    if (transcript.isBlank()) {
        return VoiceParseResult(
            rawTranscript = transcript,
            targetHole = currentHole,
            updates = emptyList(),
            isValid = false
        )
    }

    // 1. Normalize text, multi-word phrases, and homophones
    var normalized = transcript
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    // Multi-word phrase normalization BEFORE single-word number homophones (e.g. "to" -> "2")
    val multiWordReplacements = listOf(
        Regex("\\bclosest to the pin\\b") to "ctp",
        Regex("\\bclosest to pin\\b") to "ctp",
        Regex("\\bc t p\\b") to "ctp",
        Regex("\\blongest drive\\b") to "drive",
        Regex("\\blong drive\\b") to "drive",
        Regex("\\bdouble pulley\\b") to "double poley",
        Regex("\\bdouble poly\\b") to "double poley",
        Regex("\\bdouble polly\\b") to "double poley",
        Regex("\\bdouble pole e\\b") to "double poley",
        Regex("\\bdouble sandie\\b") to "double sandy",
        Regex("\\bdouble sandys\\b") to "double sandy",
        Regex("\\bpole e\\b") to "poley",
        Regex("\\blost boys\\b") to "lost boy"
    )
    for ((pattern, replacement) in multiWordReplacements) {
        normalized = normalized.replace(pattern, replacement)
    }

    val wordHomophones = mapOf(
        "one" to "1",
        "two" to "2",
        "to" to "2",
        "too" to "2",
        "three" to "3",
        "tree" to "3",
        "for" to "4",
        "fore" to "4",
        "four" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "ate" to "8",
        "nine" to "9",
        "ten" to "10",
        "eleven" to "11",
        "twelve" to "12",
        "pulley" to "poley",
        "pulleys" to "poley",
        "poly" to "poley",
        "polly" to "poley",
        "sandie" to "sandy",
        "jock" to "jacques",
        "jocks" to "jacques",
        "jacque" to "jacques",
        "jack" to "jacques"
    )

    val normalizedTokens = normalized
        .split(" ")
        .filter { it.isNotEmpty() }
        .map { wordHomophones[it] ?: it }

    var workingString = normalizedTokens.joinToString(" ")

    // 2. Extract target hole ("hole <1..18>")
    var targetHole = currentHole
    val holeRegex = Regex("\\bhole\\s+(1[0-8]|[1-9])\\b")
    val holeMatch = holeRegex.find(workingString)
    if (holeMatch != null) {
        val parsedHole = holeMatch.groupValues[1].toIntOrNull()
        if (parsedHole != null && parsedHole in 1..18) {
            targetHole = parsedHole
        }
        workingString = workingString.removeRange(holeMatch.range)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    val tokens = if (workingString.isEmpty()) emptyList() else workingString.split(" ")
    if (tokens.isEmpty() || activePlayers.isEmpty()) {
        return VoiceParseResult(
            rawTranscript = transcript,
            targetHole = targetHole,
            updates = emptyList(),
            isValid = false
        )
    }

    // 3. Segment tokens by matched player name
    val playerSegments = mutableListOf<Pair<Player, MutableList<String>>>()
    var index = 0
    while (index < tokens.size) {
        val matched = matchPlayerAt(tokens, index, activePlayers)
        if (matched != null) {
            val (player, consumedCount) = matched
            playerSegments.add(player to mutableListOf())
            index += consumedCount
        } else {
            if (playerSegments.isNotEmpty()) {
                playerSegments.last().second.add(tokens[index])
            }
            index++
        }
    }

    // 4. Extract score & junk dots per segment, merging repeated appearances of the same playerId
    val mergedUpdates = LinkedHashMap<String, PlayerVoiceUpdate>()
    for ((player, segmentTokens) in playerSegments) {
        var grossScore: Int? = null
        val junkDots = mutableSetOf<String>()

        var sIdx = 0
        while (sIdx < segmentTokens.size) {
            val token = segmentTokens[sIdx]
            val nextToken = segmentTokens.getOrNull(sIdx + 1)

            if (token == "double" && nextToken == "poley") {
                junkDots.add("hasDoublePoley")
                sIdx += 2
                continue
            }
            if (token == "double" && nextToken == "sandy") {
                junkDots.add("hasDoubleSandy")
                sIdx += 2
                continue
            }
            if (token == "lost" && nextToken == "boy") {
                junkDots.add("hasLostBoy")
                sIdx += 2
                continue
            }

            val intVal = token.toIntOrNull()
            if (intVal != null && intVal in 1..12) {
                grossScore = intVal
            } else {
                when (token) {
                    "poley" -> junkDots.add("hasPoley")
                    "sandy" -> junkDots.add("hasSandy")
                    "drive" -> junkDots.add("hasDrive")
                    "jacques" -> junkDots.add("hasJacques")
                    "ctp" -> junkDots.add("hasCtp")
                }
            }
            sIdx++
        }

        val existing = mergedUpdates[player.id]
        if (existing == null) {
            mergedUpdates[player.id] = PlayerVoiceUpdate(
                playerId = player.id,
                playerName = player.name,
                grossScore = grossScore,
                junkDots = junkDots
            )
        } else {
            mergedUpdates[player.id] = existing.copy(
                grossScore = grossScore ?: existing.grossScore,
                junkDots = existing.junkDots + junkDots
            )
        }
    }

    val updatesList = mergedUpdates.values.toList()
    val isValid = updatesList.any { it.grossScore != null || it.junkDots.isNotEmpty() }

    return VoiceParseResult(
        rawTranscript = transcript,
        targetHole = targetHole,
        updates = updatesList,
        isValid = isValid
    )
}

private fun matchPlayerAt(
    tokens: List<String>,
    index: Int,
    activePlayers: List<Player>
): Pair<Player, Int>? {
    // 1. Exact case-insensitive match (supports multi-word names first)
    val sortedByWordCount = activePlayers.map { player ->
        val nameTokens = player.name.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        player to nameTokens
    }.sortedByDescending { it.second.size }

    for ((player, nameTokens) in sortedByWordCount) {
        if (nameTokens.isEmpty()) continue
        if (index + nameTokens.size <= tokens.size) {
            val slice = tokens.subList(index, index + nameTokens.size)
            if (slice == nameTokens) {
                return player to nameTokens.size
            }
        }
    }

    // 2. Fallback: Levenshtein edit distance <= 1 for single-token names with 4+ characters
    val currentToken = tokens[index]
    if (currentToken.length >= 3) {
        var bestMatch: Player? = null
        var bestDistance = Int.MAX_VALUE
        for ((player, nameTokens) in sortedByWordCount) {
            if (nameTokens.size == 1) {
                val targetName = nameTokens[0]
                if (targetName.length >= 4) {
                    val dist = levenshteinDistance(currentToken, targetName)
                    if (dist <= 1 && dist < bestDistance) {
                        bestDistance = dist
                        bestMatch = player
                    }
                }
            }
        }
        if (bestMatch != null) {
            return bestMatch to 1
        }
    }

    return null
}

private fun levenshteinDistance(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    val dp = IntArray(n + 1) { it }
    for (i in 1..m) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..n) {
            val temp = dp[j]
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[j] = minOf(
                dp[j] + 1,
                dp[j - 1] + 1,
                prev + cost
            )
            prev = temp
        }
    }
    return dp[n]
}
