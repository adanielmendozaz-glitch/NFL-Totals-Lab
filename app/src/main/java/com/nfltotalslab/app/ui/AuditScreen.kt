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
import com.nfltotalslab.app.audit.AuditSnapshot
import com.nfltotalslab.app.audit.AuditStat
import com.nfltotalslab.app.branding.TeamBadge
import com.nfltotalslab.app.branding.teamBrand
import java.util.Locale

@Composable
fun AuditScreen(snapshot:AuditSnapshot,onBack:()->Unit){
    val core=snapshot.core
    Column(Modifier.fillMaxSize()){
        AuditHeader(
            "AUDIT LAB",
            "ROI teórico @${snapshot.odds} · 1u por pick · solo registros liquidados.",
            onBack
        )

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    AuditMetric("N",core.n.toString(),Modifier.weight(1f))
                    AuditMetric("HIT",core.hitRate?.times(100)?.f1()?.plus("%") ?: "—",Modifier.weight(1f))
                    AuditMetric("ROI",core.roi?.times(100)?.f1()?.plus("%") ?: "—",Modifier.weight(1f),core.roi)
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    AuditMetric("BRIER",core.brier?.f3() ?: "—",Modifier.weight(1f))
                    AuditMetric("ECE",snapshot.ece?.times(100)?.f1()?.plus(" pp") ?: "—",Modifier.weight(1f))
                    AuditMetric("W-L-P","${core.wins}-${core.losses}-${core.pushes}",Modifier.weight(1f))
                }
            }

            item{AuditSectionTitle("CORE vs SHADOW · menor Brier es mejor")}
            items(snapshot.modelComparison){AuditStatRow(it)}

            item{AuditSectionTitle("PROBABILIDAD · calibración + rentabilidad")}
            items(snapshot.byProbability){AuditStatRow(it)}

            item{AuditSectionTitle("MERCADO · OVER vs UNDER")}
            items(snapshot.byMarket){AuditStatRow(it)}

            item{AuditSectionTitle("FILTRO · PASS / LEAN / JUGABLE")}
            items(snapshot.byClassification){AuditStatRow(it)}

            item{AuditSectionTitle("EQUIPOS · el N importa")}
            items(snapshot.byTeam){AuditStatRow(it)}

            item{
                Text(
                    "Audit Lab observa; no cambia pesos ni promueve un Shadow automáticamente. " +
                    "Con muestras pequeñas, ROI y hit rate pueden ser muy ruidosos.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize=9.sp,
                    modifier=Modifier.padding(vertical=10.dp)
                )
            }
        }
    }
}

@Composable
private fun AuditHeader(title:String,subtitle:String,onBack:()->Unit){
    Row(
        Modifier.fillMaxWidth().padding(14.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        OutlinedButton(onClick=onBack){Text("←")}
        Column(Modifier.padding(start=10.dp)){
            Text(title,fontWeight=FontWeight.Black,fontSize=20.sp)
            Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp)
        }
    }
}

@Composable
private fun AuditMetric(
    label:String,
    value:String,
    modifier:Modifier=Modifier,
    signedValue:Double?=null
){
    val valueColor=when{
        signedValue==null->MaterialTheme.colorScheme.onSurface
        signedValue>0.0->MaterialTheme.colorScheme.primary
        signedValue<0.0->MaterialTheme.colorScheme.error
        else->MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier=modifier,
        color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.30f),
        shape=RoundedCornerShape(12.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ){
        Column(
            Modifier.padding(vertical=10.dp),
            horizontalAlignment=Alignment.CenterHorizontally
        ){
            Text(label,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=8.sp,fontWeight=FontWeight.Black)
            Text(value,color=valueColor,fontSize=15.sp,fontWeight=FontWeight.Black)
        }
    }
}

@Composable
private fun AuditSectionTitle(text:String){
    Text(
        text,
        color=MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize=10.sp,
        fontWeight=FontWeight.Black,
        modifier=Modifier.padding(top=7.dp,bottom=2.dp)
    )
}

@Composable
private fun AuditStatRow(x:AuditStat){
    val roiColor=when{
        x.roi==null->MaterialTheme.colorScheme.onSurfaceVariant
        x.roi>0.0->MaterialTheme.colorScheme.primary
        x.roi<0.0->MaterialTheme.colorScheme.error
        else->MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        Modifier.fillMaxWidth(),
        color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.30f),
        shape=RoundedCornerShape(13.dp),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
    ){
        Row(
            Modifier.padding(11.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            if(teamBrand(x.label).logoUrl!=null){
                TeamBadge(x.label,24.dp)
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)){
                Text(x.label,fontWeight=FontWeight.Black,fontSize=11.sp)
                Text(
                    "N ${x.n} · W-L-P ${x.wins}-${x.losses}-${x.pushes} · P̄ ${x.avgProbability?.times(100)?.f1() ?: "—"}%",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize=9.sp
                )
            }
            Column(horizontalAlignment=Alignment.End){
                Text(
                    "Hit ${x.hitRate?.times(100)?.f1() ?: "—"}% · ROI ${x.roi?.times(100)?.f1() ?: "—"}%",
                    color=roiColor,
                    fontWeight=FontWeight.Bold,
                    fontSize=9.sp
                )
                Text(
                    "Brier ${x.brier?.f3() ?: "—"}",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize=9.sp
                )
            }
        }
    }
}

private fun Double.f1()=String.format(Locale.US,"%.1f",this)
private fun Double.f3()=String.format(Locale.US,"%.3f",this)
