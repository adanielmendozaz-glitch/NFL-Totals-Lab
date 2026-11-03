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

class SimcastGdxTestActivity:AndroidApplication(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  initialize(MatchScene(intent.getStringExtra("away")?:"AWAY",intent.getStringExtra("home")?:"HOME",
   intent.getStringExtra("playKind")?:"Pase completo",intent.getIntExtra("yards",12),
   intent.getIntExtra("down",1),intent.getIntExtra("distance",10),
   intent.getStringExtra("qb")?:"QB",intent.getStringExtra("receiver")?:"WR"),AndroidApplicationConfiguration())
 }
}
private class MatchScene(val away:String,val home:String,val kind:String,val yards:Int,val down:Int,val distance:Int,val qb:String,val receiver:String):ApplicationAdapter(){
 private lateinit var shapes:ShapeRenderer
 private lateinit var batch:SpriteBatch
 private lateinit var font:BitmapFont
 private lateinit var camera:OrthographicCamera
 private var time=0f
 private val offense=Array(11){Vector2()}
 private val defense=Array(11){Vector2()}
 override fun create(){
  shapes=ShapeRenderer();batch=SpriteBatch()
  font=BitmapFont().apply{data.setScale(.16f)}
  camera=OrthographicCamera();camera.setToOrtho(false,120f,53.3f)
  val ys=floatArrayOf(26f,20f,19f,23f,26f,30f,34f,5f,48f,12f,41f)
  for(i in 0..10)offense[i].set(if(i==0)45f else if(i==1)43f else 49f,ys[i])
  for(i in 0..10)defense[i].set(if(i<4)54f else if(i<7)60f else 66f,5f+i*4.3f)
 }
 override fun resize(width:Int,height:Int){Gdx.gl.glViewport(0,0,width,height);camera.setToOrtho(false,120f,53.3f)}
 private fun smooth(t:Float):Float{val u=MathUtils.clamp(t,0f,1f);return u*u*(3f-2f*u)}
 private fun player(i:Int,t:Float):Vector2{
  val a=(t-1.4f).coerceAtLeast(0f)
  val run=kind.contains("Carrera",true)
  val p=if(i<11)offense[i].cpy() else defense[i-11].cpy()
  if(i<11)when(i){
   0->p.x-=4f*smooth(a/2f)
   1->{p.x+=(if(run)16f else 5f)*smooth(a/4f);p.y+=4f*smooth(a/3f)}
   in 2..6->{p.x+=2f*smooth(a/1.5f);p.y+=MathUtils.sin(a*2f+i)*.3f}
   7->{p.x+=22f*smooth(a/5f);p.y+=10f*smooth((a-2f)/2f)}
   8->{p.x+=23f*smooth(a/5f);p.y-=8f*smooth((a-2f)/2f)}
   9->{p.x+=15f*smooth(a/4f);p.y+=8f*smooth((a-2f)/2f)}
   else->{p.x+=14f*smooth(a/4f);p.y-=8f*smooth((a-2f)/2f)}
  }else{
   val n=i-11
   if(n<4)p.x-=5f*smooth(a/2f)
   else{
    val target=player(intArrayOf(1,0,10,7,8,9,10)[n-4],t)
    p.x=MathUtils.lerp(p.x,target.x-1f,smooth(a/7f))
    p.y=MathUtils.lerp(p.y,target.y+2f,smooth(a/7f))
   }
  }
  return p
 }
 override fun render(){
  time+=Gdx.graphics.deltaTime.coerceIn(0f,.05f)
  val t=time%12f
  Gdx.gl.glClearColor(.05f,.27f,.14f,1f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
  camera.update();shapes.projectionMatrix=camera.combined;batch.projectionMatrix=camera.combined
  shapes.begin(ShapeRenderer.ShapeType.Filled)
  shapes.color=Color(.12f,.2f,.42f,1f)
  shapes.rect(0f,0f,10f,53.3f);shapes.rect(110f,0f,10f,53.3f)
  for(i in 0 until 22){
   val p=player(i,t)
   shapes.color=if(i<11)Color.WHITE else Color(.15f,.58f,.98f,1f)
   shapes.circle(p.x,p.y,1.05f,20)
  }
  val run=kind.contains("Carrera",true)
  val ball=if(run)player(if(t<3f)0 else 1,t) else if(t<5.5f)player(0,t) else if(t<7f){
   val u=smooth((t-5.5f)/1.5f)
   val a=player(0,5.5f);val b=player(7,7f)
   Vector2(MathUtils.lerp(a.x,b.x,u),MathUtils.lerp(a.y,b.y,u)+4f*MathUtils.sin(u*MathUtils.PI))
  }else player(7,t)
  shapes.color=Color.ORANGE;shapes.circle(ball.x+.5f,ball.y,.65f,14)
  shapes.end()
  shapes.begin(ShapeRenderer.ShapeType.Line)
  shapes.color=Color.WHITE
  for(x in 10..110 step 5)shapes.line(x.toFloat(),0f,x.toFloat(),53.3f)
  for(x in 11..109)if(x%5!=0){shapes.line(x.toFloat(),19f,x.toFloat(),20f);shapes.line(x.toFloat(),33f,x.toFloat(),34f)}
  shapes.color=Color.CYAN;shapes.line(50f,0f,50f,53.3f)
  shapes.color=Color.YELLOW;shapes.line((50+distance.coerceIn(1,25)).toFloat(),0f,(50+distance.coerceIn(1,25)).toFloat(),53.3f)
  shapes.end()
  batch.begin()
  val phase=when{t<1.4f->"FORMATION";t<5.5f->"SNAP / ROUTES";t<7f->"BALL IN FLIGHT";t<10f->"CATCH / PURSUIT";else->"RESET"}
  font.draw(batch,"SIMCAST V1.8  |  $away @ $home",11f,51f)
  font.draw(batch,"$phase  |  ${Gdx.graphics.framesPerSecond} FPS",11f,48f)
  font.draw(batch,"$down & $distance  |  $kind  |  $yards YD",11f,45f)
  font.draw(batch,"QB ${qb.take(22)}  /  WR ${receiver.take(22)}",11f,5f)
  for(x in 20..100 step 10){val n=if(x<=60)x-10 else 110-x;font.draw(batch,"$n",x.toFloat()-1f,39f)}
  batch.end()
 }
 override fun dispose(){shapes.dispose();batch.dispose();font.dispose()}
}
