package com.nfltotalslab.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Four independently versioned snapshots. All rows are stored unchanged, with their source provenance. */
enum class NflFeed(val title:String, val source:String, val url:String) {
    TEAM_RATINGS("Team Ratings · EPA", "The Margin", "https://themarginsports.com/v1/nfl/team-epa.csv"),
    TEAM_STATS("Team Season Stats", "nflverse", "https://github.com/nflverse/nflverse-data/releases/download/stats_team/stats_team_reg_2026.csv"),
    PLAYER_TOTALS("Player Season Totals", "nflverse", "https://github.com/nflverse/nflverse-data/releases/download/stats_player/stats_player_reg_2026.csv"),
    PLAYER_WEEKLY("Player Weekly Stats", "nflverse", "https://github.com/nflverse/nflverse-data/releases/download/stats_player/stats_player_week_2026.csv");

    fun address(season:Int) = if(this==TEAM_RATINGS) url else url.replace("2026", season.toString())
}

data class FeedSnapshot(
    val feed:NflFeed, val season:Int, val throughWeek:Int, val rows:Int,
    val sourceAsOf:String, val importedAt:Long, val sha256:String, val origin:String
)

data class FeedLoadResult(val feed:NflFeed, val rows:Int, val throughWeek:Int, val changed:Boolean, val note:String)

class DataCenter(private val db:DbHelper){
    fun snapshot(season:Int, feed:NflFeed):FeedSnapshot? = db.readableDatabase.rawQuery(
        "SELECT through_week, row_count, source_as_of, imported_at, sha256, origin FROM nfl_feed_snapshots WHERE season=? AND feed=?",
        arrayOf(season.toString(),feed.name)
    ).use{c->
        if(!c.moveToFirst()) null else FeedSnapshot(feed, season, c.getInt(0),c.getInt(1),c.getString(2) ?: "",
            c.getLong(3),c.getString(4),c.getString(5))
    }

    fun all(season:Int):List<FeedSnapshot> = NflFeed.entries.mapNotNull{snapshot(season,it)}

    suspend fun refresh(season:Int, force:Boolean=false):List<String> = withContext(Dispatchers.IO){
        val now=System.currentTimeMillis()
        val last=db.getKv("feed_sync_$season")?.toLongOrNull() ?: 0L
        if(!force && now-last<12L*60L*60L*1000L) return@withContext emptyList()
        val notes=mutableListOf<String>()
        NflFeed.entries.forEach{feed->
            runCatching{
                val conn=URL(feed.address(season)).openConnection() as HttpURLConnection
                conn.connectTimeout=12000
                conn.readTimeout=25000
                conn.setRequestProperty("User-Agent","NFL-Totals-Lab/1.2 research client")
                conn.instanceFollowRedirects=true
                try {
                    require(conn.responseCode in 200..299){"HTTP ${conn.responseCode}"}
                    importCsv(feed,season,conn.inputStream.bufferedReader(Charsets.UTF_8),"AUTO: ${feed.address(season)}")
                } finally {conn.disconnect()}
            }.onSuccess{notes += "${feed.title}: ${it.note}"}
                .onFailure{notes += "${feed.title}: ${it.message ?: "no disponible"}"}
        }
        db.putKv("feed_sync_$season",System.currentTimeMillis().toString())
        notes
    }

    /** Transactional and strict: reject wrong season/invalid files before modifying saved snapshots. */
    suspend fun importDocument(feed:NflFeed,season:Int,reader:BufferedReader):FeedLoadResult =
        withContext(Dispatchers.IO){ importCsv(feed, season, reader, "IMPORTACIÓN MANUAL") }

    private fun importCsv(feed:NflFeed,season:Int,reader:BufferedReader,origin:String):FeedLoadResult {
        val start=reader.readLine()?.removePrefix("\uFEFF") ?: error("CSV vacío")
        val headers=Csv.parseLine(start).map{it.trim()}
        require(headers.size>4){"CSV sin suficientes columnas"}
        val ixSeason=Csv.index(headers,"season")
        require(ixSeason>=0){"Falta columna season"}
        val ixWeek=Csv.index(headers,"through_week","week")
        val ixTeam=Csv.index(headers,"team","recent_team","team_abbr")
        val ixPlayer=Csv.index(headers,"player_id","gsis_id","player_name","player_display_name","player")
        if(feed==NflFeed.TEAM_RATINGS){
            require(Csv.index(headers,"off_epa_play")>=0 && Csv.index(headers,"def_epa_play")>=0){"No es CSV Team EPA de The Margin"}
            require(Csv.index(headers,"through_week")>=0){"Falta through_week"}
        }else if(feed==NflFeed.PLAYER_WEEKLY){
            require(Csv.index(headers,"week")>=0 && ixPlayer>=0 && ixTeam>=0){"Faltan player, team o week"}
        }else if(feed==NflFeed.PLAYER_TOTALS){
            require(ixPlayer>=0 && ixTeam>=0){"Faltan player o team"}
        }else {
            require(ixTeam>=0){"Falta team"}
        }
        val data=linkedMapOf<String,JSONObject>()
        var maxWeek=0
        var asOf=""
        val ixAsOf=Csv.index(headers,"as_of")
        var count=0
        var byteCount=start.length
        reader.useLines { lines->
            lines.forEach {line->
                byteCount+=line.length
                require(byteCount<=22_000_000){"CSV supera límite de 22 MB"}
                if(line.isBlank())return@forEach
                count++
                require(count<=70_000){"Demasiadas filas"}
                val values=Csv.parseLine(line)
                if(values.size!=headers.size)return@forEach
                if(Csv.value(values,ixSeason)?.trim()?.toIntOrNull()!=season)return@forEach
                val team=Csv.value(values,ixTeam)?.trim()?.uppercase(Locale.US).orEmpty()
                val player=Csv.value(values,ixPlayer)?.trim().orEmpty()
                val week=Csv.value(values,ixWeek)?.toIntOrNull() ?: 0
                if(feed==NflFeed.PLAYER_WEEKLY && week !in 1..23)return@forEach
                if(feed==NflFeed.TEAM_RATINGS && week !in 0..23)return@forEach
                val key=when(feed){
                    NflFeed.TEAM_RATINGS, NflFeed.TEAM_STATS -> team
                    NflFeed.PLAYER_TOTALS -> "$player|$team"
                    NflFeed.PLAYER_WEEKLY -> "$player|$team|$week"
                }
                if(key.isBlank() || key.startsWith("|"))return@forEach
                val obj=JSONObject()
                headers.forEachIndexed { i,h-> obj.put(h,values[i]) }
                data[key]=obj
                maxWeek=maxOf(maxWeek,week)
                if(ixAsOf>=0)asOf=Csv.value(values,ixAsOf).orEmpty()
            }
        }
        require(data.isNotEmpty()){"No se encontraron filas de la temporada $season"}
        val minRows=if(feed==NflFeed.TEAM_RATINGS || feed==NflFeed.TEAM_STATS) 16 else 20
        require(data.size>=minRows){"Importación incompleta: ${data.size} filas (mínimo $minRows)"}
        val officialWeek=db.loadGames(season)
            .filter{it.gameType=="REG" && it.finished}
            .maxOfOrNull{it.week} ?: 0
        // Cumulative season assets have no week column. Record inferred cutoff conservatively.
        val week=if(feed==NflFeed.TEAM_RATINGS || feed==NflFeed.PLAYER_WEEKLY)maxWeek else officialWeek
        require(week in 0..23){"Semana inválida"}
        val digest=MessageDigest.getInstance("SHA-256")
        data.toSortedMap().forEach{(key,value)->
            digest.update(key.toByteArray(Charsets.UTF_8))
            digest.update(value.toString().toByteArray(Charsets.UTF_8))
        }
        val sha=digest.digest().joinToString(""){"%02x".format(it)}
        val previous=snapshot(season,feed)
        if(previous?.sha256==sha)return FeedLoadResult(feed,data.size,week,false,"Sin cambios · ${data.size} filas")
        require(previous==null || week>=previous.throughWeek){"Archivo anterior al guardado (week $week < ${previous?.throughWeek})"}
        val now=System.currentTimeMillis()
        val w=db.writableDatabase
        w.beginTransaction()
        try {
            w.delete("nfl_feed_rows","season=? AND feed=?",arrayOf(season.toString(),feed.name))
            data.forEach{(key,value)->
                val row=ContentValues().apply{
                    put("season",season);put("feed",feed.name);put("row_key",key);put("payload",value.toString())
                }
                w.insertOrThrow("nfl_feed_rows",null,row)
            }
            val meta=ContentValues().apply{
                put("season",season);put("feed",feed.name);put("through_week",week)
                put("row_count",data.size);put("source_as_of",asOf);put("imported_at",now)
                put("sha256",sha);put("origin",origin)
            }
            w.insertWithOnConflict("nfl_feed_snapshots",null,meta,SQLiteDatabase.CONFLICT_REPLACE)
            w.insert("nfl_feed_events",null,ContentValues().apply{
                put("season",season);put("feed",feed.name);put("through_week",week)
                put("row_count",data.size);put("sha256",sha);put("imported_at",now)
            })
            w.setTransactionSuccessful()
        } finally{w.endTransaction()}
        return FeedLoadResult(feed,data.size,week,true,"Actualizado · ${data.size} filas · hasta week $week")
    }

    private fun teamRow(season:Int,feed:NflFeed,team:String):JSONObject? = db.readableDatabase.rawQuery(
        "SELECT payload FROM nfl_feed_rows WHERE season=? AND feed=? AND row_key=?",
        arrayOf(season.toString(),feed.name,team)
    ).use{c->if(c.moveToFirst()) JSONObject(c.getString(0)) else null}

    /** Pregame-only, double-counting-safe backstop. PBP remains the primary input if sufficiently sampled. */
    fun enriched(game:GameRecord,base:TeamMetrics?):TeamMetrics? {
        if(base==null)return null
        val official=if(base.games>=2 && base.plays>=100) base else {
            val snap=snapshot(game.season,NflFeed.TEAM_RATINGS)
            if(snap==null || snap.throughWeek>=game.week || snap.throughWeek<1)base else {
                val row=teamRow(game.season,NflFeed.TEAM_RATINGS,base.team)
                val off=row?.optString("off_epa_play")?.toDoubleOrNull()
                val def=row?.optString("def_epa_play")?.toDoubleOrNull()
                if(off!=null && def!=null && off.isFinite() && def.isFinite() &&
                    off in -2.0..2.0 && def in -2.0..2.0){
                    base.copy(offEpaPerPlay=off,defEpaAllowedPerPlay=def)
                }else base
            }
        }
        // Team Stats serves as a low-sample volume fallback, not an independent copy of PBP.
        if(official.games>0 && official.plays>0)return official
        val snap=snapshot(game.season,NflFeed.TEAM_STATS)
        if(snap==null || snap.throughWeek>=game.week || snap.throughWeek<1)return official
        val row=teamRow(game.season,NflFeed.TEAM_STATS,base.team) ?: return official
        val plays=row.optString("plays").toIntOrNull()
            ?: ((row.optString("passing_attempts").toIntOrNull() ?: 0) +
                (row.optString("rushing_attempts").toIntOrNull() ?: 0))
        if(plays<=0)return official
        return official.copy(plays=plays)
    }

    fun exportHistory(season:Int):JSONArray = JSONArray().apply{
        db.readableDatabase.rawQuery("SELECT feed,through_week,row_count,sha256,imported_at FROM nfl_feed_events WHERE season=? ORDER BY imported_at",
            arrayOf(season.toString())).use{c->
            while(c.moveToNext())put(JSONObject().apply{
                put("feed",c.getString(0));put("through_week",c.getInt(1));put("row_count",c.getInt(2))
                put("sha256",c.getString(3));put("imported_at",c.getLong(4));put("season",season)
            })
        }
    }

    fun export(season:Int):JSONArray = JSONArray().apply{
        all(season).forEach{snapshot->
            put(JSONObject().apply{
                put("feed",snapshot.feed.name);put("season",snapshot.season);put("through_week",snapshot.throughWeek)
                put("row_count",snapshot.rows);put("source_as_of",snapshot.sourceAsOf)
                put("imported_at",snapshot.importedAt);put("sha256",snapshot.sha256);put("origin",snapshot.origin)
                put("rows",JSONArray().apply{
                    db.readableDatabase.rawQuery("SELECT row_key,payload FROM nfl_feed_rows WHERE season=? AND feed=? ORDER BY row_key",
                        arrayOf(season.toString(),snapshot.feed.name)).use{c->
                        while(c.moveToNext())put(JSONObject().apply{
                            put("key",c.getString(0));put("payload",JSONObject(c.getString(1)))
                        })
                    }
                })
            })
        }
    }

    companion object{
        fun ensureTables(w:SQLiteDatabase){
            w.execSQL("""CREATE TABLE IF NOT EXISTS nfl_feed_snapshots(
                season INTEGER NOT NULL,feed TEXT NOT NULL,through_week INTEGER NOT NULL,
                row_count INTEGER NOT NULL,source_as_of TEXT NOT NULL,imported_at INTEGER NOT NULL,
                sha256 TEXT NOT NULL,origin TEXT NOT NULL,PRIMARY KEY(season,feed))""")
            w.execSQL("""CREATE TABLE IF NOT EXISTS nfl_feed_rows(
                season INTEGER NOT NULL,feed TEXT NOT NULL,row_key TEXT NOT NULL,payload TEXT NOT NULL,
                PRIMARY KEY(season,feed,row_key))""")
            w.execSQL("""CREATE TABLE IF NOT EXISTS nfl_feed_events(
                id INTEGER PRIMARY KEY AUTOINCREMENT,season INTEGER NOT NULL,feed TEXT NOT NULL,
                through_week INTEGER NOT NULL,row_count INTEGER NOT NULL,sha256 TEXT NOT NULL,
                imported_at INTEGER NOT NULL)""")
        }

        /** Caller owns the surrounding Data Vault transaction. Does not replace newer local snapshots. */
        fun restore(w:SQLiteDatabase,root:JSONObject){
            val feeds=root.optJSONArray("data_center_feeds") ?: return
            for(i in 0 until feeds.length()){
                val info=feeds.optJSONObject(i) ?: continue
                val kind=runCatching{NflFeed.valueOf(info.getString("feed"))}.getOrNull() ?: continue
                val season=info.optInt("season",0)
                if(season !in 1999..2100)continue
                val exists=w.rawQuery("SELECT 1 FROM nfl_feed_snapshots WHERE season=? AND feed=?",arrayOf(season.toString(),kind.name))
                    .use{it.moveToFirst()}
                if(exists)continue
                val rows=info.optJSONArray("rows") ?: continue
                val n=info.optInt("row_count",-1)
                require(n==rows.length() && n>0){"Data Center corrupto: ${kind.name}"}
                w.insertOrThrow("nfl_feed_snapshots",null,ContentValues().apply{
                    put("season",season);put("feed",kind.name);put("through_week",info.getInt("through_week"))
                    put("row_count",n);put("source_as_of",info.optString("source_as_of",""))
                    put("imported_at",info.optLong("imported_at",0L));put("sha256",info.getString("sha256"))
                    put("origin",info.optString("origin","RESTORE"))
                })
                for(k in 0 until rows.length()){
                    val row=rows.getJSONObject(k)
                    w.insertOrThrow("nfl_feed_rows",null,ContentValues().apply{
                        put("season",season);put("feed",kind.name);put("row_key",row.getString("key"))
                        put("payload",row.getJSONObject("payload").toString())
                    })
                }
            }
            val events=root.optJSONArray("data_center_history") ?: return
            for(i in 0 until events.length()){
                val e=events.optJSONObject(i) ?: continue
                val kind=runCatching{NflFeed.valueOf(e.getString("feed"))}.getOrNull() ?: continue
                val season=e.optInt("season",0)
                if(season !in 1999..2100)continue
                val hash=e.optString("sha256","")
                if(hash.length!=64)continue
                val imported=e.optLong("imported_at",0L)
                val seen=w.rawQuery("SELECT 1 FROM nfl_feed_events WHERE season=? AND feed=? AND sha256=? AND imported_at=? LIMIT 1",
                    arrayOf(season.toString(),kind.name,hash,imported.toString())).use{it.moveToFirst()}
                if(!seen)w.insert("nfl_feed_events",null,ContentValues().apply{
                    put("season",season);put("feed",kind.name);put("through_week",e.optInt("through_week",0))
                    put("row_count",e.optInt("row_count",0));put("sha256",hash);put("imported_at",imported)
                })
            }
        }
    }
}
