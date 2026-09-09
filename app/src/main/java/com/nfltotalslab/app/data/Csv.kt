package com.nfltotalslab.app.data

object Csv {
    fun parseLine(line: String): List<String> {
        val out = ArrayList<String>()
        val b = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    b.append('"'); i++
                }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += b.toString(); b.setLength(0) }
                else -> b.append(ch)
            }
            i++
        }
        out += b.toString()
        return out
    }

    fun index(headers: List<String>, vararg candidates: String): Int {
        for (candidate in candidates) {
            val i = headers.indexOfFirst { it.equals(candidate, ignoreCase = true) }
            if (i >= 0) return i
        }
        return -1
    }

    fun value(row: List<String>, index: Int): String? =
        if (index >= 0 && index < row.size) row[index].takeIf { it.isNotBlank() && it != "NA" } else null
}
