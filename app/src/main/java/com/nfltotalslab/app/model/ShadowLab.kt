package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import kotlin.math.max

object ShadowLab {
    fun fromCore(core: Prediction): List<ShadowPrediction> =
        core.engines.mapIndexed { index, e ->
            val pick = if (e.pOver >= e.pUnder) "OVER" else "UNDER"
            ShadowPrediction(
                id = core.id * 10L + (index + 1).toLong(),
                gameId = core.gameId,
                season = core.season,
                week = core.week,
                awayTeam = core.awayTeam,
                homeTeam = core.homeTeam,
                line = core.line,
                modelName = e.name,
                pick = pick,
                probability = max(e.pOver, e.pUnder),
                projection = e.projection,
                inputKey = core.inputKey,
                createdAt = core.createdAt
            )
        }
}
