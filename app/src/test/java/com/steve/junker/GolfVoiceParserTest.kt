package com.steve.junker

import com.steve.junker.data.models.Player
import com.steve.junker.voice.parseGolfTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GolfVoiceParserTest {

    private val players = listOf(
        Player("p1", "Mark", 8),
        Player("p2", "Steve", 12),
        Player("p3", "Dave", 15),
        Player("p4", "Bob", 18)
    )

    @Test
    fun `parses scores and homophones for multiple players`() {
        val result = parseGolfTranscript(
            transcript = "Mark fore, Steve tree, Dave ate, Bob to",
            currentHole = 3,
            activePlayers = players
        )

        assertTrue(result.isValid)
        assertEquals(3, result.targetHole)
        assertEquals(4, result.updates.size)
        assertEquals(4, result.updates[0].grossScore)
        assertEquals(3, result.updates[1].grossScore)
        assertEquals(8, result.updates[2].grossScore)
        assertEquals(2, result.updates[3].grossScore)
    }

    @Test
    fun `extracts target hole and removes it from working string`() {
        val result = parseGolfTranscript(
            transcript = "Hole 14 Mark 5 Steve four",
            currentHole = 1,
            activePlayers = players
        )

        assertTrue(result.isValid)
        assertEquals(14, result.targetHole)
        assertEquals(2, result.updates.size)
        assertEquals(5, result.updates[0].grossScore)
        assertEquals(4, result.updates[1].grossScore)
    }

    @Test
    fun `parses junk dots and avoids double poley triggering poley`() {
        val result = parseGolfTranscript(
            transcript = "Mark 4 double poly sandy, Steve 3 closest to the pin long drive, Dave 5 jocks lost boys, Bob 4 pole e",
            currentHole = 7,
            activePlayers = players
        )

        assertTrue(result.isValid)
        val mark = result.updates.find { it.playerId == "p1" }!!
        assertEquals(4, mark.grossScore)
        assertEquals(setOf("hasDoublePoley", "hasSandy"), mark.junkDots)

        val steve = result.updates.find { it.playerId == "p2" }!!
        assertEquals(3, steve.grossScore)
        assertEquals(setOf("hasCtp", "hasDrive"), steve.junkDots)

        val dave = result.updates.find { it.playerId == "p3" }!!
        assertEquals(5, dave.grossScore)
        assertEquals(setOf("hasJacques", "hasLostBoy"), dave.junkDots)

        val bob = result.updates.find { it.playerId == "p4" }!!
        assertEquals(4, bob.grossScore)
        assertEquals(setOf("hasPoley"), bob.junkDots)
    }

    @Test
    fun `merges repeated player appearances and handles Levenshtein typos`() {
        val result = parseGolfTranscript(
            transcript = "Marc 5 Steeve 4 Mark polly",
            currentHole = 9,
            activePlayers = players
        )

        assertTrue(result.isValid)
        assertEquals(2, result.updates.size)

        val mark = result.updates.find { it.playerId == "p1" }!!
        assertEquals(5, mark.grossScore)
        assertEquals(setOf("hasPoley"), mark.junkDots)

        val steve = result.updates.find { it.playerId == "p2" }!!
        assertEquals(4, steve.grossScore)
    }

    @Test
    fun `normalizes pulley, sandie, jack, and double pole e homophones`() {
        val result = parseGolfTranscript(
            transcript = "Mark 4 pulley sandie, Steve 5 double pulley, Dave 3 double pole e jack, Bob 4 pulleys",
            currentHole = 5,
            activePlayers = players
        )

        assertTrue(result.isValid)
        assertEquals(setOf("hasPoley", "hasSandy"), result.updates.find { it.playerId == "p1" }!!.junkDots)
        assertEquals(setOf("hasDoublePoley"), result.updates.find { it.playerId == "p2" }!!.junkDots)
        assertEquals(setOf("hasDoublePoley", "hasJacques"), result.updates.find { it.playerId == "p3" }!!.junkDots)
        assertEquals(setOf("hasPoley"), result.updates.find { it.playerId == "p4" }!!.junkDots)
    }

    @Test
    fun `parses double sandy variants without triggering single sandy`() {
        val result = parseGolfTranscript(
            transcript = "Mark 4 double sandy, Steve 5 double sandie, Dave 3 double sandys",
            currentHole = 6,
            activePlayers = players
        )

        assertTrue(result.isValid)
        assertEquals(setOf("hasDoubleSandy"), result.updates.find { it.playerId == "p1" }!!.junkDots)
        assertEquals(setOf("hasDoubleSandy"), result.updates.find { it.playerId == "p2" }!!.junkDots)
        assertEquals(setOf("hasDoubleSandy"), result.updates.find { it.playerId == "p3" }!!.junkDots)
    }

    @Test
    fun `returns invalid when no score or junk dot is parsed`() {
        val result = parseGolfTranscript(
            transcript = "Hello testing microphone",
            currentHole = 2,
            activePlayers = players
        )

        assertFalse(result.isValid)
        assertEquals(2, result.targetHole)
        assertTrue(result.updates.isEmpty())
    }
}
