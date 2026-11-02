package com.nfltotalslab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.Prediction
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

private data class Play2D(val away:Boolean,val start:Int,val finish:Int,val down:Int,val toGo:Int,val seconds:Int,val type:String,val points:Int,val turnover:Boolean,val endDrive:Boolean,val awayScore:Int,val homeScore:Int,val elapsed:Int)

private fun generatePlays(game:GameRecord,prediction:Prediction):List<Play2D>{
    val rng=Random((game.gameId+":"+prediction.id+":v14").hashCode())
    val out=mutableListOf<Play2D>()
    var elapsed=0;var awayScore=0;var homeScore=0;var away=true;var pos=25;var down=1;var toGo=10
    // Fixed possession budget and clock; never force a target final score.
    while(elapsed<3600 && out.size<230){
        val remaining=3600-elapsed
        val pass=rng.nextDouble()<if(down==3) .70 else .55
        val r=rng.nextDouble()
        var type:String;var yards:Int;var turnover=false;var endDrive=false;var points=0
        if(pass){
            when {
                r<.045->{type="Intercepción";yards=0;turnover=true;endDrive=true}
                r<.105->{type="Sack";yards=-rng.nextInt(2,10)}
                r<.39->{type="Pase incompleto";yards=0}
                else->{type="Pase completo";yards=(rng.nextDouble()*rng.nextDouble()*42).toInt()+1}
            }
        }else{
            when{
                r<.016->{type="Fumble";yards=0;turnover=true;endDrive=true}
                r<.20->{type="Carrera detenida";yards=-rng.nextInt(0,4)}
                else->{type="Carrera";yards=(rng.nextDouble()*rng.nextDouble()*17).toInt()+1}
            }
        }
        val start=pos
        pos=(pos+yards).coerceIn(1,100)
        if(pos>=100){type="Touchdown";points=if(rng.nextDouble()<.94)7 else 6;endDrive=true}
        else if(!endDrive){
            if(yards>=toGo){down=1;toGo=min(10,100-pos)}
            else {down++;toGo=(toGo-yards).coerceAtLeast(1)}
            if(down>4){
                if(pos>=63 && rng.nextDouble()<.75){type="Gol de campo";points=if(rng.nextDouble()<(.82-(100-pos)*.004).coerceIn(.40,.90))3 else 0}
                else type="Despeje"
                endDrive=true
            }
        }
        val seconds=if(type=="Pase incompleto")rng.nextInt(5,12) else rng.nextInt(20,43)
        elapsed+=min(seconds,remaining)
        if(away)awayScore+=points else homeScore+=points
        out+=Play2D(away,start,pos,if(endDrive)4 else down,toGo,seconds,type,points,turnover,endDrive,awayScore,homeScore,elapsed)
        if(endDrive){
            away=!away
            pos=if(turnover)(100-pos).coerceIn(5,95) else 25
            down=1;toGo=10
        }
    }
    return out
}

@Composable
fun Simcast2D(game:GameRecord,prediction:Prediction){
    val plays=remember(game.gameId,prediction.id){generatePlays(game,prediction)}
    var index by remember(game.gameId,prediction.id){mutableIntStateOf(0)}
    var playing by remember(game.gameId,prediction.id){mutableStateOf(false)}
    var speed by remember{mutableIntStateOf(1)}
    LaunchedEffect(playing,speed,game.gameId,prediction.id){
        while(playing && index<plays.lastIndex){delay(1100L/speed);index++}
        if(index>=plays.lastIndex)playing=false
    }
    val p=plays[index]
    val progress by animateFloatAsState(targetValue=index.toFloat(),animationSpec=tween(650/speed),label="play")
    val phase=(progress-index+1f).coerceIn(0f,1f)
    val elapsed=p.elapsed.coerceAtMost(3599)
    val quarter=(elapsed/900+1).coerceIn(1,4)
    val left=900-elapsed%900
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("SIMCAST 2D V1.4 · MOTOR DE JUGADAS EXPERIMENTAL",color=Color(0xFFFFC861),style=MaterialTheme.typography.labelMedium)
        Text("${game.awayTeam} ${p.awayScore} — ${p.homeScore} ${game.homeTeam}    Q$quarter ${"%02d:%02d".format(left/60,left%60)}",color=Color.White)
        Canvas(Modifier.fillMaxWidth().height(220.dp)){
            drawRect(Color(0xFF155D3B))
            for(yard in 0..100 step 10){val x=size.width*yard/100f;drawLine(Color.White.copy(alpha=.60f),Offset(x,0f),Offset(x,size.height),1.dp.toPx())}
            val dir=if(p.away)1f else -1f
            val scrimmage=if(p.away)p.start.toFloat() else 100f-p.start
            val x=size.width*scrimmage/100f
            drawLine(Color(0xFF51BDF0),Offset(x,0f),Offset(x,size.height),2.dp.toPx())
            val first=(scrimmage+dir*p.toGo).coerceIn(0f,100f)
            drawLine(Color(0xFFFFC861),Offset(size.width*first/100f,0f),Offset(size.width*first/100f,size.height),1.5.dp.toPx())
            val radius=4.5.dp.toPx()
            for(i in 0 until 22){
                val offense=i<11
                val n=if(offense)i else i-11
                val yBase=size.height*(n+1)/12f
                val baseX=x+dir*(if(offense)-9.dp.toPx() else 9.dp.toPx())
                val advance=when{
                    !offense -> (if(n%3==0) 11f else 5f)*phase
                    n==0 -> 2f*phase
                    n<=5 -> 2.5f*phase
                    n<=8 -> (8f+n*1.3f)*phase
                    else -> (4f+n*1.2f)*phase
                }
                val lateral=if(n%2==0) 1f else -1f
                val px=(baseX+dir*advance.dp.toPx()).coerceIn(radius,size.width-radius)
                val py=(yBase+lateral*phase*(if(n>5) 13.dp.toPx() else 3.dp.toPx())).coerceIn(radius,size.height-radius)
                drawCircle(if(offense)Color(0xFFECEFF3) else Color(0xFF2D82E5),radius,Offset(px,py))
                drawCircle(Color(0xFF0A1B28),radius,Offset(px,py),style=Stroke(0.8.dp.toPx()))
            }
            val ballX=size.width*(if(p.away)p.finish else 100-p.finish)/100f
            drawCircle(Color(0xFFFFC861),6.dp.toPx(),Offset(ballX.coerceIn(6f,size.width-6f),size.height*.5f))
            drawRect(Color.White.copy(alpha=.45f),style=Stroke(1.dp.toPx()))
        }
        Text("Jugada ${index+1}/${plays.size} · ${if(p.away)game.awayTeam else game.homeTeam} · ${p.type} · ${p.finish-p.start} yd${if(p.points>0)" · +${p.points} pts" else ""}",color=Color.White)
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){
            OutlinedButton(onClick={index=0;playing=false},contentPadding=PaddingValues(horizontal=8.dp)){Text("Inicio")}
            Button(onClick={if(index==plays.lastIndex)index=0;playing=!playing},contentPadding=PaddingValues(horizontal=8.dp)){Text(if(playing)"Pausa" else "Play")}
            OutlinedButton(onClick={playing=false;index=(index+1).coerceAtMost(plays.lastIndex)},contentPadding=PaddingValues(horizontal=8.dp)){Text("Siguiente")}
            OutlinedButton(onClick={speed=if(speed==4)1 else speed*2},contentPadding=PaddingValues(horizontal=8.dp)){Text("${speed}x")}
        }
        Text("Simulación táctica ilustrativa con downs, reloj y posesiones. No es play-by-play real ni está calibrada contra datos históricos; no modifica el Core, Ranking o apuestas.",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
    }
}
