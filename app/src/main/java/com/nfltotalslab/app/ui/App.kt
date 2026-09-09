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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow

private enum class MainTab{JORNADA,RANKING,EQUIPOS,AJUSTES}
private enum class SubTab{NONE,CENSO,APUESTAS,BANK,BRIER}

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
    var bets by remember{mutableStateOf(repo.bets())}
    var bank by remember{mutableStateOf(repo.bank())}
    var syncing by remember{mutableStateOf(false)}
    var syncMsg by remember{mutableStateOf<String?>(null)}
    var selected by remember{mutableStateOf<Prediction?>(null)}

    fun refresh(){
        games=repo.games(season);metrics=repo.metrics(season);preds=repo.predictions();bets=repo.bets();bank=repo.bank()
    }

    Scaffold(
        containerColor=Bg,
        topBar={
            Column(Modifier.background(Bg).padding(horizontal=16.dp,vertical=10.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("NFL TOTALS LAB",color=Green,fontSize=11.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                        Text("V0.2 · NATIVE DRIVE CORE",color=Text,fontWeight=FontWeight.Black,fontSize=19.sp)
                    }
                    Text(if(syncing)"SYNC…" else "SQLite OK",color=if(syncing)Amber else Green,fontWeight=FontWeight.Bold,fontSize=12.sp)
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
                sub==SubTab.APUESTAS->BetsScreen(bets){sub=SubTab.NONE}
                sub==SubTab.BANK->BankScreen(bank,onAdd={repo.addBank(it,"Ajuste manual");refresh()},onBack={sub=SubTab.NONE})
                sub==SubTab.BRIER->BrierScreen(preds){sub=SubTab.NONE}
                main==MainTab.JORNADA->ScheduleScreen(
                    games=games,preds=preds,syncing=syncing,
                    onSync={
                        scope.launch{
                            syncing=true;syncMsg="Descargando calendario + PBP + roster + lesiones…"
                            val r=runCatching{repo.sync(season)}
                            syncMsg=r.fold(
                                onSuccess={"${it.scheduleGames} juegos · ${it.pbpTeams} equipos PBP · roster ${it.rosterPlayers}"},
                                onFailure={"Error: ${it.message}"}
                            )
                            syncing=false;refresh()
                        }
                    },
                    onAnalyze={g->
                        scope.launch{
                            syncMsg="Simulando ${g.awayTeam} @ ${g.homeTeam}…"
                            val p=repo.analyze(g);selected=p;refresh();syncMsg="Análisis guardado en Censo + Ranking"
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
    Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){
        listOf("▱" to SubTab.CENSO,"🎟" to SubTab.APUESTAS,"▣" to SubTab.BANK,"⚗" to SubTab.BRIER).forEach{(ic,t)->
            val name=when(t){SubTab.CENSO->"Censo";SubTab.APUESTAS->"Apuestas";SubTab.BANK->"Bank";else->"Brier LAB"}
            Surface(
                modifier=Modifier.weight(1f).clickable{onClick(t)},
                color=Card,shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)
            ){Row(Modifier.padding(vertical=11.dp),horizontalArrangement=Arrangement.Center){
                Text("$ic $name",color=Muted,fontSize=10.sp,fontWeight=FontWeight.Bold)
            }}
        }
    }
}

@Composable
private fun ScheduleScreen(games:List<GameRecord>,preds:List<Prediction>,syncing:Boolean,onSync:()->Unit,onAnalyze:(GameRecord)->Unit){
    var week by remember(games){mutableIntStateOf(
        games.firstOrNull{!it.finished && it.gameType=="REG"}?.week ?: games.maxOfOrNull{it.week} ?: 1
    )}
    val list=games.filter{it.week==week && it.gameType=="REG"}
    Column(Modifier.fillMaxSize()){
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("JORNADA · WEEK $week",color=Text,fontWeight=FontWeight.Black,fontSize=18.sp)
                Text("${list.size} partidos · toca una tarjeta para analizar",color=Muted,fontSize=11.sp)
            }
            Button(onClick=onSync,enabled=!syncing,colors=ButtonDefaults.buttonColors(containerColor=Green)){
                Text(if(syncing)"SYNC…" else "SINCRONIZAR",fontSize=10.sp,fontWeight=FontWeight.Black)
            }
        }
        Row(Modifier.padding(horizontal=14.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={if(week>1)week--},modifier=Modifier.weight(1f)){Text("← Semana")}
            OutlinedButton(onClick={if(week<22)week++},modifier=Modifier.weight(1f)){Text("Semana →")}
        }
        Spacer(Modifier.height(8.dp))
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
                    val p=preds.firstOrNull{it.gameId==g.gameId}
                    GameCard(g,p){onAnalyze(g)}
                }
            }
        }
    }
}

@Composable
private fun GameCard(g:GameRecord,p:Prediction?,onClick:()->Unit){
    val status=if(g.finished)"FINAL" else "${g.gameDay.takeLast(5)} ${g.gameTime}"
    Surface(
        modifier=Modifier.fillMaxWidth().clickable{onClick()},
        color=Card,shape=RoundedCornerShape(18.dp),
        border=androidx.compose.foundation.BorderStroke(1.dp,if(p?.classification?.startsWith("JUGABLE")==true)Green else Border)
    ){
        Column{
            Box(Modifier.fillMaxWidth().height(4.dp).background(if(p?.classification?.startsWith("JUGABLE")==true)Green else Blue))
            Column(Modifier.padding(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(status,color=if(g.finished)Muted else Green,fontWeight=FontWeight.Black,fontSize=10.sp,modifier=Modifier.weight(1f))
                    Surface(color=Panel,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                        Text("O/U ${g.totalLine?.let{fmt(it)} ?: "—"}",Modifier.padding(horizontal=8.dp,vertical=4.dp),fontWeight=FontWeight.Black,fontSize=10.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                TeamLine(g.awayTeam,g.awayScore)
                Spacer(Modifier.height(7.dp))
                TeamLine(g.homeTeam,g.homeScore)
                HorizontalDivider(Modifier.padding(vertical=10.dp),color=Border)
                Text("Week ${g.week} · ${g.roof ?: "roof —"}",color=Muted,fontSize=10.sp)
                if(g.wind!=null || g.temp!=null) Text("${g.temp?.let{"${it.toInt()}°F"} ?: ""} ${g.wind?.let{"· viento ${it.toInt()}"} ?: ""}",color=Muted,fontSize=10.sp)
                if(p!=null){
                    Spacer(Modifier.height(9.dp))
                    Surface(color=Panel,shape=RoundedCornerShape(12.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                        Column(Modifier.padding(9.dp)){
                            Text("FULL GAME O/U",color=Muted,fontSize=9.sp,fontWeight=FontWeight.Bold)
                            Text("${p.pick} ${fmt(p.line)} · ${(p.probability*100).toInt()}%",color=if(p.classification.startsWith("JUGABLE"))Green else Amber,fontWeight=FontWeight.Black,fontSize=12.sp)
                            Text(p.classification,color=Muted,fontSize=9.sp)
                        }
                    }
                } else {
                    Text("Toca el cuadro para analizar",Modifier.padding(top=8.dp),color=Muted,fontSize=9.sp)
                }
            }
        }
    }
}

@Composable private fun TeamLine(team:String,score:Int?){
    Row(verticalAlignment=Alignment.CenterVertically){
        Surface(color=Panel,shape=RoundedCornerShape(8.dp),modifier=Modifier.size(34.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
            Box(contentAlignment=Alignment.Center){Text(team.take(3),fontWeight=FontWeight.Black,fontSize=10.sp,color=Blue)}
        }
        Text(team,Modifier.padding(start=8.dp).weight(1f),fontWeight=FontWeight.Black,fontSize=17.sp)
        Text(score?.toString() ?: "—",fontWeight=FontWeight.Black,fontSize=22.sp)
    }
}

@Composable
private fun RankingScreen(preds:List<Prediction>,onPick:(Prediction)->Unit){
    val latest=preds.groupBy{it.gameId}.mapNotNull{it.value.maxByOrNull{p->p.createdAt}}.sortedByDescending{it.probability}
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
    Surface(Modifier.fillMaxWidth().clickable{onClick()},color=Card,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text("${p.awayTeam} @ ${p.homeTeam}",fontWeight=FontWeight.Black)
                Text("μ ${fmt(p.projection)} · Week ${p.week} · ${p.result ?: "PENDIENTE"}",color=Muted,fontSize=10.sp)
            }
            Column(horizontalAlignment=Alignment.End){
                Text("${p.pick} ${fmt(p.line)}",fontWeight=FontWeight.Black,color=if(p.classification.startsWith("JUGABLE"))Green else Text)
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
                Surface(color=Card,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                    Column(Modifier.padding(12.dp)){
                        Row{
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
    SimpleHeader("CENSO","Cada análisis queda guardado; los finales se liquidan al sincronizar.",onBack)
    LazyColumn(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        items(preds){PredictionRow(it){}}
    }
}

@Composable
private fun BetsScreen(bets:List<BetRecord>,onBack:()->Unit){
    Column{
        SimpleHeader("APUESTAS","Control de picks seleccionados.",onBack)
        LazyColumn(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(bets){b->
                Surface(color=Card,shape=RoundedCornerShape(14.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                    Row(Modifier.fillMaxWidth().padding(12.dp)){
                        Column(Modifier.weight(1f)){Text(b.market,fontWeight=FontWeight.Black);Text(b.gameId,color=Muted,fontSize=9.sp)}
                        Column(horizontalAlignment=Alignment.End){Text("$${b.stake.format2()}",fontWeight=FontWeight.Black);Text(b.status,color=Muted,fontSize=10.sp)}
                    }
                }
            }
        }
    }
}

@Composable
private fun BankScreen(entries:List<BankEntry>,onAdd:(Double)->Unit,onBack:()->Unit){
    var amount by remember{mutableStateOf("")}
    val total=entries.sumOf{it.amount}
    Column{
        SimpleHeader("BANK","Registro local SQLite.",onBack)
        Column(Modifier.padding(14.dp)){
            Surface(color=Card,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                Column(Modifier.fillMaxWidth().padding(16.dp)){
                    Text("BANK ACTUAL",color=Muted,fontSize=10.sp)
                    Text("$${total.format2()}",fontWeight=FontWeight.Black,fontSize=30.sp,color=Green)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value=amount,onValueChange={amount=it},label={Text("Ajuste + / -")},modifier=Modifier.fillMaxWidth())
            Button(onClick={amount.toDoubleOrNull()?.let{onAdd(it);amount=""}},modifier=Modifier.fillMaxWidth().padding(top=8.dp)){Text("Guardar movimiento")}
        }
    }
}

@Composable
private fun BrierScreen(preds:List<Prediction>,onBack:()->Unit){
    val settled=preds.filter{it.result=="WIN"||it.result=="LOSS"}
    val brier=if(settled.isEmpty())null else settled.map{
        val y=if(it.result=="WIN")1.0 else 0.0
        (it.probability-y).pow(2)
    }.average()
    Column{
        SimpleHeader("BRIER LAB","Calibración real del modelo contra picks liquidados.",onBack)
        Column(Modifier.padding(14.dp)){
            Surface(color=Card,shape=RoundedCornerShape(16.dp),border=androidx.compose.foundation.BorderStroke(1.dp,Border)){
                Column(Modifier.fillMaxWidth().padding(16.dp)){
                    Text("BRIER SCORE",color=Muted,fontSize=10.sp)
                    Text(brier?.format3() ?: "—",fontWeight=FontWeight.Black,fontSize=32.sp,color=Green)
                    Text("${settled.size} predicciones liquidadas · menor es mejor",color=Muted,fontSize=10.sp)
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
                Text("100,000 simulaciones por motor",color=Green,fontWeight=FontWeight.Bold,fontSize=11.sp)
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
