package com.nfltotalslab.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfltotalslab.app.branding.TeamBadge
import com.nfltotalslab.app.data.*
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.max

private val DetailBg=Color(0xFF07111F)
private val DetailPanel=Color(0xFF0B2634)
private val DetailCard=Color(0xFF0B2031)
private val DetailGreen=Color(0xFF39D6A3)
private val DetailBlue=Color(0xFF35AEEB)
private val DetailRed=Color(0xFFFF6482)
private val DetailAmber=Color(0xFFFFC861)
private val DetailText=Color(0xFFF1F5FB)
private val DetailMuted=Color(0xFF8FA0B7)
private val DetailBorder=Color(0xFF1A3B52)

@Composable
fun GameDetailScreen(
    game:GameRecord,
    prediction:Prediction?,
    onBack:()->Unit,
    onAnalyze:suspend()->Prediction,
    onLoadRoster:suspend()->GameRosterIntelligence,
    onBet:(Prediction)->Unit
){
    val scope=rememberCoroutineScope()
    var localPrediction by remember(game.gameId,prediction?.id){mutableStateOf(prediction)}
    var roster by remember(game.gameId){mutableStateOf<GameRosterIntelligence?>(null)}
    var rosterLoading by remember(game.gameId){mutableStateOf(false)}
    var rosterError by remember(game.gameId){mutableStateOf<String?>(null)}
    var analyzing by remember(game.gameId){mutableStateOf(false)}
    var awayExpanded by remember(game.gameId){mutableStateOf(false)}
    var homeExpanded by remember(game.gameId){mutableStateOf(false)}

    fun loadRoster(){
        if(rosterLoading)return
        scope.launch{
            rosterLoading=true
            rosterError=null
            runCatching{onLoadRoster()}
                .onSuccess{roster=it}
                .onFailure{rosterError=it.message ?: "No se pudo cargar roster"}
            rosterLoading=false
        }
    }

    LaunchedEffect(game.gameId){loadRoster()}

    Surface(Modifier.fillMaxSize(),color=DetailBg){
        Column(Modifier.fillMaxSize()){
            Row(
                Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=13.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                OutlinedButton(onClick=onBack){Text("←")}
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)){
                    Text("${game.awayTeam} @ ${game.homeTeam}",color=DetailText,fontWeight=FontWeight.Black,fontSize=23.sp)
                    Text(
                        "Week ${game.week} · ${game.gameDay} ${game.gameTime} · ${if(game.finished)"FINAL" else "PRE-GAME"}",
                        color=DetailMuted,fontSize=10.sp
                    )
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding=PaddingValues(horizontal=14.dp,vertical=4.dp),
                verticalArrangement=Arrangement.spacedBy(10.dp)
            ){
                item{MatchupScoreCard(game)}

                item{
                    SectionTitle("DATOS BASE · MERCADO Y CONTEXTO")
                    SurfaceCard{
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            SmallMetric("TOTAL",game.totalLine?.let{fmt1(it)} ?: "—",Modifier.weight(1f))
                            SmallMetric("SPREAD",game.spreadLine?.let{fmt1(it)} ?: "—",Modifier.weight(1f))
                            SmallMetric("ROOF",game.roof ?: "—",Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${game.surface ?: "surface —"} · ${game.temp?.let{"${it.toInt()}°F"} ?: "temp —"} · ${game.wind?.let{"viento ${it.toInt()}"} ?: "wind —"}",
                            color=DetailMuted,fontSize=10.sp
                        )
                    }
                }

                item{
                    SectionTitle("LECTURA OFICIAL · CORE")
                    val p=localPrediction
                    if(p==null){
                        SurfaceCard{
                            Text("Aún no hay predicción vigente para este juego.",color=DetailMuted,fontSize=11.sp)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick={
                                    scope.launch{
                                        analyzing=true
                                        runCatching{onAnalyze()}.onSuccess{localPrediction=it}
                                        analyzing=false
                                    }
                                },
                                enabled=!game.finished && !analyzing
                            ){Text(if(analyzing)"ANALIZANDO…" else "ANALIZAR TOTAL O/U")}
                        }
                    }else{
                        CoreDecisionCard(p)
                    }
                }

                localPrediction?.let{p->
                    item{
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Button(
                                onClick={
                                    scope.launch{
                                        analyzing=true
                                        runCatching{onAnalyze()}.onSuccess{localPrediction=it}
                                        analyzing=false
                                    }
                                },
                                enabled=!game.finished && !analyzing,
                                modifier=Modifier.weight(1f),
                                colors=ButtonDefaults.buttonColors(containerColor=DetailBlue)
                            ){
                                Text(if(analyzing)"ANALIZANDO…" else "REANALIZAR CORE",fontSize=10.sp,fontWeight=FontWeight.Black)
                            }
                            Button(
                                onClick={onBet(p)},
                                enabled=!game.finished,
                                modifier=Modifier.weight(1f),
                                colors=ButtonDefaults.buttonColors(containerColor=DetailGreen)
                            ){
                                Text("AÑADIR A APUESTAS",fontSize=10.sp,fontWeight=FontWeight.Black)
                            }
                        }
                    }

                    item{
                        SectionTitle("MOTORES · LECTURA BASE")
                        SurfaceCard{
                            p.engines.forEachIndexed{idx,e->
                                val side=if(e.pOver>=e.pUnder)"OVER" else "UNDER"
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical=5.dp),
                                    verticalAlignment=Alignment.CenterVertically
                                ){
                                    Text(e.name,Modifier.weight(1f),color=DetailMuted,fontSize=10.sp)
                                    Text(
                                        "μ ${fmt1(e.projection)} · $side ${(max(e.pOver,e.pUnder)*100).f1()}%",
                                        color=DetailText,fontSize=10.sp,fontWeight=FontWeight.Bold
                                    )
                                }
                                if(idx<p.engines.lastIndex)HorizontalDivider(color=DetailBorder)
                            }
                            Spacer(Modifier.height(6.dp))
                            val agree=p.engines.count{
                                val side=if(it.pOver>=it.pUnder)"OVER" else "UNDER"
                                side==p.pick
                            }
                            Text(
                                "Consenso $agree/${p.engines.size} · dispersión ${engineDispersionLabel(p)}",
                                color=DetailMuted,fontSize=9.sp
                            )
                        }
                    }
                }

                item{
                    SectionTitle("ROSTER INTELLIGENCE · TITULARES, SUPLENTES Y BAJAS")
                    when{
                        rosterLoading->SurfaceCard{
                            Row(verticalAlignment=Alignment.CenterVertically){
                                CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text("Cargando depth charts e injury report…",color=DetailMuted,fontSize=10.sp)
                            }
                        }
                        rosterError!=null->SurfaceCard{
                            Text("Roster no disponible: $rosterError",color=DetailRed,fontSize=10.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick={loadRoster()}){Text("REINTENTAR")}
                        }
                        roster!=null->{
                            val intel=roster!!
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                                TeamRosterCard(intel.away,awayExpanded){awayExpanded=!awayExpanded}
                                TeamRosterCard(intel.home,homeExpanded){homeExpanded=!homeExpanded}
                            }
                        }
                        else->SurfaceCard{OutlinedButton(onClick={loadRoster()}){Text("CARGAR ROSTER")}}
                    }
                }

                item{
                    SectionTitle("ROSTER SHADOW · IMPACTO EN LA DECISIÓN")
                    val p=localPrediction
                    val intel=roster
                    if(p==null || intel==null){
                        SurfaceCard{
                            Text("Necesita Core + Roster Intelligence para calcular el Shadow.",color=DetailMuted,fontSize=10.sp)
                        }
                    }else{
                        val baseOver=if(p.pick=="OVER")p.probability else 1.0-p.probability
                        val shift=(intel.totalAdjustment*.025*intel.reliability).coerceIn(-.15,.15)
                        val adjOver=(baseOver+shift).coerceIn(.05,.95)
                        val adjPick=if(adjOver>=.5)"OVER" else "UNDER"
                        val adjProb=max(adjOver,1.0-adjOver)
                        val adjProjection=p.projection+intel.totalAdjustment

                        SurfaceCard{
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                SmallMetric("CORE μ",fmt1(p.projection),Modifier.weight(1f))
                                SmallMetric("ROSTER Δ",signed1(intel.totalAdjustment),Modifier.weight(1f))
                                SmallMetric("AJUSTADA μ",fmt1(adjProjection),Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "$adjPick ${fmt1(p.line)}",
                                color=if(adjPick==p.pick)DetailGreen else DetailAmber,
                                fontSize=25.sp,fontWeight=FontWeight.Black
                            )
                            Text(
                                "${(adjProb*100).f1()}% · reliability ${(intel.reliability*100).f1()}%",
                                color=DetailText,fontSize=13.sp,fontWeight=FontWeight.Bold
                            )
                            Text(
                                if(adjPick==p.pick)
                                    "ROSTER CONFIRMA la dirección del Core."
                                else
                                    "ROSTER CONTRADICE la dirección del Core · NO promover automáticamente.",
                                color=if(adjPick==p.pick)DetailGreen else DetailRed,
                                fontSize=10.sp,fontWeight=FontWeight.Bold,
                                modifier=Modifier.padding(top=5.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "EXPERIMENTAL · READ ONLY. Se guarda como Shadow para auditoría; no modifica Ranking, Core, Calibration ni la apuesta oficial.",
                                color=DetailAmber,fontSize=9.sp
                            )
                        }
                    }
                }

                roster?.let{intel->
                    item{
                        SectionTitle("FUENTES Y ESTADO")
                        SurfaceCard{
                            Text(intel.source,color=DetailText,fontWeight=FontWeight.Bold,fontSize=10.sp)
                            Text(
                                "Depth ${if(intel.away.depthLoaded&&intel.home.depthLoaded)"OK" else "PARCIAL"} · " +
                                    "Injuries ${if(intel.away.injuriesLoaded&&intel.home.injuriesLoaded)"OK" else "PARCIAL"} · actualizado al abrir.",
                                color=DetailMuted,fontSize=9.sp
                            )
                        }
                    }
                }

                item{Spacer(Modifier.height(18.dp))}
            }
        }
    }
}

@Composable
private fun MatchupScoreCard(g:GameRecord){
    SurfaceCard{
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                TeamBadge(g.awayTeam,42.dp)
                Text(g.awayTeam,color=DetailText,fontWeight=FontWeight.Black,fontSize=18.sp)
                Text(g.awayScore?.toString() ?: "0",color=DetailText,fontWeight=FontWeight.Black,fontSize=30.sp)
            }
            Text(if(g.finished)"FINAL" else "PRE-GAME",color=DetailMuted,fontWeight=FontWeight.Bold,fontSize=12.sp)
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                TeamBadge(g.homeTeam,42.dp)
                Text(g.homeTeam,color=DetailText,fontWeight=FontWeight.Black,fontSize=18.sp)
                Text(g.homeScore?.toString() ?: "0",color=DetailText,fontWeight=FontWeight.Black,fontSize=30.sp)
            }
        }
    }
}

@Composable
private fun CoreDecisionCard(p:Prediction){
    Surface(
        color=DetailCard,
        shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,if(p.classification.startsWith("JUGABLE"))DetailGreen else DetailAmber)
    ){
        Column(Modifier.fillMaxWidth().padding(16.dp)){
            Text("FULL GAME · ${p.classification}",color=DetailMuted,fontSize=10.sp,fontWeight=FontWeight.Black)
            Text("${p.pick} ${fmt1(p.line)}",color=DetailText,fontSize=28.sp,fontWeight=FontWeight.Black)
            Text(
                "${(p.probability*100).f1()}% · Proyección ${fmt1(p.projection)}",
                color=DetailGreen,fontSize=15.sp,fontWeight=FontWeight.Black
            )
            Text(
                "${if(p.analysisSource=="AUTO_CENSUS")"AUTO CENSUS" else "MANUAL"} · Core ${p.modelVersion}",
                color=DetailMuted,fontSize=9.sp
            )
        }
    }
}

@Composable
private fun TeamRosterCard(team:TeamRosterIntelligence,expanded:Boolean,onToggle:()->Unit){
    val visible=if(expanded)team.players else team.players.filter{it.starter || it.injuryStatus.isNotBlank()}.take(14)
    Surface(
        color=DetailCard,
        shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,DetailBorder)
    ){
        Column(Modifier.fillMaxWidth().padding(13.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                TeamBadge(team.team,34.dp)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)){
                    Text(team.team,color=DetailText,fontSize=18.sp,fontWeight=FontWeight.Black)
                    Text(
                        "Titulares ${team.startersAvailable}/${team.startersTotal} · OUT ${team.outCount} · Q ${team.questionableCount}",
                        color=DetailMuted,fontSize=9.sp
                    )
                }
                Text(
                    when{
                        team.outCount>=4->"TOCADO"
                        team.outCount>=2->"ATENCIÓN"
                        else->"ESTABLE"
                    },
                    color=when{
                        team.outCount>=4->DetailRed
                        team.outCount>=2->DetailAmber
                        else->DetailGreen
                    },
                    fontSize=9.sp,fontWeight=FontWeight.Black
                )
            }
            Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SmallMetric("OFENSA","${team.offenseAvailability.f1()}/100",Modifier.weight(1f))
                SmallMetric("DEFENSA","${team.defenseAvailability.f1()}/100",Modifier.weight(1f))
                SmallMetric("DEPTH",if(team.depthLoaded)"OK" else "—",Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            visible.forEachIndexed{idx,p->
                PlayerAvailabilityRow(p)
                if(idx<visible.lastIndex)HorizontalDivider(color=DetailBorder)
            }
            if(team.players.isEmpty()){
                Text("No se recibió depth chart para este equipo.",color=DetailMuted,fontSize=9.sp)
            }else{
                Text(
                    if(expanded)"Ocultar profundidad" else "Ver roster/depth completo (${team.players.size})",
                    color=DetailBlue,fontSize=10.sp,fontWeight=FontWeight.Bold,
                    modifier=Modifier.fillMaxWidth().clickable{onToggle()}.padding(top=10.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerAvailabilityRow(p:RosterPlayerState){
    val status=p.injuryStatus.ifBlank{"ACTIVE / sin reporte"}
    val statusColor=when{
        p.unavailable->DetailRed
        status.contains("Doubt",true)||status.contains("Question",true)->DetailAmber
        else->DetailGreen
    }
    Row(Modifier.fillMaxWidth().padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
        Surface(color=DetailPanel,shape=RoundedCornerShape(8.dp),border=BorderStroke(1.dp,DetailBorder)){
            Text(
                if(p.depthRank<90)p.depthRank.toString() else "•",
                Modifier.padding(horizontal=8.dp,vertical=4.dp),
                color=DetailText,fontSize=9.sp,fontWeight=FontWeight.Black
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)){
            Text(p.name,color=DetailText,fontSize=10.sp,fontWeight=FontWeight.Bold)
            Text("${p.position} · ${p.depthLabel}",color=DetailMuted,fontSize=8.sp)
        }
        Column(horizontalAlignment=Alignment.End){
            Text(status,color=statusColor,fontSize=9.sp,fontWeight=FontWeight.Black)
            if(p.injuryDetail.isNotBlank())Text(p.injuryDetail,color=DetailMuted,fontSize=8.sp)
        }
    }
}

@Composable
private fun SectionTitle(text:String){
    Text(text,color=DetailMuted,fontSize=10.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(top=5.dp,bottom=2.dp))
}

@Composable
private fun SurfaceCard(content:@Composable ColumnScope.()->Unit){
    Surface(color=DetailCard,shape=RoundedCornerShape(18.dp),border=BorderStroke(1.dp,DetailBorder)){
        Column(Modifier.fillMaxWidth().padding(14.dp),content=content)
    }
}

@Composable
private fun SmallMetric(label:String,value:String,modifier:Modifier=Modifier){
    Surface(modifier=modifier,color=DetailPanel,shape=RoundedCornerShape(11.dp),border=BorderStroke(1.dp,DetailBorder)){
        Column(Modifier.padding(vertical=9.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(label,color=DetailMuted,fontSize=7.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center)
            Text(value,color=DetailText,fontSize=12.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center)
        }
    }
}

private fun engineDispersionLabel(p:Prediction):String{
    if(p.engines.size<2)return "—"
    val xs=p.engines.map{it.projection}
    val range=(xs.maxOrNull()?:0.0)-(xs.minOrNull()?:0.0)
    return when{range<4.0->"BAJA";range<8.0->"MEDIA";else->"ALTA"}
}

private fun fmt1(v:Double)=String.format(Locale.US,"%.1f",v)
private fun Double.f1()=String.format(Locale.US,"%.1f",this)
private fun signed1(v:Double)=String.format(Locale.US,"%+.1f",v)
