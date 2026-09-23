package com.nfltotalslab.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

class MatchupFeatureService {

    private data class PbpAgg(
        var rushAtt:Int=0,
        var rushYards:Double=0.0,
        var rightRushAtt:Int=0,
        var rightRushYards:Double=0.0,
        var firstDownRushAtt:Int=0,
        var firstDownRushYards:Double=0.0,
        var designedRush:Int=0,
        var dropbacks:Int=0,
        var defRushAtt:Int=0,
        var defRushYards:Double=0.0,
        var defRightRushAtt:Int=0,
        var defRightRushYards:Double=0.0,
        var defFirstDownRushAtt:Int=0,
        var defFirstDownRushYards:Double=0.0
    )

    private data class PressureAgg(
        var pressures:Double=0.0,
        var opportunities:Double=0.0,
        var pctSum:Double=0.0,
        var pctRows:Int=0
    )

    private data class YacAgg(
        var yac:Double=0.0,
        var carries:Double=0.0
    )

    suspend fun fetchFeatures(season:Int):List<MatchupFeature> = withContext(Dispatchers.IO){
        val out=mutableListOf<MatchupFeature>()
        runCatching{fetchPbpFeatures(season)}.onSuccess{out+=it}
        runCatching{fetchPressureFeatures(season)}.onSuccess{out+=it}
        runCatching{fetchRbYacFeatures(season)}.onSuccess{out+=it}
        out.groupBy{"${it.season}|${it.team}|${it.feature}"}
            .mapNotNull{(_,rows)->rows.maxByOrNull{it.updatedAt}}
            .sortedWith(compareBy<MatchupFeature>{it.team}.thenBy{it.feature})
    }

    private fun fetchPbpFeatures(season:Int):List<MatchupFeature>{
        val url="https://github.com/nflverse/nflverse-data/releases/download/pbp/play_by_play_${season}.csv.gz"
        val conn=open(url)
        return try{
            GZIPInputStream(conn.inputStream).bufferedReader().use{br->
                val headers=Csv.parseLine(br.readLine())
                val iPost=Csv.index(headers,"posteam")
                val iDef=Csv.index(headers,"defteam")
                val iYards=Csv.index(headers,"yards_gained")
                val iRush=Csv.index(headers,"rush_attempt")
                val iDrop=Csv.index(headers,"qb_dropback")
                val iScramble=Csv.index(headers,"qb_scramble")
                val iKneel=Csv.index(headers,"qb_kneel")
                val iRunLoc=Csv.index(headers,"run_location")
                val iDown=Csv.index(headers,"down")
                val map=linkedMapOf<String,PbpAgg>()

                br.lineSequence().forEach{line->
                    val row=Csv.parseLine(line)
                    val post=Csv.value(row,iPost)?.let(::normalizeTeam)
                    val def=Csv.value(row,iDef)?.let(::normalizeTeam)
                    val yards=Csv.value(row,iYards)?.toDoubleOrNull() ?: 0.0
                    val rush=isOne(Csv.value(row,iRush))
                    val drop=isOne(Csv.value(row,iDrop))
                    val scramble=isOne(Csv.value(row,iScramble))
                    val kneel=isOne(Csv.value(row,iKneel))
                    val loc=Csv.value(row,iRunLoc)?.lowercase()
                    val down=Csv.value(row,iDown)?.toDoubleOrNull()?.roundToInt()

                    if(post!=null){
                        val a=map.getOrPut(post){PbpAgg()}
                        if(drop)a.dropbacks++
                        if(rush && !kneel){
                            a.rushAtt++
                            a.rushYards+=yards
                            if(!scramble)a.designedRush++
                            if(loc=="right"){
                                a.rightRushAtt++
                                a.rightRushYards+=yards
                            }
                            if(down==1){
                                a.firstDownRushAtt++
                                a.firstDownRushYards+=yards
                            }
                        }
                    }

                    if(def!=null && rush && !kneel){
                        val a=map.getOrPut(def){PbpAgg()}
                        a.defRushAtt++
                        a.defRushYards+=yards
                        if(loc=="right"){
                            a.defRightRushAtt++
                            a.defRightRushYards+=yards
                        }
                        if(down==1){
                            a.defFirstDownRushAtt++
                            a.defFirstDownRushYards+=yards
                        }
                    }
                }

                buildList{
                    map.forEach{(team,a)->
                        addRate(season,team,"rush_right_ypc","OFFENSE",a.rightRushYards,a.rightRushAtt,"NFLVERSE_PBP","THIS_SEASON",18,this)
                        val neutralPlays=a.designedRush+a.dropbacks
                        if(neutralPlays>0)add(MatchupFeature(season,team,"rush_rate","OFFENSE",a.designedRush.toDouble()/neutralPlays,neutralPlays,"NFLVERSE_PBP","THIS_SEASON",sampleReliability(neutralPlays,80)))
                        if(a.rushAtt>0)add(MatchupFeature(season,team,"rush_yards","OFFENSE",a.rushYards,a.rushAtt,"NFLVERSE_PBP","THIS_SEASON_CUMULATIVE",sampleReliability(a.rushAtt,45)))
                        addRate(season,team,"first_down_ypc","OFFENSE",a.firstDownRushYards,a.firstDownRushAtt,"NFLVERSE_PBP","THIS_SEASON",24,this)

                        addRate(season,team,"rush_right_ypc_allowed","DEFENSE_ALLOWED",a.defRightRushYards,a.defRightRushAtt,"NFLVERSE_PBP","THIS_SEASON",18,this)
                        addRate(season,team,"ypc_allowed","DEFENSE_ALLOWED",a.defRushYards,a.defRushAtt,"NFLVERSE_PBP","THIS_SEASON",45,this)
                        if(a.defRushAtt>0)add(MatchupFeature(season,team,"rush_yards_allowed","DEFENSE_ALLOWED",a.defRushYards,a.defRushAtt,"NFLVERSE_PBP","THIS_SEASON_CUMULATIVE",sampleReliability(a.defRushAtt,45)))
                        addRate(season,team,"first_down_ypc_allowed","DEFENSE_ALLOWED",a.defFirstDownRushYards,a.defFirstDownRushAtt,"NFLVERSE_PBP","THIS_SEASON",24,this)
                    }
                }
            }
        }finally{conn.disconnect()}
    }

    private fun fetchPressureFeatures(season:Int):List<MatchupFeature>{
        val url="https://github.com/nflverse/nflverse-data/releases/download/pfr_advstats/advstats_week_pass_${season}.csv"
        openText(url).use{br->
            val headers=Csv.parseLine(br.readLine())
            val iTeam=Csv.index(headers,"team","recent_team")
            val iOpp=Csv.index(headers,"opponent","opp")
            val iPress=Csv.index(headers,"times_pressured")
            val iPct=Csv.index(headers,"times_pressured_pct","pressure_pct")
            val iAtt=Csv.index(headers,"pass_attempts","passing_attempts","attempts")
            val iSack=Csv.index(headers,"times_sacked","sacks")
            if(iTeam<0 || iOpp<0 || (iPress<0 && iPct<0))return emptyList()

            val off=linkedMapOf<String,PressureAgg>()
            val def=linkedMapOf<String,PressureAgg>()
            br.lineSequence().forEach{line->
                val row=Csv.parseLine(line)
                val team=Csv.value(row,iTeam)?.let(::normalizeTeam) ?: return@forEach
                val opp=Csv.value(row,iOpp)?.let(::normalizeTeam) ?: return@forEach
                val pressures=Csv.value(row,iPress)?.toDoubleOrNull()
                val pctRaw=Csv.value(row,iPct)?.toDoubleOrNull()
                val pct=pctRaw?.let{if(it>1.0)it/100.0 else it}
                val att=Csv.value(row,iAtt)?.toDoubleOrNull()
                val sacks=Csv.value(row,iSack)?.toDoubleOrNull() ?: 0.0
                val opportunities=when{
                    att!=null -> (att+sacks).coerceAtLeast(0.0)
                    pressures!=null && pct!=null && pct>0.0 -> pressures/pct
                    else -> null
                }
                fun apply(a:PressureAgg){
                    if(pressures!=null)a.pressures+=pressures
                    if(opportunities!=null && opportunities>0.0)a.opportunities+=opportunities
                    if(pct!=null){a.pctSum+=pct;a.pctRows++}
                }
                apply(off.getOrPut(team){PressureAgg()})
                apply(def.getOrPut(opp){PressureAgg()})
            }
            return buildList{
                off.forEach{(team,a)->pressureFeature(season,team,"pressure_allowed_rate","OFFENSE",a)?.let(::add)}
                def.forEach{(team,a)->pressureFeature(season,team,"pressure_rate","DEFENSE",a)?.let(::add)}
            }
        }
    }

    private fun fetchRbYacFeatures(season:Int):List<MatchupFeature>{
        val url="https://github.com/nflverse/nflverse-data/releases/download/pfr_advstats/advstats_week_rush_${season}.csv"
        openText(url).use{br->
            val headers=Csv.parseLine(br.readLine())
            val iTeam=Csv.index(headers,"team","recent_team")
            val iOpp=Csv.index(headers,"opponent","opp")
            val iPos=Csv.index(headers,"position","pos")
            val iAtt=Csv.index(headers,"rushing_attempts","rush_attempts","carries","attempts")
            val iYac=Csv.index(headers,"rushing_yards_after_contact","yards_after_contact","yac")
            val iYacAvg=Csv.index(headers,"rushing_yards_after_contact_avg","yards_after_contact_avg","yac_per_att","yac_per_carry")
            if(iTeam<0 || iOpp<0 || iPos<0 || (iYac<0 && iYacAvg<0))return emptyList()

            val off=linkedMapOf<String,YacAgg>()
            val def=linkedMapOf<String,YacAgg>()
            br.lineSequence().forEach{line->
                val row=Csv.parseLine(line)
                val pos=Csv.value(row,iPos)?.uppercase() ?: return@forEach
                if(pos !in setOf("RB","HB","FB"))return@forEach
                val team=Csv.value(row,iTeam)?.let(::normalizeTeam) ?: return@forEach
                val opp=Csv.value(row,iOpp)?.let(::normalizeTeam) ?: return@forEach
                val rawAtt=Csv.value(row,iAtt)?.toDoubleOrNull()
                val rawYac=Csv.value(row,iYac)?.toDoubleOrNull()
                val rawAvg=Csv.value(row,iYacAvg)?.toDoubleOrNull()
                val carries=when{
                    rawAtt!=null && rawAtt>0.0 -> rawAtt
                    rawYac!=null && rawAvg!=null && rawAvg>0.0 -> rawYac/rawAvg
                    else -> null
                } ?: return@forEach
                val yac=rawYac ?: rawAvg?.times(carries) ?: return@forEach
                fun apply(a:YacAgg){a.yac+=yac;a.carries+=carries}
                apply(off.getOrPut(team){YacAgg()})
                apply(def.getOrPut(opp){YacAgg()})
            }
            return buildList{
                off.forEach{(team,a)->
                    if(a.carries>0.0)add(MatchupFeature(season,team,"rb_yac_per_carry","OFFENSE",a.yac/a.carries,a.carries.roundToInt(),"NFLVERSE_PFR_ADV_RUSH","THIS_SEASON",sampleReliability(a.carries.roundToInt(),30)))
                }
                def.forEach{(team,a)->
                    if(a.carries>0.0)add(MatchupFeature(season,team,"rb_yac_allowed","DEFENSE_ALLOWED",a.yac/a.carries,a.carries.roundToInt(),"NFLVERSE_PFR_ADV_RUSH","THIS_SEASON",sampleReliability(a.carries.roundToInt(),30)))
                }
            }
        }
    }

    private fun pressureFeature(season:Int,team:String,name:String,side:String,a:PressureAgg):MatchupFeature?{
        val value=when{
            a.opportunities>0.0 -> a.pressures/a.opportunities
            a.pctRows>0 -> a.pctSum/a.pctRows
            else -> return null
        }.coerceIn(0.0,1.0)
        val n=if(a.opportunities>0.0)a.opportunities.roundToInt() else a.pctRows
        return MatchupFeature(season,team,name,side,value,n,"NFLVERSE_PFR_ADV_PASS","THIS_SEASON",sampleReliability(n,80))
    }

    private fun addRate(season:Int,team:String,feature:String,side:String,numerator:Double,denominator:Int,source:String,scope:String,priorN:Int,target:MutableList<MatchupFeature>){
        if(denominator<=0)return
        target+=MatchupFeature(season,team,feature,side,numerator/denominator,denominator,source,scope,sampleReliability(denominator,priorN))
    }

    private fun sampleReliability(n:Int,priorN:Int):Double =
        if(n<=0)0.0 else (n.toDouble()/(n+priorN).toDouble()).coerceIn(0.0,1.0)

    private fun isOne(raw:String?):Boolean = raw?.toDoubleOrNull()?.let{it>=0.5}==true

    private fun normalizeTeam(raw:String):String = when(raw.uppercase()){
        "LAR"->"LA";"WSH"->"WAS";"GNB"->"GB";"KAN"->"KC";"NWE"->"NE";
        "NOR"->"NO";"SFO"->"SF";"TAM"->"TB";"LVR"->"LV";else->raw.uppercase()
    }

    private fun openText(url:String):BufferedReader{
        val c=open(url)
        return object:BufferedReader(InputStreamReader(c.inputStream,Charsets.UTF_8)){
            override fun close(){super.close();c.disconnect()}
        }
    }

    private fun open(url:String):HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply{
            requestMethod="GET";connectTimeout=15000;readTimeout=120000
            instanceFollowRedirects=true
            setRequestProperty("User-Agent","NFL-Totals-Lab-Matchup-Features/0.9.3")
            connect()
            if(responseCode !in 200..299)throw IllegalStateException("HTTP $responseCode en $url")
        }
}
