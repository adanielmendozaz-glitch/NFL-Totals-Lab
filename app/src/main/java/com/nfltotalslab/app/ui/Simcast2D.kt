package com.nfltotalslab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.data.Prediction
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.random.Random

private data class SimDrive(val offenseAway:Boolean,val start:Int,val end:Int,val points:Int,val seconds:Int,val description:String)

private fun buildSimDrives(game:GameRecord,prediction:Prediction):List<SimDrive>{
    // Illustrative drive sequence conditioned on the projected total, not official play-by-play.
    val seed=(game.gameId+":"+prediction.id).hashCode()
    val rng=Random(seed)
    val target=prediction.projection.coerceIn(10.0,85.0)
    val drives=mutableListOf<SimDrive>()
    var total=0
    repeat(22){index->
        val offenseAway=index%2==0
        val start=rng.nextInt(15,41)
        val remaining=(target-total).coerceAtLeast(0.0)
        val probability=(remaining/(22-index).coerceAtLeast(1)/5.0).coerceIn(.05,.72)
        val score=rng.nextDouble()<probability
        val points=if(score){if(rng.nextDouble()<.62)7 else 3}else 0
        val end=if(points==7)100 else (start+rng.nextInt(4,53)).coerceAtMost(96)
        val event=when(points){7->"Touchdown";3->"Gol de campo";else->listOf("Punt","Serie sin puntos","Pérdida de balón")[rng.nextInt(3)]}
        drives+=SimDrive(offenseAway,start,end,points,rng.nextInt(65,165),event)
        total+=points
    }
    return drives
}

@Composable
fun Simcast2D(game:GameRecord,prediction:Prediction){
    val drives=remember(game.gameId,prediction.id){buildSimDrives(game,prediction)}
    var index by remember(game.gameId,prediction.id){mutableIntStateOf(0)}
    var playing by remember(game.gameId,prediction.id){mutableStateOf(false)}
    var speed by remember{mutableIntStateOf(1)}
    LaunchedEffect(playing,speed,game.gameId,prediction.id){
        while(playing && index<drives.lastIndex){delay(1300L/speed);index++}
        if(index>=drives.lastIndex)playing=false
    }
    val current=drives[index]
    val completed=drives.take(index+1)
    val away=completed.filter{it.offenseAway}.sumOf{it.points}
    val home=completed.filter{!it.offenseAway}.sumOf{it.points}
    val elapsed=completed.sumOf{it.seconds}.coerceAtMost(3600)
    val quarter=(elapsed/900+1).coerceAtMost(4)
    val left=(900-elapsed%900).coerceAtLeast(0)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("SIMCAST 2D · DEMOSTRACIÓN PREDICTIVA",color=Color(0xFFFFC861),style=MaterialTheme.typography.labelMedium)
        Text("${game.awayTeam} $away  —  $home ${game.homeTeam}    Q$quarter ${"%02d:%02d".format(left/60,left%60)}",color=Color.White)
        Canvas(Modifier.fillMaxWidth().height(170.dp)){
            drawRect(Color(0xFF19623F))
            for(yard in 0..100 step 10){
                val x=size.width*yard/100f
                drawLine(Color.White.copy(alpha=.65f),Offset(x,0f),Offset(x,size.height),1.5.dp.toPx())
            }
            val xStart=size.width*current.start/100f
            val xEnd=size.width*current.end/100f
            drawLine(Color(0xFF4FC3F7),Offset(xStart,0f),Offset(xStart,size.height),2.dp.toPx())
            drawLine(Color(0xFFFFD166),Offset(xStart,size.height*.5f),Offset(xEnd,size.height*.5f),4.dp.toPx())
            val ballX=xEnd
            drawCircle(Color(0xFFFFC861),7.dp.toPx(),Offset(ballX,size.height*.5f))
            for(i in 0..10){
                val y=size.height*(i+1)/12f
                drawCircle(Color(0xFFE8E8E8),4.dp.toPx(),Offset((xStart-12.dp.toPx()).coerceAtLeast(5f),y))
                drawCircle(Color(0xFF2B7DE9),4.dp.toPx(),Offset((xStart+12.dp.toPx()).coerceAtMost(size.width-5f),y))
            }
            drawRect(Color.White.copy(alpha=.4f),style=Stroke(1.dp.toPx()))
        }
        Text("Serie ${index+1}/${drives.size} · ${if(current.offenseAway)game.awayTeam else game.homeTeam} · ${current.description} · +${current.points} pts",color=Color.White)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
            OutlinedButton(onClick={index=0;playing=false}){Text("Reiniciar")}
            Button(onClick={if(index==drives.lastIndex)index=0;playing=!playing}){Text(if(playing)"Pausa" else "Reproducir")}
            OutlinedButton(onClick={playing=false;index=(index+1).coerceAtMost(drives.lastIndex)}){Text("Siguiente")}
            OutlinedButton(onClick={speed=if(speed==4)1 else speed*2}){Text("${speed}x")}
        }
        Text("Trayectoria ilustrativa condicionada a μ=${"%.1f".format(prediction.projection)}. No son jugadas reales ni una muestra exacta del Monte Carlo; no altera Core, Ranking ni apuestas.",color=Color(0xFFB7C5D6),style=MaterialTheme.typography.bodySmall)
    }
}
