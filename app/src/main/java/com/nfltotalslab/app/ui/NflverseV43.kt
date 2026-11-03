package com.nfltotalslab.app.ui

import android.content.Context
import org.json.JSONObject

internal object NflverseV43 {
    fun load(context:Context,season:Int,week:Int):Map<String,V43DriveProfile> {
        val raw=context.assets.open("nflverse_drive_profiles.json").bufferedReader().use { it.readText() }
        val obj=JSONObject(raw)
        require(obj.getString("schema")=="nflverse-v43-drive-profiles")
        val arr=obj.getJSONArray("profiles")
        val result=mutableMapOf<String,V43DriveProfile>()
        for(i in 0 until arr.length()) {
            val p=arr.getJSONObject(i)
            if(p.getInt("season")!=season || p.getInt("week")!=week)continue
            result[p.getString("team")]=V43DriveProfile(
                p.getDouble("drives_per_game"),p.getDouble("plays_per_drive"),
                p.getDouble("seconds_per_drive"),p.getDouble("seconds_per_play"),
                p.getDouble("pass_rate"),p.getInt("games"))
        }
        return result
    }
}
