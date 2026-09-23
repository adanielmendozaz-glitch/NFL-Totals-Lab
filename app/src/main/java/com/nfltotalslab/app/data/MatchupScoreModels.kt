package com.nfltotalslab.app.data

data class MatchupScoreCensus(
    val gameId:String,
    val season:Int,
    val week:Int,
    val awayTeam:String,
    val homeTeam:String,
    val awayProjection:Double,
    val homeProjection:Double,
    val totalProjection:Double,
    val marketLine:Double,
    val pick:String,
    val probability:Double,
    val reliability:Double,
    val coverageCount:Int,
    val coverageTotal:Int,
    val status:String,
    val awayFeatureAdjustment:Double,
    val homeFeatureAdjustment:Double,
    val awayLearningAdjustment:Double,
    val homeLearningAdjustment:Double,
    val awayLearningN:Int,
    val homeLearningN:Int,
    val inputKey:String,
    val createdAt:Long = System.currentTimeMillis(),
    val finalAway:Int? = null,
    val finalHome:Int? = null,
    val result:String? = null
){
    val finalTotal:Int? get() =
        if(finalAway==null || finalHome==null)null else finalAway+finalHome
}

data class MatchupScoreAudit(
    val n:Int,
    val totalMae:Double?,
    val teamMae:Double?,
    val awayMae:Double?,
    val homeMae:Double?,
    val totalBias:Double?,
    val disagreementN:Int,
    val coreWinsWhenDisagree:Int,
    val matchupWinsWhenDisagree:Int
)
