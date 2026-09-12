
package com.nfltotalslab.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfltotalslab.app.branding.TeamBadge
import com.nfltotalslab.app.data.BankEntry
import com.nfltotalslab.app.data.BetRecord
import com.nfltotalslab.app.data.Prediction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val PBg=Color(0xFF071321)
private val PCard=Color(0xFF0C1D2C)
private val PPanel=Color(0xFF091827)
private val PBorder=Color(0xFF173B54)
private val PText=Color(0xFFF3F7FB)
private val PMuted=Color(0xFF8EA1B6)
private val PGreen=Color(0xFF39D4A3)
private val PRed=Color(0xFFF06A80)
private val PAmber=Color(0xFFF2BF63)
private val PBlue=Color(0xFF63B5F6)

@Composable
fun BetsPortfolioScreen(
    bets:List<BetRecord>,
    preds:List<Prediction>,
    bank:List<BankEntry>,
    onBack:()->Unit
){
    val predMap=preds.associateBy{it.id}
    val settled=bets.filter{it.status!="PENDING"}
    val pending=bets.filter{it.status=="PENDING"}
    val bankBase=bank.sumOf{it.amount}.coerceAtLeast(0.0)
    val pendingExposure=pending.sumOf{it.stake}
    val wins=settled.count{it.status=="WIN"}
    val losses=settled.count{it.status=="LOSS"}
    val pushes=settled.count{it.status=="PUSH"}
    val pnl=settled.sumOf{it.pnl}
    val stake=settled.sumOf{it.stake}
    val roi=if(stake>0)pnl/stake*100.0 else null
    val unit=standardUnit(bets)

    Column(Modifier.fillMaxSize().background(PBg)){
        PortfolioHeader("APUESTAS","Libro de tickets · riesgo · disciplina · auditoría.",onBack)
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal=12.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)
        ){
            item{
                PortfolioSection("Resumen del portfolio"){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        TinyMetric("P&L",moneySigned(pnl),Modifier.weight(1f),if(pnl>=0)PGreen else PRed)
                        TinyMetric("ROI",roi?.let{"${it.f1()}%"} ?: "—",Modifier.weight(1f),if((roi?:0.0)>=0)PGreen else PRed)
                        TinyMetric("RÉCORD","$wins-$losses-$pushes",Modifier.weight(1f))
                        TinyMetric("PEND.","${pending.size}",Modifier.weight(1f),PAmber)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        TinyMetric("STAKE LIQ.","$${stake.f2()}",Modifier.weight(1f))
                        TinyMetric("COMPROM.","$${pendingExposure.f2()}",Modifier.weight(1f),PAmber)
                        TinyMetric("1 U","$${unit.f2()}",Modifier.weight(1f),PBlue)
                    }
                }
            }

            item{
                PortfolioSection("Disciplina del modelo"){
                    DisciplineBox("SIGUE MODELO",disciplineStats("MODEL",bets,predMap),PGreen,"Apuesta alineada con la selección principal.")
                    Spacer(Modifier.height(7.dp))
                    DisciplineBox("APUESTA LEAN",disciplineStats("LEAN",bets,predMap),PBlue,"Se apostó una señal secundaria.")
                    Spacer(Modifier.height(7.dp))
                    DisciplineBox("APUESTA PASS",disciplineStats("PASS",bets,predMap),PAmber,"Se apostó pese al filtro PASS.")
                    Spacer(Modifier.height(7.dp))
                    DisciplineBox("CONTRA MODELO",disciplineStats("AGAINST",bets,predMap),PRed,"La apuesta difirió de la selección principal.")
                    val snap=bets.count{predMap[it.predictionId]!=null}
                    Text("Con snapshot: $snap · Sin snapshot: ${bets.size-snap}",color=PMuted,fontSize=8.sp,modifier=Modifier.padding(top=7.dp))
                }
            }

            item{
                PortfolioSection("Libro de apuestas"){
                    LedgerHeader()
                    bets.sortedByDescending{it.createdAt}.forEachIndexed{idx,b->
                        if(idx>0)HorizontalDivider(color=PBorder)
                        BetLedgerRow(b,predMap[b.predictionId],bankBase,pendingExposure,unit)
                    }
                    if(bets.isEmpty())Text("No hay tickets registrados.",color=PMuted,fontSize=9.sp,modifier=Modifier.padding(12.dp))
                }
            }
            item{Spacer(Modifier.height(8.dp))}
        }
    }
}

private data class DStats(
    val total:Int,val pending:Int,val wins:Int,val losses:Int,val pushes:Int,
    val pnl:Double,val roi:Double?,val brier:Double?
)

private fun disciplineStats(bucket:String,bets:List<BetRecord>,predMap:Map<Long,Prediction>):DStats{
    fun cat(b:BetRecord):String{
        val p=predMap[b.predictionId] ?: return "NONE"
        val dir=b.market.substringBefore(" ").uppercase(Locale.US)
        if(dir!=p.pick.uppercase(Locale.US))return "AGAINST"
        return when{
            p.classification.startsWith("LEAN")->"LEAN"
            p.classification.startsWith("PASS")->"PASS"
            else->"MODEL"
        }
    }
    val rows=bets.filter{cat(it)==bucket}
    val settled=rows.filter{it.status!="PENDING"}
    val stake=settled.sumOf{it.stake}
    val pnl=settled.sumOf{it.pnl}
    val bs=settled.mapNotNull{b->
        val p=predMap[b.predictionId] ?: return@mapNotNull null
        val y=when(b.status){"WIN"->1.0;"LOSS"->0.0;else->return@mapNotNull null}
        val e=p.probability-y;e*e
    }
    return DStats(
        rows.size,rows.count{it.status=="PENDING"},rows.count{it.status=="WIN"},
        rows.count{it.status=="LOSS"},rows.count{it.status=="PUSH"},pnl,
        if(stake>0)pnl/stake*100.0 else null,
        if(bs.isNotEmpty())bs.average() else null
    )
}

@Composable
private fun DisciplineBox(title:String,s:DStats,accent:Color,description:String){
    Surface(color=PPanel,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,PBorder)){
        Column(Modifier.fillMaxWidth().padding(11.dp)){
            Text(title,color=PMuted,fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=.7.sp)
            Text("${s.wins}-${s.losses}-${s.pushes}",fontSize=23.sp,fontWeight=FontWeight.Black,color=PText)
            Text("${s.total} registradas · ${s.pending} pendientes",color=PMuted,fontSize=8.sp)
            Text("P&L: ${moneySigned(s.pnl)}",color=if(s.pnl>=0)PGreen else PRed,fontSize=9.sp,fontWeight=FontWeight.Bold)
            Text("ROI: ${s.roi?.let{"${it.f1()}%"} ?: "—"}",color=PMuted,fontSize=9.sp)
            Text("Brier: ${s.brier?.f3() ?: "—"}",color=PMuted,fontSize=9.sp)
            Text(description,color=PMuted,fontSize=8.sp,modifier=Modifier.padding(top=5.dp))
        }
    }
}

@Composable
private fun LedgerHeader(){
    Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=8.dp)){
        H("JUEGO",2.25f);H("PICK",.9f);H("MOMIO",.8f);H("STAKE",.8f);H("U",.45f);H("RIESGO",.65f)
    }
    HorizontalDivider(color=PBorder)
}
@Composable private fun RowScope.H(t:String,w:Float){Text(t,Modifier.weight(w),color=PMuted,fontSize=7.sp,fontWeight=FontWeight.Black)}

@Composable
private fun BetLedgerRow(b:BetRecord,p:Prediction?,bankBase:Double,pendingExposure:Double,unit:Double){
    val risk=if(bankBase>0)b.stake/bankBase*100.0 else 0.0
    val exp=if(bankBase>0)pendingExposure/bankBase*100.0 else 0.0
    val level=when{risk<=3->"CONTROLADA";risk<=5->"MODERADA";risk<=10->"ALTA";else->"EXCESIVA"}
    val lc=when(level){"CONTROLADA"->PGreen;"MODERADA"->PAmber;else->PRed}
    val br=if(p!=null)when(b.status){
        "WIN"->{val e=p.probability-1.0;e*e}
        "LOSS"->{val e=p.probability;e*e}
        else->null
    }else null

    Column(Modifier.fillMaxWidth().padding(vertical=9.dp,horizontal=7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(2.25f)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    if(p!=null){TeamBadge(p.awayTeam,18.dp);Spacer(Modifier.width(2.dp));TeamBadge(p.homeTeam,18.dp);Spacer(Modifier.width(5.dp))}
                    Text(p?.let{"${it.awayTeam} @ ${it.homeTeam}"} ?: b.gameId,color=PText,fontSize=9.sp,fontWeight=FontWeight.Bold,maxLines=2)
                }
                Text(p?.let{"W${it.week} · ${dateShort(b.createdAt)}"} ?: dateShort(b.createdAt),color=PMuted,fontSize=7.sp)
            }
            Column(Modifier.weight(.9f)){Text(b.market.substringBeforeLast(" "),fontSize=9.sp,color=PText);Text(b.market.substringAfterLast(" "),fontSize=9.sp,fontWeight=FontWeight.Black,color=PText)}
            Column(Modifier.weight(.8f)){Text(b.odds.f2(),fontSize=9.sp,color=PText);Text(americanOdds(b.odds),color=PMuted,fontSize=7.sp)}
            Text("$${b.stake.f2()}",Modifier.weight(.8f),fontSize=9.sp,color=PText)
            Text("${(b.stake/unit).f2()}",Modifier.weight(.45f),fontSize=9.sp,color=PText)
            Text("${risk.f1()}%",Modifier.weight(.65f),fontSize=9.sp,color=PText)
        }
        Spacer(Modifier.height(7.dp))
        Surface(color=PBg.copy(alpha=.45f),shape=RoundedCornerShape(9.dp),border=androidx.compose.foundation.BorderStroke(1.dp,PBorder)){
            Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically){
                AuditMini("EXP.",if(b.status=="PENDING")"${exp.f1()}%" else "${risk.f1()}%",Modifier.weight(.75f))
                AuditMini("NIVEL",level,Modifier.weight(1.2f),lc)
                AuditMini("PROB.",p?.let{"${(it.probability*100).f1()}%"} ?: "—",Modifier.weight(.8f))
                AuditMini("BRIER",br?.f3() ?: "—",Modifier.weight(.75f))
                AuditMini("CLV","—",Modifier.weight(.6f))
                AuditMini("ESTADO",if(b.status=="PENDING")"PENDING" else b.status,Modifier.weight(.85f))
                AuditMini("UTIL.",if(b.status=="PENDING")"—" else signedCompact(b.pnl),Modifier.weight(.75f),if(b.pnl>=0)PGreen else PRed)
            }
        }
    }
}

@Composable
private fun RowScope.AuditMini(label:String,value:String,modifier:Modifier,color:Color=PText){
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally){
        Text(label,color=PMuted,fontSize=6.sp,fontWeight=FontWeight.Black)
        Text(value,color=color,fontSize=8.sp,fontWeight=FontWeight.Bold,maxLines=1)
    }
}

@Composable
fun BankPortfolioScreen(
    entries:List<BankEntry>,
    bets:List<BetRecord>,
    onAdd:(Double,String)->Unit,
    onBack:()->Unit
){
    var amount by remember{mutableStateOf("")}
    var note by remember{mutableStateOf("")}

    val sortedEntries=entries.sortedBy{it.createdAt}
    val initial=sortedEntries.firstOrNull{it.amount>0}?.amount ?: 0.0
    val capital=entries.sumOf{it.amount}
    val settled=bets.filter{it.status!="PENDING"}
    val pending=bets.filter{it.status=="PENDING"}
    val pnl=settled.sumOf{it.pnl}
    val current=capital+pnl
    val committed=pending.sumOf{it.stake}
    val available=current-committed
    val exposure=if(current>0)committed/current*100.0 else 0.0
    val stake=settled.sumOf{it.stake}
    val roi=if(stake>0)pnl/stake*100.0 else null
    val wins=settled.count{it.status=="WIN"};val losses=settled.count{it.status=="LOSS"};val pushes=settled.count{it.status=="PUSH"}
    val now=System.currentTimeMillis()
    val last7=bets.filter{it.status!="PENDING" && now-it.createdAt<=7L*24*60*60*1000}.sumOf{it.pnl}
    val month=SimpleDateFormat("yyyy-MM",Locale.US).format(Date(now))
    val monthPnl=bets.filter{it.status!="PENDING" && SimpleDateFormat("yyyy-MM",Locale.US).format(Date(it.createdAt))==month}.sumOf{it.pnl}

    Column(Modifier.fillMaxSize().background(PBg)){
        PortfolioHeader("BANK","Bankroll · exposición · movimientos · rendimiento.",onBack)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
            item{
                PortfolioSection("Estado del bankroll"){
                    BankKpi("BANK INICIAL","$${initial.f2()}")
                    BankKpi("CAPITAL REGISTRADO","$${capital.f2()}","depósitos y retiros manuales")
                    BankKpi("SALDO ACTUAL","$${current.f2()}","capital + P&L liquidado",PGreen)
                    BankKpi("DISPONIBLE","$${available.f2()}","después de apuestas pendientes")
                    BankKpi("COMPROMETIDO","$${committed.f2()}","${pending.size} pendientes · ${exposure.f1()}%",PAmber)
                    BankKpi("EXPOSICIÓN",exposureLabel(exposure),"${exposure.f1()}% del saldo",exposureColor(exposure))
                    BankKpi("P&L APUESTAS",moneySigned(pnl),unitsText(pnl,standardUnit(bets)),if(pnl>=0)PGreen else PRed)
                    BankKpi("ROI",roi?.let{"${it.f1()}%"} ?: "—","Stake liquidado $${stake.f2()}")
                    BankKpi("ÚLTIMOS 7 DÍAS",moneySigned(last7),"por fecha de ticket",if(last7>=0)PGreen else PRed)
                    BankKpi("MES ACTUAL",moneySigned(monthPnl),"por fecha de ticket",if(monthPnl>=0)PGreen else PRed)
                    BankKpi("RÉCORD","$wins-$losses-$pushes","W-L-P")
                }
            }
            item{
                PortfolioSection("Curva del bankroll"){
                    BankrollCurve(entries,bets)
                    Text("Reconstrucción por timestamp de movimientos y tickets liquidados.",color=PMuted,fontSize=8.sp,modifier=Modifier.padding(top=7.dp))
                }
            }
            item{
                PortfolioSection("Movimientos de capital"){
                    Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=7.dp)){H("FECHA",1.1f);H("TIPO",1f);H("IMPORTE",1.1f);H("NOTA",1.6f)}
                    HorizontalDivider(color=PBorder)
                    entries.sortedByDescending{it.createdAt}.forEachIndexed{idx,e->
                        if(idx>0)HorizontalDivider(color=PBorder)
                        val pos=e.amount>=0
                        Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(dateOnly(e.createdAt),Modifier.weight(1.1f),fontSize=9.sp,color=PText)
                            Text(if(pos)"DEPÓSITO" else "RETIRO",Modifier.weight(1f),fontSize=8.sp,color=if(pos)PGreen else PRed)
                            Text(moneySigned(e.amount),Modifier.weight(1.1f),fontSize=9.sp,fontWeight=FontWeight.Bold,color=PText)
                            Text(e.note.ifBlank{"—"},Modifier.weight(1.6f),fontSize=8.sp,color=PMuted,maxLines=2)
                        }
                    }
                }
            }
            item{
                PortfolioSection("Nuevo movimiento"){
                    OutlinedTextField(value=amount,onValueChange={amount=it},label={Text("Importe + / -")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value=note,onValueChange={note=it},label={Text("Nota")},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    Button(onClick={
                        val v=amount.toDoubleOrNull()
                        if(v!=null && v!=0.0){onAdd(v,note.ifBlank{"Ajuste manual"});amount="";note=""}
                    },modifier=Modifier.fillMaxWidth().padding(top=7.dp),colors=ButtonDefaults.buttonColors(containerColor=PGreen)){
                        Text("GUARDAR MOVIMIENTO",fontSize=9.sp,fontWeight=FontWeight.Black)
                    }
                }
            }
            item{
                PortfolioSection("Rendimiento diario · apuestas"){
                    Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=7.dp)){H("FECHA",1.2f);H("RÉCORD",1f);H("STAKE",1.2f);H("P&L",1f);H("ROI",.8f)}
                    HorizontalDivider(color=PBorder)
                    dailyRows(bets,standardUnit(bets)).forEachIndexed{idx,r->
                        if(idx>0)HorizontalDivider(color=PBorder)
                        Row(Modifier.fillMaxWidth().padding(horizontal=7.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){
                            Text(r.day,Modifier.weight(1.2f),fontSize=9.sp,color=PText)
                            Text("${r.wins}-${r.losses}-${r.pushes}",Modifier.weight(1f),fontSize=9.sp,color=PText,textAlign=TextAlign.Center)
                            Column(Modifier.weight(1.2f),horizontalAlignment=Alignment.End){Text("$${r.stake.f2()}",fontSize=9.sp,color=PText);Text("${r.units.f2()} U",fontSize=7.sp,color=PMuted)}
                            Text(moneySigned(r.pnl),Modifier.weight(1f),fontSize=9.sp,color=if(r.pnl>=0)PGreen else PRed,textAlign=TextAlign.End)
                            Text("${r.roi.f1()}%",Modifier.weight(.8f),fontSize=9.sp,color=PText,textAlign=TextAlign.End)
                        }
                    }
                }
            }
            item{Spacer(Modifier.height(8.dp))}
        }
    }
}

@Composable
private fun PortfolioHeader(title:String,subtitle:String,onBack:()->Unit){
    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
        OutlinedButton(onClick=onBack,contentPadding=PaddingValues(horizontal=12.dp,vertical=6.dp)){Text("←",fontSize=18.sp)}
        Spacer(Modifier.width(10.dp))
        Column{Text(title,color=PText,fontWeight=FontWeight.Black,fontSize=19.sp);Text(subtitle,color=PMuted,fontSize=9.sp)}
    }
}

@Composable
private fun PortfolioSection(title:String,content:@Composable ColumnScope.()->Unit){
    Surface(color=PCard,shape=RoundedCornerShape(17.dp),border=androidx.compose.foundation.BorderStroke(1.dp,PBorder)){
        Column(Modifier.fillMaxWidth().padding(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.width(3.dp).height(20.dp).background(PBlue,RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(7.dp));Text(title,color=PText,fontWeight=FontWeight.Black,fontSize=15.sp)
            }
            Spacer(Modifier.height(10.dp));content()
        }
    }
}

@Composable
private fun TinyMetric(label:String,value:String,modifier:Modifier=Modifier,color:Color=PText){
    Surface(modifier=modifier,color=PPanel,shape=RoundedCornerShape(10.dp),border=androidx.compose.foundation.BorderStroke(1.dp,PBorder)){
        Column(Modifier.padding(vertical=8.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text(label,color=PMuted,fontSize=6.sp,fontWeight=FontWeight.Black,maxLines=1);Text(value,color=color,fontSize=10.sp,fontWeight=FontWeight.Black,maxLines=1)
        }
    }
}

@Composable
private fun BankKpi(label:String,value:String,subtitle:String?=null,valueColor:Color=PText){
    Surface(color=PPanel,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,PBorder),modifier=Modifier.fillMaxWidth().padding(bottom=7.dp)){
        Column(Modifier.padding(horizontal=12.dp,vertical=10.dp)){
            Text(label,color=PMuted,fontSize=8.sp,fontWeight=FontWeight.Black,letterSpacing=.5.sp)
            Text(value,color=valueColor,fontSize=20.sp,fontWeight=FontWeight.Black)
            subtitle?.let{Text(it,color=PMuted,fontSize=8.sp)}
        }
    }
}

@Composable
private fun BankrollCurve(entries:List<BankEntry>,bets:List<BetRecord>){
    val events=mutableListOf<Pair<Long,Double>>()
    entries.forEach{events+=it.createdAt to it.amount}
    bets.filter{it.status!="PENDING"}.forEach{events+=it.createdAt to it.pnl}
    val sorted=events.sortedBy{it.first}
    var running=0.0
    val values=mutableListOf<Double>()
    if(sorted.isEmpty())values+=0.0
    sorted.forEach{(_,d)->running+=d;values+=running}

    Surface(color=PPanel,shape=RoundedCornerShape(13.dp),border=androidx.compose.foundation.BorderStroke(1.dp,PBorder)){
        Canvas(Modifier.fillMaxWidth().height(145.dp).padding(10.dp)){
            val mn=values.minOrNull() ?: 0.0;val mx=values.maxOrNull() ?: 1.0;val rg=(mx-mn).takeIf{it>0.0001} ?: 1.0
            val left=8f;val right=size.width-8f;val top=8f;val bottom=size.height-8f
            drawLine(PBorder,Offset(left,bottom),Offset(right,bottom),strokeWidth=1.5f);drawLine(PBorder,Offset(left,top),Offset(left,bottom),strokeWidth=1.5f)
            val pts=values.mapIndexed{i,v->Offset(if(values.size<=1)left else left+(right-left)*i/(values.size-1f),bottom-(bottom-top)*((v-mn)/rg).toFloat())}
            for(i in 0 until pts.size-1)drawLine(PBlue,pts[i],pts[i+1],strokeWidth=4f)
            pts.lastOrNull()?.let{drawCircle(PGreen,6f,it)}
        }
    }
}

private data class DayPerf(val day:String,val wins:Int,val losses:Int,val pushes:Int,val stake:Double,val units:Double,val pnl:Double,val roi:Double)
private fun dailyRows(bets:List<BetRecord>,unit:Double):List<DayPerf>{
    val f=SimpleDateFormat("yyyy-MM-dd",Locale.US)
    return bets.filter{it.status!="PENDING"}.groupBy{f.format(Date(it.createdAt))}.map{(d,rs)->
        val st=rs.sumOf{it.stake};val pn=rs.sumOf{it.pnl}
        DayPerf(d,rs.count{it.status=="WIN"},rs.count{it.status=="LOSS"},rs.count{it.status=="PUSH"},st,st/unit,pn,if(st>0)pn/st*100.0 else 0.0)
    }.sortedByDescending{it.day}
}

private fun standardUnit(bets:List<BetRecord>):Double{
    val s=bets.map{it.stake}.filter{it>0}.sorted()
    return if(s.isEmpty())100.0 else s[s.size/2].coerceAtLeast(1.0)
}
private fun americanOdds(d:Double):String{if(d<=1)return "—";val n=if(d>=2)((d-1)*100).toInt() else (-100/(d-1)).toInt();return if(n>0)"+$n" else n.toString()}
private fun moneySigned(v:Double)="${if(v>=0) "+" else "-"}$${abs(v).f2()}"
private fun signedCompact(v:Double)="${if(v>=0) "+" else "-"}${abs(v).f2()}"
private fun unitsText(v:Double,u:Double)="${if(v>=0) "+" else "-"}${(abs(v)/u).f2()} U"
private fun exposureLabel(p:Double)=when{p<=3->"CONTROLADA";p<=5->"MODERADA";p<=10->"ALTA";else->"EXCESIVA"}
private fun exposureColor(p:Double)=when{p<=3->PGreen;p<=5->PAmber;else->PRed}
private fun dateShort(ms:Long)=SimpleDateFormat("MM-dd",Locale.US).format(Date(ms))
private fun dateOnly(ms:Long)=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(ms))
private fun Double.f1()=String.format(Locale.US,"%.1f",this)
private fun Double.f2()=String.format(Locale.US,"%.2f",this)
private fun Double.f3()=String.format(Locale.US,"%.3f",this)
