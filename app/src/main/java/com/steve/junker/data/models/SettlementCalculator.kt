package com.steve.junker.data.models

import kotlin.math.abs
import kotlin.math.roundToInt

data class SettlementTransaction(
    val fromName: String,
    val toName: String,
    val amount: Double
)

data class EntityNetStanding(
    val id: String,
    val name: String,
    val points: Int,
    val netDollars: Double
)

data class SettlementSummary(
    val standings: List<EntityNetStanding>,
    val transactions: List<SettlementTransaction>
)

private fun roundCents(value: Double): Double {
    return (value * 100.0).roundToInt() / 100.0
}

fun calculateSettlement(
    entityTotals: Map<String, Int>,
    players: List<Player>,
    teams: List<Team>,
    useTeams: Boolean,
    gameType: GameType,
    pointValue: Double
): SettlementSummary {
    val isTeamMode = gameType == GameType.RAW_JUNKER && useTeams && teams.isNotEmpty()

    if (isTeamMode) {
        return calculateTeamSettlement(entityTotals, players, teams, pointValue)
    }

    if (players.isEmpty()) {
        return SettlementSummary(emptyList(), emptyList())
    }

    val n = players.size
    val totalPoints = players.sumOf { entityTotals[it.id] ?: 0 }
    val avgPoints = totalPoints.toDouble() / n.toDouble()

    val rawStandings = players.map { player ->
        val pts = entityTotals[player.id] ?: 0
        val net = if (pointValue <= 0.0) 0.0 else (pts - avgPoints) * n * pointValue
        EntityNetStanding(
            id = player.id,
            name = player.name,
            points = pts,
            netDollars = roundCents(net)
        )
    }.sortedWith(compareByDescending<EntityNetStanding> { it.netDollars }.thenByDescending { it.points })

    if (pointValue <= 0.0 || rawStandings.all { abs(it.netDollars) <= 0.005 }) {
        return SettlementSummary(rawStandings, emptyList())
    }

    val transactions = simplifyDebts(rawStandings)
    return SettlementSummary(rawStandings, transactions)
}

private fun calculateTeamSettlement(
    entityTotals: Map<String, Int>,
    players: List<Player>,
    teams: List<Team>,
    pointValue: Double
): SettlementSummary {
    if (teams.size == 2) {
        val team1 = teams[0]
        val team2 = teams[1]
        val team1Points = entityTotals[team1.id] ?: 0
        val team2Points = entityTotals[team2.id] ?: 0
        val diff = team1Points - team2Points
        val perPlayerPayout = if (pointValue <= 0.0) 0.0 else roundCents(abs(diff) * pointValue)

        fun resolveTeamPlayers(team: Team): List<Player> =
            team.playerIds.map { pId -> players.find { it.id == pId } ?: Player(pId, pId, 0) }

        if (diff == 0 || pointValue <= 0.0 || perPlayerPayout < 0.01) {
            val tiedStandings = resolveTeamPlayers(team1).map {
                EntityNetStanding(id = it.id, name = it.name, points = team1Points, netDollars = 0.0)
            } + resolveTeamPlayers(team2).map {
                EntityNetStanding(id = it.id, name = it.name, points = team2Points, netDollars = 0.0)
            }
            return SettlementSummary(tiedStandings, emptyList())
        }

        val winningTeam = if (diff > 0) team1 else team2
        val losingTeam = if (diff > 0) team2 else team1
        val winningPoints = if (diff > 0) team1Points else team2Points
        val losingPoints = if (diff > 0) team2Points else team1Points

        val winnerPlayers = resolveTeamPlayers(winningTeam)
        val loserPlayers = resolveTeamPlayers(losingTeam)

        val standings = winnerPlayers.map { p ->
            EntityNetStanding(id = p.id, name = p.name, points = winningPoints, netDollars = perPlayerPayout)
        } + loserPlayers.map { p ->
            EntityNetStanding(id = p.id, name = p.name, points = losingPoints, netDollars = -perPlayerPayout)
        }

        val pairCount = minOf(loserPlayers.size, winnerPlayers.size)
        val transactions = (0 until pairCount).map { i ->
            SettlementTransaction(
                fromName = loserPlayers[i].name,
                toName = winnerPlayers[i].name,
                amount = perPlayerPayout
            )
        }

        return SettlementSummary(standings, transactions)
    }

    val numTeams = teams.size
    val totalTeamPoints = teams.sumOf { entityTotals[it.id] ?: 0 }
    val avgTeamPoints = totalTeamPoints.toDouble() / numTeams.toDouble()

    val teamStandings = teams.map { team ->
        val pts = entityTotals[team.id] ?: 0
        val net = if (pointValue <= 0.0) 0.0 else (pts - avgTeamPoints) * numTeams * pointValue
        EntityNetStanding(
            id = team.id,
            name = team.name,
            points = pts,
            netDollars = roundCents(net)
        )
    }.sortedWith(compareByDescending<EntityNetStanding> { it.netDollars }.thenByDescending { it.points })

    if (pointValue <= 0.0 || teamStandings.all { abs(it.netDollars) <= 0.005 }) {
        return SettlementSummary(teamStandings, emptyList())
    }

    return SettlementSummary(teamStandings, simplifyDebts(teamStandings))
}

private fun simplifyDebts(standings: List<EntityNetStanding>): List<SettlementTransaction> {
    data class Balance(val name: String, var amount: Double)

    val creditors = standings
        .filter { it.netDollars > 0.005 }
        .sortedByDescending { it.netDollars }
        .map { Balance(it.name, it.netDollars) }
        .toMutableList()

    val debtors = standings
        .filter { it.netDollars < -0.005 }
        .sortedByDescending { abs(it.netDollars) }
        .map { Balance(it.name, abs(it.netDollars)) }
        .toMutableList()

    val transactions = mutableListOf<SettlementTransaction>()
    var cIdx = 0
    var dIdx = 0

    while (cIdx < creditors.size && dIdx < debtors.size) {
        val creditor = creditors[cIdx]
        val debtor = debtors[dIdx]

        val transfer = minOf(creditor.amount, debtor.amount)
        val roundedTransfer = roundCents(transfer)

        if (roundedTransfer >= 0.01) {
            transactions.add(
                SettlementTransaction(
                    fromName = debtor.name,
                    toName = creditor.name,
                    amount = roundedTransfer
                )
            )
        }

        creditor.amount -= transfer
        debtor.amount -= transfer

        if (creditor.amount <= 0.005) cIdx++
        if (debtor.amount <= 0.005) dIdx++
    }

    return transactions
}
