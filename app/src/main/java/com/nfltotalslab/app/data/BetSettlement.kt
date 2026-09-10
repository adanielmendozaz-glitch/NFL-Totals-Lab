package com.nfltotalslab.app.data

import android.content.ContentValues

/**
 * Liquida apuestas pendientes usando el resultado ya liquidado de la predicción
 * vinculada. Mantiene el historial persistente y evita cierres manuales.
 */
fun settlePendingBets(db: DbHelper): Int {
    val writable = db.writableDatabase
    val rows = mutableListOf<BetSettlementRow>()

    db.readableDatabase.rawQuery(
        """
        SELECT b.id,b.stake,b.odds,p.result
        FROM bets b
        JOIN predictions p ON p.id=b.prediction_id
        WHERE b.status='PENDING' AND p.result IS NOT NULL
        """.trimIndent(),
        null
    ).use { c ->
        while (c.moveToNext()) {
            rows += BetSettlementRow(
                id = c.getLong(0),
                stake = c.getDouble(1),
                odds = c.getDouble(2),
                result = c.getString(3)
            )
        }
    }

    if (rows.isEmpty()) return 0

    writable.beginTransaction()
    try {
        rows.forEach { row ->
            val pnl = when (row.result) {
                "WIN" -> row.stake * (row.odds - 1.0)
                "LOSS" -> -row.stake
                else -> 0.0
            }
            val values = ContentValues().apply {
                put("status", row.result)
                put("pnl", pnl)
            }
            writable.update("bets", values, "id=?", arrayOf(row.id.toString()))
        }
        writable.setTransactionSuccessful()
    } finally {
        writable.endTransaction()
    }

    return rows.size
}

private data class BetSettlementRow(
    val id: Long,
    val stake: Double,
    val odds: Double,
    val result: String
)
