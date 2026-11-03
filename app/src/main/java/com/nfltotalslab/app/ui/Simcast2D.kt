package com.nfltotalslab.app.ui

import android.content.Intent
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
import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.Prediction
import com.nfltotalslab.app.data.GameRosterIntelligence
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class SimPlay(val away:Boolean,val start:Int,val finish:Int,val down:Int,val distance:Int,val seconds:Int,val kind:String,val yards:Int,val points:Int,val awayScore:Int,val homeScore:Int,val elapsed:Int)
private val offenseRoles=listOf("QB","RB","LT","LG","C","RG","RT","WR","WR","TE","WR")
private val defenseRoles=listOf("DE","DT","DT","DE","LB","LB","LB","CB","CB","S","S")

private fun createPlays(game:GameRecord,prediction:Prediction):List<SimPlay>{
    val rng=Random((game.gameId+":"+prediction.id+":v15").hashCode())
    val result=mutableListOf<SimPlay>()
    var elapsed=0;var awayScore=0;var homeScore=0;var away=true;var field=25;var down=1;var distance=10
    while(elapsed<3600 && result.size<230){
        val beforeDown=down;val beforeDistance=distance;val start=field
        val pass=rng.nextDouble()<(if(down>=3).68 else .54)
        val roll=rng.nextDouble()
        var kind:String;var yards:Int;var points=0;var change=false
        if(pass){
            when{
                roll<.027->{kind="Intercepcion";yards=0;change=true}
                roll<.105->{kind="Sack";yards=-rng.nextInt(2,10)}
                roll<.42->{kind="Pase incompleto";yards=0}
                else->{kind="Pase completo";yards=(rng.nextDouble()*rng.nextDouble()*44).toInt()+1}
            }
        }else{
            when{
                roll<.012->{kind="Fumble";yards=0;change=true}
                roll<.20->{kind="Carrera detenida";yards=-rng.nextInt(0,4)}
                else->{kind="Carrera";yards=(rng.nextDouble()*rng.nextDouble()*19).toInt()+1}
            }
        }
        field=(field+yards).coerceIn(1,100)
        if(field==100){kind="Touchdown";points=if(rng.nextDouble()<.94)7 else 6;change=true}
        else if(!change){
            if(yards>=distance){down=1;distance=min(10,100-field)}
            else{down++;distance=(distance-yards).coerceAtLeast(1)}
            if(down>4){
                if(field>=63 && rng.nextDouble()<.85){
                    kind="Intento de gol de campo"
                    if(rng.nextDouble()<(.84-(100-field)*.005).coerceIn(.35,.90))points=3
                }else kind="Despeje"
                change=true
            }
        }
        val seconds=if(kind=="Pase incompleto")rng.nextInt(5,12) else rng.nextInt(18,42)
        elapsed+=min(seconds,3600-elapsed)
        if(away)awayScore+=points else homeScore+=points
        result+=SimPlay(away,start,field,beforeDown,beforeDistance,seconds,kind,yards,points,awayScore,homeScore,elapsed)
        if(change){away=!away;field=if(kind=="Intercepcion"||kind=="Fumble")(100-field).coerceIn(5,95) else 25;down=1;distance=10}
    }
    return result
}

private fun playerName(roster:GameRosterIntelligence?,away:Boolean,role:String,index:Int):String{
    val team=if(away)roster?.away else roster?.home
    val matches=team?.players?.filter{it.position.equals(role,ignoreCase=true)&&!it.unavailable}
        ?.sortedWith(compareBy({!it.coreStarter},{it.depthRank})) .orEmpty()
    return matches.getOrNull(index)?.name ?: role
}

@Composable
fun Simcast2D(game:GameRecord,prediction:Prediction,roster:GameRosterIntelligence?=null){
    val plays=remember(game.gameId,prediction.id){createPlays(game,prediction)}
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
        Text("SIMCAST 2D V1.5 · VISTA TACTICA EXPERIMENTAL",color=Color(0xFFFFC861),style=MaterialTheme.typography.labelMedium)
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
        Text("Participantes ilustrativos: $actor${if(p.kind=="Pase completo")" → $receiverName" else ""}",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
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
                })
            }
            testContext.startActivity(Intent(testContext,SimcastGdxTestActivity::class.java).apply {
                putExtra("away",game.awayTeam); putExtra("home",game.homeTeam)
                putExtra("plays",serialized.toString())
                putExtra("line",prediction.line.toDouble())
                putExtra("qb",qbName); putExtra("receiver",receiverName); putExtra("runner",rbName)
            })
        }){Text("Abrir Simcast V1.9 · partido completo")}
        Text("EXPERIMENTAL: nombres proceden del roster si esta disponible; las asignaciones y rutas son ilustrativas, no tracking real. Motor sin calibracion historica. No altera Core, Ranking ni apuestas.",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
    }
}
