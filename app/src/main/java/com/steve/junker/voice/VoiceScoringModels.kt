package com.steve.junker.voice

data class PlayerVoiceUpdate(
    val playerId: String,
    val playerName: String,
    val grossScore: Int? = null,
    val junkDots: Set<String> = emptySet()
)

data class VoiceParseResult(
    val rawTranscript: String,
    val targetHole: Int,
    val updates: List<PlayerVoiceUpdate>,
    val isValid: Boolean
)
