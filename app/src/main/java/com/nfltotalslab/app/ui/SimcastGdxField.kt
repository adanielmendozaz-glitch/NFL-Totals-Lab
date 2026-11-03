package com.nfltotalslab.app.ui

import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.Color
import kotlin.math.sin
import kotlin.math.cos

private class FootballScene : ApplicationAdapter() {
    private lateinit var shapes: ShapeRenderer
    private lateinit var camera: OrthographicCamera
    private var elapsed = 0f
    override fun create() {
        shapes = ShapeRenderer()
        camera = OrthographicCamera().apply { setToOrtho(false, 120f, 53.3f) }
    }
    override fun resize(width: Int, height: Int) {
        Gdx.gl.glViewport(0, 0, width, height)
        camera.setToOrtho(false, 120f, 53.3f)
    }
    override fun render() {
        elapsed += Gdx.graphics.deltaTime.coerceAtMost(.05f)
        Gdx.gl.glClearColor(.055f, .29f, .17f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        shapes.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(.10f,.19f,.42f,1f)
        shapes.rect(0f,0f,10f,53.3f)
        shapes.rect(110f,0f,10f,53.3f)
        val progress = (elapsed % 6f) / 6f
        for (i in 0 until 22) {
            val offense = i < 11
            val n = i % 11
            val row = (n / 6).toFloat()
            val col = (n % 6).toFloat()
            val x0 = if(offense) 52f - row * 5f else 59f + row * 5f
            val y0 = 6f + col * 8f + row * 3f
            val dx = if(offense) (if(n < 7) 3f else 17f) * progress else -5f * progress
            val dy = if(n > 6) sin(progress * 3.14159f + n) * 6f else 0f
            shapes.color = if(offense) Color.WHITE else Color(.16f,.56f,.95f,1f)
            shapes.circle(x0 + dx, y0 + dy, 1.35f, 12)
        }
        shapes.color = Color(.93f,.62f,.20f,1f)
        shapes.ellipse(49f + progress * 18f, 26f + sin(progress * 3.14159f)*8f, 1.8f, 1f)
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color(1f,1f,1f,.65f)
        for (yard in 10..110 step 5) shapes.line(yard.toFloat(),0f,yard.toFloat(),53.3f)
        shapes.color = Color.CYAN
        shapes.line(55f,0f,55f,53.3f)
        shapes.color = Color.YELLOW
        shapes.line(65f,0f,65f,53.3f)
        shapes.end()
    }
    override fun dispose() { shapes.dispose() }
}

/** Standalone libGDX rendering prototype. Not linked to the prediction or game play state. */
@Composable
fun SimcastGdxPrototype() {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        factory = { context ->
            val host = object : AndroidApplication() {}
            val config = AndroidApplicationConfiguration().apply {
                useImmersiveMode = false
                useWakelock = false
            }
            host.initializeForView(FootballScene(), config)
        }
    )
}
