package com.nfltotalslab.app.ui

import android.content.Context
import org.json.JSONObject
import kotlin.math.*

/** Real 2026 weekly player statistics. Only weeks STRICTLY before matchup week are used. */
internal data class PlayerUsage2026(val targets:Int,val receptions:Int,val carries:Int,val games:Int)
internal object SimcastPlayerUsage2026 {
    fun load(context:Context,season:Int,week:Int):Map<String,Map<String,PlayerUsage2026>> {
        if(season!=2026 || week<=1)return emptyMap()
        val obj=JSONObject(context.assets.open("player_usage_2026.json").bufferedReader().use{it.readText()})
        require(obj.getString("schema")=="nflverse-player-usage-weekly-v1")
        val rows=obj.getJSONArray("rows")
        val groups=mutableMapOf<String,MutableMap<String,MutableList<IntArray>>>()
        for(i in 0 until rows.length()) {
            val p=rows.getJSONObject(i)
            if(p.getInt("week")>=week)continue // no future information leakage
            val team=p.getString("team");val name=p.getString("name").trim().lowercase()
            groups.getOrPut(team){mutableMapOf()}.getOrPut(name){mutableListOf()}.add(intArrayOf(p.optInt("targets"),p.optInt("receptions"),p.optInt("carries")))
        }
        return groups.mapValues { (_,players)-> players.mapValues { (_,games)->
            PlayerUsage2026(games.sumOf{it[0]},games.sumOf{it[1]},games.sumOf{it[2]},games.size)
        } }
    }
}
