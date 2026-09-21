package com.nfltotalslab.app.data

data class RosterPlayerState(
    val name:String,
    val position:String,
    val unit:String,
    val depthRank:Int,
    val athleteId:String = "",
    val coreStarter:Boolean = false,
    val injuryStatus:String = "",
    val injuryDetail:String = ""
){
    val rankOne:Boolean get() = depthRank == 1
    val starter:Boolean get() = coreStarter
    val depthLabel:String get() = when{
        coreStarter -> "TITULAR · NÚCLEO"
        depthRank==1 -> "RANK 1 · PAQUETE"
        depthRank==2 -> "SUPLENTE 2"
        depthRank==3 -> "SUPLENTE 3"
        depthRank>3 && depthRank<90 -> "DEPTH $depthRank"
        else -> "ROSTER"
    }

    val unavailable:Boolean get() {
        val s=injuryStatus.uppercase()
        return s.contains("OUT") || s.contains("INJURED RESERVE") ||
            s=="IR" || s.contains("PUP") || s.contains("SUSP")
    }
}

data class TeamRosterIntelligence(
    val team:String,
    val players:List<RosterPlayerState>,
    val offenseAvailability:Double,
    val defenseAvailability:Double,
    val offensePenalty:Double,
    val defenseLeak:Double,
    val startersTotal:Int,
    val startersAvailable:Int,
    val offenseCoreStarters:Int,
    val defenseCoreStarters:Int,
    val rawRankOneCount:Int,
    val outCount:Int,
    val questionableCount:Int,
    val depthLoaded:Boolean,
    val injuriesLoaded:Boolean,
    val injuryEndpointOk:Boolean,
    val injurySchemaOk:Boolean,
    val injuryRawCount:Int,
    val injuryParsedCount:Int,
    val injuryState:String,
    val rosterLoaded:Boolean,
    val depthSource:String,
    val fetchedAt:Long = System.currentTimeMillis()
)

data class GameRosterIntelligence(
    val away:TeamRosterIntelligence,
    val home:TeamRosterIntelligence,
    val totalAdjustment:Double,
    val reliability:Double,
    val decisionReady:Boolean,
    val qualityReason:String,
    val source:String,
    val fingerprint:String,
    val fetchedAt:Long = System.currentTimeMillis()
)
