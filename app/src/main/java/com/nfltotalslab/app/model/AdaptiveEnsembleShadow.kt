package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.ShadowPrediction
import kotlin.math.max

/**
 * Adaptive Ensemble Shadow V1
 * Aprende pesos sólo de partidos FINAL previos. Nunca modifica el Core.
 */
object AdaptiveEnsembleShadow {
    private val baseWeights = linkedMapOf(
        "Markov Drive" to .30,
        "Negative Binomial" to .25,
        "Drive Monte Carlo" to .20,
        "Bayesian" to .15,
        "Shadow Poisson" to .10
    )

    fun fromCore(core: Prediction, history: List<ShadowPrediction>): ShadowPrediction? {
        val current = core.engines.associateBy { it.name }
        if (!baseWeights.keys.all { it in current }) return null

        val settledLatest = history
            .filter { it.modelName in baseWeights.keys && (it.result == "WIN" || it.result == "LOSS") }
            .groupBy { "${it.gameId}|${it.modelName}" }
            .mapNotNull { (_, rows) -> rows.maxByOrNull { it.createdAt } }

        val adjusted = linkedMapOf<String, Double>()
        baseWeights.forEach { (name, base) ->
            val rows = settledLatest.filter { it.modelName == name }
            val n = rows.size
            val brier = if (rows.isEmpty()) null else rows.map {
                val y = if (it.result == "WIN") 1.0 else 0.0
                val e = it.probability - y
                e * e
            }.average()
            val reliability = n.toDouble() / (n + 32.0)
            val skill = if (brier == null) 1.0 else (0.25 / brier.coerceAtLeast(.08)).coerceIn(.65, 1.35)
            adjusted[name] = base * (1.0 + reliability * (skill - 1.0))
        }

        val z = adjusted.values.sum().takeIf { it > 0.0 } ?: return null
        val weights = adjusted.mapValues { it.value / z }
        var projection = 0.0
        var pOver = 0.0
        var pUnder = 0.0
        weights.forEach { (name, w) ->
            val e = current.getValue(name)
            projection += e.projection * w
            pOver += e.pOver * w
            pUnder += e.pUnder * w
        }

        val pick = if (pOver >= pUnder) "OVER" else "UNDER"
        val prob = max(pOver, pUnder)
        val fingerprint = weights.entries.joinToString(",") {
            "${it.key}=${java.lang.String.format(java.util.Locale.US, "%.4f", it.value)}"
        }

        return ShadowPrediction(
            id = core.id * 100L + 91L,
            gameId = core.gameId,
            season = core.season,
            week = core.week,
            awayTeam = core.awayTeam,
            homeTeam = core.homeTeam,
            line = core.line,
            modelName = "Adaptive Ensemble",
            pick = pick,
            probability = prob,
            projection = projection,
            inputKey = "${core.inputKey}|ADAPT_V1|$fingerprint",
            createdAt = System.currentTimeMillis()
        )
    }
}
