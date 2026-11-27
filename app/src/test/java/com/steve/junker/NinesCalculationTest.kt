package com.steve.junker

import com.steve.junker.data.models.HoleScore
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.calculateNinesPoints
import org.junit.Assert.assertEquals
import org.junit.Test

class NinesCalculationTest {

    private val p1 = Player("p1", "Alice", 5)
    private val p2 = Player("p2", "Bob", 10)
    private val p3 = Player("p3", "Charlie", 15)
    private val p4 = Player("p4", "Dave", 20)

    private fun emptyHoles(): MutableList<HoleScore> = MutableList(18) { HoleScore() }

    @Test
    fun `three players no ties returns 5-3-1`() {
        val players = listOf(p1, p2, p3)
        val scores = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 4, netScore = 3) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 5, netScore = 4) },
            "p3" to emptyHoles().apply { this[0] = HoleScore(grossScore = 6, netScore = 5) }
        )

        val result = calculateNinesPoints(scores, players, isBlitzEnabled = false)

        assertEquals(5, result.holePoints["p1"]!![0])
        assertEquals(3, result.holePoints["p2"]!![0])
        assertEquals(1, result.holePoints["p3"]!![0])
        assertEquals(5, result.playerTotals["p1"])
        assertEquals(3, result.playerTotals["p2"])
        assertEquals(1, result.playerTotals["p3"])
    }

    @Test
    fun `three players two-way tie for first returns 4-4-1`() {
        val players = listOf(p1, p2, p3)
        val scores = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 4, netScore = 3) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 4, netScore = 3) },
            "p3" to emptyHoles().apply { this[0] = HoleScore(grossScore = 5, netScore = 4) }
        )

        val result = calculateNinesPoints(scores, players, isBlitzEnabled = false)

        assertEquals(4, result.holePoints["p1"]!![0])
        assertEquals(4, result.holePoints["p2"]!![0])
        assertEquals(1, result.holePoints["p3"]!![0])
    }

    @Test
    fun `four players no ties returns 7-5-3-1`() {
        val players = listOf(p1, p2, p3, p4)
        val scores = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 3, netScore = 3) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 4, netScore = 4) },
            "p3" to emptyHoles().apply { this[0] = HoleScore(grossScore = 5, netScore = 5) },
            "p4" to emptyHoles().apply { this[0] = HoleScore(grossScore = 6, netScore = 6) }
        )

        val result = calculateNinesPoints(scores, players, isBlitzEnabled = false)

        assertEquals(7, result.holePoints["p1"]!![0])
        assertEquals(5, result.holePoints["p2"]!![0])
        assertEquals(3, result.holePoints["p3"]!![0])
        assertEquals(1, result.holePoints["p4"]!![0])
    }

    @Test
    fun `four players tie scenarios match 6-6-3-1, 5-5-5-1, and 4-4-4-4`() {
        val players = listOf(p1, p2, p3, p4)

        // Hole 1: 2-way tie for 1st -> 6-6-3-1
        // Hole 2: 3-way tie for 1st -> 5-5-5-1
        // Hole 3: 4-way tie -> 4-4-4-4
        val scores = mapOf(
            "p1" to emptyHoles().apply {
                this[0] = HoleScore(grossScore = 4, netScore = 3)
                this[1] = HoleScore(grossScore = 4, netScore = 4)
                this[2] = HoleScore(grossScore = 4, netScore = 4)
            },
            "p2" to emptyHoles().apply {
                this[0] = HoleScore(grossScore = 4, netScore = 3)
                this[1] = HoleScore(grossScore = 4, netScore = 4)
                this[2] = HoleScore(grossScore = 4, netScore = 4)
            },
            "p3" to emptyHoles().apply {
                this[0] = HoleScore(grossScore = 5, netScore = 4)
                this[1] = HoleScore(grossScore = 4, netScore = 4)
                this[2] = HoleScore(grossScore = 4, netScore = 4)
            },
            "p4" to emptyHoles().apply {
                this[0] = HoleScore(grossScore = 6, netScore = 5)
                this[1] = HoleScore(grossScore = 6, netScore = 5)
                this[2] = HoleScore(grossScore = 4, netScore = 4)
            }
        )

        val result = calculateNinesPoints(scores, players, isBlitzEnabled = false)

        assertEquals(6, result.holePoints["p1"]!![0])
        assertEquals(6, result.holePoints["p2"]!![0])
        assertEquals(3, result.holePoints["p3"]!![0])
        assertEquals(1, result.holePoints["p4"]!![0])

        assertEquals(5, result.holePoints["p1"]!![1])
        assertEquals(5, result.holePoints["p2"]!![1])
        assertEquals(5, result.holePoints["p3"]!![1])
        assertEquals(1, result.holePoints["p4"]!![1])

        assertEquals(4, result.holePoints["p1"]!![2])
        assertEquals(4, result.holePoints["p2"]!![2])
        assertEquals(4, result.holePoints["p3"]!![2])
        assertEquals(4, result.holePoints["p4"]!![2])

        assertEquals(15, result.playerTotals["p1"])
        assertEquals(15, result.playerTotals["p2"])
        assertEquals(12, result.playerTotals["p3"])
        assertEquals(6, result.playerTotals["p4"])
    }

    @Test
    fun `blitz awards all points when sole lowest is 2 or more strokes better`() {
        val players3 = listOf(p1, p2, p3)
        val scores3 = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 3, netScore = 2) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 5, netScore = 4) },
            "p3" to emptyHoles().apply { this[0] = HoleScore(grossScore = 6, netScore = 5) }
        )

        val result3 = calculateNinesPoints(scores3, players3, isBlitzEnabled = true)
        assertEquals(9, result3.holePoints["p1"]!![0])
        assertEquals(0, result3.holePoints["p2"]!![0])
        assertEquals(0, result3.holePoints["p3"]!![0])

        val players4 = listOf(p1, p2, p3, p4)
        val scores4 = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 3, netScore = 2) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 5, netScore = 4) },
            "p3" to emptyHoles().apply { this[0] = HoleScore(grossScore = 6, netScore = 5) },
            "p4" to emptyHoles().apply { this[0] = HoleScore(grossScore = 7, netScore = 6) }
        )

        val result4 = calculateNinesPoints(scores4, players4, isBlitzEnabled = true)
        assertEquals(16, result4.holePoints["p1"]!![0])
        assertEquals(0, result4.holePoints["p2"]!![0])
        assertEquals(0, result4.holePoints["p3"]!![0])
        assertEquals(0, result4.holePoints["p4"]!![0])
    }

    @Test
    fun `blitz does not trigger if margin is 1 stroke or tied`() {
        val players = listOf(p1, p2, p3)
        val scores = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 3, netScore = 3) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 4, netScore = 4) },
            "p3" to emptyHoles().apply { this[0] = HoleScore(grossScore = 6, netScore = 6) }
        )

        val result = calculateNinesPoints(scores, players, isBlitzEnabled = true)
        assertEquals(5, result.holePoints["p1"]!![0])
        assertEquals(3, result.holePoints["p2"]!![0])
        assertEquals(1, result.holePoints["p3"]!![0])
    }

    @Test
    fun `incomplete hole where any player has grossScore 0 is skipped`() {
        val players = listOf(p1, p2, p3)
        val scores = mapOf(
            "p1" to emptyHoles().apply { this[0] = HoleScore(grossScore = 3, netScore = 3) },
            "p2" to emptyHoles().apply { this[0] = HoleScore(grossScore = 4, netScore = 4) },
            "p3" to emptyHoles() // grossScore = 0
        )

        val result = calculateNinesPoints(scores, players, isBlitzEnabled = true)
        assertEquals(0, result.holePoints["p1"]!![0])
        assertEquals(0, result.holePoints["p2"]!![0])
        assertEquals(0, result.holePoints["p3"]!![0])
        assertEquals(0, result.playerTotals["p1"])
    }
}
