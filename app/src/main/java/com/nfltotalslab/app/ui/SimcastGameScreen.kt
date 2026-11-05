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
import androidx.compose.ui.platform.LocalUriHandler
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
    onSimulate:suspend (GameRecord,Int,GameLineup)->GameSim,
    onBack:()->Unit
){
    val scope=rememberCoroutineScope()
    val currentWeek=games.filter{!it.finished && it.gameType=="REG"}.minOfOrNull{it.week}
        ?: games.maxOfOrNull{it.week} ?: 1
    var week by remember(initialGame?.gameId,games.size){mutableIntStateOf(initialGame?.week ?: currentWeek)}
    var gameId by remember(initialGame?.gameId,games.size){mutableStateOf(initialGame?.gameId ?: games.firstOrNull{it.week==week}?.gameId)}
    val available=games.filter{it.week==week && it.gameType=="REG"}.sortedBy{it.gameDay+" "+it.gameTime}
    val selected=games.firstOrNull{it.gameId==gameId}
    val preset=selected?.let{PregameLineup.forGame(it)} ?: GameLineup()
    var awayQb by remember(selected?.gameId){mutableStateOf(preset.away.startingQb)}
    var homeQb by remember(selected?.gameId){mutableStateOf(preset.home.startingQb)}
    var awayOut by remember(selected?.gameId){mutableStateOf(preset.away.outNames.joinToString(", "))}
    var homeOut by remember(selected?.gameId){mutableStateOf(preset.home.outNames.joinToString(", "))}
    val browser=LocalUriHandler.current
    var sim by remember{mutableStateOf<GameSim?>(null)}
    var index by remember{mutableIntStateOf(0)}
    var playing by remember{mutableStateOf(false)}
    var loading by remember{mutableStateOf(false)}
    var speed by remember{mutableFloatStateOf(2f)}
    var phase by remember{mutableFloatStateOf(0f)}
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
    LaunchedEffect(selected?.gameId){sim=null;index=0;phase=0f;playing=false;selectedTeam=null;error=null}
    // Animate individual events at ~30 FPS, rather than jumping between static formations.
    LaunchedEffect(sim,playing,speed,index){
        val s=sim
        if(playing && s!=null && s.plays.isNotEmpty()){
            while(phase<1f){
                delay(33)
                phase=(phase+0.018f*speed).coerceAtMost(1f)
            }
            if(index>=s.plays.lastIndex) playing=false
            else {phase=0f;index++}
        }
    }
    fun splitOut(raw:String)=raw.split(",",";").map{it.trim()}.filter{it.isNotBlank()}.toSet()
    fun chosenLineup():GameLineup {
        fun team(qb:String,excluded:String,base:TeamLineup):TeamLineup {
            val absent=splitOut(excluded)
            val same=qb.trim().equals(base.startingQb,ignoreCase=true) &&
                    absent.map{it.lowercase()}.toSet()==base.outNames.map{it.lowercase()}.toSet()
            return TeamLineup(qb.trim(),absent,
                if(same)base.source else "Modificación manual · requiere verificación",same && base.verified)
        }
        return GameLineup(team(awayQb,awayOut,preset.away),team(homeQb,homeOut,preset.home))
    }
    fun runScenario(){
        val g=selected ?: return
        scope.launch{
            playing=false;loading=true;error=null
            val seed=g.gameId.hashCode() xor (seedCounter*104729)
            seedCounter++
            runCatching{onSimulate(g,seed,chosenLineup())}
                .onSuccess{sim=it;index=0;phase=0f;selectedTeam=it.away;playing=true}
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
            Surface(color=scCard,shape=RoundedCornerShape(14.dp)){
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                    Text("TITULARES Y LESIONES · PREPARTIDO",color=scAmber,fontSize=12.sp,fontWeight=FontWeight.Black)
                    Text("Confirma el QB1 y elimina solo jugadores OUT/IR. QUESTIONABLE se mantiene hasta conocer los inactivos.",color=scMuted,fontSize=10.sp)
                    if(selected!=null && preset.home.verified){
                        Text("✓ BAL · Joe Fagnano confirmado por el equipo · 05/11/2026",color=scGreen,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        Text("OUT/IR: Lamar Jackson, Tyler Huntley. Fagnano completó prácticas con molestias de tobillo. JAX: Bhayshul Tuten era QUESTIONABLE; no se excluye sin confirmar inactivos.",color=scMuted,fontSize=10.sp)
                    }else {
                        Text("Sin confirmación oficial integrada para este encuentro. Revisa el reporte antes de simular.",color=scAmber,fontSize=10.sp)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(value=awayQb,onValueChange={awayQb=it},label={Text("QB1 ${selected?.awayTeam ?: "VIS"}")},
                            placeholder={Text("Auto por uso")},singleLine=true,modifier=Modifier.weight(1f))
                        OutlinedTextField(value=homeQb,onValueChange={homeQb=it},label={Text("QB1 ${selected?.homeTeam ?: "LOC"}")},
                            placeholder={Text("Auto por uso")},singleLine=true,modifier=Modifier.weight(1f))
                    }
                    OutlinedTextField(value=awayOut,onValueChange={awayOut=it},label={Text("OUT/IR ${selected?.awayTeam ?: "VIS"} · nombres separados por coma")},
                        modifier=Modifier.fillMaxWidth(),maxLines=2)
                    OutlinedTextField(value=homeOut,onValueChange={homeOut=it},label={Text("OUT/IR ${selected?.homeTeam ?: "LOC"} · nombres separados por coma")},
                        modifier=Modifier.fillMaxWidth(),maxLines=2)
                    TextButton(onClick={browser.openUri("https://www.nfl.com/injuries/")}){
                        Text("CONSULTAR INFORME OFICIAL NFL",color=scBlue,fontSize=11.sp)
                    }
                    Text("La app no valida automáticamente los nombres escritos. El JSON registrará titular, ausentes y procedencia de la revisión.",color=scMuted,fontSize=10.sp)
                }
            }
        }
        item{
            Button(
                modifier=Modifier.fillMaxWidth(),enabled=selected!=null&&!loading,
                onClick={runScenario()},colors=ButtonDefaults.buttonColors(containerColor=scGreen)
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
                        val w=size.width;val h=size.height
                        fun pos(x:Float,y:Float)=Offset((.045f+x*.91f)*w,(.09f+y*.82f)*h)
                        drawRoundRect(Color(0xff164439),cornerRadius=CornerRadius(14f),size=size)
                        // Bands and hashes make the down marker read like a real field.
                        for(i in 0..19){
                            val x=i/20f
                            if(i%2==0) drawRect(Color.White.copy(alpha=.025f),
                                topLeft=pos(x,0f),size=androidx.compose.ui.geometry.Size(w*.91f/20f,h*.82f))
                        }
                        for(i in 0..10){
                            val x=i/10f
                            drawLine(Color.White.copy(alpha=if(i%5==0).6f else .26f),pos(x,0f),pos(x,1f),if(i%5==0)2.2f else 1.2f)
                        }
                        for(i in 0..19){
                            val x=i/20f
                            drawLine(Color.White.copy(alpha=.30f),pos(x,.36f),pos(x,.40f),1.5f)
                            drawLine(Color.White.copy(alpha=.30f),pos(x,.60f),pos(x,.64f),1.5f)
                        }
                        drawLine(Color.White.copy(alpha=.7f),pos(0f,0f),pos(1f,0f),2f)
                        drawLine(Color.White.copy(alpha=.7f),pos(0f,1f),pos(1f,1f),2f)
                        val event=play
                        val t=phase.coerceIn(0f,1f)
                        val line=(event?.fromYard ?: 25)/100f
                        val x=line.coerceIn(.18f,.72f)
                        val gain=((event?.yards ?: 0)/100f).coerceIn(-.22f,.45f)
                        val goal=((event?.fromYard ?: 25)+(event?.distance ?: 10))/100f
                        drawLine(scAmber,pos(x,0f),pos(x,1f),3f)
                        drawLine(scBlue,pos(goal.coerceIn(.01f,.99f),0f),pos(goal.coerceIn(.01f,.99f),1f),2.5f)
                        val isPass=event?.kind in listOf("PASS","PASS_TD","INCOMPLETE","INT")
                        val isRun=event?.kind in listOf("RUN","RUSH_TD","FUMBLE")
                        val isSack=event?.kind=="SACK"
                        val kicking=event?.kind in listOf("PUNT","FG","FG_FALLADO")
                        val offenseColor=if(event?.offense==s.away) scBlue else Color(0xff9b8aff)
                        val defenseColor=if(event?.offense==s.away) Color(0xffffa27c) else scBlue
                        val radius=min(w,h)*.018f
                        fun agent(cx:Float,cy:Float,color:Color,holder:Boolean=false){
                            val c=pos(cx.coerceIn(.035f,.965f),cy.coerceIn(.035f,.965f))
                            drawCircle(Color.Black.copy(alpha=.2f),radius*1.35f,center=c+Offset(1.5f,2f))
                            drawCircle(color,radius,center=c)
                            drawCircle(Color.White.copy(alpha=.65f),radius*.30f,center=c+Offset(-radius*.19f,-radius*.20f))
                            if(holder)drawCircle(scAmber,radius*.42f,center=c+Offset(radius*.5f,0f))
                        }
                        // Offense: QB, five OL, RB, three WR, TE.
                        val oY=listOf(.50f,.27f,.38f,.50f,.62f,.73f,.67f,.09f,.89f,.23f,.80f)
                        for(i in 0..10){
                            var px=x+if(i==0)-.08f else if(i==6)-.14f else if(i in 1..5)-.014f else -.045f
                            var py=oY[i]
                            val targetDepth=gain.coerceAtLeast(.08f).coerceAtMost(.35f)
                            if(isPass){
                                when(i){
                                    0->px-=.030f*t
                                    7,8,9,10->{
                                        px+=(targetDepth+if(i==7).045f else .0f)*t
                                        py+=(if(i==7).12f else if(i==8)-.11f else if(i==9).055f else -.04f)*t
                                    }
                                    else->px+=.025f*t
                                }
                            }else if(isRun){
                                if(i==6)px+=gain*t+ .04f*t
                                else if(i in 1..5)px+=.052f*t
                                else if(i==0)px-=.02f*t
                                else px+=.035f*t
                            }else if(isSack){
                                if(i==0)px-=.035f*t
                                else if(i in 1..5)px-=.008f*t
                            }else if(kicking){
                                if(i==0)px+=.02f*t
                                else px+=.009f*t
                            }
                            agent(px,py,offenseColor,isRun&&i==6||isPass&&i==0&&t<.42f)
                            if(isPass && i in 7..10){
                                drawLine(offenseColor.copy(alpha=.21f),pos(x-.045f,oY[i]),pos(px,py),1.6f)
                            }
                        }
                        // Defense: uneven 4-3-inspired front, linebackers and secondary with pursuit.
                        val dY=listOf(.30f,.42f,.58f,.70f,.23f,.50f,.77f,.10f,.90f,.36f,.64f)
                        for(i in 0..10){
                            var dx=x+if(i<4).055f else if(i<7).125f else .22f
                            var dy=dY[i]
                            if(isRun){
                                dx+=(-.012f+gain*.42f)*t
                                dy+=(.67f-dY[i])*.13f*t
                            }else if(isPass){
                                dx+=if(i<4)-.06f*t else if(i<7)-.015f*t else gain*.3f*t
                                dy+=(if(i%2==0)-.04f else .04f)*t
                            }else if(isSack){
                                if(i<4)dx-=.09f*t
                            }
                            agent(dx,dy,defenseColor)
                        }
                        // Ball travels after a passing release; running ball follows the carrier.
                        val bx=when{
                            isPass && t>.40f->(x-.11f)+(gain.coerceAtLeast(.10f)+.14f)*((t-.40f)/.60f)
                            isPass->x-.11f
                            isRun->x-.14f+gain*t+.04f*t
                            kicking->x-.08f+.47f*t
                            else->x-.10f
                        }
                        val ballY=if(isPass && t>.40f) .50f+(.23f-.50f)*((t-.40f)/.60f) else if(isRun).67f else .50f
                        drawCircle(scAmber,radius*.57f,pos(bx.coerceIn(.02f,.98f),ballY),
                            style=androidx.compose.ui.graphics.drawscope.Fill)
                    }
                }
            }
            item{
                Text("JUGADA ${index+1}/${s.plays.size} · ${play?.kind ?: "—"}",color=scText,fontWeight=FontWeight.Bold)
                Text(play?.description ?: "—",color=scMuted,fontSize=12.sp)
                LinearProgressIndicator(progress={(index+phase).coerceAtMost(s.plays.size.toFloat())/max(1,s.plays.size)},modifier=Modifier.fillMaxWidth(),color=scBlue)
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){
                    OutlinedButton(enabled=index>0,onClick={index--;phase=0f;playing=false},modifier=Modifier.weight(1f)){Text("◀",fontSize=11.sp)}
                    Button(onClick={playing=!playing},modifier=Modifier.weight(1.6f)){
                        Text(if(playing)"PAUSA" else "REPRODUCIR",fontSize=10.sp)
                    }
                    OutlinedButton(enabled=index<s.plays.lastIndex,onClick={index++;phase=0f;playing=false},modifier=Modifier.weight(1f)){Text("▶",fontSize=11.sp)}
                    OutlinedButton(onClick={index=s.plays.lastIndex;phase=1f;playing=false},modifier=Modifier.weight(1.2f)){
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
                        Row(Modifier.fillMaxWidth()) { QuarterLine(s.away,s.awayQuarters) }
                        Row(Modifier.fillMaxWidth()) { QuarterLine(s.home,s.homeQuarters) }
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
                Text("Alineaciones: ${s.away} = ${s.lineup.away.startingQb.ifBlank { "QB por historial (no confirmado)" }}; ${s.home} = ${s.lineup.home.startingQb.ifBlank { "QB por historial (no confirmado)" }}.",color=scMuted,fontSize=10.sp)
                Text("Se guardan hasta 3 simulaciones recientes por partido para Data Vault V7. Una sola corrida NO produce probabilidad O/U.",color=scMuted,fontSize=11.sp)
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
