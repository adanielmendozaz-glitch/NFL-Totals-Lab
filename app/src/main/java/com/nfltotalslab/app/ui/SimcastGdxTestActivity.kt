package com.nfltotalslab.app.ui

import android.os.Bundle
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.min

class SimcastGdxTestActivity:AndroidApplication(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  initialize(BroadcastScene(
   intent.getStringExtra("away")?:"AWAY",
   intent.getStringExtra("home")?:"HOME",
   intent.getStringExtra("plays")?:"[]",
   intent.getDoubleExtra("line",0.0).toFloat(),
   intent.getStringExtra("qb")?:"QB",
   intent.getStringExtra("receiver")?:"WR",
   intent.getStringExtra("runner")?:"RB"
  ),AndroidApplicationConfiguration().apply{useImmersiveMode=false})
 }
}
private data class BroadcastPlay(
 val away:Boolean,val start:Int,val finish:Int,val down:Int,val distance:Int,
 val elapsed:Int,val kind:String,val yards:Int,val awayScore:Int,val homeScore:Int
)
private class BroadcastScene(
 private val away:String,private val home:String,raw:String,
 private val totalLine:Float,private val qb:String,private val receiver:String,private val runner:String
):ApplicationAdapter(){
 private val plays=mutableListOf<BroadcastPlay>()
 private lateinit var shapes:ShapeRenderer
 private lateinit var batch:SpriteBatch
 private lateinit var font:BitmapFont
 private lateinit var camera:OrthographicCamera
 private val offense=Array(11){Vector2()}
 private val defense=Array(11){Vector2()}
 private var index=0
 private var phase=0f
 private var paused=false
 private var speed=1f
 private val duration=5.2f
 private val navy=Color(.055f,.095f,.17f,1f)
 private val turf=Color(.055f,.29f,.16f,1f)
 private val white=Color(.94f,.96f,1f,1f)
 init{
  try{
   val a=JSONArray(raw)
   for(i in 0 until a.length()){
    val o=a.getJSONObject(i)
    plays.add(BroadcastPlay(o.optBoolean("a"),o.optInt("s"),o.optInt("f"),
     o.optInt("d",1),o.optInt("g",10),o.optInt("t"),o.optString("k","Carrera"),
     o.optInt("y"),o.optInt("as"),o.optInt("hs")))
   }
  }catch(_:Exception){}
  if(plays.isEmpty())plays.add(BroadcastPlay(true,25,32,1,10,22,"Pase completo",7,0,0))
 }
 override fun create(){
  shapes=ShapeRenderer()
  batch=SpriteBatch()
  font=BitmapFont().apply{data.setScale(.20f);color=Color.WHITE}
  camera=OrthographicCamera()
  camera.setToOrtho(false,120f,75f)
  val ys=floatArrayOf(26f,20f,19f,23f,26f,30f,34f,5f,48f,12f,41f)
  for(i in 0..10)offense[i].set(if(i==0)45f else if(i==1)43f else 49f,ys[i])
  for(i in 0..10)defense[i].set(if(i<4)54f else if(i<7)60f else 66f,5f+i*4.3f)
 }
 override fun resize(width:Int,height:Int){
  Gdx.gl.glViewport(0,0,width,height)
  camera.setToOrtho(false,120f,75f)
 }
 private fun ease(t:Float):Float{
  val u=MathUtils.clamp(t,0f,1f);return u*u*(3f-2f*u)
 }
 private fun player(i:Int,t:Float,p:BroadcastPlay):Vector2{
  val a=(t-.8f).coerceAtLeast(0f)
  val run=p.kind.contains("Carrera",true)||p.kind.contains("Fumble",true)
  val pos=if(i<11)offense[i].cpy() else defense[i-11].cpy()
  if(i<11)when(i){
   0->pos.x-=3f*ease(a/1.2f)
   1->{pos.x+=(if(run)min(18f,abs(p.yards).toFloat()+5f) else 4f)*ease(a/2.8f);pos.y+=3f*ease(a/2f)}
   in 2..6->{pos.x+=2f*ease(a/1.5f);pos.y+=MathUtils.sin(a*2f+i)*.3f}
   7->{pos.x+=19f*ease(a/3.3f);pos.y+=9f*ease((a-1f)/1.8f)}
   8->{pos.x+=19f*ease(a/3.3f);pos.y-=8f*ease((a-1f)/1.8f)}
   9->{pos.x+=13f*ease(a/2.8f);pos.y+=7f*ease((a-1f)/1.5f)}
   else->{pos.x+=12f*ease(a/2.8f);pos.y-=7f*ease((a-1f)/1.5f)}
  }else{
   val n=i-11
   if(n<4)pos.x-=4f*ease(a/1.6f)
   else{
    val target=player(intArrayOf(1,0,10,7,8,9,10)[n-4],t,p)
    pos.x=MathUtils.lerp(pos.x,target.x-1f,ease(a/4f))
    pos.y=MathUtils.lerp(pos.y,target.y+2f,ease(a/4f))
   }
  }
  if(!p.away)pos.x=120f-pos.x
  pos.y+=11f
  return pos
 }
 private fun ball(t:Float,p:BroadcastPlay):Vector2{
  val run=p.kind.contains("Carrera",true)||p.kind.contains("Fumble",true)
  if(run)return player(if(t<1.7f)0 else 1,t,p)
  if(t<2.7f)return player(0,t,p)
  if(t<3.7f){
   val u=ease((t-2.7f)/1f)
   val a=player(0,2.7f,p);val b=player(7,3.7f,p)
   return Vector2(MathUtils.lerp(a.x,b.x,u),MathUtils.lerp(a.y,b.y,u)+3f*MathUtils.sin(u*MathUtils.PI))
  }
  return player(7,t,p)
 }
 override fun render(){
  if(Gdx.input.justTouched()){
   val touch=camera.unproject(Vector3(Gdx.input.x.toFloat(),Gdx.input.y.toFloat(),0f))
   if(touch.y<10f){
    when{
     touch.x<38f->paused=!paused
     touch.x<77f->speed=if(speed>=4f)1f else speed*2f
     else->{index=(index+1).coerceAtMost(plays.lastIndex);phase=0f}
    }
   }
  }
  if(!paused&&index<plays.size){
   phase+=Gdx.graphics.deltaTime.coerceIn(0f,.05f)*speed
   if(phase>=duration){
    phase=0f
    if(index<plays.lastIndex)index++ else paused=true
   }
  }
  val p=plays[index]
  val seconds=p.elapsed.coerceIn(0,3599)
  val quarter=(seconds/900+1).coerceIn(1,4)
  val remaining=900-seconds%900
  val total=p.awayScore+p.homeScore
  Gdx.gl.glClearColor(.035f,.08f,.14f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
  camera.update();shapes.projectionMatrix=camera.combined;batch.projectionMatrix=camera.combined
  shapes.begin(ShapeRenderer.ShapeType.Filled)
  shapes.color=navy;shapes.rect(0f,64f,120f,11f);shapes.rect(0f,0f,120f,11f)
  shapes.color=turf;shapes.rect(0f,11f,120f,53f)
  shapes.color=Color(.10f,.20f,.43f,1f)
  shapes.rect(0f,11f,10f,53f);shapes.rect(110f,11f,10f,53f)
  val los=if(p.away)p.start.toFloat()+10f else 110f-p.start
  val first=(los+(if(p.away)1f else -1f)*p.distance).coerceIn(10f,110f)
  for(i in 0 until 22){
   val v=player(i,phase,p)
   shapes.color=if(i<11)white else Color(.18f,.58f,.98f,1f)
   shapes.circle(v.x,v.y,1.05f,18)
  }
  val b=ball(phase,p);shapes.color=Color.ORANGE;shapes.circle(b.x,b.y,.63f,14)
  shapes.end()
  shapes.begin(ShapeRenderer.ShapeType.Line)
  shapes.color=Color(.83f,.93f,.85f,.55f)
  for(x in 10..110 step 5)shapes.line(x.toFloat(),11f,x.toFloat(),64f)
  for(x in 11..109)if(x%5!=0){
   shapes.line(x.toFloat(),30f,x.toFloat(),31f)
   shapes.line(x.toFloat(),44f,x.toFloat(),45f)
  }
  shapes.color=Color.CYAN;shapes.line(los,11f,los,64f)
  shapes.color=Color.YELLOW;shapes.line(first,11f,first,64f)
  shapes.end()
  batch.begin()
  font.draw(batch,"$away  ${p.awayScore}     ${p.homeScore}  $home",5f,72f)
  font.draw(batch,"Q$quarter  ${remaining/60}:${(remaining%60).toString().padStart(2,'0')}  |  ${p.down} & ${p.distance}  |  ${if(p.away)away else home} BALL",5f,67f)
  font.draw(batch,"TOTAL $total   O/U ${if(totalLine>0f)totalLine.toString() else "--"}   |  PLAY ${index+1}/${plays.size}   |  ${Gdx.graphics.framesPerSecond} FPS",65f,72f)
  font.draw(batch,"${p.kind.take(28)}  ${p.yards} yd",65f,67f)
  for(x in 20..100 step 10){
   val n=if(x<=60)x-10 else 110-x
   font.draw(batch,"$n",x.toFloat()-1.5f,53f)
   font.draw(batch,"$n",x.toFloat()-1.5f,21f)
  }
  font.draw(batch,"${if(paused)"PLAY" else "PAUSE"}",9f,5.8f)
  font.draw(batch,"SPEED ${speed.toInt()}x",45f,5.8f)
  font.draw(batch,"NEXT PLAY",84f,5.8f)
  font.draw(batch,"QB ${qb.take(14)}   WR ${receiver.take(14)}   RB ${runner.take(14)}",5f,13f)
  batch.end()
 }
 override fun dispose(){shapes.dispose();batch.dispose();font.dispose()}
}
