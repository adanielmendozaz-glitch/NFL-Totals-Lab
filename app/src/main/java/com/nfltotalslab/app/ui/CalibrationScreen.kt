package com.nfltotalslab.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfltotalslab.app.calibration.CalibrationState
import com.nfltotalslab.app.data.CalibrationSnapshot
import java.util.Locale

@Composable
fun ProgressiveCalibrationScreen(state:CalibrationState,snapshots:List<CalibrationSnapshot>,onBack:()->Unit){
    val settled=snapshots.filter{it.result=="WIN" || it.result=="LOSS"}.groupBy{it.gameId}.mapNotNull{(_,x)->x.maxByOrNull{it.createdAt}}
    val raw=if(settled.isEmpty())null else settled.map{val y=if(it.result=="WIN")1.0 else 0.0;val e=it.rawProbability-y;e*e}.average()
    val cal=if(settled.isEmpty())null else settled.map{val y=if(it.result=="WIN")1.0 else 0.0;val e=it.calibratedProbability-y;e*e}.average()
    val gain=if(raw!=null&&cal!=null)raw-cal else null
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick=onBack){Text("←")};Column(Modifier.padding(start=10.dp)){Text("PROGRESSIVE CALIBRATION",fontWeight=FontWeight.Black,fontSize=18.sp);Text("Pregame congelado · fuera de muestra",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item{Surface(shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.fillMaxWidth().padding(15.dp)){Text("MADUREZ ${state.maturity}",fontSize=10.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Black);Text("N entrenamiento ${state.trainN}",fontSize=24.sp,fontWeight=FontWeight.Black);Text("α ${f3(state.intercept)} · β ${f3(state.slope)}",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){CalMetric("RAW",raw?.let(::f3)?:"—",Modifier.weight(1f));CalMetric("CAL",cal?.let(::f3)?:"—",Modifier.weight(1f));CalMetric("MEJORA",gain?.let(::f3)?:"—",Modifier.weight(1f),gain)}}
            items(snapshots.sortedByDescending{it.createdAt}.take(80)){x->Surface(shape=RoundedCornerShape(13.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Row(Modifier.fillMaxWidth().padding(11.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("${x.awayTeam} @ ${x.homeTeam}",fontWeight=FontWeight.Black,fontSize=11.sp);Text("W${x.week} · train ${x.trainN} · ${x.maturity} · ${x.result?:"PENDIENTE"}",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Column(horizontalAlignment=Alignment.End){Text("RAW ${(x.rawProbability*100).f1()}%",fontSize=10.sp);Text("CAL ${(x.calibratedProbability*100).f1()}%",fontWeight=FontWeight.Black,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}}}}
            item{Text("MEJORA > 0 = menor Brier. CAL no cambia el Core automáticamente.",fontSize=9.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp))}
        }
    }
}
@Composable private fun CalMetric(label:String,value:String,modifier:Modifier=Modifier,delta:Double?=null){val c=when{delta==null->MaterialTheme.colorScheme.onSurface;delta>0->MaterialTheme.colorScheme.primary;delta<0->MaterialTheme.colorScheme.error;else->MaterialTheme.colorScheme.onSurfaceVariant};Surface(modifier=modifier,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.padding(vertical=9.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(label,fontSize=8.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontSize=15.sp,fontWeight=FontWeight.Black,color=c)}}}
private fun f3(x:Double)=String.format(Locale.US,"%.3f",x)
private fun Double.f1()=String.format(Locale.US,"%.1f",this)
