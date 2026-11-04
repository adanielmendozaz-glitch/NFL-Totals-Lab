package com.nfltotalslab.app.ui

import android.content.Intent
import com.nfltotalslab.app.branding.teamBrand
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.nfltotalslab.app.data.DbHelper
import com.nfltotalslab.app.data.TeamMetrics
import com.nfltotalslab.app.data.MatchupFeature
import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.GameRosterIntelligence
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class SimPlay(val away:Boolean,val start:Int,val finish:Int,val down:Int,val distance:Int,val seconds:Int,val kind:String,val yards:Int,val points:Int,val awayScore:Int,val homeScore:Int,val elapsed:Int,val passer:String="",val receiver:String="",val receiverPosition:String="",val rusher:String="")
private val offenseRoles=listOf("QB","RB","LT","LG","C","RG","RT","WR","WR","TE","WR")
private val defenseRoles=listOf("DE","DT","DT","DE","LB","LB","LB","CB","CB","S","S")

private fun createPlays(game:GameRecord,prediction:Prediction,metrics:Map<String,TeamMetrics>,
    features:List<MatchupFeature>,rosters:Map<String,List<V3Athlete>>,profiles:Map<String,V43DriveProfile>,usage:Map<String,Map<String,PlayerUsage2026>>):List<SimPlay> =
    SimcastV3Engine.simulate(game.awayTeam,game.homeTeam,(game.gameId+":"+prediction.id+":v3").hashCode(),metrics,features,rosters,profiles,usage)
        .map { SimPlay(it.away,it.start,it.finish,it.down,it.distance,0,it.kind,it.yards,it.points,
            it.awayScore,it.homeScore,it.elapsed,it.passer,it.receiver,it.receiverPosition,it.rusher) }

private fun playerName(roster:GameRosterIntelligence?,away:Boolean,role:String,index:Int):String{
    val team=if(away)roster?.away else roster?.home
    val matches=team?.players?.filter{it.position.equals(role,ignoreCase=true)&&!it.unavailable}
        ?.sortedWith(compareBy({!it.coreStarter},{it.depthRank})) .orEmpty()
    return matches.getOrNull(index)?.name ?: role
}

@Composable
fun Simcast2D(game:GameRecord,prediction:Prediction,roster:GameRosterIntelligence?=null){
    val context=LocalContext.current
    val metrics=remember(game.gameId,game.season) { try { DbHelper(context).use { db -> db.loadMetrics(game.season).associateBy { it.team } } } catch (_:Exception) { emptyMap<String,TeamMetrics>() } }
    val features=remember(game.gameId,game.season){try{DbHelper(context).use{it.loadMatchupFeatures(game.season)}}catch(_:Exception){emptyList<MatchupFeature>()}}
    val rosterMap=remember(roster){ listOf(game.awayTeam to roster?.away,game.homeTeam to roster?.home).associate { (code,team) ->
        code to (team?.players?.map{V3Athlete(it.name,it.position,it.coreStarter,it.depthRank,it.unavailable)} ?: emptyList()) } }
    val nflverseProfiles=remember(game.season,game.week){NflverseV43.load(context,game.season,game.week)}
    val usage2026=remember(game.season,game.week){SimcastPlayerUsage2026.load(context,game.season,game.week)}
    // V4.4 DRIVE ENGINE: fail visibly if historical profiles are absent.
    if(!nflverseProfiles.containsKey(game.awayTeam) || !nflverseProfiles.containsKey(game.homeTeam)){
        Text("SIMCAST NO DISPONIBLE: faltan perfiles nflverse para esta semana.")
        return
    }
    val plays=remember(game.gameId,prediction.id,metrics,features,rosterMap,nflverseProfiles){createPlays(game,prediction,metrics,features,rosterMap,nflverseProfiles,usage2026)}
    val props=remember(game.gameId,prediction.id,metrics,features,rosterMap,nflverseProfiles){SimcastV3Engine.props(game.awayTeam,game.homeTeam,(game.gameId+":"+prediction.id+":props").hashCode(),metrics,features,rosterMap,profiles=nflverseProfiles,usage=usage2026)}
    var showProps by remember { mutableStateOf(false) }
    var index by remember(game.gameId,prediction.id){mutableIntStateOf(0)}
    var playing by remember(game.gameId,prediction.id){mutableStateOf(false)}
    var speed by remember{mutableIntStateOf(1)}
    var showNames by remember{mutableStateOf(false)}
    val motion=remember{Animatable(0f)}
    LaunchedEffect(index,speed){motion.snapTo(0f);motion.animateTo(1f,tween((1000/speed).coerceAtLeast(150)))}
    LaunchedEffect(playing,speed,game.gameId,prediction.id){
        while(playing && index<plays.lastIndex){delay((1250L/speed).coerceAtLeast(180));index++}
        if(index>=plays.lastIndex)playing=false
    }
    if(plays.isEmpty())return
    val p=plays[index]
    val elapsed=p.elapsed.coerceAtMost(3599)
    val quarter=(elapsed/900+1).coerceIn(1,4)
    val remaining=900-elapsed%900
    val offenseTeam=if(p.away)game.awayTeam else game.homeTeam
    val defenseTeam=if(p.away)game.homeTeam else game.awayTeam
    val receiverName=playerName(roster,p.away,"WR",0)
    val qbName=playerName(roster,p.away,"QB",0)
    val rbName=playerName(roster,p.away,"RB",0)
    val actor=if(p.kind.contains("Pase")||p.kind=="Sack"||p.kind=="Intercepcion")qbName else rbName
    val testContext = LocalContext.current
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("NFL SIMCAST V5 · PLAYER USAGE 2026",color=Color(0xFFFFC861),style=MaterialTheme.typography.labelMedium)
        Text("NFLVERSE ${game.season} · perfiles ${nflverseProfiles.size} · ${game.awayTeam}: ${nflverseProfiles[game.awayTeam]?.sampleGames ?: 0} juegos / ${game.homeTeam}: ${nflverseProfiles[game.homeTeam]?.sampleGames ?: 0} juegos",color=Color(0xFFFFC861),style=MaterialTheme.typography.bodySmall)
        Text("USAGE NFLVERSE 2026 · ${usage2026.values.sumOf{it.size}} jugadores con historial anterior a semana ${game.week} · sin datos: fallback por posición",color=Color(0xFFFFC861),style=MaterialTheme.typography.bodySmall)
        Text("DATOS ${game.season} · ${if(metrics[game.awayTeam]?.games ?: 0 >= 2 && metrics[game.homeTeam]?.games ?: 0 >= 2) "EPA registrado" else "EPA insuficiente: fallback"} · ${features.size} métricas matchup · ${rosterMap.values.sumOf{it.size}} jugadores",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
            listOf(game.awayTeam,game.homeTeam).forEach { code ->
                val m=metrics[code]
                Column(Modifier.weight(1f)){
                    Text("$code · ${m?.games ?: 0} partidos",color=Color.White)
                    Text("EPA OFF ${if(m!=null && m.games>=2)"%.3f".format(m.offEpaPerPlay) else "N/D"}",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
                    Text("EPA DEF ${if(m!=null && m.games>=2)"%.3f".format(m.defEpaAllowedPerPlay) else "N/D"}",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("${game.awayTeam} ${p.awayScore} — ${p.homeScore} ${game.homeTeam}   Q$quarter ${"%02d:%02d".format(remaining/60,remaining%60)}",color=Color.White)
        Text("$offenseTeam · ${p.down}° y ${p.distance} · Balon en yarda ${p.start}",color=Color(0xFFB7C5D6))
        Canvas(Modifier.fillMaxWidth().height(240.dp)){
            val w=size.width;val h=size.height
            val left=w*.075f;val usable=w*.85f
            fun x(yard:Float)=left+usable*(yard+10f)/120f
            drawRect(Color(0xFF155D3B))
            drawRect(Color(0xFF193D67),topLeft=Offset(0f,0f),size=androidx.compose.ui.geometry.Size(x(0f),h))
            drawRect(Color(0xFF244D75),topLeft=Offset(x(100f),0f),size=androidx.compose.ui.geometry.Size(w-x(100f),h))
            for(yard in 0..100 step 5){
                val px=x(yard.toFloat())
                drawLine(Color.White.copy(alpha=if(yard%10==0).8f else .35f),Offset(px,0f),Offset(px,h),if(yard%10==0)1.3.dp.toPx() else .7.dp.toPx())
                if(yard in 10..90 && yard%10==0){
                    val num=if(yard<=50)yard else 100-yard
                    drawContext.canvas.nativeCanvas.drawText("$num",px,h*.18f,android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textAlign=android.graphics.Paint.Align.CENTER;textSize=11.dp.toPx();isAntiAlias=true})
                }
            }
            for(yard in 1..99){
                val px=x(yard.toFloat())
                drawLine(Color.White.copy(alpha=.55f),Offset(px,h*.37f),Offset(px,h*.40f),1.dp.toPx())
                drawLine(Color.White.copy(alpha=.55f),Offset(px,h*.60f),Offset(px,h*.63f),1.dp.toPx())
            }
            val dir=if(p.away)1f else -1f
            val los=if(p.away)p.start.toFloat() else 100f-p.start
            val first=(los+dir*p.distance).coerceIn(0f,100f)
            drawLine(Color(0xFF54C5FF),Offset(x(los),0f),Offset(x(los),h),2.dp.toPx())
            drawLine(Color(0xFFFFD166),Offset(x(first),0f),Offset(x(first),h),2.dp.toPx())
            val roles=offenseRoles+defenseRoles
            val counts=mutableMapOf<String,Int>()
            val labels=ArrayList<Pair<Offset,String>>()
            for(i in 0 until 22){
                val offense=i<11;val n=if(offense)i else i-11
                val role=roles[i]
                val y0=when(n){0->.50f;1->.67f;2->.40f;3->.45f;4->.50f;5->.55f;6->.60f;7->.17f;8->.83f;9->.31f;else->.93f}
                val depth=if(offense){when(n){0-> -5f;1-> -8f;in 2..6-> -1.5f;else-> -2f}}else{if(n<4)3f else if(n<7)8f else 14f}
                val startY=y0*h
                val startX=x((los+dir*depth).coerceIn(0f,100f))
                val progress=motion.value
                val move=when{
                    !offense && n<4 -> -dir*min(4f,progress*4f)
                    !offense -> -dir*progress*(if(n>=7)2f else 5f)
                    n==0 -> -dir*progress*if(p.kind=="Sack")2f else 4f
                    n==1 -> dir*progress*if(p.kind.contains("Carrera")||p.kind=="Touchdown")min(14f,abs(p.yards).toFloat()) else 2f
                    n in 2..6 -> dir*progress*1.5f
                    else -> dir*progress*(if(p.kind.contains("Pase")||p.kind=="Touchdown")min(22f,abs(p.yards).toFloat()+7f) else 4f)
                }
                val drift=if(n==7||n==8)(if(n==7)1f else -1f)*progress*h*.06f else 0f
                val px=(startX+move*usable/120f).coerceIn(7f,w-7f)
                val py=(startY+drift).coerceIn(7f,h-7f)
                val center=Offset(px,py)
                drawCircle(if(offense)Color(0xFFF0F4F8) else Color(0xFF2787F0),5.dp.toPx(),center)
                drawCircle(Color(0xFF092338),5.dp.toPx(),center,style=Stroke(.8.dp.toPx()))
                if(showNames && (n==0||n==1||n==7||n==8)){
                    val key="${offense}_$role";val ordinal=counts[key]?:0;counts[key]=ordinal+1
                    val name=playerName(roster,if(offense)p.away else !p.away,role,ordinal)
                    labels+=center to name.substringAfterLast(' ').take(12)
                }
            }
            val end=if(p.away)p.finish.toFloat() else 100f-p.finish
            val ballX=x(los+(end-los)*motion.value)
            drawCircle(Color(0xFFFFBF57),5.dp.toPx(),Offset(ballX,h*.5f))
            val paint=android.graphics.Paint().apply{color=android.graphics.Color.WHITE;textSize=9.dp.toPx();isAntiAlias=true;textAlign=android.graphics.Paint.Align.CENTER;setShadowLayer(3f,1f,1f,android.graphics.Color.BLACK)}
            for((point,label) in labels){drawContext.canvas.nativeCanvas.drawText(label,point.x,(point.y-8.dp.toPx()).coerceAtLeast(10.dp.toPx()),paint)}
            drawContext.canvas.nativeCanvas.drawText(game.awayTeam,x(-5f),h*.90f,paint)
            drawContext.canvas.nativeCanvas.drawText(game.homeTeam,x(105f),h*.90f,paint)
        }
        Text("Jugada ${index+1}/${plays.size} · ${p.kind} · ${p.yards} yd${if(p.points>0)" · +${p.points} pts" else ""}",color=Color.White)
        Text("Jugadores simulados: ${p.passer.ifBlank{p.rusher}}${if(p.receiver.isNotBlank())" → ${p.receiver}" else ""}",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
            OutlinedButton(onClick={index=0;playing=false},contentPadding=PaddingValues(horizontal=7.dp)){Text("Inicio")}
            Button(onClick={if(index==plays.lastIndex)index=0;playing=!playing},contentPadding=PaddingValues(horizontal=7.dp)){Text(if(playing)"Pausa" else "Play")}
            OutlinedButton(onClick={playing=false;index=(index+1).coerceAtMost(plays.lastIndex)},contentPadding=PaddingValues(horizontal=7.dp)){Text("Siguiente")}
            OutlinedButton(onClick={speed=if(speed==4)1 else speed*2},contentPadding=PaddingValues(horizontal=7.dp)){Text("${speed}x")}
        }
        OutlinedButton(onClick={showNames=!showNames}){Text(if(showNames)"Ocultar etiquetas" else "Mostrar nombres / posiciones")}
        OutlinedButton(onClick={
            val serialized = org.json.JSONArray()
            plays.forEach { sp ->
                serialized.put(org.json.JSONObject().apply {
                    put("a",sp.away); put("s",sp.start); put("f",sp.finish)
                    put("d",sp.down); put("g",sp.distance); put("t",sp.elapsed)
                    put("k",sp.kind); put("y",sp.yards)
                    put("as",sp.awayScore); put("hs",sp.homeScore)
                    put("passer",sp.passer);put("receiver",sp.receiver);put("receiver_position",sp.receiverPosition);put("rusher",sp.rusher)
                    put("points",sp.points)
                })
            }
            testContext.startActivity(Intent(testContext,SimcastGdxTestActivity::class.java).apply {
                putExtra("away",game.awayTeam); putExtra("home",game.homeTeam)
                putExtra("plays",serialized.toString())
                putExtra("line",prediction.line.toDouble())
                putExtra("qb",qbName); putExtra("receiver",receiverName); putExtra("runner",rbName)
                putExtra("awayColor",teamBrand(game.awayTeam).primary.toArgb())
                putExtra("homeColor",teamBrand(game.homeTeam).primary.toArgb())
                putExtra("simcastGameKey",game.gameId+":"+prediction.id)
                putExtra("simcastNflverse",org.json.JSONObject().apply {
                    put("source","nflverse/nflverse-data pbp")
                    put("season",game.season);put("week",game.week)
                    for(code in listOf(game.awayTeam,game.homeTeam)){
                        val v=nflverseProfiles.getValue(code)
                        put(code,org.json.JSONObject().put("sample_games",v.sampleGames)
                            .put("drives_per_game",v.drivesPerGame).put("plays_per_drive",v.playsPerDrive)
                            .put("seconds_per_drive",v.secondsPerDrive).put("seconds_per_play",v.secondsPerPlay)
                            .put("pass_rate",v.passRate))
                    }
                }.toString())
                putExtra("simcastProps",org.json.JSONArray().apply{props.forEach{v->put(org.json.JSONObject().apply{
                    put("team",v.team);put("player",v.player);put("market",v.market);put("mean",v.mean)
                    put("p10",v.p10);put("p50",v.median);put("p90",v.p90);put("n",v.sample)
                })}}.toString())
                putExtra("simcastStrengths",org.json.JSONObject().apply {
                    for(code in listOf(game.awayTeam,game.homeTeam)) {
                        val m=metrics[code]
                        put(code,org.json.JSONObject().apply {
                            put("season",game.season);put("source","NFL Totals Lab team_metrics");put("sample_games",m?.games ?: 0)
                            if(m!=null && m.games>=2) { put("off_epa",m.offEpaPerPlay);put("def_epa_allowed",m.defEpaAllowedPerPlay);put("success_rate",m.successRate);put("explosive_rate",m.explosiveRate) }
                            else put("status","insufficient_data")
                        })
                    }
                }.toString())
                putExtra("simcastRoster",org.json.JSONObject().apply {
                    for (code in listOf(game.awayTeam,game.homeTeam)) {
                        put(code,org.json.JSONObject().put("players",org.json.JSONArray().apply {
                            rosterMap[code].orEmpty().forEach { pl -> put(org.json.JSONObject()
                                .put("name",pl.name).put("position",pl.position)
                                .put("unavailable",pl.unavailable)) }
                        }))
                    }
                }.toString())
            })
        }){Text("ABRIR SIMCAST V4.5 · PRO STADIUM")}
        OutlinedButton(onClick={showProps=!showProps}){Text(if(showProps)"Ocultar Player Props" else "Player Props · Monte Carlo (400)")}
        if(showProps){
            Text("400 simulaciones · línea de prueba P50+0.5 (NO es línea de mercado).",color=Color(0xFFFFC861))
            props.take(18).forEach { v -> Text("${v.team} ${v.player.take(19)} ${v.market}: ${"%.1f".format(v.mean)} yd (${v.p10.toInt()}–${v.p90.toInt()}) · O ${"%.0f".format(v.pOver(v.median+0.5)*100)}%",color=Color.White,style=MaterialTheme.typography.bodySmall) }
        }
        Text("EXPERIMENTAL: nombres proceden del roster si esta disponible; las asignaciones y rutas son ilustrativas, no tracking real. Markov/Monte Carlo experimentales; Player Usage usa historial 2026 anterior al partido cuando está disponible. No altera Core, Ranking ni apuestas.",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
    }
}
