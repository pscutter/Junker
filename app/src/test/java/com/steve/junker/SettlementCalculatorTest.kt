package com.steve.junker

import com.steve.junker.data.models.GameType
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Team
import com.steve.junker.data.models.calculateSettlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettlementCalculatorTest {

    private val p1 = Player("p1", "Alice", 5)
    private val p2 = Player("p2", "Bob", 10)
    private val p3 = Player("p3", "Charlie", 15)
    private val p4 = Player("p4", "Dave", 20)

    @Test
    fun `individual settlement computes pairwise differentials and greedy minimum transactions`() {
        // 3 players with points 10, 7, 4 -> avg = 7
        // Alice: (10 - 7) * 3 * $1 = +$9.00
        // Bob: (7 - 7) * 3 * $1 = $0.00
        // Charlie: (4 - 7) * 3 * $1 = -$9.00
        val totals = mapOf("p1" to 10, "p2" to 7, "p3" to 4)
        val summary = calculateSettlement(
            entityTotals = totals,
            players = listOf(p1, p2, p3),
            teams = emptyList(),
            useTeams = false,
            gameType = GameType.NINES,
            pointValue = 1.0
        )

        assertEquals(9.0, summary.standings[0].netDollars, 0.01)
        assertEquals(0.0, summary.standings[1].netDollars, 0.01)
        assertEquals(-9.0, summary.standings[2].netDollars, 0.01)

        assertEquals(1, summary.transactions.size)
        assertEquals("Charlie", summary.transactions[0].fromName)
        assertEquals("Alice", summary.transactions[0].toName)
        assertEquals(9.0, summary.transactions[0].amount, 0.01)
    }

    @Test
    fun `four player settlement with fractional pointValue simplifies debts greedily`() {
        // Points: 12, 10, 6, 4 -> avg = 8
        // Alice: +4 * 4 * 0.5 = +$8.00
        // Bob: +2 * 4 * 0.5 = +$4.00
        // Charlie: -2 * 4 * 0.5 = -$4.00
        // Dave: -4 * 4 * 0.5 = -$8.00
        val totals = mapOf("p1" to 12, "p2" to 10, "p3" to 6, "p4" to 4)
        val summary = calculateSettlement(
            entityTotals = totals,
            players = listOf(p1, p2, p3, p4),
            teams = emptyList(),
            useTeams = false,
            gameType = GameType.RAW_JUNKER,
            pointValue = 0.5
        )

        assertEquals(2, summary.transactions.size)
        assertEquals("Dave", summary.transactions[0].fromName)
        assertEquals("Alice", summary.transactions[0].toName)
        assertEquals(8.0, summary.transactions[0].amount, 0.01)

        assertEquals("Charlie", summary.transactions[1].fromName)
        assertEquals("Bob", summary.transactions[1].toName)
        assertEquals(4.0, summary.transactions[1].amount, 0.01)
    }

    @Test
    fun `2v2 team settlement computes per-player net standings and two 1-to-1 transactions`() {
        val teams = listOf(
            Team("team_1", "Alice / Bob", listOf("p1", "p2")),
            Team("team_2", "Charlie / Dave", listOf("p3", "p4"))
        )
        // Team 1 has 12 dots, Team 2 has 7 dots -> diff = 5 * $2.00 = $10.00 per player
        val totals = mapOf("team_1" to 12, "team_2" to 7)
        val summary = calculateSettlement(
            entityTotals = totals,
            players = listOf(p1, p2, p3, p4),
            teams = teams,
            useTeams = true,
            gameType = GameType.RAW_JUNKER,
            pointValue = 2.0
        )

        assertEquals(4, summary.standings.size)
        assertEquals("Alice", summary.standings[0].name)
        assertEquals(10.0, summary.standings[0].netDollars, 0.01)
        assertEquals("Bob", summary.standings[1].name)
        assertEquals(10.0, summary.standings[1].netDollars, 0.01)
        assertEquals("Charlie", summary.standings[2].name)
        assertEquals(-10.0, summary.standings[2].netDollars, 0.01)
        assertEquals("Dave", summary.standings[3].name)
        assertEquals(-10.0, summary.standings[3].netDollars, 0.01)

        assertEquals(2, summary.transactions.size)
        assertEquals("Charlie", summary.transactions[0].fromName)
        assertEquals("Alice", summary.transactions[0].toName)
        assertEquals(10.0, summary.transactions[0].amount, 0.01)
        assertEquals("Dave", summary.transactions[1].fromName)
        assertEquals("Bob", summary.transactions[1].toName)
        assertEquals(10.0, summary.transactions[1].amount, 0.01)
    }

    @Test
    fun `zero pointValue or tied entities return empty transactions`() {
        val totals = mapOf("p1" to 10, "p2" to 5, "p3" to 3)
        val zeroValSummary = calculateSettlement(
            entityTotals = totals,
            players = listOf(p1, p2, p3),
            teams = emptyList(),
            useTeams = false,
            gameType = GameType.NINES,
            pointValue = 0.0
        )
        assertTrue(zeroValSummary.transactions.isEmpty())

        val tiedSummary = calculateSettlement(
            entityTotals = mapOf("p1" to 6, "p2" to 6, "p3" to 6),
            players = listOf(p1, p2, p3),
            teams = emptyList(),
            useTeams = false,
            gameType = GameType.NINES,
            pointValue = 2.0
        )
        assertTrue(tiedSummary.transactions.isEmpty())
    }
}
