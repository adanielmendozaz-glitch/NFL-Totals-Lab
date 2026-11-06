package com.nfltotalslab.app.simcast

/** Structural football checks. No claim of real-world statistical calibration. */
object SimcastIntegrity {
    fun check(s:GameSim):List<String> {
        val errors=mutableListOf<String>()
        if(s.awayQuarters.size!=4||s.homeQuarters.size!=4)errors+="Invalid quarter arrays"
        if(s.awayQuarters.any{it<0}||s.homeQuarters.any{it<0}||s.awayOvertime<0||s.homeOvertime<0)
            errors+="Negative scores"
        var awayScore=0
        var homeScore=0
        var halftimeSeen=false
        var afterHalftime=false
        var firstQ3:SimPlay?=null
        var q2LastDrive=-1
        val scoring=mapOf("FG" to 3,"PASS_TD" to 6,"RUSH_TD" to 6,
            "XP_GOOD" to 1,"XP_MISSED" to 0,"TWO_GOOD" to 2,"TWO_FAILED" to 0)
        var prevQuarter=1
        var prevClock=901
        var touchdownWaiting=false
        var touchdownTeam=""
        s.plays.forEachIndexed{idx,p->
            if(p.index!=idx+1)errors+="Event index at ${idx+1}"
            if(p.quarter !in 1..5)errors+="Invalid period at ${p.index}"
            if(p.quarter<prevQuarter)errors+="Period reversed at ${p.index}"
            if(p.quarter==prevQuarter && p.clock>prevClock)errors+="Clock increased at ${p.index}"
            if(p.clock !in 0..if(p.quarter==5)600 else 900)errors+="Clock outside period at ${p.index}"
            if(p.fromYard !in 0..100 || p.toYard !in 0..100)errors+="Illegal field position at ${p.index}"
            if(p.quarter==2 && p.kind !in setOf("HALFTIME"))q2LastDrive=p.drive
            if(p.kind=="HALFTIME"){
                halftimeSeen=true
                if(p.quarter!=2)errors+="Halftime in wrong period"
            }
            if(p.quarter==3){
                if(firstQ3==null)firstQ3=p
                if(!halftimeSeen)errors+="Q3 before halftime"
                if(!afterHalftime && p.kind=="KICKOFF")afterHalftime=true
                if(p.kind !in setOf("KICKOFF") && p.drive==q2LastDrive)
                    errors+="Drive crosses halftime at ${p.index}"
            }
            if(p.kind in setOf("PASS_TD","RUSH_TD")){
                touchdownWaiting=true;touchdownTeam=p.offense
            }else if(p.kind.startsWith("XP_") || p.kind.startsWith("TWO_")){
                if(!touchdownWaiting || touchdownTeam!=p.offense)
                    errors+="Try without TD at ${p.index}"
                touchdownWaiting=false
            }else if(touchdownWaiting && p.kind !in setOf("HALFTIME","OVERTIME_START")){
                // A winning walk-off OT touchdown is allowed without a try.
                if(p.quarter!=5)errors+="Unresolved try after TD at ${p.index}"
                touchdownWaiting=false
            }
            if(p.kind in scoring && p.points!=scoring[p.kind])errors+="Incorrect points for ${p.kind} at ${p.index}"
            if(p.points<0 || p.points>6 && p.kind!="FG")errors+="Illegal play points at ${p.index}"
            if(p.points>0){
                if(p.offense==s.away)awayScore+=p.points
                else if(p.offense==s.home)homeScore+=p.points
                else errors+="Unknown scoring team at ${p.index}"
            }
            if(p.awayScore!=awayScore || p.homeScore!=homeScore)errors+="Score reconciliation at ${p.index}"
            prevQuarter=p.quarter;prevClock=p.clock
        }
        if(!halftimeSeen)errors+="Halftime missing"
        if(firstQ3?.kind!="KICKOFF")errors+="Q3 must begin with kickoff"
        if(s.overtimePlayed && s.awayQuarters.sum()!=s.homeQuarters.sum())
            errors+="Overtime without regulation tie"
        if(!s.overtimePlayed && s.plays.any{it.quarter==5})errors+="OT period not declared"
        if(awayScore!=s.awayFinal || homeScore!=s.homeFinal)errors+="Final scores differ from plays"
        if(s.players.any{it.passAttempts<it.completions || it.targets<it.receptions})
            errors+="Impossible individual passing totals"
        return errors.distinct().take(20)
    }
}
