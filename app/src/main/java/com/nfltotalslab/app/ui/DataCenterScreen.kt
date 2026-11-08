package com.nfltotalslab.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfltotalslab.app.data.*
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.util.Date

@Composable
fun DataCenterScreen(
    season:Int,
    snapshots:List<FeedSnapshot>,
    onSync:suspend (Int,Boolean)->List<String>,
    onImport:suspend (Int,NflFeed,BufferedReader)->FeedLoadResult,
    onReload:()->Unit,
    onBack:()->Unit
){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var importing by remember{mutableStateOf(false)}
    var syncing by remember{mutableStateOf(false)}
    var selectedFeed by remember{mutableStateOf<NflFeed?>(null)}
    var message by remember{mutableStateOf("Cada fuente se valida y guarda por separado.")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val feed=selectedFeed
        selectedFeed=null
        if(uri!=null && feed!=null){
            scope.launch {
                importing=true
                runCatching{
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use{reader->onImport(season,feed,reader)}
                        ?: error("No fue posible abrir el archivo")
                }.onSuccess{message="${feed.title}: ${it.note}";onReload()}
                    .onFailure{message="ERROR ${feed.title}: ${it.message}"}
                importing=false
            }
        }
    }
    Column(
        Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ){
        Row(verticalAlignment=Alignment.CenterVertically){
            OutlinedButton(onClick=onBack){Text("← VOLVER",fontSize=11.sp)}
            Spacer(Modifier.width(10.dp))
            Column {
                Text("NFL DATA CENTER",fontSize=20.sp,fontWeight=FontWeight.Black,color=Text)
                Text("Temporada $season · NFL Totals Lab V1.6.2",fontSize=11.sp,color=Muted)
            }
        }
        Text("Fuentes descargables independientes",color=Green,fontWeight=FontWeight.Bold,fontSize=12.sp)
        Text("Simcast: EPA anterior al partido como respaldo + Player Weekly para volumen y eficiencia. Acumulados sin corte verificable NO se usan para partidos históricos. El Core sigue independiente.",
            color=Muted,fontSize=11.sp)
        NflFeed.entries.forEach{ feed->
            val status=snapshots.firstOrNull{it.feed==feed}
            Surface(color=Card,shape=RoundedCornerShape(13.dp)){
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(feed.title,color=Text,fontWeight=FontWeight.Bold,fontSize=14.sp)
                    Text("Fuente: ${feed.source}",color=Muted,fontSize=11.sp)
                    if(status!=null){
                        Text("${status.rows} filas · ${if(status.throughWeek>0)"hasta semana ${status.throughWeek}" else "semana de corte no determinada"}",color=Green,fontSize=11.sp)
                        Text("Importado: ${Date(status.importedAt)}",color=Muted,fontSize=10.sp)
                        if(status.sourceAsOf.isNotBlank())Text("Datos hasta: ${status.sourceAsOf}",color=Muted,fontSize=10.sp)
                        Text("Huella SHA-256: ${status.sha256.take(14)}…",color=Muted,fontSize=9.sp)
                        if(status.throughWeek==0 && feed in setOf(NflFeed.TEAM_STATS,NflFeed.PLAYER_TOTALS))
                            Text("Solo consulta: corte semanal no verificable; excluido de calibración pregame.",color=Amber,fontSize=10.sp)
                    }else Text("SIN CARGAR · sin datos para esta fuente",color=Amber,fontSize=11.sp)
                    OutlinedButton(
                        modifier=Modifier.fillMaxWidth(),
                        enabled=!importing && !syncing,
                        onClick={selectedFeed=feed;picker.launch(arrayOf("text/*","application/csv","application/octet-stream"))}
                    ){Text("IMPORTAR CSV MANUALMENTE",fontSize=10.sp)}
                }
            }
        }
        Button(
            modifier=Modifier.fillMaxWidth(),
            enabled=!syncing && !importing,
            onClick={scope.launch{
                syncing=true
                message="Verificando cuatro fuentes remotas…"
                runCatching{onSync(season,true)}.onSuccess{
                    message=it.joinToString("\n").ifBlank{"No hubo cambios"};onReload()
                }.onFailure{message="ERROR SYNC: ${it.message}"}
                syncing=false
            }},
            colors=ButtonDefaults.buttonColors(containerColor=Blue)
        ){Text(if(syncing)"SINCRONIZANDO…" else "SINCRONIZAR 4 FUENTES",fontSize=12.sp,fontWeight=FontWeight.Black)}
        Surface(color=Panel,shape=RoundedCornerShape(10.dp)){
            Text(message,Modifier.fillMaxWidth().padding(12.dp),color=Text,fontSize=11.sp)
        }
        Text("Controles de integridad: temporada, columnas, semana, tamaño mínimo, huella digital y reemplazo transaccional. No modifica pronósticos iniciados o finalizados.",
            color=Muted,fontSize=10.sp)
        Spacer(Modifier.height(12.dp))
    }
}
