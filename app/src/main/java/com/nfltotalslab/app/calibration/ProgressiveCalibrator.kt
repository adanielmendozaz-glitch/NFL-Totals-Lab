package com.nfltotalslab.app.calibration

import com.nfltotalslab.app.data.Prediction
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

data class CalibrationState(val trainN:Int,val intercept:Double,val slope:Double,val maturity:String){
    fun calibrate(raw:Double):Double{
        val p=raw.coerceIn(.001,.999)
        val x=ln(p/(1.0-p))
        val z=(intercept+slope*x).coerceIn(-30.0,30.0)
        val q=if(z>=0.0)1.0/(1.0+exp(-z)) else exp(z)/(1.0+exp(z))
        return q.coerceIn(.01,.99)
    }
}

object ProgressiveCalibrator {
    private const val RIDGE=24.0
    fun fit(predictions:List<Prediction>):CalibrationState{
        val rows=predictions.filter{it.analysisSource=="AUTO_CENSUS" && (it.result=="WIN" || it.result=="LOSS")}
            .groupBy{it.gameId}.mapNotNull{(_,xs)->xs.maxByOrNull{it.createdAt}}
        var a=0.0; var b=1.0
        if(rows.isNotEmpty()){
            for(iter in 0 until 30){
                var g0=RIDGE*a; var g1=RIDGE*(b-1.0); var h00=RIDGE; var h01=0.0; var h11=RIDGE
                rows.forEach{r->
                    val p=r.probability.coerceIn(.001,.999); val x=ln(p/(1.0-p)); val y=if(r.result=="WIN")1.0 else 0.0
                    val z=(a+b*x).coerceIn(-30.0,30.0); val q=if(z>=0.0)1.0/(1.0+exp(-z)) else exp(z)/(1.0+exp(z))
                    val e=q-y; val w=max(1e-7,q*(1.0-q)); g0+=e; g1+=e*x; h00+=w; h01+=w*x; h11+=w*x*x
                }
                val det=h00*h11-h01*h01; if(abs(det)<1e-10)break
                val da=(h11*g0-h01*g1)/det; val db=(-h01*g0+h00*g1)/det
                a=(a-da).coerceIn(-3.0,3.0); b=(b-db).coerceIn(.15,2.5)
                if(abs(da)+abs(db)<1e-7)break
            }
        }
        return CalibrationState(rows.size,a,b,maturity(rows.size))
    }
    fun maturity(n:Int):String=when{n<16->"SEED";n<32->"PROVISIONAL";n<64->"FORMING";n<128->"USABLE";else->"STRONG"}
}
