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

/** Scripted animation only; no effect on Core predictions or actual play data. */
class SimcastGdxTestActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialize(TacticalPassingDemo(), AndroidApplicationConfiguration().apply {
            useImmersiveMode = false
            useWakelock = false
        })
    }
}

private class TacticalPassingDemo : ApplicationAdapter() {
    private lateinit var shapes: ShapeRenderer
    private lateinit var camera: OrthographicCamera
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private var elapsed = 0f
    private val offense = arrayOf(
        Vector2(46f, 26.6f), Vector2(43f, 22f),
        Vector2(50f, 18f), Vector2(50f, 22f), Vector2(50f, 26.6f),
        Vector2(50f, 31f), Vector2(50f, 35f),
        Vector2(49f, 5f), Vector2(49f, 48f),
        Vector2(49f, 12f), Vector2(49f, 41f)
    )
    private val defense = Array(11) { i ->
        Vector2(55f + (i % 3) * 3f, 5f + (i / 3) * 13f + (i % 3) * 2f)
    }
    private fun ease(t: Float): Float {
        val u = MathUtils.clamp(t, 0f, 1f)
        return u * u * (3f - 2f * u)
    }
    private fun player(i: Int, t: Float): Vector2 {
        val p = offense[i].cpy()
        val a = (t - 1.1f).coerceAtLeast(0f)
        when (i) {
            0 -> p.x -= 5f * ease(a / 2f)
            1 -> { p.x += 7f * ease(a / 3f); p.y += 3f * ease(a / 3f) }
            in 2..6 -> { p.x += 2f * ease(a / 1.5f); p.y += .3f * MathUtils.sin(a * 2f + i) }
            7 -> { p.x += 20f * ease(a / 5f); p.y += 10f * ease((a - 2f) / 2f) }
            8 -> { p.x += 19f * ease(a / 5f); p.y -= 7f * ease((a - 2.5f) / 2f) }
            9 -> { p.x += 13f * ease(a / 3.5f); p.y += 8f * ease((a - 2f) / 2f) }
            10 -> { p.x += 14f * ease(a / 3.5f); p.y -= 8f * ease((a - 2f) / 2f) }
        }
        return p
    }
    private fun defender(i: Int, t: Float): Vector2 {
        val p = defense[i].cpy()
        val a = (t - 1.1f).coerceAtLeast(0f)
        if (i < 5) p.x -= 5f * ease(a / 2f)
        else {
            val receiver = player(intArrayOf(7, 8, 9, 10, 7, 8)[i - 5], t)
            val pursuit = ease(a / 6f)
            p.x = MathUtils.lerp(p.x, receiver.x - 1.5f, pursuit)
            p.y = MathUtils.lerp(p.y, receiver.y + if (i % 2 == 0) 2f else -2f, pursuit)
        }
        return p
    }
    override fun create() {
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont().apply { data.setScale(.19f) }
        camera = OrthographicCamera()
        camera.setToOrtho(false, 120f, 53.3f)
    }
    override fun resize(width: Int, height: Int) {
        Gdx.gl.glViewport(0, 0, width, height)
        camera.setToOrtho(false, 120f, 53.3f)
    }
    override fun render() {
        elapsed += Gdx.graphics.deltaTime.coerceIn(0f, .05f)
        val t = elapsed % 11f
        Gdx.gl.glClearColor(.04f, .26f, .13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(.10f, .20f, .40f, 1f)
        shapes.rect(0f, 0f, 10f, 53.3f)
        shapes.rect(110f, 0f, 10f, 53.3f)
        for (i in 0 until 22) {
            val p = if (i < 11) player(i, t) else defender(i - 11, t)
            shapes.color = if (i < 11) Color.WHITE else Color(.13f, .59f, .96f, 1f)
            shapes.circle(p.x, p.y, 1.05f, 16)
        }
        val qb = player(0, t)
        val receiver = player(7, t)
        val ball = when {
            t < 1.1f -> Vector2(50f, 26.6f)
            t < 5.2f -> qb.cpy().add(.8f, 0f)
            t < 6.5f -> {
                val u = (t - 5.2f) / 1.3f
                Vector2(MathUtils.lerp(qb.x, receiver.x, u), MathUtils.lerp(qb.y, receiver.y, u) + 4f * MathUtils.sin(u * MathUtils.PI))
            }
            else -> receiver.cpy().add(.7f, 0f)
        }
        shapes.color = Color.ORANGE
        shapes.circle(ball.x, ball.y, .65f, 12)
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color.WHITE
        for (x in 10..110 step 5) shapes.line(x.toFloat(), 0f, x.toFloat(), 53.3f)
        shapes.color = Color.CYAN
        shapes.line(50f, 0f, 50f, 53.3f)
        shapes.color = Color.YELLOW
        shapes.line(60f, 0f, 60f, 53.3f)
        shapes.end()
        batch.begin()
        val phase = when {
            t < 1.1f -> "FORMATION"
            t < 5.2f -> "SNAP + ROUTES"
            t < 6.5f -> "PASS"
            t < 9.4f -> "RECEPTION + PURSUIT"
            else -> "RESET"
        }
        font.draw(batch, "SIMCAST V1.7 - TACTICAL DEMO", 12f, 51f)
        font.draw(batch, "$phase | ${Gdx.graphics.framesPerSecond} FPS", 12f, 48f)
        font.draw(batch, "ILLUSTRATIVE PLAY - NOT REAL TRACKING", 12f, 4f)
        batch.end()
    }
    override fun dispose() {
        shapes.dispose()
        batch.dispose()
        font.dispose()
    }
}
