package com.steve.junker

import com.steve.junker.data.models.Course
import com.steve.junker.data.models.GameType
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Team
import com.steve.junker.ui.viewmodel.GameViewModel
import com.steve.junker.voice.PlayerVoiceUpdate
import com.steve.junker.voice.VoiceParseResult
import com.steve.junker.voice.parseGolfTranscript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameViewModelVoiceTest {

    private val p1 = Player("p1", "Mark", 5)
    private val p2 = Player("p2", "Steve", 10)
    private val p3 = Player("p3", "Keith", 15)
    private val p4 = Player("p4", "Bob", 20)
    private val players = listOf(p1, p2, p3, p4)
    private val course = Course("c1", "Test Course", List(18) { it + 1 }, List(18) { 4 })

    @Test
    fun `applyVoiceBatch updates gross scores, net scores, and all junk dots in RAW_JUNKER individual mode`() {
        val vm = GameViewModel()
        vm.setupGame(players, course, matchInTeamMode = false, gameType = GameType.RAW_JUNKER)

        val parsed = parseGolfTranscript(
            transcript = "Hole 3 Mark 4 sandy drive, Steve 5 poley, Keith 4 double poley, Bob 3 jacques lost boy ctp",
            currentHole = 1,
            activePlayers = players
        )
        assertTrue(parsed.isValid)

        var completed = false
        vm.applyVoiceBatch(parsed) { completed = true }

        assertTrue(completed)
        assertEquals(3, vm.currentHole.value)

        val holeIdx = 2
        val scores = vm.scores.value

        val mScore = scores["p1"]!![holeIdx]
        assertEquals(4, mScore.grossScore)
        assertEquals(4, mScore.netScore) // lowest hcp = 5 -> 0 strokes
        assertTrue(mScore.hasSandy)
        assertTrue(mScore.hasDrive)

        val sScore = scores["p2"]!![holeIdx]
        assertEquals(5, sScore.grossScore)
        assertEquals(4, sScore.netScore) // wheeled hcp 5 >= hole hcp 3 -> 1 stroke
        assertTrue(sScore.hasPoley)
        assertFalse(sScore.hasDrive)

        val kScore = scores["p3"]!![holeIdx]
        assertEquals(4, kScore.grossScore)
        assertTrue(kScore.hasDoublePoley)

        val bScore = scores["p4"]!![holeIdx]
        assertEquals(3, bScore.grossScore)
        assertTrue(bScore.hasJacques)
        assertTrue(bScore.hasLostBoy)
        assertTrue(bScore.hasCtp)
    }

    @Test
    fun `applyVoiceBatch preserves existing grossScore when only junk dots are spoken and works in 2v2 team mode`() {
        val vm = GameViewModel()
        val teams = listOf(
            Team("team_1", "Mark / Steve", listOf("p1", "p2")),
            Team("team_2", "Keith / Bob", listOf("p3", "p4"))
        )
        vm.setupGame(players, course, matchInTeamMode = true, activeTeamsList = teams, gameType = GameType.RAW_JUNKER)

        // First set Mark's gross score to 4 on hole 1
        vm.updateGrossScore("p1", 1, 4)

        // Now apply a voice batch with only a junk dot for Mark (grossScore = null) and drive for Steve
        val batch = VoiceParseResult(
            rawTranscript = "Mark poley, Steve drive",
            targetHole = 1,
            updates = listOf(
                PlayerVoiceUpdate("p1", "Mark", grossScore = null, junkDots = setOf("hasPoley")),
                PlayerVoiceUpdate("p2", "Steve", grossScore = null, junkDots = setOf("hasDrive"))
            ),
            isValid = true
        )

        vm.applyVoiceBatch(batch) {}

        val mScore = vm.scores.value["p1"]!![0]
        assertEquals(4, mScore.grossScore) // preserved
        assertTrue(mScore.hasPoley)
        assertFalse(mScore.hasDrive)

        val sScore = vm.scores.value["p2"]!![0]
        assertTrue(sScore.hasDrive)
    }

    @Test
    fun `when useHandicaps is false, netScore equals grossScore for all players`() {
        val vm = GameViewModel()
        vm.setupGame(players, course, matchInTeamMode = false, gameType = GameType.RAW_JUNKER, useHandicaps = false)

        vm.updateGrossScore("p4", 1, 6) // Bob has hcp 20 vs Mark hcp 5, normally gets strokes
        assertEquals(6, vm.scores.value["p4"]!![0].netScore)

        val parsed = parseGolfTranscript("Hole 2 Keith 5", currentHole = 1, activePlayers = players)
        vm.applyVoiceBatch(parsed) {}
        assertEquals(5, vm.scores.value["p3"]!![1].grossScore)
        assertEquals(5, vm.scores.value["p3"]!![1].netScore)
    }
}
