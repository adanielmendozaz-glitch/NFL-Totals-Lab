package com.nfltotalslab.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.nfltotalslab.app.data.*
import com.nfltotalslab.app.audit.AuditLab
import com.nfltotalslab.app.branding.TeamBadge
import com.nfltotalslab.app.branding.teamBrand
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

private enum class MainTab{JORNADA,RANKING,EQUIPOS,AJUSTES}
private enum class SubTab{NONE,CENSO,CORE,SHADOW,AUDIT,APUESTAS,BANK,BRIER,CALIB}

@Composable
fun NflTotalsApp(context:Context){
    val repo=remember{NflRepository(DbHelper(context))}
    val scope=rememberCoroutineScope()
    var main by remember{mutableStateOf(MainTab.JORNADA)}
    var sub by remember{mutableStateOf(SubTab.NONE)}
    var season by remember{mutableIntStateOf(2026)}
    var games by remember{mutableStateOf(repo.games(season))}
    var metrics by remember{mutableStateOf(repo.metrics(season))}
    var preds by remember{mutableStateOf(repo.predictions())}
    var shadows by remember{mutableStateOf(repo.shadows())}
    var calibrations by remember{mutableStateOf(repo.calibrations())}
    var bets by remember{mutableStateOf(repo.bets())}
    var bank by remember{mutableStateOf(repo.bank())}
    var syncing by remember{mutableStateOf(false)}
    var syncMsg by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<Prediction?>(null)}
    var liveEnabled by remember{mutableStateOf(false)}
    var liveLoading by remember{mutableStateOf(false)}
    var liveScores by remember{mutableStateOf<Map<String,LiveGameState>>(emptyMap())}

    fun refresh(){
        games=repo.games(season);metrics=repo.metrics(season);preds=repo.predictions();shadows=repo.shadows();calibrations=repo.calibrations();bets=repo.bets();bank=repo.bank()
    }

    LaunchedEffect(liveEnabled,season){
        if(!liveEnabled)return@LaunchedEffect
        while(liveEnabled){
            val activeWeek=games.firstOrNull{!it.finished && it.gameType=="REG"}?.week
                ?: games.maxOfOrNull{it.week}
                ?: 1
            liveLoading=true
            val r=runCatching{repo.liveScores(season,activeWeek)}
            r.onSuccess{
                liveScores=it
                refresh()
                val liveCount=it.values.count{x->x.isLive}
                syncMsg=if(liveCount>0)"LIVE ✓ · $liveCount juego(s) en curso · refresco 60s" else "LIVE ✓ · sin juegos en curso"
            }.onFailure{
                syncMsg="LIVE observer: ${it.message}"
            }
            liveLoading=false
            delay(60_000)
        }
    }

    Scaffold(
        containerColor=Bg,
        topBar={
            Column(Modifier.background(Bg).padding(horizontal=16.dp,vertical=10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("NFL TOTALS LAB",color=Green,fontSize=11.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                        Text("V0.8.1 · BANK + BETS PRO",color=Text,fontWeight=FontWeight.Black,fontSize=19.sp)
                    }
                    Text(
                        when{
                            syncing -> "SYNC…"
                            liveEnabled -> if(liveLoading)"LIVE…" else "LIVE ON"
                            else -> "SQLite OK"
                        },
                        color=when{syncing->Amber;liveEnabled->Red;else->Green},
                        fontWeight=FontWeight.Bold,fontSize=12.sp
                    )
                }
                syncMsg?.let{Text(it,color=Muted,fontSize=11.sp)}
            }
        },
        bottomBar={
            Column(Modifier.background(Panel)){
                if(main==MainTab.JORNADA && sub==SubTab.NONE) SubBar { sub=it }
                NavigationBar(containerColor=Panel){
                    navItem(main==MainTab.JORNADA,"⌂","Jornada"){main=MainTab.JORNADA;sub=SubTab.NONE}
                    navItem(main==MainTab.RANKING,"▥","Ranking"){main=MainTab.RANKING;sub=SubTab.NONE}
                    navItem(main==MainTab.EQUIPOS,"♙","Equipos"){main=MainTab.EQUIPOS;sub=SubTab.NONE}
                    navItem(main==MainTab.AJUSTES,"⚙","Ajustes"){main=MainTab.AJUSTES;sub=SubTab.NONE}
                }
            }
        }
    ){pad->
        Box(Modifier.fillMaxSize().padding(pad).background(Bg)){
            when{
                sub==SubTab.CENSO->CensusScreen(preds){sub=SubTab.NONE}
                sub==SubTab.CORE->CoreScreen(preds){sub=SubTab.NONE}
                sub==SubTab.SHADOW->ShadowScreen(shadows){sub=SubTab.NONE}
                sub==SubTab.AUDIT->AuditScreen(AuditLab.build(preds,shadows)){sub=SubTab.NONE}
                sub==SubTab.APUESTAS->BetsScreen(bets,preds){sub=SubTab.NONE}
                sub==SubTab.BANK->BankScreen(bank,bets,onAdd={amount,note->repo.addBank(amount,note);refresh()},onBack={sub=SubTab.NONE})
                sub==SubTab.BRIER->BrierScreen(preds,shadows){sub=SubTab.NONE}
                sub==SubTab.CALIB->ProgressiveCalibrationScreen(repo.calibrationState(),calibrations){sub=SubTab.NONE}
                main==MainTab.JORNADA->ScheduleScreen(
                    games=games,preds=preds,syncing=syncing,
                    liveScores=liveScores,liveEnabled=liveEnabled,liveLoading=liveLoading,
                    onToggleLive={liveEnabled=!liveEnabled;if(!liveEnabled)liveScores=emptyMap()},
                    onSync={
                        scope.launch{
                            syncing=true
                            syncMsg="FAST · calendario, líneas y resultados…"
                            val fast=runCatching{repo.fastSync(season)}
                            if(fast.isFailure){
                                syncMsg="FAST error: ${fast.exceptionOrNull()?.message}"
                                syncing=false
                                return@launch
                            }

                            refresh()
                            syncMsg="${fast.getOrThrow().message} · DEEP DATA…"

                            val deep=runCatching{repo.deepSync(season)}
                            refresh()
                            syncMsg=deep.fold(
                                onSuccess={fast.getOrThrow().message+" · "+it.message},
                                onFailure={fast.getOrThrow().message+" · DEEP error: "+it.message}
                            )
                            syncing=false
                        }
                    },
                    onAnalyze={g->
                        scope.launch{
                            syncMsg="Simulando ${g.awayTeam} @ ${g.homeTeam}…"
                            runCatching{repo.analyze(g)}.onSuccess{p->selected=p;refresh();syncMsg="Análisis guardado en Censo + Ranking"}.onFailure{syncMsg=it.message ?: "Análisis bloqueado"}
                        }
                    }
                )
                main==MainTab.RANKING->RankingScreen(preds){selected=it}
                main==MainTab.EQUIPOS->TeamsScreen(metrics)
                main==MainTab.AJUSTES->SettingsScreen(season,repo.lastSync(),onSeason={season=it;refresh()})
            }
            selected?.let{p->
                PredictionDialog(p,onDismiss={selected=null},onBet={
                    repo.addBet(p);refresh();selected=null
                })
            }
        }
    }
}

@Composable private fun RowScope.navItem(selected:Boolean,icon:String,label:String,onClick:()->Unit){
    NavigationBarItem(selected=selected,onClick=onClick,icon={Text(icon,fontSize=20.sp)},label={Text(label,fontSize=10.sp)})
}

@Composable
private fun SubBar(onClick:(SubTab)->Unit){
    Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=5.dp)){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            SubButton("▱","Censo",Modifier.weight(1f)){onClick(SubTab.CENSO)}
            SubButton("◉","Core",Modifier.weight(1f)){onClick(SubTab.CORE)}
            SubButton("◇","Shadow",Modifier.weight(1f)){onClick(SubTab.SHADOW)}
            SubButton("⌁","Audit",Modifier.weight(1f)){onClick(SubTab.AUDIT)}
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
            SubButton("🎟","Apuestas",Modifier.weight(1f)){onClick(SubTab.APUESTAS)}
            SubButton("▣","Bank",Modifier.weight(1f)){onClick(SubTab.BANK)}
            SubButton("⚗","Brier",Modifier.weight(1f)){onClick(SubTab.BRIER)}
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth()){
            SubButton("≈","Calib",Modifier.fillMaxWidth()){onClick(SubTab.CALIB)}
        }
    }
}

@Composable
private fun SubButton(icon:String,label:String,modifier:Modifier=Modifier,onClick:()->Unit){
    Surface(
        modifier=modifier.clickable{onClick()},
        color=Card,
        shape=RoundedCornerShape(12.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
    ){
        Row(Modifier.padding(vertical=9.dp),horizontalArrangement=Arrangement.Center){
            Text("$icon $label",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold)
        }
    }
}

@Composable
private fun ScheduleScreen(
    games:List<GameRecord>,
    preds:List<Prediction>,
    syncing:Boolean,
    liveScores:Map<String,LiveGameState>,
    liveEnabled:Boolean,
    liveLoading:Boolean,
    onToggleLive:()->Unit,
    onSync:()->Unit,
    onAnalyze:(GameRecord)->Unit
){
    var week by remember(games){mutableIntStateOf(
        games.firstOrNull{!it.finished && it.gameType=="REG"}?.week ?: games.maxOfOrNull{it.week} ?: 1
    )}
    val list=games.filter{it.week==week && it.gameType=="REG"}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("JORNADA · WEEK $week",color=Text,fontWeight=FontWeight.Black,fontSize=18.sp)
                Text("${list.size} partidos · SYNC analiza jornada completa · toque = reanálisis manual",color=Muted,fontSize=11.sp)
            }
            Button(onClick=onSync,enabled=!syncing,colors=ButtonDefaults.buttonColors(containerColor=Green)){
                Text(if(syncing)"SYNC…" else "SINCRONIZAR",fontSize=10.sp,fontWeight=FontWeight.Black)
            }
        }
        Row(Modifier.padding(horizontal=14.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={if(week>1)week--},modifier=Modifier.weight(1f)){Text("← Semana")}
            OutlinedButton(onClick={if(week<22)week++},modifier=Modifier.weight(1f)){Text("Semana →")}
        }
        OutlinedButton(
            onClick=onToggleLive,
            modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=6.dp),
            colors=ButtonDefaults.outlinedButtonColors(contentColor=if(liveEnabled)Red else Green)
        ){
            Text(
                when{
                    liveLoading -> "● LIVE · ACTUALIZANDO…"
                    liveEnabled -> "● LIVE ON · AUTO 60s"
                    else -> "○ ACTIVAR MARCADOR LIVE"
                },
                fontWeight=FontWeight.Black,fontSize=10.sp
            )
        }
        Spacer(Modifier.height(2.dp))
        if(list.isEmpty()){
            EmptyState("No hay jornada local todavía.\nPulsa SINCRONIZAR.")
        }else{
            LazyVerticalGrid(
                columns=GridCells.Fixed(2),
                contentPadding=PaddingValues(10.dp),
                horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ){
                items(list,key={it.gameId}){g->
                    val gamePreds=preds.filter{it.gameId==g.gameId}
                    val p=gamePreds.firstOrNull{it.analysisSource=="AUTO_CENSUS"} ?: gamePreds.firstOrNull()
                    val live=liveScores["${g.awayTeam}@${g.homeTeam}"]
                    GameCard(g,p,live){onAnalyze(g)}
                }
            }
        }
    }
}

@Composable
private fun GameCard(g:GameRecord,p:Prediction?,live:LiveGameState?,onClick:()->Unit){
    val liveNow=live?.isLive==true
    val finalNow=g.finished || live?.isFinal==true
    val status=when{
        liveNow -> live?.let{"● LIVE · ${it.detail.ifBlank{"Q${it.period} ${it.clock}"}}"} ?: "● LIVE"
        finalNow -> "FINAL"
        else -> "${g.gameDay.takeLast(5)} ${g.gameTime}"
    }
    val awayScore=if(liveNow || live?.isFinal==true)live?.awayScore else g.awayScore
    val homeScore=if(liveNow || live?.isFinal==true)live?.homeScore else g.homeScore
    Surface(
        modifier=Modifier.fillMaxWidth().clickable(enabled=!finalNow){onClick()},
        color=Card,shape=RoundedCornerShape(18.dp),
        border=androidx.compose.foundation.BorderStroke(
            1.dp,
            when{
                liveNow->Red
                p?.classification?.startsWith("JUGABLE")==true->Green
                else->Border
            }
        )
    ){
        Column{
            Box(Modifier.fillMaxWidth().height(4.dp).background(if(p?.classification?.startsWith("JUGABLE")==true)Green else Blue))
            Column(Modifier.padding(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(status,color=when{liveNow->Red;finalNow->Muted;else->Green},fontWeight=FontWeight.Black,fontSize=10.sp,modifier=Modifier.weight(1f))
                    Surface(color=Panel,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                        Text("O/U ${g.totalLine?.let{fmt(it)} ?: "—"}",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontWeight=FontWeight.Black,fontSize=10.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TeamLine(g.awayTeam,awayScore)
                Spacer(Modifier.height(7.dp))
                TeamLine(g.homeTeam,homeScore)
                HorizontalDivider(Modifier.padding(vertical=10.dp),color=Border)
                Text("Week ${g.week} · ${g.roof ?: "roof —"}",color=Muted,fontSize=10.sp)
                if(g.wind!=null || g.temp!=null) Text("${g.temp?.let{"${it.toInt()}°F"} ?: ""} ${g.wind?.let{"· viento ${it.toInt()}"} ?: ""}",color=Muted,fontSize=10.sp)
                if(liveNow || live?.isFinal==true){
                    Text(
                        "Marcador ${awayScore ?: 0}-${homeScore ?: 0} · total ${live?.total ?: ((awayScore?:0)+(homeScore?:0))}${p?.let{" · línea ${fmt(it.line)}"} ?: ""}",
                        color=if(liveNow)Red else Muted,fontSize=10.sp,fontWeight=FontWeight.Bold
                    )
                }
                if(p!=null){
                    Spacer(Modifier.height(9.dp))
                    Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                        Column(Modifier.padding(9.dp)){
                            Text("FULL GAME O/U · ${if(p.analysisSource=="AUTO_CENSUS")"AUTO" else "MANUAL"}",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold)
                            Text("${p.pick} ${fmt(p.line)} · ${(p.probability*100).toInt()}%",color=if(p.classification.startsWith("JUGABLE"))Green else Amber,fontWeight=FontWeight.Black,fontSize=12.sp)
                            Text(p.classification,color=Muted,fontSize=9.sp)
                        }
                    }
                } else {
                    Text(if(finalNow)"Final · sin análisis postgame" else "Toca el cuadro para analizar",Modifier.padding(top=8.dp),color=Muted,fontSize=9.sp)
                }
            }
        }
    }
}

@Composable
private fun TeamLine(team:String,score:Int?){
    val brand=teamBrand(team)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        TeamBadge(team,30.dp)
        Spacer(Modifier.width(8.dp))
        Text(team,Modifier.weight(1f),fontWeight=FontWeight.Black,fontSize=17.sp)
        Surface(
            color=brand.primary.copy(alpha=.14f),
            shape=RoundedCornerShape(8.dp),
            border=androidx.compose.foundation.BorderStroke(1.dp,brand.primary.copy(alpha=.45f))
        ){
            Text(
                score?.toString() ?: "—",
                Modifier.padding(horizontal=8.dp,vertical=2.dp),
                color=Text,
                fontWeight=FontWeight.Black,
                fontSize=20.sp
            )
        }
    }
}

@Composable
private fun RankingScreen(preds:List<Prediction>,onPick:(Prediction)->Unit){
    val latest=preds.groupBy{it.gameId}.mapNotNull{(_,rows)->rows.filter{it.analysisSource=="AUTO_CENSUS"}.maxByOrNull{it.createdAt} ?: rows.maxByOrNull{it.createdAt}}.sortedByDescending{it.probability}
    Column(Modifier.fillMaxSize().padding(14.dp)){
        Text("RANKING",fontWeight=FontWeight.Black,fontSize=20.sp)
        Text("1 predicción vigente por partido · ordenada por probabilidad",color=Muted,fontSize=11.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(latest){p->PredictionRow(p){onPick(p)}}
        }
    }
}

@Composable
private fun PredictionRow(p:Prediction,onClick:()->Unit){
    Surface(
        Modifier.fillMaxWidth().clickable{onClick()},
        color=Card,
        shape=RoundedCornerShape(14.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
    ){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Row(verticalAlignment=Alignment.CenterVertically){
                TeamBadge(p.awayTeam,24.dp)
                Spacer(Modifier.width(3.dp))
                TeamBadge(p.homeTeam,24.dp)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)){
                Text("${p.awayTeam} @ ${p.homeTeam}",fontWeight=FontWeight.Black)
                Text(
                    "μ ${fmt(p.projection)} · W${p.week} · ${if(p.analysisSource=="AUTO_CENSUS")"AUTO" else "MANUAL"} · ${p.result ?: "PENDIENTE"}",
                    color=Muted,fontSize=10.sp
                )
            }
            Column(horizontalAlignment=Alignment.End){
                Text(
                    "${p.pick} ${fmt(p.line)}",
                    fontWeight=FontWeight.Black,
                    color=if(p.classification.startsWith("JUGABLE"))Green else Text
                )
                Text("${(p.probability*100).format1()}% · ${p.classification}",color=Muted,fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun TeamsScreen(metrics:List<TeamMetrics>){
    Column(Modifier.fillMaxSize().padding(14.dp)){
        Text("EQUIPOS",fontWeight=FontWeight.Black,fontSize=20.sp)
        Text("EPA · drives · scoring · roster · lesiones",color=Muted,fontSize=11.sp)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(metrics.sortedBy{it.team}){m->
                val brand=teamBrand(m.team)
                Surface(
                    color=Card,
                    shape=RoundedCornerShape(14.dp),
                    border=androidx.compose.foundation.BorderStroke(1.dp,brand.primary.copy(alpha=.35f))
                ){
                    Column(Modifier.padding(12.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            TeamBadge(m.team,32.dp)
                            Spacer(Modifier.width(9.dp))
                            Text(m.team,fontWeight=FontWeight.Black,fontSize=18.sp,modifier=Modifier.weight(1f))
                            Text("${m.games} GP",color=Muted,fontSize=11.sp)
                        }
                        MetricLine("EPA/play",m.offEpaPerPlay.format3(),"EPA def",m.defEpaAllowedPerPlay.format3())
                        MetricLine("Drives/G",m.drivesPerGame.format2(),"TD/drive",(m.tdPerDrive*100).format1()+"%")
                        MetricLine("Success",(m.successRate*100).format1()+"%","Explosivas",(m.explosiveRate*100).format1()+"%")
                        MetricLine("Roster",m.rosterCount.toString(),"Lesiones",m.injuryCount.toString())
                    }
                }
            }
        }
    }
}

@Composable private fun MetricLine(a:String,av:String,b:String,bv:String){
    Row(Modifier.fillMaxWidth().padding(top=6.dp)){
        Text("$a ",color=Muted,fontSize=10.sp);Text(av,fontWeight=FontWeight.Bold,fontSize=10.sp,modifier=Modifier.weight(1f))
        Text("$b ",color=Muted,fontSize=10.sp);Text(bv,fontWeight=FontWeight.Bold,fontSize=10.sp)
    }
}

@Composable
private fun CensusScreen(preds:List<Prediction>,onBack:()->Unit){
    SimpleHeader("CENSO","Snapshots AUTO + reanálisis manuales. Historial completo y persistente.",onBack)
    LazyColumn(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        items(preds){PredictionRow(it){}}
    }
}


@Composable
private fun CoreScreen(preds:List<Prediction>,onBack:()->Unit){
    val latest=preds.groupBy{it.gameId}.mapNotNull{(_,rows)->rows.filter{it.analysisSource=="AUTO_CENSUS"}.maxByOrNull{it.createdAt} ?: rows.maxByOrNull{it.createdAt}}.sortedByDescending{it.createdAt}
    val autoSnapshots=preds.count{it.analysisSource=="AUTO_CENSUS"}
    val manualSnapshots=preds.count{it.analysisSource=="MANUAL"}
    val settled=latest.count{it.result=="WIN"||it.result=="LOSS"||it.result=="PUSH"}

    Column(Modifier.fillMaxSize()){
        SimpleHeader("CORE","1 registro vigente por juego · dataset de observación, sin tocar pesos.",onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal=14.dp),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ){
            CoreStat("JUEGOS",latest.size.toString(),Modifier.weight(1f))
            CoreStat("AUTO",autoSnapshots.toString(),Modifier.weight(1f))
            CoreStat("MANUAL",manualSnapshots.toString(),Modifier.weight(1f))
            CoreStat("FINAL",settled.toString(),Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.weight(1f).padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            items(latest){p->PredictionRow(p){}}
        }
    }
}

@Composable
private fun CoreStat(label:String,value:String,modifier:Modifier=Modifier){
    Surface(
        modifier=modifier,
        color=Card,
        shape=RoundedCornerShape(12.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
    ){
        Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(label,color=Muted,fontSize=8.sp,fontWeight=FontWeight.Bold)
            Text(value,fontWeight=FontWeight.Black,fontSize=16.sp,color=Green)
        }
    }
}

@Composable
private fun ShadowScreen(shadows:List<ShadowPrediction>,onBack:()->Unit){
    val latest=shadows
        .groupBy{"${it.gameId}|${it.modelName}"}
        .mapNotNull{(_,rows)->rows.maxByOrNull{it.createdAt}}
    val models=latest.groupBy{it.modelName}.toSortedMap()

    Column(Modifier.fillMaxSize()){
        SimpleHeader(
            "SHADOW LAB",
            "5 componentes + OppAdj EPA / Tempo / Composite · observación pregame.",
            onBack
        )

        if(latest.isEmpty()){
            EmptyState("Aún no hay Shadow snapshots.\nPulsa SINCRONIZAR antes del kickoff.")
        }else{
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal=14.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)
            ){
                item{
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        CoreStat("MODELOS",models.size.toString(),Modifier.weight(1f))
                        CoreStat("SNAPSHOTS",shadows.size.toString(),Modifier.weight(1f))
                        CoreStat("JUEGOS",latest.map{it.gameId}.distinct().size.toString(),Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                models.forEach{(name,rows)->
                    item{
                        val settled=rows.filter{it.result=="WIN"||it.result=="LOSS"}
                        val wins=settled.count{it.result=="WIN"}
                        val hit=if(settled.isEmpty())null else wins*100.0/settled.size
                        val brier=if(settled.isEmpty())null else settled.map{
                            val y=if(it.result=="WIN")1.0 else 0.0
                            (it.probability-y).pow(2)
                        }.average()

                        Surface(
                            color=Card,
                            shape=RoundedCornerShape(14.dp),
                            border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                        ){
                            Column(Modifier.fillMaxWidth().padding(12.dp)){
                                Text(name,fontWeight=FontWeight.Black,fontSize=15.sp)
                                Text(
                                    "N ${settled.size} · W $wins · Hit ${hit?.format1() ?: "—"}% · Brier ${brier?.format3() ?: "—"}",
                                    color=Muted,fontSize=10.sp
                                )
                            }
                        }
                    }
                }

                item{
                    Surface(
                        color=Panel,
                        shape=RoundedCornerShape(12.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                    ){
                        Text(
                            "OPP ADJ gana peso gradualmente con el historial. " +
                            "1 juego previo ≈17%; 3 ≈50%; 6+ = 100%. " +
                            "No se genera sin historial de ambos equipos.",
                            Modifier.padding(10.dp),
                            color=Muted,
                            fontSize=9.sp
                        )
                    }
                }

                item{
                    Spacer(Modifier.height(4.dp))
                    Text("ÚLTIMOS SHADOWS",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Black)
                }

                items(latest.sortedByDescending{it.createdAt}.take(80)){x->
                    ShadowRow(x)
                }
            }
        }
    }
}

@Composable
private fun ShadowRow(x:ShadowPrediction){
    Surface(
        Modifier.fillMaxWidth(),
        color=Card,
        shape=RoundedCornerShape(13.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
    ){
        Row(Modifier.padding(11.dp),verticalAlignment=Alignment.CenterVertically){
            TeamBadge(x.awayTeam,22.dp)
            Spacer(Modifier.width(3.dp))
            TeamBadge(x.homeTeam,22.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)){
                Text("${x.awayTeam} @ ${x.homeTeam}",fontWeight=FontWeight.Black,fontSize=12.sp)
                Text("${x.modelName} · W${x.week} · ${x.result ?: "PENDIENTE"}",color=Muted,fontSize=9.sp)
            }
            Column(horizontalAlignment=Alignment.End){
                Text("${x.pick} ${fmt(x.line)}",fontWeight=FontWeight.Black,fontSize=12.sp)
                Text("${(x.probability*100).format1()}% · μ ${fmt(x.projection)}",color=Muted,fontSize=9.sp)
            }
        }
    }
}

@Composable
private fun BetsScreen(
    bets:List<BetRecord>,
    preds:List<Prediction>,
    onBack:()->Unit
){
    var filter by remember{mutableStateOf("ALL")}
    val predMap=preds.associateBy{it.id}
    val pending=bets.filter{it.status=="PENDING"}
    val settled=bets.filter{it.status!="PENDING"}
    val wins=settled.count{it.status=="WIN"}
    val losses=settled.count{it.status=="LOSS"}
    val pushes=settled.count{it.status=="PUSH"}
    val settledStake=settled.sumOf{it.stake}
    val pnl=settled.sumOf{it.pnl}
    val roi=if(settledStake>0.0)pnl/settledStake*100.0 else null
    val hit=if(wins+losses>0)wins*100.0/(wins+losses) else null
    val exposure=pending.sumOf{it.stake}

    val visible=when(filter){
        "PENDING"->pending
        "WIN"->bets.filter{it.status=="WIN"}
        "LOSS"->bets.filter{it.status=="LOSS"}
        else->bets
    }

    Column(Modifier.fillMaxSize()){
        SimpleHeader("APUESTAS PRO","Portfolio de picks · exposición y rendimiento.",onBack)

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            item{
                Surface(
                    color=Card,
                    shape=RoundedCornerShape(16.dp),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                ){
                    Column(Modifier.fillMaxWidth().padding(14.dp)){
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text("P&L REALIZADO",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold)
                                Text(
                                    "${if(pnl>=0) "+" else ""}$${pnl.format2()}",
                                    color=if(pnl>=0)Green else Red,
                                    fontSize=28.sp,
                                    fontWeight=FontWeight.Black
                                )
                            }
                            Column(horizontalAlignment=Alignment.End){
                                Text("ROI",color=Muted,fontSize=9.sp)
                                Text(
                                    roi?.let{"${if(it>=0) "+" else ""}${it.format1()}%"} ?: "—",
                                    color=when{
                                        roi==null->Muted
                                        roi>=0->Green
                                        else->Red
                                    },
                                    fontWeight=FontWeight.Black,
                                    fontSize=17.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                            ProMetric("EXPOSICIÓN","$${exposure.format2()}",Modifier.weight(1f),Amber)
                            ProMetric("HIT",hit?.let{"${it.format1()}%"} ?: "—",Modifier.weight(1f),Blue)
                            ProMetric("W-L-P","$wins-$losses-$pushes",Modifier.weight(1f),Text)
                        }
                    }
                }
            }

            item{
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(6.dp)
                ){
                    listOf("ALL","PENDING","WIN","LOSS").forEach{f->
                        FilterPill(
                            label=when(f){
                                "ALL"->"TODAS"
                                "PENDING"->"ABIERTAS"
                                "WIN"->"WIN"
                                else->"LOSS"
                            },
                            selected=filter==f,
                            modifier=Modifier.weight(1f)
                        ){filter=f}
                    }
                }
            }

            if(visible.isEmpty()){
                item{
                    Surface(
                        color=Card,
                        shape=RoundedCornerShape(14.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                    ){
                        Text(
                            "No hay apuestas en este filtro.",
                            Modifier.fillMaxWidth().padding(22.dp),
                            color=Muted,
                            textAlign=TextAlign.Center
                        )
                    }
                }
            }else{
                items(visible,key={it.id}){b->
                    ProBetCard(b,predMap[b.predictionId])
                }
            }

            item{Spacer(Modifier.height(10.dp))}
        }
    }
}

@Composable
private fun ProBetCard(b:BetRecord,p:Prediction?){
    val statusColor=when(b.status){
        "WIN"->Green
        "LOSS"->Red
        "PUSH"->Amber
        else->Blue
    }

    val potentialProfit=b.stake*(b.odds-1.0)
    val potentialReturn=b.stake*b.odds

    Surface(
        Modifier.fillMaxWidth(),
        color=Card,
        shape=RoundedCornerShape(15.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,statusColor.copy(alpha=.55f))
    ){
        Column(Modifier.padding(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                if(p!=null){
                    TeamBadge(p.awayTeam,24.dp)
                    Spacer(Modifier.width(3.dp))
                    TeamBadge(p.homeTeam,24.dp)
                    Spacer(Modifier.width(8.dp))
                }

                Column(Modifier.weight(1f)){
                    Text(
                        p?.let{"${it.awayTeam} @ ${it.homeTeam}"} ?: b.gameId,
                        fontWeight=FontWeight.Black,
                        fontSize=12.sp
                    )
                    Text(formatDateTime(b.createdAt),color=Muted,fontSize=8.sp)
                }

                Surface(
                    color=statusColor.copy(alpha=.12f),
                    shape=RoundedCornerShape(20.dp),
                    border=androidx.compose.foundation.BorderStroke(1.dp,statusColor.copy(alpha=.50f))
                ){
                    Text(
                        if(b.status=="PENDING")"ABIERTA" else b.status,
                        Modifier.padding(horizontal=8.dp,vertical=4.dp),
                        color=statusColor,
                        fontWeight=FontWeight.Black,
                        fontSize=8.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(b.market,fontWeight=FontWeight.Black,fontSize=18.sp)
                    if(p!=null){
                        Text(
                            "${(p.probability*100).format1()}% · ${p.classification} · μ ${fmt(p.projection)}",
                            color=Muted,
                            fontSize=9.sp
                        )
                    }
                }
                Column(horizontalAlignment=Alignment.End){
                    Text("@ ${b.odds.format2()}",fontWeight=FontWeight.Black,fontSize=12.sp)
                    Text("Stake $${b.stake.format2()}",color=Muted,fontSize=9.sp)
                }
            }

            HorizontalDivider(Modifier.padding(vertical=9.dp),color=Border)

            Row(Modifier.fillMaxWidth()){
                Column(Modifier.weight(1f)){
                    Text("BENEFICIO POT.",color=Muted,fontSize=8.sp)
                    Text("+$${potentialProfit.format2()}",fontWeight=FontWeight.Bold,fontSize=11.sp)
                }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("RETORNO POT.",color=Muted,fontSize=8.sp)
                    Text("$${potentialReturn.format2()}",fontWeight=FontWeight.Bold,fontSize=11.sp)
                }
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.End){
                    Text("P&L",color=Muted,fontSize=8.sp)
                    Text(
                        if(b.status=="PENDING")"—" else "${if(b.pnl>=0) "+" else ""}$${b.pnl.format2()}",
                        color=when{
                            b.status=="PENDING"->Muted
                            b.pnl>=0->Green
                            else->Red
                        },
                        fontWeight=FontWeight.Black,
                        fontSize=11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label:String,
    selected:Boolean,
    modifier:Modifier=Modifier,
    onClick:()->Unit
){
    Surface(
        modifier=modifier.clickable{onClick()},
        color=if(selected)Green.copy(alpha=.16f) else Card,
        shape=RoundedCornerShape(20.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,if(selected)Green else Border)
    ){
        Text(
            label,
            Modifier.padding(vertical=7.dp),
            textAlign=TextAlign.Center,
            color=if(selected)Green else Muted,
            fontWeight=FontWeight.Black,
            fontSize=8.sp
        )
    }
}

@Composable
private fun ProMetric(
    label:String,
    value:String,
    modifier:Modifier=Modifier,
    valueColor:Color=Text
){
    Surface(
        modifier=modifier,
        color=Panel,
        shape=RoundedCornerShape(11.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
    ){
        Column(
            Modifier.padding(vertical=9.dp),
            horizontalAlignment=Alignment.CenterHorizontally
        ){
            Text(label,color=Muted,fontSize=7.sp,fontWeight=FontWeight.Bold)
            Text(value,color=valueColor,fontSize=12.sp,fontWeight=FontWeight.Black)
        }
    }
}

private fun formatDateTime(ms:Long):String =
    SimpleDateFormat("dd MMM · HH:mm",Locale.getDefault()).format(Date(ms))

@Composable
private fun BankScreen(
    entries:List<BankEntry>,
    bets:List<BetRecord>,
    onAdd:(Double,String)->Unit,
    onBack:()->Unit
){
    var amount by remember{mutableStateOf("")}
    var note by remember{mutableStateOf("")}

    val total=entries.sumOf{it.amount}
    val deposits=entries.filter{it.amount>0}.sumOf{it.amount}
    val withdrawals=-entries.filter{it.amount<0}.sumOf{it.amount}
    val pendingExposure=bets.filter{it.status=="PENDING"}.sumOf{it.stake}
    val realizedPnl=bets.filter{it.status!="PENDING"}.sumOf{it.pnl}

    Column(Modifier.fillMaxSize()){
        SimpleHeader("BANK PRO","Gestión de banca · capital, exposición y movimientos.",onBack)

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            item{
                Surface(
                    color=Card,
                    shape=RoundedCornerShape(18.dp),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Green.copy(alpha=.55f))
                ){
                    Column(Modifier.fillMaxWidth().padding(16.dp)){
                        Text("BANK ACTUAL",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold)
                        Text("$${total.format2()}",fontWeight=FontWeight.Black,fontSize=32.sp,color=Green)
                        Text("${entries.size} movimiento(s) registrados",color=Muted,fontSize=9.sp)
                    }
                }
            }

            item{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    ProMetric("ENTRADAS","$${deposits.format2()}",Modifier.weight(1f),Green)
                    ProMetric("RETIROS","$${withdrawals.format2()}",Modifier.weight(1f),Red)
                    ProMetric("EXPOSICIÓN","$${pendingExposure.format2()}",Modifier.weight(1f),Amber)
                }
                Spacer(Modifier.height(7.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                    ProMetric(
                        "P&L APUESTAS",
                        "${if(realizedPnl>=0) "+" else ""}$${realizedPnl.format2()}",
                        Modifier.weight(1f),
                        if(realizedPnl>=0)Green else Red
                    )
                    ProMetric("MOVIMIENTOS",entries.size.toString(),Modifier.weight(1f),Blue)
                }
            }

            item{
                Surface(
                    color=Card,
                    shape=RoundedCornerShape(15.dp),
                    border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                ){
                    Column(Modifier.padding(13.dp)){
                        Text("NUEVO MOVIMIENTO",fontSize=10.sp,fontWeight=FontWeight.Black)
                        Text("Positivo = depósito · negativo = retiro.",color=Muted,fontSize=8.sp)

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value=amount,
                            onValueChange={amount=it},
                            label={Text("Monto + / -")},
                            modifier=Modifier.fillMaxWidth(),
                            singleLine=true
                        )

                        Spacer(Modifier.height(6.dp))

                        OutlinedTextField(
                            value=note,
                            onValueChange={note=it},
                            label={Text("Concepto / nota")},
                            modifier=Modifier.fillMaxWidth(),
                            singleLine=true
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(6.dp)
                        ){
                            listOf(100,250,500).forEach{v->
                                OutlinedButton(
                                    onClick={amount=v.toString()},
                                    modifier=Modifier.weight(1f)
                                ){
                                    Text("+$v",fontSize=9.sp)
                                }
                            }
                        }

                        Button(
                            onClick={
                                val value=amount.toDoubleOrNull()
                                if(value!=null && value!=0.0){
                                    onAdd(value,note.ifBlank{"Ajuste manual"})
                                    amount=""
                                    note=""
                                }
                            },
                            modifier=Modifier.fillMaxWidth().padding(top=8.dp),
                            colors=ButtonDefaults.buttonColors(containerColor=Green)
                        ){
                            Text("GUARDAR MOVIMIENTO",fontWeight=FontWeight.Black,fontSize=10.sp)
                        }
                    }
                }
            }

            item{
                Text("HISTORIAL DE MOVIMIENTOS",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Black)
            }

            if(entries.isEmpty()){
                item{
                    Surface(
                        color=Card,
                        shape=RoundedCornerShape(14.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                    ){
                        Text(
                            "Aún no hay movimientos de banca.",
                            Modifier.fillMaxWidth().padding(20.dp),
                            textAlign=TextAlign.Center,
                            color=Muted
                        )
                    }
                }
            }else{
                items(entries,key={it.id}){e->
                    val positive=e.amount>=0
                    Surface(
                        color=Card,
                        shape=RoundedCornerShape(13.dp),
                        border=androidx.compose.foundation.BorderStroke(1.dp,Border)
                    ){
                        Row(
                            Modifier.fillMaxWidth().padding(11.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Surface(
                                color=(if(positive)Green else Red).copy(alpha=.12f),
                                shape=RoundedCornerShape(10.dp)
                            ){
                                Text(
                                    if(positive)"＋" else "－",
                                    Modifier.padding(horizontal=9.dp,vertical=6.dp),
                                    color=if(positive)Green else Red,
                                    fontWeight=FontWeight.Black
                                )
                            }

                            Spacer(Modifier.width(9.dp))

                            Column(Modifier.weight(1f)){
                                Text(e.note.ifBlank{"Movimiento"},fontWeight=FontWeight.Bold,fontSize=11.sp)
                                Text(formatDateTime(e.createdAt),color=Muted,fontSize=8.sp)
                            }

                            Text(
                                "${if(positive) "+" else ""}$${e.amount.format2()}",
                                color=if(positive)Green else Red,
                                fontWeight=FontWeight.Black,
                                fontSize=13.sp
                            )
                        }
                    }
                }
            }

            item{
                Text(
                    "Bank y P&L se muestran por separado para evitar doble conteo. " +
                    "La banca refleja únicamente movimientos registrados; las apuestas conservan su P&L propio.",
                    color=Muted,
                    fontSize=8.sp,
                    modifier=Modifier.padding(vertical=8.dp)
                )
            }
        }
    }
}

private data class CalBucket(
    val label:String,
    val n:Int,
    val actual:Double?,
    val predicted:Double?,
    val gap:Double?
)

private fun calibrationBuckets(rows:List<Pair<Double,Boolean>>):List<CalBucket>{
    val specs=listOf(
        Triple("50–54.9%",.50,.55),
        Triple("55–59.9%",.55,.60),
        Triple("60–64.9%",.60,.65),
        Triple("65–69.9%",.65,.70),
        Triple("70%+",.70,1.01)
    )
    return specs.map{(label,lo,hi)->
        val r=rows.filter{it.first>=lo && it.first<hi}
        if(r.isEmpty())CalBucket(label,0,null,null,null)
        else{
            val pred=r.map{it.first}.average()
            val actual=r.count{it.second}.toDouble()/r.size
            CalBucket(label,r.size,actual,pred,actual-pred)
        }
    }
}

@Composable
private fun BrierScreen(preds:List<Prediction>,shadows:List<ShadowPrediction>,onBack:()->Unit){
    val core=preds
        .filter{it.analysisSource=="AUTO_CENSUS"}
        .groupBy{it.gameId}
        .mapNotNull{(_,rows)->rows.filter{it.result=="WIN"||it.result=="LOSS"}.maxByOrNull{it.createdAt}}

    val coreBrier=if(core.isEmpty())null else core.map{
        val y=if(it.result=="WIN")1.0 else 0.0
        (it.probability-y).pow(2)
    }.average()

    val buckets=calibrationBuckets(core.map{it.probability to (it.result=="WIN")})

    val shadowLatest=shadows
        .groupBy{"${it.gameId}|${it.modelName}"}
        .mapNotNull{(_,rows)->rows.filter{it.result=="WIN"||it.result=="LOSS"}.maxByOrNull{it.createdAt}}
    val shadowModels=shadowLatest.groupBy{it.modelName}

    Column(Modifier.fillMaxSize()){
        SimpleHeader("BRIER + CALIBRATION","Core oficial + auditoría Shadow por probabilidad.",onBack)

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal=14.dp),
            verticalArrangement=Arrangement.spacedBy(8.dp)
        ){
            item{
                Surface(color=Card,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                    Column(Modifier.fillMaxWidth().padding(16.dp)){
                        Text("CORE BRIER",color=Muted,fontSize=10.sp)
                        Text(coreBrier?.format3() ?: "—",fontWeight=FontWeight.Black,fontSize=32.sp,color=Green)
                        Text("${core.size} juegos oficiales liquidados · 1 cierre por partido",color=Muted,fontSize=10.sp)
                    }
                }
            }

            item{
                Text("CALIBRACIÓN POR PROBABILIDAD",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Black)
            }

            items(buckets){b->
                Surface(color=Card,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                    Row(Modifier.fillMaxWidth().padding(11.dp),verticalAlignment=Alignment.CenterVertically){
                        Text(b.label,fontWeight=FontWeight.Black,fontSize=11.sp,modifier=Modifier.weight(1f))
                        Column(horizontalAlignment=Alignment.End){
                            Text("N ${b.n} · Real ${b.actual?.times(100)?.format1() ?: "—"}%",fontSize=10.sp)
                            Text(
                                "Pred ${b.predicted?.times(100)?.format1() ?: "—"}% · Gap ${b.gap?.times(100)?.format1() ?: "—"} pp",
                                color=Muted,fontSize=9.sp
                            )
                        }
                    }
                }
            }

            item{
                Spacer(Modifier.height(4.dp))
                Text("SHADOW BRIER",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Black)
            }

            shadowModels.toSortedMap().forEach{(name,rows)->
                item{
                    val brier=if(rows.isEmpty())null else rows.map{
                        val y=if(it.result=="WIN")1.0 else 0.0
                        (it.probability-y).pow(2)
                    }.average()
                    val wins=rows.count{it.result=="WIN"}
                    val hit=if(rows.isEmpty())null else wins*100.0/rows.size
                    Surface(color=Card,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                        Row(Modifier.fillMaxWidth().padding(11.dp)){
                            Text(name,Modifier.weight(1f),fontWeight=FontWeight.Black,fontSize=11.sp)
                            Text("N ${rows.size} · Hit ${hit?.format1() ?: "—"}% · B ${brier?.format3() ?: "—"}",color=Muted,fontSize=9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(season:Int,lastSync:Long?,onSeason:(Int)->Unit){
    Column(Modifier.fillMaxSize().padding(14.dp)){
        Text("AJUSTES",fontWeight=FontWeight.Black,fontSize=20.sp)
        Spacer(Modifier.height(10.dp))
        Surface(color=Card,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
            Column(Modifier.padding(14.dp)){
                Text("TEMPORADA",color=Muted,fontSize=10.sp)
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(season.toString(),fontWeight=FontWeight.Black,fontSize=24.sp,modifier=Modifier.weight(1f))
                    OutlinedButton(onClick={onSeason(season-1)}){Text("−")}
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(onClick={onSeason(season+1)}){Text("+")}
                }
                HorizontalDivider(Modifier.padding(vertical=12.dp),color=Border)
                Text("CORE",color=Muted,fontSize=10.sp)
                Text("Markov Drive 30% · NegBin 25% · Drive MC 20% · Bayesian 15% · Shadow Poisson 10%",fontSize=12.sp)
                Spacer(Modifier.height(8.dp))
                Text("100,000 simulaciones por motor · Shadow audita cada motor",color=Green,fontWeight=FontWeight.Bold,fontSize=11.sp)
                Text("FINAL LIVE/SYNC → Core + Shadow + Brier + Audit automático",color=Muted,fontSize=10.sp)
                Spacer(Modifier.height(12.dp))
                Text("DATA VAULT",color=Muted,fontSize=10.sp)
                Text("SQLite local protegido por sandbox de Android",fontWeight=FontWeight.Bold,fontSize=12.sp)
                Text(lastSync?.let{"Último sync: "+Date(it).toString()} ?: "Aún no sincronizado",color=Muted,fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun PredictionDialog(p:Prediction,onDismiss:()->Unit,onBet:()->Unit){
    AlertDialog(
        onDismissRequest=onDismiss,
        confirmButton={Button(onClick=onBet){Text("Añadir a Apuestas")}},
        dismissButton={OutlinedButton(onClick=onDismiss){Text("Cerrar")}},
        title={Column{Text("${p.awayTeam} @ ${p.homeTeam}");Text(p.classification,color=if(p.classification.startsWith("JUGABLE"))Green else Amber,fontSize=12.sp)}},
        text={
            Column{
                Text("${p.pick} ${fmt(p.line)}",fontWeight=FontWeight.Black,fontSize=27.sp)
                Text("${(p.probability*100).format1()}% · Proyección ${fmt(p.projection)}",color=Green,fontWeight=FontWeight.Bold)
                Text("${if(p.analysisSource=="AUTO_CENSUS")"AUTO CENSUS" else "MANUAL"} · Core ${p.modelVersion}",color=Muted,fontSize=10.sp)
                Spacer(Modifier.height(12.dp))
                p.engines.forEach{e->
                    Row(Modifier.fillMaxWidth().padding(vertical=3.dp)){
                        Text(e.name,Modifier.weight(1f),color=Muted,fontSize=11.sp)
                        Text("μ ${fmt(e.projection)} · O ${(e.pOver*100).format1()}%",fontSize=11.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
        },
        containerColor=Panel
    )
}

@Composable
private fun SimpleHeader(title:String,subtitle:String,onBack:()->Unit){
    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
        OutlinedButton(onClick=onBack){Text("←")}
        Column(Modifier.padding(start=10.dp)){Text(title,fontWeight=FontWeight.Black,fontSize=20.sp);Text(subtitle,color=Muted,fontSize=10.sp)}
    }
}

@Composable private fun EmptyState(text:String){
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
        Text(text,color=Muted,textAlign=TextAlign.Center)
    }
}

private fun fmt(v:Double)=String.format(Locale.US,"%.1f",v)
private fun Double.format1()=String.format(Locale.US,"%.1f",this)
private fun Double.format2()=String.format(Locale.US,"%.2f",this)
private fun Double.format3()=String.format(Locale.US,"%.3f",this)
