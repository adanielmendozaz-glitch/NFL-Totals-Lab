package com.nfltotalslab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.sin

/** Visual style study; intentionally NOT a play-by-play or scoring engine. */
@Composable
fun SimcastPreviewScreen(onBack:()->Unit){
    val plays=listOf("Pase al lado derecho", "Acarreo por el centro", "Pase profundo", "Captura del QB", "Barrida por izquierda")
    var play by remember{mutableIntStateOf(0)}
    var phase by remember{mutableFloatStateOf(0f)}
    var running by remember{mutableStateOf(true)}
    var speed by remember{mutableFloatStateOf(1f)}
    LaunchedEffect(running,play,speed){
        while(running){
            delay(33)
            phase+=0.012f*speed
            if(phase>=1f){
                phase=0f
                play=(play+1)%plays.size
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xff09131e)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            OutlinedButton(onClick=onBack){Text("← VOLVER",fontSize=11.sp)}
            Spacer(Modifier.width(10.dp))
            Column{
                Text("SIMCAST · VISUAL LAB",fontSize=18.sp,fontWeight=FontWeight.Black,color=Color.White)
                Text("Prototipo 2D / estilo original",fontSize=11.sp,color=Color(0xff79d1e0))
            }
        }
        Surface(color=Color(0xff132536),shape=RoundedCornerShape(12.dp)){
            Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween){
                Column{Text("VISITANTE",color=Color(0xff94a3b8),fontSize=10.sp);Text("00",color=Color.White,fontWeight=FontWeight.Black,fontSize=26.sp)}
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Text("DEMO VISUAL",color=Color(0xffffd36d),fontSize=12.sp,fontWeight=FontWeight.Black)
                    Text("Q1 · 12:34 · 1° Y 10",color=Color.White,fontSize=11.sp)
                }
                Column(horizontalAlignment=Alignment.End){Text("LOCAL",color=Color(0xff94a3b8),fontSize=10.sp);Text("00",color=Color.White,fontWeight=FontWeight.Black,fontSize=26.sp)}
            }
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.55f)){
            val w=size.width;val h=size.height
            fun xy(x:Float,y:Float) = Offset((0.055f+x*.89f)*w, (0.10f+y*.78f)*h)
            drawRoundRect(color=Color(0xff122c32),size=size,cornerRadius=androidx.compose.ui.geometry.CornerRadius(14f,14f))
            val field=Path().apply{
                moveTo(.055f*w,.10f*h);lineTo(.945f*w,.10f*h)
                lineTo(.945f*w,.88f*h);lineTo(.055f*w,.88f*h);close()
            }
            drawPath(field,color=Color(0xff195449))
            for(i in 0..10){
                val x=i/10f
                drawLine(Color(0xffaed6c2).copy(alpha=if(i%5==0).65f else .3f),xy(x,0f),xy(x,1f),strokeWidth=if(i%5==0)2.2f else 1.2f)
            }
            for(i in 1..9){
                val y=i/10f
                drawLine(Color.White.copy(alpha=.12f),xy(0f,y),xy(1f,y),strokeWidth=1f)
            }
            drawLine(Color(0xffffd06d),xy(.42f,0f),xy(.42f,1f),strokeWidth=3f)
            drawLine(Color(0xff60d2e6),xy(.56f,0f),xy(.56f,1f),strokeWidth=2f)
            // 11 offensive + 11 defensive agents; animation moves with play type.
            val offense=listOf(
                .36f to .18f,.36f to .27f,.40f to .34f,.40f to .41f,.40f to .50f,
                .40f to .59f,.40f to .66f,.36f to .74f,.36f to .83f,.29f to .50f,.24f to .57f
            )
            val defense=listOf(
                .49f to .15f,.50f to .25f,.49f to .35f,.48f to .43f,.48f to .55f,
                .49f to .64f,.50f to .76f,.50f to .85f,.62f to .32f,.62f to .52f,.62f to .70f
            )
            val t=phase.coerceIn(0f,1f)
            offense.forEachIndexed {i,p->
                val isReceiver=i in listOf(0,1,7,8)
                val dx=when(play){
                    0->if(isReceiver) .19f*t else if(i==9)-.035f*t else .025f*t
                    1->if(i==10) .19f*t else .03f*t
                    2->if(isReceiver) .32f*t else if(i==9)-.04f*t else .02f*t
                    3->if(i==9)-.07f*t else .01f*t
                    else->if(i==10) .14f*t else .025f*t
                }
                val dy=when(play){0->if(i==1)-.12f*t else 0f;2->if(i==8).07f*t else 0f;4->if(i==10)-.16f*t else 0f;else->0f}
                drawCircle(Color(0xff73c9ff),radius=min(w,h)*.019f,center=xy((p.first+dx).coerceIn(.03f,.97f),(p.second+dy).coerceIn(.05f,.95f)))
                drawCircle(Color(0xffddf4ff),radius=min(w,h)*.007f,center=xy((p.first+dx).coerceIn(.03f,.97f),(p.second+dy).coerceIn(.05f,.95f)))
            }
            defense.forEachIndexed {i,p->
                val track=when(play){0,2->.035f*t;1,4->-.015f*t;else->-.07f*t}
                drawCircle(Color(0xffffa27c),radius=min(w,h)*.019f,center=xy((p.first+track).coerceIn(.05f,.95f),p.second))
            }
            val bx=when(play){0->.30f+.33f*t;1->.24f+.23f*t;2->.30f+.50f*t;3->.30f-.07f*t;else->.24f+.17f*t}
            val by=when(play){0->.50f-.2f*t;1->.57f;2->.50f+.26f*t;3->.50f;else->.57f-.20f*t}
            drawCircle(Color(0xffffe890),radius=min(w,h)*.013f,center=xy(bx,by))
        }
        Text("Jugada ${play+1}/${plays.size} · ${plays[play]}",color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp)
        LinearProgressIndicator(progress={phase},modifier=Modifier.fillMaxWidth(),color=Color(0xff59c6e3))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={running=!running},modifier=Modifier.weight(1f)){
                Text(if(running)"PAUSA" else "REPRODUCIR",fontSize=11.sp)
            }
            OutlinedButton(onClick={play=(play+1)%plays.size;phase=0f},modifier=Modifier.weight(1f)){
                Text("SIGUIENTE",fontSize=11.sp)
            }
        }
        Text("Velocidad ${speed.toInt()}×",color=Color(0xffd8e9ee),fontSize=12.sp)
        Slider(value=speed,onValueChange={speed=it},valueRange=1f..4f,steps=2)
        Text("AVISO: demostración de movimientos. Marcador, trayectorias y reloj son ficticios; todavía NO están conectados a Markov Drive ni a Player Usage Engine.",
            fontSize=11.sp,color=Color(0xffffd36d))
    }
}
