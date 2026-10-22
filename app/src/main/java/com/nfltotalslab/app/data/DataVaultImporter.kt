package com.nfltotalslab.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

data class VaultImportPreview(
    val schema:String,
    val appVersion:String,
    val season:Int,
    val currentModelVersion:String,
    val games:Int,
    val metrics:Int,
    val predictions:Int,
    val shadows:Int,
    val calibrations:Int,
    val matchupFeatures:Int,
    val matchupSnapshots:Int,
    val matchupScores:Int,
    val bets:Int,
    val bank:Int,
    val modelVersions:List<String>
)

data class VaultRestoreResult(
    val schema:String,
    val season:Int,
    val inserted:Int,
    val skipped:Int
){
    fun shortSummary():String = "IMPORT ✓ · +$inserted filas · $skipped existentes omitidas"
}

object DataVaultImporter {
    private const val PREFIX="NFL_TOTALS_LAB_DATA_VAULT"

    fun preview(raw:String):VaultImportPreview{
        val root=parseRoot(raw)
        val predictions=arr(root,"predictions")
        val versions=(0 until predictions.length())
            .mapNotNull{predictions.optJSONObject(it)?.optString("modelVersion")?.takeIf{v->v.isNotBlank()}}
            .distinct()
            .sorted()

        return VaultImportPreview(
            schema=root.optString("schema"),
            appVersion=root.optString("app_version","legacy"),
            season=root.optInt("season",0),
            currentModelVersion=root.optString("current_model_version","legacy"),
            games=arr(root,"games").length(),
            metrics=arr(root,"metrics").length(),
            predictions=predictions.length(),
            shadows=arr(root,"shadows").length(),
            calibrations=arr(root,"calibrations").length(),
            matchupFeatures=arr(root,"matchup_features","matchupFeatures").length(),
            matchupSnapshots=arr(root,"matchup_snapshots","matchupSnapshots").length(),
            matchupScores=arr(root,"matchup_score_census","matchupScores").length(),
            bets=arr(root,"bets").length(),
            bank=arr(root,"bank").length(),
            modelVersions=versions
        )
    }

    fun restore(db:DbHelper,raw:String):VaultRestoreResult{
        val root=parseRoot(raw)
        val schema=root.optString("schema")
        val season=root.optInt("season",0)
        val w=db.writableDatabase
        var inserted=0
        var skipped=0

        fun insert(table:String,values:ContentValues){
            val row=w.insertWithOnConflict(table,null,values,SQLiteDatabase.CONFLICT_IGNORE)
            if(row==-1L)skipped++ else inserted++
        }

        w.beginTransaction()
        try{
            each(root,"games"){j->
                insert("games",ContentValues().apply{
                    put("game_id",j.req("gameId"));put("season",j.optInt("season",season));put("week",j.optInt("week",0))
                    put("game_type",j.optString("gameType","REG"));put("game_day",j.optString("gameDay",""));put("game_time",j.optString("gameTime",""))
                    put("away_team",j.req("awayTeam"));put("home_team",j.req("homeTeam"))
                    putJson("away_score",j.value("awayScore"));putJson("home_score",j.value("homeScore"))
                    putJson("total_line",j.value("totalLine"));putJson("spread_line",j.value("spreadLine"))
                    putJson("roof",j.value("roof"));putJson("surface",j.value("surface"));putJson("temp",j.value("temp"));putJson("wind",j.value("wind"))
                    put("synced_at",System.currentTimeMillis())
                })
            }

            each(root,"metrics"){j->
                insert("team_metrics",ContentValues().apply{
                    put("season",j.optInt("season",season));put("team",j.req("team"));put("games",j.optInt("games",0));put("plays",j.optInt("plays",0))
                    put("drives",j.optInt("drives",0));put("points_for",j.optInt("pointsFor",0));put("off_epa",j.optDouble("offEpaPerPlay",0.0))
                    put("def_epa",j.optDouble("defEpaAllowedPerPlay",0.0));put("success_rate",j.optDouble("successRate",.43))
                    put("explosive_rate",j.optDouble("explosiveRate",.10));put("turnover_rate",j.optDouble("turnoverRate",.018))
                    put("td_per_drive",j.optDouble("tdPerDrive",.22));put("fg_per_drive",j.optDouble("fgPerDrive",.15))
                    put("drives_per_game",j.optDouble("drivesPerGame",10.6));put("points_per_drive",j.optDouble("pointsPerDrive",2.05))
                    put("roster_count",j.optInt("rosterCount",0));put("injury_count",j.optInt("injuryCount",0))
                })
            }

            each(root,"predictions"){j->
                insert("predictions",ContentValues().apply{
                    put("id",j.optLong("id",System.currentTimeMillis()));put("game_id",j.req("gameId"));put("season",j.optInt("season",season));put("week",j.optInt("week",0))
                    put("away_team",j.req("awayTeam"));put("home_team",j.req("homeTeam"));put("line",j.optDouble("line",44.5))
                    put("pick",j.optString("pick","UNDER"));put("probability",j.optDouble("probability",.5));put("projection",j.optDouble("projection",44.5))
                    put("classification",j.optString("classification","IMPORTED"));put("engines",enginesString(j.optJSONArray("engines")))
                    put("analysis_source",j.optString("analysisSource","AUTO_CENSUS"));put("model_version",j.optString("modelVersion",root.optString("current_model_version","legacy")))
                    put("input_key",j.optString("inputKey",""));putJson("final_total",j.value("finalTotal"));putJson("result",j.value("result"));put("created_at",j.optLong("createdAt",System.currentTimeMillis()))
                })
            }

            each(root,"shadows"){j->
                insert("shadow_predictions",ContentValues().apply{
                    put("id",j.optLong("id",System.currentTimeMillis()));put("game_id",j.req("gameId"));put("season",j.optInt("season",season));put("week",j.optInt("week",0))
                    put("away_team",j.req("awayTeam"));put("home_team",j.req("homeTeam"));put("line",j.optDouble("line",44.5));put("model_name",j.req("modelName"))
                    put("pick",j.optString("pick","UNDER"));put("probability",j.optDouble("probability",.5));put("projection",j.optDouble("projection",44.5))
                    put("input_key",j.optString("inputKey",""));putJson("final_total",j.value("finalTotal"));putJson("result",j.value("result"));put("created_at",j.optLong("createdAt",System.currentTimeMillis()))
                })
            }

            each(root,"calibrations"){j->
                insert("calibration_snapshots",ContentValues().apply{
                    val id=j.optLong("id",System.currentTimeMillis())
                    put("id",id);put("prediction_id",j.optLong("predictionId",id));put("game_id",j.req("gameId"));put("season",j.optInt("season",season));put("week",j.optInt("week",0))
                    put("away_team",j.req("awayTeam"));put("home_team",j.req("homeTeam"));put("line",j.optDouble("line",44.5));put("pick",j.optString("pick","UNDER"))
                    put("raw_probability",j.optDouble("rawProbability",.5));put("calibrated_probability",j.optDouble("calibratedProbability",.5));put("intercept",j.optDouble("intercept",0.0))
                    put("slope",j.optDouble("slope",1.0));put("train_n",j.optInt("trainN",0));put("maturity",j.optString("maturity","IMPORTED"));put("input_key",j.optString("inputKey",""))
                    putJson("final_total",j.value("finalTotal"));putJson("result",j.value("result"));put("created_at",j.optLong("createdAt",System.currentTimeMillis()))
                })
            }

            each(root,"matchup_features","matchupFeatures"){j->
                insert("matchup_team_features",ContentValues().apply{
                    put("season",j.optInt("season",season));put("team",j.req("team"));put("feature",j.req("feature"));put("side",j.optString("side",""))
                    put("value",j.optDouble("value",0.0));put("sample_n",j.optInt("sampleN",0));put("source",j.optString("source","IMPORTED"));put("scope",j.optString("scope","IMPORTED"))
                    put("reliability",j.optDouble("reliability",0.0));put("updated_at",j.optLong("updatedAt",System.currentTimeMillis()))
                })
            }

            each(root,"matchup_snapshots","matchupSnapshots"){j->
                insert("matchup_feature_snapshots",ContentValues().apply{
                    put("game_id",j.req("gameId"));put("season",j.optInt("season",season));put("week",j.optInt("week",0));put("team",j.req("team"));put("opponent",j.optString("opponent",""))
                    put("feature",j.req("feature"));put("side",j.optString("side",""));put("value",j.optDouble("value",0.0));put("sample_n",j.optInt("sampleN",0))
                    put("source",j.optString("source","IMPORTED"));put("scope",j.optString("scope","IMPORTED"));put("reliability",j.optDouble("reliability",0.0))
                    putJson("core_prediction_id",j.value("corePredictionId"));putJson("core_projection",j.value("coreProjection"));putJson("market_line",j.value("marketLine"))
                    put("core_input_key",j.optString("coreInputKey",""));put("created_at",j.optLong("createdAt",System.currentTimeMillis()));putJson("final_total",j.value("finalTotal"))
                })
            }

            each(root,"matchup_score_census","matchupScores"){j->
                insert("matchup_score_census",ContentValues().apply{
                    put("game_id",j.req("gameId"));put("season",j.optInt("season",season));put("week",j.optInt("week",0));put("away_team",j.req("awayTeam"));put("home_team",j.req("homeTeam"))
                    put("away_projection",j.optDouble("awayProjection",0.0));put("home_projection",j.optDouble("homeProjection",0.0));put("total_projection",j.optDouble("totalProjection",0.0));put("market_line",j.optDouble("marketLine",44.5))
                    put("pick",j.optString("pick","UNDER"));put("probability",j.optDouble("probability",.5));put("reliability",j.optDouble("reliability",0.0));put("coverage_count",j.optInt("coverageCount",0));put("coverage_total",j.optInt("coverageTotal",0))
                    put("status",j.optString("status","IMPORTED"));put("away_feature_adjustment",j.optDouble("awayFeatureAdjustment",0.0));put("home_feature_adjustment",j.optDouble("homeFeatureAdjustment",0.0))
                    put("away_learning_adjustment",j.optDouble("awayLearningAdjustment",0.0));put("home_learning_adjustment",j.optDouble("homeLearningAdjustment",0.0));put("away_learning_n",j.optInt("awayLearningN",0));put("home_learning_n",j.optInt("homeLearningN",0))
                    put("input_key",j.optString("inputKey",""));put("created_at",j.optLong("createdAt",System.currentTimeMillis()));putJson("final_away",j.value("finalAway"));putJson("final_home",j.value("finalHome"));putJson("result",j.value("result"))
                })
            }

            each(root,"bets"){j->
                insert("bets",ContentValues().apply{
                    put("id",j.optLong("id",System.currentTimeMillis()));put("prediction_id",j.optLong("predictionId",0L));put("game_id",j.req("gameId"));put("market",j.optString("market",""))
                    put("odds",j.optDouble("odds",1.91));put("stake",j.optDouble("stake",0.0));put("status",j.optString("status","PENDING"));put("pnl",j.optDouble("pnl",0.0));put("created_at",j.optLong("createdAt",System.currentTimeMillis()))
                })
            }

            each(root,"bank"){j->
                insert("bank",ContentValues().apply{
                    put("id",j.optLong("id",System.currentTimeMillis()));put("amount",j.optDouble("amount",0.0));put("note",j.optString("note","Imported Data Vault"));put("created_at",j.optLong("createdAt",System.currentTimeMillis()))
                })
            }

            mergeKv(w,"last_sync",root.optLongOrNull("last_sync"))
            mergeKv(w,"last_deep_sync",root.optLongOrNull("last_deep_sync"))
            w.setTransactionSuccessful()
        }finally{
            w.endTransaction()
        }

        return VaultRestoreResult(schema,season,inserted,skipped)
    }

    private fun parseRoot(raw:String):JSONObject{
        val root=runCatching{JSONObject(raw)}.getOrElse{throw IllegalArgumentException("JSON inválido: ${it.message}")}
        val schema=root.optString("schema","")
        require(schema.startsWith(PREFIX)){"No es un NFL Totals Lab Data Vault compatible"}
        val season=root.optInt("season",-1)
        require(season in 2000..2100){"Temporada inválida en Data Vault"}
        return root
    }

    private fun arr(root:JSONObject,vararg names:String):JSONArray{
        names.forEach{name->
            if(root.has(name) && !root.isNull(name)){
                val v=root.opt(name)
                if(v is JSONArray)return v
            }
        }
        return JSONArray()
    }

    private inline fun each(root:JSONObject,vararg names:String,block:(JSONObject)->Unit){
        val a=arr(root,*names)
        for(i in 0 until a.length())a.optJSONObject(i)?.let(block)
    }

    private fun JSONObject.req(key:String):String{
        val s=optString(key,"").trim()
        require(s.isNotEmpty()){ "Campo obligatorio vacío: $key" }
        return s
    }

    private fun JSONObject.value(key:String):Any? =
        if(!has(key) || isNull(key))null else opt(key)

    private fun JSONObject.optLongOrNull(key:String):Long? =
        if(!has(key) || isNull(key))null else optLong(key)

    private fun ContentValues.putJson(key:String,value:Any?){
        when(value){
            null,JSONObject.NULL -> putNull(key)
            is Int -> put(key,value)
            is Long -> put(key,value)
            is Double -> put(key,value)
            is Float -> put(key,value)
            is Boolean -> put(key,if(value)1 else 0)
            is Number -> put(key,value.toDouble())
            else -> put(key,value.toString())
        }
    }

    private fun enginesString(a:JSONArray?):String{
        if(a==null)return ""
        return (0 until a.length()).mapNotNull{a.optJSONObject(it)}.joinToString("|"){e->
            listOf(
                e.optString("name",""),
                e.optDouble("projection",0.0).toString(),
                e.optDouble("pOver",.5).toString(),
                e.optDouble("pUnder",.5).toString()
            ).joinToString(",")
        }
    }

    private fun mergeKv(w:SQLiteDatabase,key:String,incoming:Long?){
        if(incoming==null)return
        val current=w.rawQuery("SELECT v FROM kv WHERE k=? LIMIT 1",arrayOf(key)).use{c->
            if(c.moveToFirst())c.getString(0)?.toLongOrNull() else null
        }
        if(current==null || incoming>current){
            val cv=ContentValues().apply{put("k",key);put("v",incoming.toString())}
            w.insertWithOnConflict("kv",null,cv,SQLiteDatabase.CONFLICT_REPLACE)
        }
    }
}
