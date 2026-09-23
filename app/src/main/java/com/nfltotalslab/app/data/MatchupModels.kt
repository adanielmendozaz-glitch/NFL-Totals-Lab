package com.nfltotalslab.app.data

data class MatchupFeature(
    val season:Int,
    val team:String,
    val feature:String,
    val side:String,
    val value:Double,
    val sampleN:Int,
    val source:String,
    val scope:String,
    val reliability:Double,
    val updatedAt:Long = System.currentTimeMillis()
)

data class MatchupFeatureSnapshot(
    val id:Long = 0L,
    val gameId:String,
    val season:Int,
    val week:Int,
    val team:String,
    val opponent:String,
    val feature:String,
    val side:String,
    val value:Double,
    val sampleN:Int,
    val source:String,
    val scope:String,
    val reliability:Double,
    val corePredictionId:Long?,
    val coreProjection:Double?,
    val marketLine:Double?,
    val coreInputKey:String,
    val createdAt:Long = System.currentTimeMillis(),
    val finalTotal:Int? = null
)
