package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.*
import kotlin.math.*
import kotlin.random.Random

class TotalsEngine(private val simulations:Int=100_000) {
    private val rng=Random.Default

    fun predict(game:GameRecord, awayRaw:TeamMetrics?, homeRaw:TeamMetrics?):Prediction {
        val away=shrink(awayRaw ?: TeamMetrics(game.season,game.awayTeam), game.awayTeam)
        val home=shrink(homeRaw ?: TeamMetrics(game.season,game.homeTeam), game.homeTeam)
        val line=game.totalLine ?: 44.5

        val drivesA=((away.drivesPerGame+home.drivesPerGame)/2.0).coerceIn(8.2,13.5)
        val drivesH=((home.drivesPerGame+away.drivesPerGame)/2.0).coerceIn(8.2,13.5)

        val epaA=(away.offEpaPerPlay*0.58 + home.defEpaAllowedPerPlay*0.42)
        val epaH=(home.offEpaPerPlay*0.58 + away.defEpaAllowedPerPlay*0.42 + 0.015)

        val ppdA=(2.02 + 3.8*epaA + 1.1*(away.successRate-.43) + .55*(away.explosiveRate-.10)).coerceIn(1.1,3.3)
        val ppdH=(2.08 + 3.8*epaH + 1.1*(home.successRate-.43) + .55*(home.explosiveRate-.10)).coerceIn(1.1,3.4)

        val markov=markov(line,drivesA,drivesH,away,home,epaA,epaH)
        val negbin=negativeBinomial(line,drivesA*ppdA,drivesH*ppdH)
        val driveMc=driveMonteCarlo(line,drivesA,drivesH,ppdA,ppdH,away,home)
        val bayes=bayesian(line,drivesA*ppdA+drivesH*ppdH)
        val poisson=poissonShadow(line,drivesA*ppdA+drivesH*ppdH)

        val engines=listOf(markov,negbin,driveMc,bayes,poisson)
        val w=doubleArrayOf(.30,.25,.20,.15,.10)
        val projection=engines.indices.sumOf{i->engines[i].projection*w[i]}
        val pOver=engines.indices.sumOf{i->engines[i].pOver*w[i]}
        val pUnder=engines.indices.sumOf{i->engines[i].pUnder*w[i]}
        val pick=if(pOver>=pUnder)"OVER" else "UNDER"
        val prob=max(pOver,pUnder)
        val classification=when {
            prob>=.66 -> "JUGABLE ★"
            prob>=.60 -> "LEAN"
            else -> "PASS"
        }
        return Prediction(
            gameId=game.gameId,season=game.season,week=game.week,awayTeam=game.awayTeam,homeTeam=game.homeTeam,
            line=line,pick=pick,probability=prob,projection=projection,classification=classification,engines=engines
        )
    }

    private fun shrink(m:TeamMetrics,team:String):TeamMetrics{
        val playWeight=m.plays.toDouble()/(m.plays+300.0)
        val driveWeight=m.drives.toDouble()/(m.drives+24.0)
        fun blend(x:Double,prior:Double,w:Double)=prior*(1-w)+x*w
        return m.copy(
            team=team,
            offEpaPerPlay=blend(m.offEpaPerPlay,0.0,playWeight),
            defEpaAllowedPerPlay=blend(m.defEpaAllowedPerPlay,0.0,playWeight),
            successRate=blend(m.successRate,.43,playWeight),
            explosiveRate=blend(m.explosiveRate,.10,playWeight),
            turnoverRate=blend(m.turnoverRate,.018,playWeight),
            tdPerDrive=blend(m.tdPerDrive,.22,driveWeight),
            fgPerDrive=blend(m.fgPerDrive,.15,driveWeight),
            drivesPerGame=blend(m.drivesPerGame,10.6,driveWeight)
        )
    }

    private fun markov(line:Double,da:Double,dh:Double,a:TeamMetrics,h:TeamMetrics,epaA:Double,epaH:Double):EngineSlice{
        var over=0; var under=0; var sum=0.0
        repeat(simulations){
            val sa=simulateDrives(sampleDrives(da),a,epaA)
            val sh=simulateDrives(sampleDrives(dh),h,epaH)
            val t=sa+sh; sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice("Markov Drive",sum/simulations,over.toDouble()/simulations,under.toDouble()/simulations)
    }

    private fun driveMonteCarlo(line:Double,da:Double,dh:Double,ppdA:Double,ppdH:Double,a:TeamMetrics,h:TeamMetrics):EngineSlice{
        var over=0; var under=0; var sum=0.0
        repeat(simulations){
            val sa=simulatePpdDrives(sampleDrives(da),ppdA,a.turnoverRate)
            val sh=simulatePpdDrives(sampleDrives(dh),ppdH,h.turnoverRate)
            val t=sa+sh;sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice("Drive Monte Carlo",sum/simulations,over.toDouble()/simulations,under.toDouble()/simulations)
    }

    private fun negativeBinomial(line:Double,meanA:Double,meanH:Double):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val t=negBin(meanA,7.0)+negBin(meanH,7.0);sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice("Negative Binomial",sum/simulations,over.toDouble()/simulations,under.toDouble()/simulations)
    }

    private fun bayesian(line:Double,mu:Double):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val t=max(0.0,mu+gaussian()*12.3);sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice("Bayesian",sum/simulations,over.toDouble()/simulations,under.toDouble()/simulations)
    }

    private fun poissonShadow(line:Double,mu:Double):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val t=poisson(mu).toDouble();sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice("Shadow Poisson",sum/simulations,over.toDouble()/simulations,under.toDouble()/simulations)
    }

    private fun sampleDrives(mu:Double)=round(mu+gaussian()*1.15).toInt().coerceIn(7,16)

    private fun simulateDrives(n:Int,m:TeamMetrics,epa:Double):Int{
        val td=(m.tdPerDrive + epa*.28).coerceIn(.08,.42)
        val fg=(m.fgPerDrive + epa*.07).coerceIn(.06,.28)
        val safety=.003
        var s=0
        repeat(n){
            val r=rng.nextDouble()
            when {
                r<td -> s+=if(rng.nextDouble()<.94)7 else 6
                r<td+fg -> s+=3
                r<td+fg+safety -> s+=2
            }
        }
        return s
    }

    private fun simulatePpdDrives(n:Int,ppd:Double,turnoverRate:Double):Int{
        val td=(ppd/7.0*.67).coerceIn(.08,.38)
        val fg=(ppd/3.0*.25).coerceIn(.06,.28)
        var s=0
        repeat(n){
            val r=rng.nextDouble()
            if(r < turnoverRate.coerceIn(.01,.12)) return@repeat
            when {
                r<td -> s+=7
                r<td+fg -> s+=3
            }
        }
        return s
    }

    private fun negBin(mean:Double,k:Double):Int {
        val lambda=gamma(k,mean/k)
        return poisson(lambda)
    }

    private fun gamma(shape:Double,scale:Double):Double{
        if(shape<1) return gamma(shape+1,scale)*rng.nextDouble().pow(1.0/shape)
        val d=shape-1.0/3.0
        val c=1.0/sqrt(9*d)
        while(true){
            val x=gaussian(); var v=1+c*x
            if(v<=0)continue
            v=v*v*v
            val u=rng.nextDouble()
            if(u<1-.0331*x.pow(4))return d*v*scale
            if(ln(u)<.5*x*x+d*(1-v+ln(v)))return d*v*scale
        }
    }

    private fun poisson(lambda:Double):Int{
        if(lambda<30){
            val l=exp(-lambda);var p=1.0;var k=0
            do{k++;p*=rng.nextDouble()}while(p>l)
            return k-1
        }
        return max(0,round(lambda+sqrt(lambda)*gaussian()).toInt())
    }

    private fun gaussian():Double{
        var u=0.0;var v=0.0
        while(u==0.0)u=rng.nextDouble()
        while(v==0.0)v=rng.nextDouble()
        return sqrt(-2*ln(u))*cos(2*Math.PI*v)
    }
}
