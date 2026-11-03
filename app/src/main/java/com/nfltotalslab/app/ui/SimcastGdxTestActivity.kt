package com.nfltotalslab.app.ui

import android.os.Bundle
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import kotlin.math.sin
import kotlin.math.cos

/** Isolated rendering test: never instantiated inside Compose. */
class SimcastGdxTestActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(SimcastTestScene(), AndroidApplicationConfiguration().apply {
            useImmersiveMode = false
            useWakelock = false
        })
    }
}
private class SimcastTestScene : ApplicationAdapter() {
    private lateinit var shapes: ShapeRenderer
    private lateinit var camera: OrthographicCamera
    private var t = 0f
    override fun create() {
        shapes = ShapeRenderer()
        camera = OrthographicCamera()
        camera.setToOrtho(false, 120f, 53.3f)
    }
    override fun resize(width: Int, height: Int) {
        Gdx.gl.glViewport(0, 0, width, height)
        camera.setToOrtho(false, 120f, 53.3f)
    }
    override fun render() {
        t += Gdx.graphics.deltaTime.coerceIn(0f, .05f)
        Gdx.gl.glClearColor(.05f, .30f, .17f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(.12f,.20f,.43f,1f)
        shapes.rect(0f,0f,10f,53.3f)
        shapes.rect(110f,0f,10f,53.3f)
        for(i in 0 until 22) {
            val offense = i < 11
            val n = i % 11
            val x0 = if(offense) 49f - (n%3)*2f else 59f+(n%3)*3f
            val y0 = 5f + (n/3)*13f + (n%3)*2f
            val phase = (t % 5f)/5f
            val x = x0 + (if(offense) 14f else -7f)*phase + sin(t*1.3f+n)*.8f
            val y = y0 + sin(t*.9f+n*1.4f)*3f*phase
            shapes.color = if(offense) Color.WHITE else Color(.15f,.58f,.95f,1f)
            shapes.circle(x,y,1.1f,12)
        }
        shapes.color = Color.ORANGE
        shapes.circle(51f+14f*(t%5f)/5f,26f+sin(t*2f)*3f,.65f,12)
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE
        for(yard in 10..110 step 5) shapes.line(yard.toFloat(),0f,yard.toFloat(),53.3f)
        shapes.color = Color.CYAN
        shapes.line(55f,0f,55f,53.3f)
        shapes.color = Color.YELLOW
        shapes.line(65f,0f,65f,53.3f)
        shapes.end()
    }
    override fun dispose() { shapes.dispose() }
}
