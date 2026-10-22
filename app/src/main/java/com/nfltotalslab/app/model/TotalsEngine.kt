package com.nfltotalslab.app.model

import com.nfltotalslab.app.data.*
import kotlin.math.*
import kotlin.random.Random

class TotalsEngine(private val simulations:Int=100_000) {
    private val rng=Random.Default

    fun predict(game:GameRecord, awayRaw:TeamMetrics?, homeRaw:TeamMetrics?):Prediction {
        val away=shrink(awayRaw ?: TeamMetrics(game.season,game.awayTeam),game.awayTeam)
        val home=shrink(homeRaw ?: TeamMetrics(game.season,game.homeTeam),game.homeTeam)
        val line=game.totalLine ?: 44.5

        val drivesA=((away.drivesPerGame+home.drivesPerGame)/2.0).coerceIn(8.2,13.5)
        val drivesH=((home.drivesPerGame+away.drivesPerGame)/2.0).coerceIn(8.2,13.5)

        val epaA=away.offEpaPerPlay*.58 + home.defEpaAllowedPerPlay*.42
        val epaH=home.offEpaPerPlay*.58 + away.defEpaAllowedPerPlay*.42 + .015

        val formulaPpdA=(
            2.02 + 3.8*epaA +
            1.1*(away.successRate-.43) +
            .55*(away.explosiveRate-.10)
        ).coerceIn(1.1,3.3)

        val formulaPpdH=(
            2.08 + 3.8*epaH +
            1.1*(home.successRate-.43) +
            .55*(home.explosiveRate-.10)
        ).coerceIn(1.1,3.4)

        val ppdA=(formulaPpdA*.75 + away.pointsPerDrive*.25).coerceIn(1.0,3.5)
        val ppdH=(formulaPpdH*.75 + home.pointsPerDrive*.25).coerceIn(1.0,3.6)

        val markov=markov(line,drivesA,drivesH,away,home,epaA,epaH)
        val negbin=negativeBinomial(line,drivesA*ppdA,drivesH*ppdH)
        val possession=possessionMonteCarlo(line,drivesA,drivesH,away,home,epaA,epaH)
        val bayes=bayesian(line,drivesA*ppdA+drivesH*ppdH)
        val poisson=poissonShadow(line,drivesA*ppdA+drivesH*ppdH)

        val engines=listOf(markov,negbin,possession,bayes,poisson)

        val distribution=EngineSlice(
            name="Distribution Family",
            projection=(negbin.projection+bayes.projection)/2.0,
            pOver=(negbin.pOver+bayes.pOver)/2.0,
            pUnder=(negbin.pUnder+bayes.pUnder)/2.0
        )

        val families=listOf(markov,distribution,possession)
        val projection=families.map{it.projection}.average()
        val pOverRaw=families.map{it.pOver}.average()
        val pUnderRaw=families.map{it.pUnder}.average()
        val pick=if(pOverRaw>=pUnderRaw)"OVER" else "UNDER"
        val rawProb=max(pOverRaw,pUnderRaw)

        val familySides=families.map{
            if(it.pOver>=it.pUnder)"OVER" else "UNDER"
        }
        val familyAgreement=familySides.count{it==pick}
        val familyDispersion=
            families.maxOf{it.projection}-families.minOf{it.projection}

        val sampleGames=minOf(awayRaw?.games ?: 0,homeRaw?.games ?: 0)
        val sampleMaturity=if(sampleGames<=0)0.0
        else sampleGames.toDouble()/(sampleGames+2.0)

        var probability=.50+(rawProb-.50)*sampleMaturity
        probability=probability.coerceIn(.50,.62)

        val edge=abs(projection-line)
        val turnoverSum=(awayRaw?.turnoverRate ?: .018)+(homeRaw?.turnoverRate ?: .018)
        val tdDriveSum=(awayRaw?.tdPerDrive ?: .22)+(homeRaw?.tdPerDrive ?: .22)

        val turnoverChaos=pick=="UNDER" && turnoverSum>=.040
        val consensusTrap=familyAgreement==3 && familyDispersion<4.0
        val scoringForm=pick=="OVER" && tdDriveSum>=.56

        val marketExtremeGuard=
            (pick=="UNDER" && line>=52.5 && edge>=4.0) ||
            (pick=="OVER" && line<=39.5 && edge>=3.5)

        val classification=when{
            sampleGames==0 -> "PASS · NO SAMPLE"
            sampleGames<3 -> "PASS · EARLY"
            turnoverChaos -> "PASS · TURNOVER CHAOS"
            consensusTrap -> "PASS · CONSENSUS TRAP"
            familyDispersion>=12.0 -> "PASS · EXTREME DISPERSION"
            marketExtremeGuard -> "PASS · MARKET EXTREME"
            scoringForm && familyAgreement>=2 && edge>=1.5 -> "LEAN · SCORING"
            familyAgreement==2 && edge>=1.5 -> "LEAN · DIVERGENCE"
            familyAgreement<2 -> "PASS · SPLIT"
            edge>=2.0 && probability>=.55 -> "LEAN"
            else -> "PASS · LOW EDGE"
        }

        return Prediction(
            gameId=game.gameId,
            season=game.season,
            week=game.week,
            awayTeam=game.awayTeam,
            homeTeam=game.homeTeam,
            line=line,
            pick=pick,
            probability=probability,
            projection=projection,
            classification=classification,
            engines=engines
        )
    }

    private fun shrink(m:TeamMetrics,team:String):TeamMetrics{
        val playWeight=m.plays.toDouble()/(m.plays+180.0)
        val driveWeight=m.drives.toDouble()/(m.drives+16.0)
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
            drivesPerGame=blend(m.drivesPerGame,10.6,driveWeight),
            pointsPerDrive=blend(m.pointsPerDrive,2.05,driveWeight)
        )
    }

    private fun markov(
        line:Double,da:Double,dh:Double,a:TeamMetrics,h:TeamMetrics,
        epaA:Double,epaH:Double
    ):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val sa=simulateMarkovDrives(sampleDrives(da),a,epaA)
            val sh=simulateMarkovDrives(sampleDrives(dh),h,epaH)
            val t=sa+sh
            sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice(
            "Markov Drive",sum/simulations,
            over.toDouble()/simulations,under.toDouble()/simulations
        )
    }

    private fun possessionMonteCarlo(
        line:Double,
        da:Double,
        dh:Double,
        away:TeamMetrics,
        home:TeamMetrics,
        epaA:Double,
        epaH:Double
    ):EngineSlice{
        var over=0;var under=0;var sum=0.0
        val chaos=(away.turnoverRate+home.turnoverRate).coerceIn(.01,.10)

        repeat(simulations){
            val a=simulatePossessions(sampleDrives(da),away,epaA,chaos)
            val h=simulatePossessions(sampleDrives(dh),home,epaH,chaos)
            val t=a+h
            sum+=t
            if(t>line)over++ else if(t<line)under++
        }

        return EngineSlice(
            "Possession Monte Carlo V2",sum/simulations,
            over.toDouble()/simulations,under.toDouble()/simulations
        )
    }

    private fun simulatePossessions(
        n:Int,
        offense:TeamMetrics,
        matchupEpa:Double,
        chaos:Double
    ):Int{
        val perPlayTo=offense.turnoverRate.coerceIn(.002,.06)
        val turnoverDrive=(
            1.0-(1.0-perPlayTo).pow(6.2)
        ).coerceIn(.015,.30)

        var td=(
            offense.tdPerDrive +
            .20*matchupEpa +
            .18*(offense.successRate-.43) +
            .70*(offense.explosiveRate-.10)
        ).coerceIn(.06,.43)

        var fg=(
            offense.fgPerDrive +
            .04*matchupEpa +
            .05*(offense.successRate-.43)
        ).coerceIn(.05,.27)

        val available=(1.0-turnoverDrive).coerceAtLeast(.68)
        val scoreMass=td+fg
        if(scoreMass>available*.94){
            val scale=(available*.94)/scoreMass
            td*=scale
            fg*=scale
        }

        var points=0
        repeat(n){
            val r=rng.nextDouble()
            when{
                r<turnoverDrive -> {
                    val chaosBoost=(chaos-.03).coerceAtLeast(0.0)
                    val x=rng.nextDouble()
                    when{
                        x<(.045+chaosBoost*.55).coerceAtMost(.10) -> points+=7
                        x<(.15+chaosBoost*1.10).coerceAtMost(.28) -> points+=3
                    }
                }
                r<turnoverDrive+td -> points+=if(rng.nextDouble()<.94)7 else 6
                r<turnoverDrive+td+fg -> points+=3
            }
        }
        return points
    }

    private fun negativeBinomial(line:Double,meanA:Double,meanH:Double):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val t=negBin(meanA,7.0)+negBin(meanH,7.0)
            sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice(
            "Negative Binomial",sum/simulations,
            over.toDouble()/simulations,under.toDouble()/simulations
        )
    }

    private fun bayesian(line:Double,mu:Double):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val t=max(0.0,mu+gaussian()*12.3)
            sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice(
            "Bayesian",sum/simulations,
            over.toDouble()/simulations,under.toDouble()/simulations
        )
    }

    private fun poissonShadow(line:Double,mu:Double):EngineSlice{
        var over=0;var under=0;var sum=0.0
        repeat(simulations){
            val t=poisson(mu).toDouble()
            sum+=t
            if(t>line)over++ else if(t<line)under++
        }
        return EngineSlice(
            "Shadow Poisson",sum/simulations,
            over.toDouble()/simulations,under.toDouble()/simulations
        )
    }

    private fun sampleDrives(mu:Double)=
        round(mu+gaussian()*1.15).toInt().coerceIn(7,16)

    private fun simulateMarkovDrives(n:Int,m:TeamMetrics,epa:Double):Int{
        val td=(m.tdPerDrive + epa*.28).coerceIn(.08,.42)
        val fg=(m.fgPerDrive + epa*.07).coerceIn(.06,.28)
        val safety=.003
        var s=0
        repeat(n){
            val r=rng.nextDouble()
            when{
                r<td -> s+=if(rng.nextDouble()<.94)7 else 6
                r<td+fg -> s+=3
                r<td+fg+safety -> s+=2
            }
        }
        return s
    }

    private fun negBin(mean:Double,k:Double):Int{
        val lambda=gamma(k,mean/k)
        return poisson(lambda)
    }

    private fun gamma(shape:Double,scale:Double):Double{
        if(shape<1)return gamma(shape+1,scale)*rng.nextDouble().pow(1.0/shape)
        val d=shape-1.0/3.0
        val c=1.0/sqrt(9*d)
        while(true){
            val x=gaussian()
            var v=1+c*x
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
