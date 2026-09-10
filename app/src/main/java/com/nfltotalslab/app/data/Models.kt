package com.nfltotalslab.app.data

data class GameRecord(
    val gameId: String,
    val season: Int,
    val week: Int,
    val gameType: String,
    val gameDay: String,
    val gameTime: String,
    val awayTeam: String,
    val homeTeam: String,
    val awayScore: Int?,
    val homeScore: Int?,
    val totalLine: Double?,
    val spreadLine: Double?,
    val roof: String?,
    val surface: String?,
    val temp: Double?,
    val wind: Double?
) {
    val finished: Boolean get() = awayScore != null && homeScore != null
    val finalTotal: Int? get() = if (finished) awayScore!! + homeScore!! else null
}

data class TeamMetrics(
    val season: Int,
    val team: String,
    val games: Int = 0,
    val plays: Int = 0,
    val drives: Int = 0,
    val pointsFor: Int = 0,
    val offEpaPerPlay: Double = 0.0,
    val defEpaAllowedPerPlay: Double = 0.0,
    val successRate: Double = 0.43,
    val explosiveRate: Double = 0.10,
    val turnoverRate: Double = 0.10,
    val tdPerDrive: Double = 0.22,
    val fgPerDrive: Double = 0.15,
    val drivesPerGame: Double = 10.6,
    val pointsPerDrive: Double = 2.05,
    val rosterCount: Int = 0,
    val injuryCount: Int = 0
)

data class EngineSlice(
    val name: String,
    val projection: Double,
    val pOver: Double,
    val pUnder: Double
)

data class Prediction(
    val id: Long = System.currentTimeMillis(),
    val gameId: String,
    val season: Int,
    val week: Int,
    val awayTeam: String,
    val homeTeam: String,
    val line: Double,
    val pick: String,
    val probability: Double,
    val projection: Double,
    val classification: String,
    val createdAt: Long = System.currentTimeMillis(),
    val finalTotal: Int? = null,
    val result: String? = null,
    val engines: List<EngineSlice> = emptyList(),
    val analysisSource: String = "MANUAL",
    val modelVersion: String = "0.4",
    val inputKey: String = ""
)

data class ShadowPrediction(
    val id: Long,
    val gameId: String,
    val season: Int,
    val week: Int,
    val awayTeam: String,
    val homeTeam: String,
    val line: Double,
    val modelName: String,
    val pick: String,
    val probability: Double,
    val projection: Double,
    val inputKey: String,
    val createdAt: Long,
    val finalTotal: Int? = null,
    val result: String? = null
)

data class BetRecord(
    val id: Long = System.currentTimeMillis(),
    val predictionId: Long,
    val gameId: String,
    val market: String,
    val odds: Double,
    val stake: Double,
    val status: String = "PENDING",
    val pnl: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

data class BankEntry(
    val id: Long = System.currentTimeMillis(),
    val amount: Double,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class SyncSummary(
    val scheduleGames: Int = 0,
    val pbpTeams: Int = 0,
    val rosterPlayers: Int = 0,
    val injuryRows: Int = 0,
    val message: String = "",
    val autoAnalyzed: Int = 0,
    val activeWeek: Int? = null
)


data class LiveGameState(
    val awayTeam: String,
    val homeTeam: String,
    val awayScore: Int,
    val homeScore: Int,
    val period: Int,
    val clock: String,
    val detail: String,
    val state: String,
    val updatedAt: Long
) {
    val matchKey: String get() = "$awayTeam@$homeTeam"
    val isLive: Boolean get() = state == "in"
    val isFinal: Boolean get() = state == "post"
    val total: Int get() = awayScore + homeScore
}
