package com.nfltotalslab.app.integrity

import com.nfltotalslab.app.data.GameRecord
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object KickoffGuard {
    private val eastern=ZoneId.of("America/New_York")
    private val formats=listOf(DateTimeFormatter.ofPattern("H:mm"),DateTimeFormatter.ofPattern("HH:mm"))
    fun isLocked(game:GameRecord,now:Instant=Instant.now()):Boolean{
        if(game.finished)return true
        val day=runCatching{LocalDate.parse(game.gameDay)}.getOrNull() ?: return false
        var time:LocalTime?=null
        for(f in formats){time=runCatching{LocalTime.parse(game.gameTime,f)}.getOrNull(); if(time!=null)break}
        val t=time ?: return false
        return !now.isBefore(day.atTime(t).atZone(eastern).toInstant())
    }
}
