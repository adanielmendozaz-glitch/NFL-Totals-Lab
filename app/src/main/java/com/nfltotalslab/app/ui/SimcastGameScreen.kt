package com.nfltotalslab.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfltotalslab.app.data.GameRecord
import com.nfltotalslab.app.simcast.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val scBg=Color(0xff09131e)
private val scCard=Color(0xff132536)
private val scText=Color(0xfff0f6fc)
private val scMuted=Color(0xff9fb1c1)
private val scGreen=Color(0xff2fd1a3)
private val scBlue=Color(0xff70c8ff)
private val scAmber=Color(0xffffd36d)

/** Per-match, two-team animated scenario. Not connected to official Core probabilities. */
@Composable
fun SimcastGameScreen(
    games:List<GameRecord>, initialGame:GameRecord?,
    onSimulate:suspend (GameRecord,Int)->GameSim,
    onBack:()->Unit
){
    val scope=rememberCoroutineScope()
    val currentWeek=games.filter{!it.finished && it.gameType=="REG"}.minOfOrNull{it.week}
        ?: games.maxOfOrNull{it.week} ?: 1
    var week by remember(initialGame?.gameId,games.size){mutableIntStateOf(initialGame?.week ?: currentWeek)}
    var gameId by remember(initialGame?.gameId,games.size){mutableStateOf(initialGame?.gameId ?: games.firstOrNull{it.week==week}?.gameId)}
    val available=games.filter{it.week==week && it.gameType=="REG"}.sortedBy{it.gameDay+" "+it.gameTime}
    val selected=games.firstOrNull{it.gameId==gameId}
    var sim by remember{mutableStateOf<GameSim?>(null)}
    var index by remember{mutableIntStateOf(0)}
    var playing by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var speed by remember{mutableFloatStateOf(2f)}
    var seedCounter by remember{mutableIntStateOf(1)}
    var error by remember{mutableStateOf<String?>(null)}
    var selectedTeam by remember{mutableStateOf<String?>(null)}
    var pendingJson by remember{mutableStateOf<String?>(null)}
    var exportStatus by remember{mutableStateOf<String?>(null)}
    val ctx=androidx.compose.ui.platform.LocalContext.current
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null){
            runCatching{ctx.contentResolver.openOutputStream(uri)?.use{
                it.write((pendingJson ?: "").toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo escribir el archivo")}
                .onSuccess{exportStatus="Simulación exportada ✓"}
                .onFailure{exportStatus="Error de exportación: ${it.message}"}
        }
    }
    LaunchedEffect(selected?.gameId){sim=null;index=0;playing=false;selectedTeam=null;error=null}
    LaunchedEffect(sim,playing,speed,index){
        val s=sim
        if(playing && s!=null && s.plays.isNotEmpty()){
            if(index>=s.plays.lastIndex){playing=false}
            else{delay((900L/speed).toLong().coerceAtLeast(70L));index++}
        }
    }
    fun runScenario(){
        val g=selected ?: return
        scope.launch{
            playing=false;loading=true;error=null
            val seed=g.gameId.hashCode() xor (seedCounter*104729)
            seedCounter++
            runCatching{onSimulate(g,seed)}
                .onSuccess{sim=it;index=0;selectedTeam=it.away;playing=true}
                .onFailure{error="Error al simular: ${it.message}"}
            loading=false
        }
    }
    val play=sim?.plays?.getOrNull(index)
    LazyColumn(
        Modifier.fillMaxSize().background(scBg),
        contentPadding=PaddingValues(14.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp)
    ){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                OutlinedButton(onClick=onBack){Text("← VOLVER",fontSize=11.sp)}
                Spacer(Modifier.width(10.dp))
                Column{
                    Text("SIMCAST · PLAYER USAGE",color=scText,fontSize=17.sp,fontWeight=FontWeight.Black)
                    Text("Partidos completos · jugadas de ambos equipos",color=scMuted,fontSize=11.sp)
                }
            }
        }
        item{
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton(enabled=week>1,onClick={week--;gameId=games.firstOrNull{it.week==week&&it.gameType=="REG"}?.gameId}){
                    Text("◀",fontSize=12.sp)
                }
                Text("SEMANA $week",Modifier.weight(1f),color=scText,fontSize=14.sp,fontWeight=FontWeight.Bold)
                OutlinedButton(enabled=week<22,onClick={week++;gameId=games.firstOrNull{it.week==week&&it.gameType=="REG"}?.gameId}){
                    Text("▶",fontSize=12.sp)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){
                available.forEach{g->
                    val chosen=g.gameId==gameId
                    if(chosen)Button(onClick={gameId=g.gameId}){Text("${g.awayTeam} @ ${g.homeTeam}",fontSize=10.sp)}
                    else OutlinedButton(onClick={gameId=g.gameId}){Text("${g.awayTeam} @ ${g.homeTeam}",fontSize=10.sp)}
                }
            }
            if(available.isEmpty())Text("Sin partidos cargados para esta semana. Sincroniza Jornada.",color=scAmber,fontSize=11.sp)
        }
        item{
            Button(
                modifier=Modifier.fillMaxWidth(),enabled=selected!=null&&!loading,
                onClick={runScenario},colors=ButtonDefaults.buttonColors(containerColor=scGreen)
            ){Text(if(loading)"GENERANDO PARTIDO…" else if(sim==null)"SIMULAR PARTIDO COMPLETO" else "NUEVA SIMULACIÓN",color=scBg,fontWeight=FontWeight.Bold)}
            error?.let{Text(it,color=scAmber,fontSize=11.sp)}
            Text("Motor experimental: drive + down/distancia + Player Weekly. No modifica el pick del Core.",color=scMuted,fontSize=10.sp)
            if(selected?.finished==true){
                Text("Partido finalizado: esta corrida NO es un backtest pregame; los datos actuales podrían ser posteriores al partido.",color=scAmber,fontSize=11.sp)
            }
        }
        if(sim!=null){
            val s=sim!!
            item{
                Surface(color=scCard,shape=RoundedCornerShape(14.dp)){
                    Column(Modifier.padding(14.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                            ScoreCell(s.away,play?.awayScore ?: 0)
                            Column(horizontalAlignment=Alignment.CenterHorizontally){
                                Text("Q${play?.quarter ?: 1}  ·  ${showClock(play?.clock ?: 900)}",color=scAmber,fontSize=12.sp,fontWeight=FontWeight.Bold)
                                Text("POSESIÓN: ${play?.offense ?: "—"}",color=scMuted,fontSize=10.sp)
                            }
                            ScoreCell(s.home,play?.homeScore ?: 0)
                        }
                        Spacer(Modifier.height(9.dp))
                        Text("${play?.down ?: 1}° y ${play?.distance ?: 10} · yarda ${play?.fromYard ?: 25} · Drive ${play?.drive ?: 1}",color=scText,fontSize=12.sp)
                    }
                }
            }
            item{
                Surface(color=Color(0xff122d32),shape=RoundedCornerShape(14.dp)){
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1.58f)){
                        drawRoundRect(Color(0xff15483d),cornerRadius=CornerRadius(14f),size=size)
                        val w=size.width;val h=size.height
                        fun pos(x:Float,y:Float)=Offset((.055f+x*.89f)*w,(.1f+y*.8f)*h)
                        for(i in 0..10){val z=i/10f
                            drawLine(Color.White.copy(alpha=if(i%5==0).6f else .22f),pos(z,0f),pos(z,1f),if(i%5==0)2f else 1f)
                        }
                        drawLine(scAmber,pos(((play?.fromYard ?: 25)/100f).coerceIn(0f,1f),0f),pos(((play?.fromYard ?: 25)/100f).coerceIn(0f,1f),1f),3f)
                        val first=((play?.fromYard ?: 25)+(play?.distance ?: 10)).coerceIn(0,100)/100f
                        drawLine(scBlue,pos(first,0f),pos(first,1f),2f)
                        val forward=(play?.yards ?: 0)/100f
                        val x=((play?.fromYard ?: 25)/100f).coerceIn(.13f,.80f)
                        // Each frame represents a game event with a route appropriate to the event type.
                        // Real player identities come from weekly usage; routes remain illustrative.
                        val pass=play?.kind in listOf("PASS","PASS_TD","INCOMPLETE","INT")
                        for(i in 0..10){
                            val y=(i+.5f)/11f
                            val route=if(pass && (i==0||i==10||i==2))(.10f+max(0f,forward)) else max(-.025f,forward*.4f)
                            val ox=(x-.025f+route).coerceIn(.035f,.965f)
                            val dx=(x+.07f+max(-.03f,forward*.45f)).coerceIn(.035f,.965f)
                            drawCircle(scBlue,min(w,h)*.017f,pos(ox,y))
                            drawCircle(Color(0xffffa27c),min(w,h)*.017f,pos(dx,1f-y))
                        }
                        drawCircle(scAmber,min(w,h)*.013f,pos((x+forward).coerceIn(.02f,.98f),.51f))
                    }
                }
            }
            item{
                Text("JUGADA ${index+1}/${s.plays.size} · ${play?.kind ?: "—"}",color=scText,fontWeight=FontWeight.Bold)
                Text(play?.description ?: "—",color=scMuted,fontSize=12.sp)
                LinearProgressIndicator(progress={if(s.plays.size==1)1f else index.toFloat()/max(1,s.plays.lastIndex)},modifier=Modifier.fillMaxWidth(),color=scBlue)
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    OutlinedButton(enabled=index>0,onClick={index--;playing=false},modifier=Modifier.weight(1f)){Text("◀",fontSize=11.sp)}
                    Button(onClick={playing=!playing},modifier=Modifier.weight(1.6f)){
                        Text(if(playing)"PAUSA" else "REPRODUCIR",fontSize=10.sp)
                    }
                    OutlinedButton(enabled=index<s.plays.lastIndex,onClick={index++;playing=false},modifier=Modifier.weight(1f)){Text("▶",fontSize=11.sp)}
                    OutlinedButton(onClick={index=s.plays.lastIndex;playing=false},modifier=Modifier.weight(1.2f)){
                        Text("FINAL",fontSize=10.sp)
                    }
                }
                Text("Velocidad de reproducción ${speed.toInt()}×",color=scMuted,fontSize=11.sp)
                Slider(value=speed,onValueChange={speed=it},valueRange=1f..8f,steps=6)
            }
            item{
                Surface(color=scCard,shape=RoundedCornerShape(12.dp)){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text("MARCADOR POR CUARTOS · SIMULADO",color=scAmber,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                            Text("Equipo",Modifier.weight(1.4f),color=scMuted,fontSize=11.sp)
                            (1..4).forEach{Text("Q$it",Modifier.weight(1f),color=scMuted,fontSize=11.sp)}
                            Text("TOT",Modifier.weight(1f),color=scText,fontSize=11.sp)
                        }
                        QuarterLine(s.away,s.awayQuarters)
                        QuarterLine(s.home,s.homeQuarters)
                        HorizontalDivider(color=Color(0xff304659))
                        listOf(s.away,s.home).forEach{team->
                            val teamPlays=s.plays.filter{it.offense==team}
                            val runs=teamPlays.count{it.kind=="RUN"||it.kind=="RUSH_TD"}
                            val passes=teamPlays.count{it.kind=="PASS"||it.kind=="PASS_TD"||it.kind=="INCOMPLETE"||it.kind=="INT"}
                            val rushYards=s.players.filter{it.team==team}.sumOf{it.rushYards}
                            val passYards=s.players.filter{it.team==team}.sumOf{it.passYards}
                            val drives=teamPlays.map{it.drive}.distinct().size
                            Text("$team · $drives drives · $passes pases · $runs acarreos · ${passYards+max(0,rushYards)} yardas of. (sin sacks)",color=scMuted,fontSize=10.sp)
                        }
                    }
                }
            }
            item{
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    val team=selectedTeam ?: s.away
                    if(team==s.away)Button(onClick={selectedTeam=s.away},modifier=Modifier.weight(1f)){Text("${s.away} · Jugadores")}
                    else OutlinedButton(onClick={selectedTeam=s.away},modifier=Modifier.weight(1f)){Text("${s.away} · Jugadores")}
                    if(team==s.home)Button(onClick={selectedTeam=s.home},modifier=Modifier.weight(1f)){Text("${s.home} · Jugadores")}
                    else OutlinedButton(onClick={selectedTeam=s.home},modifier=Modifier.weight(1f)){Text("${s.home} · Jugadores")}
                }
                val t=selectedTeam ?: s.away
                val rows=s.players.filter{it.team==t}.sortedByDescending{it.passAttempts+it.rushAttempts+it.targets}
                Surface(color=scCard,shape=RoundedCornerShape(11.dp)){
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Text("PLAYER USAGE · $t",color=scAmber,fontWeight=FontWeight.Bold,fontSize=12.sp)
                        Text("Datos de entrada hasta semana ${if(t==s.away)s.awayUsageWeek else s.homeUsageWeek} · ${if(t==s.away)s.awayPlayerCount else s.homePlayerCount} jugadores",color=scMuted,fontSize=10.sp)
                        val weights=s.usageInputs.filter{it.team==t}.sortedByDescending{it.targets+it.carries+it.passAttempts}
                        if(weights.isNotEmpty()){
                            Text("PESOS DE USO (4 SEMANAS ANTERIORES; ponderados)",color=scGreen,fontSize=10.sp,fontWeight=FontWeight.Bold)
                            weights.take(10).forEach{u->
                                Text("${u.position} ${u.name} · Pass ${u.passAttempts.toInt()} / Rush ${u.carries.toInt()} / Tgts ${u.targets.toInt()}",color=scMuted,fontSize=10.sp)
                            }
                            HorizontalDivider(color=Color(0xff304659))
                        }
                        if(rows.isEmpty())Text("Sin participantes",color=scMuted,fontSize=11.sp)
                        Text("ESTADÍSTICAS DE ESTA CORRIDA",color=scGreen,fontSize=10.sp,fontWeight=FontWeight.Bold)
                        rows.forEach{p->
                            Text("${p.position} ${p.name}",color=scText,fontSize=11.sp,fontWeight=FontWeight.Bold)
                            Text("Pase ${p.completions}/${p.passAttempts} · ${p.passYards} yd · TD ${p.passingTds} · INT ${p.interceptions}    |    Acarreos ${p.rushAttempts} · ${p.rushYards} yd · TD ${p.rushingTds}    |    Rec ${p.receptions}/${p.targets} · ${p.receivingYards} yd · TD ${p.receivingTds}",color=scMuted,fontSize=10.sp)
                        }
                    }
                }
            }
            item{
                OutlinedButton(onClick={
                    pendingJson=SimcastArchive.toJson(s).toString(2)
                    export.launch("NFL_Simcast_${s.away}_${s.home}_W${s.week}_${s.seed}.json")
                },modifier=Modifier.fillMaxWidth()){
                    Text("EXPORTAR SIMULACIÓN JSON",fontWeight=FontWeight.Bold,fontSize=12.sp)
                }
                exportStatus?.let{Text(it,color=scGreen,fontSize=11.sp)}
                Text("Se guarda automáticamente un máximo de 3 simulaciones recientes por partido para Data Vault V7. Una sola corrida NO produce una probabilidad O/U.",color=scMuted,fontSize=11.sp)
                if(s.awayUsageWeek==0 || s.homeUsageWeek==0){
                    Text("AVISO: uno o ambos equipos carecen de historial previo de Player Weekly. Los roles faltantes usan jugadores genéricos.",color=scAmber,fontSize=11.sp)
                }
                Text("Los jugadores y pesos provienen de semanas anteriores al partido. Lesiones y titulares NO están confirmados por esta pantalla; las rutas son ilustrativas. No es reproducción del partido real ni un motor físico NFL.",color=scMuted,fontSize=10.sp)
            }
        }
    }
}

@Composable
private fun ScoreCell(team:String,score:Int){
    Column(horizontalAlignment=Alignment.CenterHorizontally){
        Text(team,color=scText,fontSize=15.sp,fontWeight=FontWeight.Black)
        Text("%02d".format(score),color=scText,fontSize=26.sp,fontWeight=FontWeight.Black)
    }
}
@Composable
private fun RowScope.QuarterLine(team:String,points:List<Int>){
    Text(team,Modifier.weight(1.4f),color=scText,fontSize=11.sp)
    points.forEach{Text("$it",Modifier.weight(1f),color=scText,fontSize=11.sp)}
    Text("${points.sum()}",Modifier.weight(1f),color=scAmber,fontSize=11.sp,fontWeight=FontWeight.Bold)
}
private fun showClock(seconds:Int):String="%02d:%02d".format(seconds/60,seconds%60)
