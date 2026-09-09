package com.nfltotalslab.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelper(context: Context) : SQLiteOpenHelper(context, "nfl_totals_lab.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE games(
                game_id TEXT PRIMARY KEY,
                season INTEGER NOT NULL,
                week INTEGER NOT NULL,
                game_type TEXT,
                game_day TEXT,
                game_time TEXT,
                away_team TEXT,
                home_team TEXT,
                away_score INTEGER,
                home_score INTEGER,
                total_line REAL,
                spread_line REAL,
                roof TEXT,
                surface TEXT,
                temp REAL,
                wind REAL,
                synced_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE team_metrics(
                season INTEGER NOT NULL,
                team TEXT NOT NULL,
                games INTEGER NOT NULL,
                plays INTEGER NOT NULL,
                drives INTEGER NOT NULL,
                points_for INTEGER NOT NULL,
                off_epa REAL NOT NULL,
                def_epa REAL NOT NULL,
                success_rate REAL NOT NULL,
                explosive_rate REAL NOT NULL,
                turnover_rate REAL NOT NULL,
                td_per_drive REAL NOT NULL,
                fg_per_drive REAL NOT NULL,
                drives_per_game REAL NOT NULL,
                points_per_drive REAL NOT NULL,
                roster_count INTEGER NOT NULL,
                injury_count INTEGER NOT NULL,
                PRIMARY KEY(season, team)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE predictions(
                id INTEGER PRIMARY KEY,
                game_id TEXT NOT NULL,
                season INTEGER NOT NULL,
                week INTEGER NOT NULL,
                away_team TEXT NOT NULL,
                home_team TEXT NOT NULL,
                line REAL NOT NULL,
                pick TEXT NOT NULL,
                probability REAL NOT NULL,
                projection REAL NOT NULL,
                classification TEXT NOT NULL,
                engines TEXT NOT NULL,
                final_total INTEGER,
                result TEXT,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE bets(
                id INTEGER PRIMARY KEY,
                prediction_id INTEGER NOT NULL,
                game_id TEXT NOT NULL,
                market TEXT NOT NULL,
                odds REAL NOT NULL,
                stake REAL NOT NULL,
                status TEXT NOT NULL,
                pnl REAL NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE bank(
                id INTEGER PRIMARY KEY,
                amount REAL NOT NULL,
                note TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE kv(
                k TEXT PRIMARY KEY,
                v TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // V0.2 is the first native schema. Future migrations go here.
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS games")
            db.execSQL("DROP TABLE IF EXISTS team_metrics")
            db.execSQL("DROP TABLE IF EXISTS predictions")
            db.execSQL("DROP TABLE IF EXISTS bets")
            db.execSQL("DROP TABLE IF EXISTS bank")
            db.execSQL("DROP TABLE IF EXISTS kv")
            onCreate(db)
        }
    }

    fun upsertGames(games: List<GameRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            games.forEach { g ->
                val v = ContentValues().apply {
                    put("game_id", g.gameId); put("season", g.season); put("week", g.week)
                    put("game_type", g.gameType); put("game_day", g.gameDay); put("game_time", g.gameTime)
                    put("away_team", g.awayTeam); put("home_team", g.homeTeam)
                    if (g.awayScore == null) putNull("away_score") else put("away_score", g.awayScore)
                    if (g.homeScore == null) putNull("home_score") else put("home_score", g.homeScore)
                    if (g.totalLine == null) putNull("total_line") else put("total_line", g.totalLine)
                    if (g.spreadLine == null) putNull("spread_line") else put("spread_line", g.spreadLine)
                    put("roof", g.roof); put("surface", g.surface)
                    if (g.temp == null) putNull("temp") else put("temp", g.temp)
                    if (g.wind == null) putNull("wind") else put("wind", g.wind)
                    put("synced_at", now)
                }
                db.insertWithOnConflict("games", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun loadGames(season: Int): List<GameRecord> {
        val out = mutableListOf<GameRecord>()
        readableDatabase.rawQuery(
            "SELECT game_id,season,week,game_type,game_day,game_time,away_team,home_team,away_score,home_score,total_line,spread_line,roof,surface,temp,wind FROM games WHERE season=? ORDER BY week,game_day,game_time",
            arrayOf(season.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += GameRecord(
                    c.getString(0), c.getInt(1), c.getInt(2), c.getString(3) ?: "REG",
                    c.getString(4) ?: "", c.getString(5) ?: "", c.getString(6) ?: "",
                    c.getString(7) ?: "", if (c.isNull(8)) null else c.getInt(8),
                    if (c.isNull(9)) null else c.getInt(9),
                    if (c.isNull(10)) null else c.getDouble(10),
                    if (c.isNull(11)) null else c.getDouble(11),
                    if (c.isNull(12)) null else c.getString(12),
                    if (c.isNull(13)) null else c.getString(13),
                    if (c.isNull(14)) null else c.getDouble(14),
                    if (c.isNull(15)) null else c.getDouble(15)
                )
            }
        }
        return out
    }

    fun upsertMetrics(list: List<TeamMetrics>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            list.forEach { m ->
                val v = ContentValues().apply {
                    put("season",m.season); put("team",m.team); put("games",m.games); put("plays",m.plays)
                    put("drives",m.drives); put("points_for",m.pointsFor); put("off_epa",m.offEpaPerPlay)
                    put("def_epa",m.defEpaAllowedPerPlay); put("success_rate",m.successRate)
                    put("explosive_rate",m.explosiveRate); put("turnover_rate",m.turnoverRate)
                    put("td_per_drive",m.tdPerDrive); put("fg_per_drive",m.fgPerDrive)
                    put("drives_per_game",m.drivesPerGame); put("points_per_drive",m.pointsPerDrive)
                    put("roster_count",m.rosterCount); put("injury_count",m.injuryCount)
                }
                db.insertWithOnConflict("team_metrics", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun loadMetrics(season: Int): List<TeamMetrics> {
        val out = mutableListOf<TeamMetrics>()
        readableDatabase.rawQuery("""
            SELECT season,team,games,plays,drives,points_for,off_epa,def_epa,success_rate,
            explosive_rate,turnover_rate,td_per_drive,fg_per_drive,drives_per_game,points_per_drive,
            roster_count,injury_count FROM team_metrics WHERE season=? ORDER BY team
        """.trimIndent(), arrayOf(season.toString())).use { c ->
            while (c.moveToNext()) out += TeamMetrics(
                c.getInt(0),c.getString(1),c.getInt(2),c.getInt(3),c.getInt(4),c.getInt(5),
                c.getDouble(6),c.getDouble(7),c.getDouble(8),c.getDouble(9),c.getDouble(10),
                c.getDouble(11),c.getDouble(12),c.getDouble(13),c.getDouble(14),c.getInt(15),c.getInt(16)
            )
        }
        return out
    }

    fun savePrediction(p: Prediction) {
        val v = ContentValues().apply {
            put("id", p.id); put("game_id",p.gameId); put("season",p.season); put("week",p.week)
            put("away_team",p.awayTeam); put("home_team",p.homeTeam); put("line",p.line)
            put("pick",p.pick); put("probability",p.probability); put("projection",p.projection)
            put("classification",p.classification)
            put("engines", p.engines.joinToString("|") { "${it.name},${it.projection},${it.pOver},${it.pUnder}" })
            if (p.finalTotal == null) putNull("final_total") else put("final_total",p.finalTotal)
            put("result",p.result); put("created_at",p.createdAt)
        }
        writableDatabase.insertWithOnConflict("predictions",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun settlePredictions(games: List<GameRecord>) {
        val finals = games.filter { it.finished }.associateBy { it.gameId }
        val db = writableDatabase
        readableDatabase.rawQuery("SELECT id,game_id,line,pick FROM predictions WHERE result IS NULL", null).use { c ->
            while (c.moveToNext()) {
                val id=c.getLong(0); val gameId=c.getString(1); val line=c.getDouble(2); val pick=c.getString(3)
                val total=finals[gameId]?.finalTotal ?: continue
                val result = when {
                    total.toDouble() == line -> "PUSH"
                    pick == "OVER" && total > line -> "WIN"
                    pick == "UNDER" && total < line -> "WIN"
                    else -> "LOSS"
                }
                val v=ContentValues().apply { put("final_total",total); put("result",result) }
                db.update("predictions",v,"id=?",arrayOf(id.toString()))
            }
        }
    }

    fun loadPredictions(): List<Prediction> {
        val out=mutableListOf<Prediction>()
        readableDatabase.rawQuery("""
            SELECT id,game_id,season,week,away_team,home_team,line,pick,probability,projection,
            classification,engines,final_total,result,created_at FROM predictions ORDER BY created_at DESC
        """.trimIndent(), null).use { c ->
            while(c.moveToNext()){
                val engines=(c.getString(11) ?: "").split("|").mapNotNull { s ->
                    val p=s.split(",")
                    if(p.size==4) runCatching { EngineSlice(p[0],p[1].toDouble(),p[2].toDouble(),p[3].toDouble()) }.getOrNull() else null
                }
                out += Prediction(
                    c.getLong(0),c.getString(1),c.getInt(2),c.getInt(3),c.getString(4),c.getString(5),
                    c.getDouble(6),c.getString(7),c.getDouble(8),c.getDouble(9),c.getString(10),
                    c.getLong(14),if(c.isNull(12))null else c.getInt(12),if(c.isNull(13))null else c.getString(13),engines
                )
            }
        }
        return out
    }

    fun saveBet(b: BetRecord) {
        val v=ContentValues().apply{
            put("id",b.id);put("prediction_id",b.predictionId);put("game_id",b.gameId);put("market",b.market)
            put("odds",b.odds);put("stake",b.stake);put("status",b.status);put("pnl",b.pnl);put("created_at",b.createdAt)
        }
        writableDatabase.insertWithOnConflict("bets",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadBets(): List<BetRecord> {
        val out=mutableListOf<BetRecord>()
        readableDatabase.rawQuery("SELECT id,prediction_id,game_id,market,odds,stake,status,pnl,created_at FROM bets ORDER BY created_at DESC",null).use{c->
            while(c.moveToNext()) out+=BetRecord(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3),c.getDouble(4),c.getDouble(5),c.getString(6),c.getDouble(7),c.getLong(8))
        }
        return out
    }

    fun saveBankEntry(e: BankEntry) {
        val v=ContentValues().apply{put("id",e.id);put("amount",e.amount);put("note",e.note);put("created_at",e.createdAt)}
        writableDatabase.insertWithOnConflict("bank",null,v,SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadBank(): List<BankEntry> {
        val out=mutableListOf<BankEntry>()
        readableDatabase.rawQuery("SELECT id,amount,note,created_at FROM bank ORDER BY created_at DESC",null).use{c->
            while(c.moveToNext()) out+=BankEntry(c.getLong(0),c.getDouble(1),c.getString(2),c.getLong(3))
        }
        return out
    }

    fun putKv(k:String,v:String){
        val cv=ContentValues().apply{put("k",k);put("v",v)}
        writableDatabase.insertWithOnConflict("kv",null,cv,SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun getKv(k:String):String? =
        readableDatabase.rawQuery("SELECT v FROM kv WHERE k=?",arrayOf(k)).use{c->if(c.moveToFirst())c.getString(0)else null}
}
