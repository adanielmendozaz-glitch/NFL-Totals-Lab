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
  initialize(StadiumScene(
   intent.getStringExtra("away")?:"AWAY",intent.getStringExtra("home")?:"HOME",
   intent.getStringExtra("plays")?:"[]",intent.getDoubleExtra("line",0.0).toFloat(),
   intent.getStringExtra("qb")?:"QB",intent.getStringExtra("receiver")?:"WR",
   intent.getStringExtra("runner")?:"RB",intent.getIntExtra("awayColor",0xFF006778.toInt()),
   intent.getIntExtra("homeColor",0xFF241773.toInt())
  ),AndroidApplicationConfiguration().apply{useImmersiveMode=true})
 }
}
private data class Play(val away:Boolean,val start:Int,val finish:Int,val down:Int,val distance:Int,
 val elapsed:Int,val kind:String,val yards:Int,val awayScore:Int,val homeScore:Int)
private class StadiumScene(
 val away:String,val home:String,raw:String,val line:Float,
 val qb:String,val wr:String,val rb:String,awayArgb:Int,homeArgb:Int
):ApplicationAdapter(){
 private val plays=mutableListOf<Play>()
 private lateinit var shapes:ShapeRenderer
 private lateinit var batch:SpriteBatch
 private lateinit var font:BitmapFont
 private lateinit var cam:OrthographicCamera
 private var index=0
 private var time=0f
 private var paused=false
 private var speed=1f
 private var labels=true
 private var follow=true
 private var cameraX=60f
 private val duration=6.0f
 private val awayColor=Color((awayArgb shr 16 and 255)/255f,(awayArgb shr 8 and 255)/255f,(awayArgb and 255)/255f,1f)
 private val homeColor=Color((homeArgb shr 16 and 255)/255f,(homeArgb shr 8 and 255)/255f,(homeArgb and 255)/255f,1f)
 private val navy=Color(.035f,.07f,.13f,1f)
 private val gold=Color(.99f,.76f,.27f,1f)
 private val white=Color(.96f,.97f,1f,1f)
 private val ys=floatArrayOf(26.6f,20f,18.6f,22.6f,26.6f,30.6f,34.6f,5.5f,47.5f,12.5f,40.5f)
 init{
  try{
   val array=JSONArray(raw)
   for(i in 0 until array.length()){
    val o=array.getJSONObject(i)
    plays.add(Play(o.optBoolean("a"),o.optInt("s",25),o.optInt("f",30),
     o.optInt("d",1),o.optInt("g",10),o.optInt("t"),o.optString("k","Carrera"),
     o.optInt("y"),o.optInt("as"),o.optInt("hs")))
   }
  }catch(_:Exception){}
  if(plays.isEmpty())plays.add(Play(true,25,32,1,10,25,"Carrera",7,0,0))
 }
 override fun create(){
  shapes=ShapeRenderer();batch=SpriteBatch()
  font=BitmapFont().apply{data.setScale(.16f);color=white}
  cam=OrthographicCamera();cam.setToOrtho(false,120f,75f)
 }
 override fun resize(w:Int,h:Int){Gdx.gl.glViewport(0,0,w,h);cam.setToOrtho(false,120f,75f)}
 private fun ease(t:Float):Float{val u=MathUtils.clamp(t,0f,1f);return u*u*(3f-2f*u)}
 private fun run(p:Play)=p.kind.contains("Carrera",true)||p.kind.contains("Fumble",true)
 private fun pass(p:Play)=p.kind.contains("Pase",true)||p.kind.contains("Touchdown",true)
 private fun direction(p:Play)=if(p.away)1f else -1f
 private fun los(p:Play)=if(p.away)10f+p.start else 110f-p.start
 private fun finalX(p:Play)=if(p.away)10f+p.finish else 110f-p.finish
 private fun runnerId(p:Play)=if(run(p))1 else 7+(index%4)
 private fun start(i:Int,p:Play):Vector2{
  val d=direction(p);val x=los(p)
  if(i<11){
   val depth=when(i){0->-4.5f;1->-7.5f;in 2..6->-.9f;else->-1.3f}
   val formation= index%4
   val y=when{
    formation==1&&i==1->26.6f
    formation==2&&i==9->35f
    formation==3&&i==8->42f
    else->ys[i]
   }
   return Vector2(x+d*depth,y+11f)
  }
  val n=i-11
  val y=when(n){0->19f;1->23f;2->30f;3->34f;4->13f;5->26.6f;6->40f;7->6f;8->47f;9->17f;else->37f}
  val depth=if(n<4)3.5f else if(n<7)7f else 12f
  return Vector2(x+d*depth,y+11f)
 }
 private fun position(i:Int,t:Float,p:Play):Vector2{
  val s=start(i,p);val d=direction(p);val a=(t-1.0f).coerceAtLeast(0f)
  val u=ease(a/3.5f);val carrier=runnerId(p)
  if(i<11){
   when(i){
    0->{s.x-=d*3f*ease(a/1.7f);if(p.kind.contains("Sack",true))s.x=los(p)+d*p.yards*ease(a/3f)}
    1->if(run(p)){
      s.x=MathUtils.lerp(s.x,finalX(p),u)
      s.y+=MathUtils.sin((index%3+1)*.9f)*3f*u
     }else{s.x+=d*4f*u}
    in 2..6->{s.x+=d*2.2f*ease(a/1.5f);s.y+=MathUtils.sin(a*2f+i)*.3f}
    else->{
     val route=i-7
     val lateral=when((index+route)%5){0->-9f;1->8f;2->-3f;3->5f;else->0f}
     val gain=if(i==carrier&&pass(p))p.yards.toFloat().coerceIn(-8f,65f) else (12+route*3).toFloat()
     s.x+=d*gain*ease(a/4.0f)
     s.y+=lateral*ease((a-1.3f)/2f)
     if(i==carrier&&pass(p)){s.x=MathUtils.lerp(s.x,finalX(p),ease((a-2.2f)/2f))}
    }
   }
  }else{
   val n=i-11
   if(n<4){s.x-=d*3.5f*ease(a/1.7f)}
   else{
    val target=position(if(n<7)1 else 7+(n-7)%4,t,p)
    s.x=MathUtils.lerp(s.x,target.x-d*1.1f,ease(a/4.8f))
    s.y=MathUtils.lerp(s.y,target.y+(if(n%2==0)1.8f else -1.8f),ease(a/4.8f))
   }
  }
  return s
 }
 private fun ball(p:Play,t:Float):Vector2{
  if(t<1f)return start(4,p)
  if(run(p))return position(if(t<2f)0 else 1,t,p)
  if(t<2.4f)return position(0,t,p)
  val receiver=runnerId(p)
  if(t<3.6f){
   val a=position(0,2.4f,p);val b=position(receiver,3.6f,p)
   val u=ease((t-2.4f)/1.2f)
   return Vector2(MathUtils.lerp(a.x,b.x,u),MathUtils.lerp(a.y,b.y,u)+4f*MathUtils.sin(u*MathUtils.PI))
  }
  return position(receiver,t,p)
 }
 private fun text(s:String,x:Float,y:Float,scale:Float=.16f){
  font.data.setScale(scale);font.draw(batch,s,x,y)
 }
 override fun render(){
  val p=plays[index]
  val b=ball(p,time)
  if(Gdx.input.justTouched()){
   val touch=cam.unproject(Vector3(Gdx.input.x.toFloat(),Gdx.input.y.toFloat(),0f))
   if(touch.y<10f){
    when{
     touch.x<24f->paused=!paused
     touch.x<48f->speed=if(speed>=4f)1f else speed*2f
     touch.x<72f->{time=0f;paused=false}
     touch.x<96f->{index=(index+1).coerceAtMost(plays.lastIndex);time=0f;paused=false}
     else->follow=!follow
    }
   }else if(touch.y>64f&&touch.x>105f)labels=!labels
  }
  if(!paused){
   time+=Gdx.graphics.deltaTime.coerceIn(0f,.05f)*speed
   if(time>=duration){
    time=0f
    if(index<plays.lastIndex)index++ else paused=true
   }
  }
  val active=plays[index]
  val seconds=active.elapsed.coerceIn(0,3599)
  val q=(seconds/900+1).coerceIn(1,4)
  val left=900-seconds%900
  val total=active.awayScore+active.homeScore
  val target=if(follow)ball(active,time).x else 60f
  cameraX=MathUtils.lerp(cameraX,target.coerceIn(36f,84f),.07f)
  Gdx.gl.glClearColor(.025f,.06f,.1f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
  cam.update();shapes.projectionMatrix=cam.combined;batch.projectionMatrix=cam.combined
  shapes.begin(ShapeRenderer.ShapeType.Filled)
  shapes.color=navy;shapes.rect(0f,64f,120f,11f);shapes.rect(0f,0f,120f,11f)
  shapes.color=Color(.06f,.27f,.16f,1f);shapes.rect(0f,11f,120f,53f)
  for(x in 10..105 step 10){
   shapes.color=if(x%20==0)Color(.075f,.31f,.18f,1f) else Color(.055f,.27f,.15f,1f)
   shapes.rect(x.toFloat(),11f,10f,53f)
  }
  shapes.color=awayColor;shapes.rect(0f,11f,10f,53f)
  shapes.color=homeColor;shapes.rect(110f,11f,10f,53f)
  shapes.color=Color(.05f,.1f,.18f,1f)
  shapes.rect(1f,65f,31f,9f);shapes.rect(88f,65f,31f,9f)
  shapes.color=awayColor;shapes.rect(1f,65f,2f,9f)
  shapes.color=homeColor;shapes.rect(117f,65f,2f,9f)
  shapes.color=Color(.08f,.14f,.22f,1f);shapes.rect(34f,65f,52f,9f)
  shapes.color=Color(.10f,.17f,.27f,1f)
  for(i in 0..4)shapes.rect(2f+i*24f,1f,22f,8f)
  val attack=if(active.away)awayColor else homeColor
  val defend=if(active.away)homeColor else awayColor
  for(i in 0 until 22){
   val v=position(i,time,active)
   shapes.color=Color(0f,0f,0f,.35f);shapes.ellipse(v.x-.8f,v.y-1.25f,1.6f,.7f)
   shapes.color=if(i<11)attack else defend
   shapes.rect(v.x-.75f,v.y-.65f,1.5f,1.3f)
   shapes.color=Color(.86f,.87f,.88f,1f);shapes.circle(v.x,v.y+.8f,.62f,14)
   shapes.color=white;shapes.rect(v.x-.18f,v.y-.12f,.36f,.45f)
  }
  val ball=ball(active,time);shapes.color=gold;shapes.ellipse(ball.x-.65f,ball.y-.34f,1.3f,.68f)
  shapes.end()
  shapes.begin(ShapeRenderer.ShapeType.Line)
  shapes.color=Color(.85f,.95f,.9f,.55f)
  for(x in 10..110 step 5)shapes.line(x.toFloat(),11f,x.toFloat(),64f)
  for(x in 11..109)if(x%5!=0){
   shapes.line(x.toFloat(),30f,x.toFloat(),31f)
   shapes.line(x.toFloat(),44f,x.toFloat(),45f)
  }
  shapes.color=Color.CYAN;val x=los(active);shapes.line(x,11f,x,64f)
  shapes.color=gold;val first=(x+direction(active)*active.distance).coerceIn(10f,110f)
  shapes.line(first,11f,first,64f)
  shapes.end()
  batch.begin()
  text(away.take(4),5f,71.5f,.29f);text("${active.awayScore}",23f,71.5f,.30f)
  text("Q$q ${left/60}:${(left%60).toString().padStart(2,'0')}",39f,71.5f,.23f)
  text("${active.down} & ${active.distance}",39f,67.5f,.20f)
  text(home.take(4),90f,71.5f,.29f);text("${active.homeScore}",110f,71.5f,.30f)
  text("TOTAL $total  O/U ${if(line>0f)line else "--"}  PLAY ${index+1}/${plays.size}",5f,66.1f,.15f)
  text(if(labels)"NAMES ON" else "NAMES OFF",107f,66.1f,.12f)
  for(x0 in 20..100 step 10){
   val n=if(x0<=60)x0-10 else 110-x0
   text("$n",x0.toFloat()-1.5f,54f,.20f)
   text("$n",x0.toFloat()-1.5f,20f,.20f)
  }
  if(labels){
   text("QB ${qb.substringAfterLast(' ').take(13)}",position(0,time,active).x-3f,position(0,time,active).y+3f,.12f)
   val actor=if(run(active))rb else wr
   val actorPos=position(runnerId(active),time,active)
   text(actor.substringAfterLast(' ').take(13),actorPos.x-3f,actorPos.y+3f,.12f)
  }
  text("${active.kind.take(29)}  ${active.yards} YD",33f,62f,.19f)
  text("SIMCAST 2.0  |  ${Gdx.graphics.framesPerSecond} FPS",5f,62f,.13f)
  text(if(paused)"PLAY" else "PAUSE",7f,5.5f,.22f)
  text("${speed.toInt()}x",31f,5.5f,.23f)
  text("REPLAY",53f,5.5f,.20f)
  text("NEXT",78f,5.5f,.22f)
  text(if(follow)"CAM ON" else "CAM OFF",100f,5.5f,.16f)
  batch.end()
 }
 override fun dispose(){shapes.dispose();batch.dispose();font.dispose()}
}
