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

class SimcastGdxTestActivity:AndroidApplication(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  initialize(Stadium21(
   intent.getStringExtra("away")?:"AWAY",intent.getStringExtra("home")?:"HOME",
   intent.getStringExtra("plays")?:"[]",intent.getDoubleExtra("line",0.0).toFloat(),
   intent.getStringExtra("qb")?:"QB",intent.getStringExtra("receiver")?:"WR",
   intent.getStringExtra("runner")?:"RB",intent.getIntExtra("awayColor",0xFF006778.toInt()),
   intent.getIntExtra("homeColor",0xFF241773.toInt())
  ),AndroidApplicationConfiguration().apply{useImmersiveMode=true})
 }
}
private data class Snap(val away:Boolean,val start:Int,val finish:Int,val down:Int,val distance:Int,
 val elapsed:Int,val kind:String,val yards:Int,val awayScore:Int,val homeScore:Int)
private class Stadium21(
 val away:String,val home:String,raw:String,val line:Float,val qb:String,val wr:String,val rb:String,
 awayArgb:Int,homeArgb:Int
):ApplicationAdapter(){
 private val plays=mutableListOf<Snap>()
 private lateinit var shapes:ShapeRenderer
 private lateinit var batch:SpriteBatch
 private lateinit var font:BitmapFont
 private lateinit var cam:OrthographicCamera
 private var index=0
 private var time=0f
 private var paused=false
 private var speed=1f
 private var labels=false
 private var follow=true
 private var cameraX=60f
 private val duration=6f
 private val navy=Color(.025f,.048f,.085f,1f)
 private val white=Color(.95f,.97f,1f,1f)
 private val gold=Color(.98f,.77f,.24f,1f)
 private val cyan=Color(.20f,.77f,.98f,1f)
 private val awayColor=Color((awayArgb shr 16 and 255)/255f,(awayArgb shr 8 and 255)/255f,(awayArgb and 255)/255f,1f)
 private val homeColor=Color((homeArgb shr 16 and 255)/255f,(homeArgb shr 8 and 255)/255f,(homeArgb and 255)/255f,1f)
 private val lanes=floatArrayOf(26.5f,21f,19f,23f,26.5f,30f,34f,7f,46f,13f,40f)
 init{
  try{val a=JSONArray(raw);for(i in 0 until a.length()){
   val o=a.getJSONObject(i)
   plays.add(Snap(o.optBoolean("a"),o.optInt("s",25),o.optInt("f",30),o.optInt("d",1),
    o.optInt("g",10),o.optInt("t"),o.optString("k","Carrera"),o.optInt("y"),
    o.optInt("as"),o.optInt("hs")))
  }}catch(_:Exception){}
  if(plays.isEmpty())plays.add(Snap(true,25,32,1,10,25,"Carrera",7,0,0))
 }
 override fun create(){
  shapes=ShapeRenderer();batch=SpriteBatch()
  font=BitmapFont().apply{data.setScale(.16f);color=white}
  cam=OrthographicCamera();cam.setToOrtho(false,120f,75f)
 }
 override fun resize(w:Int,h:Int){Gdx.gl.glViewport(0,0,w,h);cam.setToOrtho(false,120f,75f)}
 private fun ease(t:Float):Float{val x=MathUtils.clamp(t,0f,1f);return x*x*(3f-2f*x)}
 private fun run(p:Snap)=p.kind.contains("Carrera",true)||p.kind.contains("Fumble",true)
 private fun dir(p:Snap)=if(p.away)1f else -1f
 private fun los(p:Snap)=if(p.away)10f+p.start else 110f-p.start
 private fun end(p:Snap)=if(p.away)10f+p.finish else 110f-p.finish
 private fun carrier(p:Snap)=if(run(p))1 else 7+(index%4)
 private fun start(i:Int,p:Snap):Vector2{
  val d=dir(p);val x=los(p)
  if(i<11){
   val formation=index%4
   val depth=when(i){0->if(formation==1)-6f else -4f;1->if(formation==2)-5f else -7f;in 2..6->-.8f;else->-1.4f}
   val y=when{
    formation==3&&i==9->35f
    formation==2&&i==1->30f
    else->lanes[i]
   }
   return Vector2(x+d*depth,y+11f)
  }
  val n=i-11
  val y=when(n){0->19f;1->23f;2->30f;3->34f;4->13f;5->26.5f;6->40f;7->7f;8->46f;9->17f;else->37f}
  val depth=if(n<4)3.3f else if(n<7)7f else 13f
  return Vector2(x+d*depth,y+11f)
 }
 private fun pos(i:Int,t:Float,p:Snap):Vector2{
  val v=start(i,p);val d=dir(p);val a=(t-1f).coerceAtLeast(0f)
  if(i<11)when(i){
   0->{v.x-=d*3f*ease(a/1.6f);if(p.kind.contains("Sack",true))v.x=los(p)+d*p.yards*ease(a/3f)}
   1->if(run(p)){
    v.x=MathUtils.lerp(v.x,end(p),ease(a/3.9f))
    v.y+=(index%3-1)*2.5f*ease(a/3f)
   }else v.x+=d*4f*ease(a/3f)
   in 2..6->{v.x+=d*2f*ease(a/1.5f);v.y+=MathUtils.sin(a*2.8f+i)*.23f}
   else->{
    val route=i-7
    val bend=when((index+route)%5){0->-8f;1->8f;2->-3f;3->4f;else->0f}
    v.x+=d*(10f+route*3f)*ease(a/3.5f)
    v.y+=bend*ease((a-1.2f)/1.9f)
    if(i==carrier(p))v.x=MathUtils.lerp(v.x,end(p),ease((a-2.1f)/2.1f))
   }
  }else{
   val n=i-11
   if(n<4)v.x-=d*3.5f*ease(a/1.8f)
   else{
    val target=pos(if(n<7)1 else 7+(n-7)%4,t,p)
    v.x=MathUtils.lerp(v.x,target.x-d*1.5f,ease(a/4.6f))
    v.y=MathUtils.lerp(v.y,target.y+(if(n%2==0)2.5f else -2.5f),ease(a/4.6f))
   }
  }
  v.x=v.x.coerceIn(10.9f,109.1f)
  v.y=v.y.coerceIn(13.5f,61.5f)
  return v
 }
 private fun ball(p:Snap,t:Float):Vector2{
  if(t<1f)return start(4,p)
  if(run(p))return pos(if(t<2f)0 else 1,t,p)
  if(t<2.5f)return pos(0,t,p)
  if(t<3.7f){
   val a=pos(0,2.5f,p);val b=pos(carrier(p),3.7f,p);val u=ease((t-2.5f)/1.2f)
   return Vector2(MathUtils.lerp(a.x,b.x,u),MathUtils.lerp(a.y,b.y,u)+3f*MathUtils.sin(u*MathUtils.PI))
  }
  return pos(carrier(p),t,p)
 }
 private fun txt(s:String,x:Float,y:Float,size:Float=.16f){
  font.data.setScale(size);font.draw(batch,s,x,y)
 }
 override fun render(){
  if(Gdx.input.justTouched()){
   val tap=cam.unproject(Vector3(Gdx.input.x.toFloat(),Gdx.input.y.toFloat(),0f))
   if(tap.y<10f)when{
    tap.x<24f->paused=!paused
    tap.x<48f->speed=if(speed>=4f)1f else speed*2f
    tap.x<72f->{time=0f;paused=false}
    tap.x<96f->{index=(index+1).coerceAtMost(plays.lastIndex);time=0f;paused=false}
    else->follow=!follow
   }else if(tap.y>64f&&tap.x>105f)labels=!labels
  }
  if(!paused){
   time+=Gdx.graphics.deltaTime.coerceIn(0f,.05f)*speed
   if(time>=duration){time=0f;if(index<plays.lastIndex)index++ else paused=true}
  }
  val p=plays[index];val elapsed=p.elapsed.coerceIn(0,3599)
  val q=(elapsed/900+1).coerceIn(1,4);val remain=900-elapsed%900
  val attack=if(p.away)awayColor else homeColor
  val defense=if(p.away)homeColor else awayColor
  val bx=ball(p,time).x
  cameraX=MathUtils.lerp(cameraX,(if(follow)bx else 60f).coerceIn(35f,85f),.08f)
  Gdx.gl.glClearColor(.02f,.04f,.07f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
  cam.update();shapes.projectionMatrix=cam.combined;batch.projectionMatrix=cam.combined
  shapes.begin(ShapeRenderer.ShapeType.Filled)
  shapes.color=navy;shapes.rect(0f,64f,120f,11f);shapes.rect(0f,0f,120f,11f)
  shapes.color=Color(.06f,.27f,.16f,1f);shapes.rect(0f,11f,120f,53f)
  for(x in 10..100 step 10){
   shapes.color=if(x%20==0)Color(.075f,.32f,.19f,1f) else Color(.06f,.29f,.17f,1f)
   shapes.rect(x.toFloat(),11f,10f,53f)
  }
  shapes.color=awayColor;shapes.rect(0f,11f,10f,53f)
  shapes.color=homeColor;shapes.rect(110f,11f,10f,53f)
  shapes.color=Color(.07f,.11f,.18f,1f)
  shapes.rect(1f,65f,35f,9f);shapes.rect(37f,65f,46f,9f);shapes.rect(84f,65f,35f,9f)
  shapes.color=awayColor;shapes.rect(1f,65f,2f,9f)
  shapes.color=homeColor;shapes.rect(117f,65f,2f,9f)
  shapes.color=Color(.09f,.16f,.24f,1f)
  for(i in 0..4)shapes.rect(2f+i*24f,1f,22f,8f)
  for(i in 0 until 22){
   val v=pos(i,time,p)
   val team=if(i<11)attack else defense
   val running=time>1f
   val stride=if(running)MathUtils.sin(time*10f+i)*.38f else 0f
   shapes.color=Color(0f,0f,0f,.32f);shapes.ellipse(v.x-.95f,v.y-1.2f,1.9f,.65f)
   shapes.color=Color(.16f,.19f,.23f,1f)
   shapes.rect(v.x-.62f,v.y-1.05f+stride,.42f,.75f)
   shapes.rect(v.x+.2f,v.y-1.05f-stride,.42f,.75f)
   shapes.color=team
   shapes.rect(v.x-.72f,v.y-.3f,1.44f,1.3f)
   shapes.rect(v.x-1.05f,v.y-.1f,.35f,.9f)
   shapes.rect(v.x+.7f,v.y-.1f,.35f,.9f)
   shapes.color=white
   shapes.circle(v.x,v.y+1.0f,.68f,16)
   shapes.color=team
   shapes.rect(v.x-.6f,v.y+1.1f,1.2f,.23f)
   shapes.color=white
   shapes.rect(v.x-.12f,v.y+.08f,.24f,.55f)
  }
  val b=ball(p,time);shapes.color=gold;shapes.ellipse(b.x-.5f,b.y-.24f,1f,.48f)
  shapes.end()
  shapes.begin(ShapeRenderer.ShapeType.Line)
  shapes.color=Color(.86f,.94f,.89f,.55f)
  for(x in 10..110 step 5)shapes.line(x.toFloat(),11f,x.toFloat(),64f)
  for(x in 11..109)if(x%5!=0){
   shapes.line(x.toFloat(),30f,x.toFloat(),31f);shapes.line(x.toFloat(),44f,x.toFloat(),45f)
  }
  shapes.color=cyan;shapes.line(los(p),11f,los(p),64f)
  shapes.color=gold;val first=(los(p)+dir(p)*p.distance).coerceIn(10f,110f)
  shapes.line(first,11f,first,64f)
  shapes.end()
  batch.begin()
  txt(away.take(4),5f,71f,.29f);txt("${p.awayScore}",26f,71f,.30f)
  txt("Q$q  ${remain/60}:${(remain%60).toString().padStart(2,'0')}",43f,71f,.23f)
  txt("${p.down} & ${p.distance}",46f,67.3f,.20f)
  txt(home.take(4),90f,71f,.29f);txt("${p.homeScore}",111f,71f,.30f)
  txt("TOTAL ${p.awayScore+p.homeScore}    O/U ${if(line>0f)line else "--"}    PLAY ${index+1}/${plays.size}",5f,66.2f,.15f)
  txt(if(labels)"LABELS ON" else "LABELS OFF",107f,66.2f,.11f)
  for(x in 20..100 step 10){
   val n=if(x<=60)x-10 else 110-x
   txt("$n",x.toFloat()-1.4f,54f,.19f);txt("$n",x.toFloat()-1.4f,20f,.19f)
  }
  if(labels){
   val a=pos(0,time,p);txt(qb.substringAfterLast(' ').take(12),a.x-3f,(a.y+3f).coerceAtMost(61f),.12f)
   val v=pos(carrier(p),time,p);txt((if(run(p))rb else wr).substringAfterLast(' ').take(12),v.x-3f,(v.y+3f).coerceAtMost(61f),.12f)
  }
  txt("${p.kind.take(28)}   ${p.yards} YD",42f,62.7f,.17f)
  txt("SIMCAST V2.1   ${Gdx.graphics.framesPerSecond} FPS",5f,62.7f,.13f)
  txt(if(paused)"PLAY" else "PAUSE",7f,5.5f,.21f)
  txt("${speed.toInt()}x",33f,5.5f,.23f)
  txt("REPLAY",54f,5.5f,.20f);txt("NEXT",79f,5.5f,.22f)
  txt(if(follow)"FOLLOW" else "FIXED",102f,5.5f,.17f)
  batch.end()
 }
 override fun dispose(){shapes.dispose();batch.dispose();font.dispose()}
}
