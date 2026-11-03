package com.nfltotalslab.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.content.Intent
import android.app.Activity
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import org.json.JSONArray
import org.json.JSONObject

class SimcastGdxTestActivity : AndroidApplication() {
    // SIMCAST_JSON_EXPORT_V1: Storage Access Framework, no storage permission needed.
    private var exportJson: String = ""
    private var exportName: String = "simcast_report.json"
    private val exportRequestCode = 8341

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == exportRequestCode && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exportJson.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("No se pudo abrir el archivo")
                android.widget.Toast.makeText(this, "JSON exportado correctamente", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Error exportando JSON: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportReport() {
        if (exportJson.isBlank()) {
            android.widget.Toast.makeText(this, "Reporte no disponible", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val picker = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, exportName)
        }
        startActivityForResult(picker, exportRequestCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val simcastRaw = intent.getStringExtra("plays") ?: "[]"
        val simcastStats = SimcastStats24(intent.getStringExtra("away") ?: "AWAY",
            intent.getStringExtra("home") ?: "HOME", simcastRaw,
            intent.getStringExtra("simcastRoster") ?: "{}",
            intent.getStringExtra("simcastGameKey") ?: "unknown", intent.getStringExtra("simcastStrengths") ?: "{}")
        try {
            val key = (intent.getStringExtra("simcastGameKey") ?: "game").replace(Regex("[^A-Za-z0-9_-]"),"_")
            exportName = "${key}_simcast_v4.json"
            exportJson = simcastStats.report(org.json.JSONArray(simcastRaw).length())
                .put("monte_carlo_props",org.json.JSONArray(intent.getStringExtra("simcastProps") ?: "[]"))
                // V4.4 DRIVE ENGINE: audit the actual profiles used.
                .put("nflverse_profile",JSONObject(intent.getStringExtra("simcastNflverse") ?: "{}"))
                .put("usage_audit",SimcastUsageAudit.build(simcastStats.report(org.json.JSONArray(simcastRaw).length()).getJSONArray("events")))
                .put("usage_model_status","PENDING_2026_PLAYER_STATS")
                .put("engine_version","V5-phase2-usage-audit-experimental")
                .put("export_schema", "simcast-json-v2")
                .toString(2)
        } catch (e: Exception) {
            exportJson = org.json.JSONObject().put("error", e.message ?: "Error de reporte").toString(2)
        }
        try {
            val dir = java.io.File(filesDir,"simcast_reports").apply { mkdirs() }
            val key = (intent.getStringExtra("simcastGameKey") ?: "game").replace(Regex("[^A-Za-z0-9_-]"),"_")
            java.io.File(dir,"${key}_v30.json").writeText(simcastStats.report(org.json.JSONArray(simcastRaw).length())
                .put("monte_carlo_props",org.json.JSONArray(intent.getStringExtra("simcastProps") ?: "[]")).toString(2))
        } catch (_: Exception) { }
        initialize(Broadcast22(
            intent.getStringExtra("away") ?: "AWAY",
            intent.getStringExtra("home") ?: "HOME",
            simcastRaw,
            intent.getDoubleExtra("line", 0.0).toFloat(),
            intent.getStringExtra("qb") ?: "QB",
            intent.getStringExtra("receiver") ?: "WR",
            intent.getStringExtra("runner") ?: "RB",
            intent.getIntExtra("awayColor", 0xFF006778.toInt()),
            intent.getIntExtra("homeColor", 0xFF241773.toInt()), simcastStats,
            intent.getStringExtra("simcastProps") ?: "[]",
            intent.getStringExtra("simcastStrengths") ?: "{}"
        ), AndroidApplicationConfiguration().apply { useImmersiveMode = true })
        // Native Android overlay: always accessible, including when the game is paused.
        val exportButton = Button(this).apply {
            text = "EXPORTAR JSON"
            textSize = 11f
            setOnClickListener { exportReport() }
        }
        val params = FrameLayout.LayoutParams(
            (150 * resources.displayMetrics.density).toInt(),
            (48 * resources.displayMetrics.density).toInt(),
            Gravity.TOP or Gravity.END
        ).apply { topMargin = (75 * resources.displayMetrics.density).toInt() }
        addContentView(exportButton, params)
    }
}

private data class SimSnap(
    val away: Boolean, val start: Int, val finish: Int, val down: Int,
    val distance: Int, val elapsed: Int, val kind: String, val yards: Int,
    val awayScore: Int, val homeScore: Int
)

private class Broadcast22(
    private val away: String, private val home: String, raw: String,
    private val line: Float, private val qb: String, private val wr: String,
    private val rb: String, awayArgb: Int, homeArgb: Int, private val stats: SimcastStats24,
    propsRaw: String, strengthsRaw: String
) : ApplicationAdapter() {
    private val plays = mutableListOf<SimSnap>()
    private val propRows = try { JSONArray(propsRaw) } catch (_:Exception) { JSONArray() }
    private val teamStrengths = try { JSONObject(strengthsRaw) } catch (_:Exception) { JSONObject() }
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private var hud: Texture? = null
    private var controls: Texture? = null
    private var yardNumbers: Texture? = null
    private var qbTag: Texture? = null
    private var skillTag: Texture? = null
    private var statsTexture: Texture? = null
    private var statsKey = ""
    private var showStats = false
    private var reportVisibleUntil = 0
    private var hudKey = ""
    private var controlsKey = ""
    private var index = 0
    private var phase = 0f
    private var paused = false
    private var speed = 1f
    private var labels = false
    private var follow = true
    private val duration = 6f
    private val awayAccent = awayArgb
    private val homeAccent = homeArgb
    private val awayColor = colorOf(awayArgb)
    private val homeColor = colorOf(homeArgb)
    private val white = Color(.96f, .98f, 1f, 1f)
    private val gold = Color(.99f, .78f, .25f, 1f)
    private val lanes = floatArrayOf(26.5f, 21f, 19f, 23f, 26.5f, 30f, 34f, 7f, 46f, 13f, 40f)

    init {
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                plays.add(SimSnap(o.optBoolean("a"), o.optInt("s", 25), o.optInt("f", 30),
                    o.optInt("d", 1), o.optInt("g", 10), o.optInt("t"),
                    o.optString("k", "Carrera"), o.optInt("y"), o.optInt("as"), o.optInt("hs")))
            }
        } catch (_: Exception) { }
        if (plays.isEmpty()) plays.add(SimSnap(true, 25, 32, 1, 10, 25, "Carrera", 7, 0, 0))
    }

    override fun create() {
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        camera = OrthographicCamera().apply { setToOrtho(false, 120f, 75f) }
        yardNumbers = makeYardNumbers()
        qbTag = makeTag(qb.substringAfterLast(' ').take(12))
        skillTag = makeTag((if (rb != "RB") rb else wr).substringAfterLast(' ').take(12))
    }

    override fun resize(width: Int, height: Int) {
        Gdx.gl.glViewport(0, 0, width, height)
        camera.setToOrtho(false, 120f, 75f)
    }

    private fun ease(t: Float): Float {
        val u = MathUtils.clamp(t, 0f, 1f)
        return u * u * (3f - 2f * u)
    }
    private fun isRun(p: SimSnap) = p.kind.contains("Carrera", true) || p.kind.contains("Fumble", true)
    private fun direction(p: SimSnap) = if (p.away) 1f else -1f
    private fun los(p: SimSnap) = if (p.away) 10f + p.start else 110f - p.start
    private fun finish(p: SimSnap) = if (p.away) 10f + p.finish else 110f - p.finish
    private fun carrier(p: SimSnap) = if (isRun(p)) 1 else 7 + index % 4

    private fun start(i: Int, p: SimSnap): Vector2 {
        val d = direction(p)
        val x = los(p)
        if (i < 11) {
            val formation = index % 4
            val depth = when (i) {
                0 -> if (formation == 1) -6f else -4f
                1 -> if (formation == 2) -5f else -7f
                in 2..6 -> -.8f
                else -> -1.4f
            }
            val y = when {
                formation == 3 && i == 9 -> 35f
                formation == 2 && i == 1 -> 30f
                else -> lanes[i]
            }
            return Vector2(x + d * depth, y + 11f)
        }
        val n = i - 11
        val y = when (n) {
            0 -> 19f; 1 -> 23f; 2 -> 30f; 3 -> 34f; 4 -> 13f; 5 -> 26.5f
            6 -> 40f; 7 -> 7f; 8 -> 46f; 9 -> 17f; else -> 37f
        }
        return Vector2(x + d * (if (n < 4) 3.3f else if (n < 7) 7f else 13f), y + 11f)
    }

    // V2.3: animation phases. Coordinates are tied to the simulated play, not to an independent outcome.
    private fun playType(p: SimSnap): String = when {
        p.kind.contains("Intercep", true) -> "PICK"
        p.kind.contains("Sack", true) -> "SACK"
        p.kind.contains("incompleto", true) -> "INCOMPLETE"
        p.kind.contains("Touchdown", true) -> "TOUCHDOWN"
        p.kind.contains("gol de campo", true) -> "FIELD_GOAL"
        p.kind.contains("Despeje", true) -> "PUNT"
        isRun(p) -> "RUN"
        else -> "PASS"
    }
    private fun clampPlayer(v: Vector2): Vector2 {
        v.x = v.x.coerceIn(10.8f, 109.2f)
        v.y = v.y.coerceIn(12.5f, 59.5f)
        return v
    }
    private fun pos(i: Int, t: Float, p: SimSnap): Vector2 {
        val v = start(i, p)
        val d = direction(p)
        val a = (t - 1f).coerceAtLeast(0f)
        val kind = playType(p)
        val move = ease(a / 3.8f)
        val target = carrier(p)
        if (i < 11) when (i) {
            0 -> {
                v.x -= d * (if (kind == "SACK") 1.5f else 3.2f) * ease(a / 1.3f)
                if (kind == "SACK") v.x = MathUtils.lerp(v.x, finish(p), ease(a / 3.2f))
            }
            1 -> if (isRun(p)) {
                v.x = MathUtils.lerp(v.x, finish(p), move)
                v.y += ((index % 3) - 1) * 3f * ease(a / 2f)
            } else {
                v.x += d * 3.6f * ease(a / 2.4f)
            }
            in 2..6 -> {
                // The offensive line forms a pocket rather than drifting across the field.
                val pocket = if (i == 4) .7f else 1.8f
                v.x += d * pocket * ease(a / 1.4f)
                v.y += MathUtils.sin(a * 2.4f + i) * .25f
            }
            else -> {
                val route = i - 7
                val routeId = (index + route) % 5
                val depth = when (routeId) { 0 -> 9f; 1 -> 16f; 2 -> 24f; 3 -> 12f; else -> 20f }
                val cut = when (routeId) { 0 -> -9f; 1 -> 7f; 2 -> 0f; 3 -> 10f; else -> -7f }
                v.x += d * depth * ease(a / 3.6f)
                v.y += cut * ease((a - 1.1f) / 2.1f)
                if (i == target && kind in setOf("PASS", "TOUCHDOWN")) {
                    v.x = MathUtils.lerp(v.x, finish(p), ease((a - 2.0f) / 2.2f))
                }
            }
        } else {
            val n = i - 11
            if (n < 4) {
                v.x -= d * (if (kind == "SACK") 6f else 2.2f) * ease(a / 2f)
                v.y += MathUtils.sin(n.toFloat() * 2f + a) * .5f
            } else {
                val pursue = if (kind == "SACK") 0 else if (kind == "PICK" && n >= 7) target else if (isRun(p)) 1 else 7 + (n - 7).coerceAtLeast(0) % 4
                val focus = pos(pursue, t, p)
                val pursuit = ease((a - if (n >= 7) .7f else .2f) / 3.7f)
                v.x = MathUtils.lerp(v.x, focus.x - d * 1.1f, pursuit)
                v.y = MathUtils.lerp(v.y, focus.y + (if (n % 2 == 0) 1.6f else -1.6f), pursuit)
            }
        }
        return clampPlayer(v)
    }

    private fun ball(p: SimSnap, t: Float): Vector2 {
        val kind = playType(p)
        if (t < 1f) return start(4, p)
        if (kind == "RUN") return pos(if (t < 1.9f) 0 else 1, t, p)
        if (kind == "SACK") return pos(0, t, p)
        if (kind == "FIELD_GOAL" || kind == "PUNT") {
            val u = ease((t - 1.7f) / 2.7f)
            return Vector2(MathUtils.lerp(los(p), finish(p), u), 36f + 15f * MathUtils.sin(u * MathUtils.PI))
        }
        if (t < 2.3f) return pos(0, t, p)
        val from = pos(0, 2.3f, p)
        val to = when (kind) {
            "INCOMPLETE" -> Vector2(finish(p) + direction(p) * 7f, 48f)
            "PICK" -> pos(18, 3.8f, p)
            else -> pos(carrier(p), 3.8f, p)
        }
        val u = ease((t - 2.3f) / 1.5f)
        if (t < 3.8f) return Vector2(MathUtils.lerp(from.x, to.x, u),
            MathUtils.lerp(from.y, to.y, u) + 4.5f * MathUtils.sin(u * MathUtils.PI))
        return when (kind) {
            "INCOMPLETE" -> Vector2(to.x, to.y - 3f * ease((t - 3.8f) / 1f))
            "PICK" -> pos(18, t, p)
            else -> pos(carrier(p), t, p)
        }
    }

    // Android Canvas produces crisp, antialiased HUD text. Rebuild only when the play changes.
    private fun makeHud(p: SimSnap): Texture {
        val w = 1600
        val h = 256
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rect(color: Int, l: Float, t: Float, r: Float, b: Float) {
            paint.color = color; paint.style = Paint.Style.FILL
            c.drawRoundRect(l, t, r, b, 12f, 12f, paint)
        }
        fun text(s: String, x: Float, y: Float, size: Float, color: Int = android.graphics.Color.WHITE, bold: Boolean = true) {
            paint.color = color; paint.textSize = size
            paint.typeface = if (bold) Typeface.create("sans-serif-condensed", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            paint.style = Paint.Style.FILL
            c.drawText(s, x, y, paint)
        }
        val dark = android.graphics.Color.rgb(12, 21, 35)
        val panel = android.graphics.Color.rgb(24, 38, 58)
        val muted = android.graphics.Color.rgb(174, 195, 216)
        val amber = android.graphics.Color.rgb(255, 205, 99)
        rect(dark, 0f, 0f, 1600f, 256f)
        rect(panel, 16f, 14f, 472f, 156f)
        rect(panel, 488f, 14f, 1112f, 156f)
        rect(panel, 1128f, 14f, 1584f, 156f)
        rect(awayAccent, 16f, 14f, 27f, 156f)
        rect(homeAccent, 1573f, 14f, 1584f, 156f)
        // Offline shield-style team identifiers; official licensed logo files are not bundled.
        fun shield(code: String, x: Float, accent: Int) {
            rect(accent, x, 34f, x + 104f, 138f)
            rect(dark, x + 7f, 41f, x + 97f, 131f)
            text(code.take(3), x + 18f, 99f, 43f)
        }
        shield(away, 42f, awayAccent)
        shield(home, 1150f, homeAccent)
        text(away.take(4), 165f, 78f, 53f)
        text("${p.awayScore}", 355f, 119f, 88f)
        text(home.take(4), 1280f, 78f, 53f)
        text("${p.homeScore}", 1465f, 119f, 88f)
        val quarter = (p.elapsed.coerceIn(0, 3599) / 900 + 1).coerceIn(1, 4)
        val remaining = 900 - p.elapsed.coerceIn(0, 3599) % 900
        val clock = "%02d:%02d".format(remaining / 60, remaining % 60)
        text("Q$quarter", 540f, 77f, 43f, muted)
        text(clock, 658f, 85f, 70f)
        text("${p.down} & ${p.distance}", 922f, 77f, 45f, amber)
        text("POS ${if (p.away) away else home}", 541f, 132f, 33f, muted)
        text("SIMCAST V3.1 | PRO STADIUM", 38f, 204f, 30f, muted)
        val total = p.awayScore + p.homeScore
        val needed = if (line > 0f) kotlin.math.floor((line - total).toDouble()).toInt() + 1 else 0
        text("TOTAL  $total", 574f, 205f, 37f)
        text("O/U  ${if (line > 0f) line.toString() else "--"}", 827f, 205f, 37f, amber)
        text(if (line > 0f) "OVER: ${needed.coerceAtLeast(0)} PTS" else "LINEA NO DISPONIBLE", 1135f, 205f, 34f, muted)
        text("JUGADA ${index + 1}/${plays.size}", 38f, 243f, 24f, muted)
        text("${p.kind.take(30)}  ${p.yards} YD", 574f, 243f, 26f, muted)
        return textureFrom(bitmap)
    }

    private fun makeStatsPanel(until: Int): Texture {
        val bmp = Bitmap.createBitmap(1200, 650, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xF0091526.toInt()
        c.drawRoundRect(0f, 0f, 1200f, 650f, 24f, 24f, p)
        p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        p.color = android.graphics.Color.WHITE
        p.textSize = 40f
        c.drawText("V3.1 | PLAYER INTELLIGENCE - $until/${plays.size}", 34f, 55f, p)
        p.textSize = 26f
        p.color = 0xFFFFCD63.toInt()
        c.drawText("SIMULACION · NO SON RESULTADOS REALES", 34f, 94f, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 23f
        var y = 139f
        for (row in stats.summary(until).take(12)) {
            c.drawText(row.take(43), 35f, y, p)
            y += 35f
        }
        // A second column exposes the exact Monte Carlo projections sent by the Compose model.
        p.color = 0xFF70DBDB.toInt()
        p.textSize = 25f
        c.drawText("MONTE CARLO | 400 CORRIDAS (NO CALIBRADO)", 620f, 140f, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 22f
        for (i in 0 until kotlin.math.min(propRows.length(), 12)) {
            val v = propRows.optJSONObject(i) ?: continue
            val label = "${v.optString("team")} ${v.optString("player").take(15)} ${v.optString("market")}" 
            val statsLine = "MEDIA ${"%.1f".format(v.optDouble("mean"))} | P10 ${v.optInt("p10")} P50 ${v.optInt("p50")} P90 ${v.optInt("p90")}" 
            val yy = 175f + i * 35f
            c.drawText(label.take(48), 625f, yy, p)
            c.drawText(statsLine, 625f, yy + 18f, p.apply { textSize = 17f })
            p.textSize = 22f
        }
        p.textSize = 20f
        p.color = 0xFFB4C4D7.toInt()
        c.drawText("Datos locales: ${away} ${teamStrengths.optJSONObject(away)?.optInt("sample_games") ?: 0} juegos / ${home} ${teamStrengths.optJSONObject(home)?.optInt("sample_games") ?: 0} juegos", 34f, 592f, p)
        c.drawText("Cerrar arriba a la derecha | JSON en simcast_reports", 34f, 622f, p)
        return textureFrom(bmp)
    }

    private fun makeControls(): Texture {
        val w = 1200
        val h = 96
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val names = arrayOf(if (paused) "PLAY" else "PAUSE", "${speed.toInt()}x", "REPLAY", "NEXT", if (labels) "LABELS ON" else "LABELS OFF")
        for (i in names.indices) {
            val left = 8f + i * 240f
            paint.color = android.graphics.Color.rgb(24, 38, 58)
            c.drawRoundRect(left, 6f, left + 220f, 90f, 12f, 12f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            paint.textSize = 32f
            val width = paint.measureText(names[i])
            c.drawText(names[i], left + 110f - width / 2f, 61f, paint)
        }
        return textureFrom(bitmap)
    }

    private fun makeTag(label: String): Texture {
        val bitmap = Bitmap.createBitmap(260, 54, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xD510192A.toInt()
        c.drawRoundRect(0f, 0f, 260f, 54f, 10f, 10f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 32f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        c.drawText(label, 12f, 38f, paint)
        return textureFrom(bitmap)
    }

    private fun makeYardNumbers(): Texture {
        val bitmap = Bitmap.createBitmap(1200, 520, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0xE7FFFFFF.toInt()
        paint.textSize = 34f
        paint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        for (x in 20..100 step 10) {
            val n = if (x <= 60) x - 10 else 110 - x
            val t = n.toString()
            val px = x * 10f - paint.measureText(t) / 2f
            c.drawText(t, px, 90f, paint)
            c.drawText(t, px, 440f, paint)
        }
        return textureFrom(bitmap)
    }

    private fun textureFrom(bitmap: Bitmap): Texture {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val pixmap = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (y in 0 until h) for (x in 0 until w) {
            val v = pixels[y * w + x]
            pixmap.drawPixel(x, y, ((v shl 8) or ((v ushr 24) and 255)))
        }
        bitmap.recycle()
        val texture = Texture(pixmap)
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        pixmap.dispose()
        return texture
    }

    private fun colorOf(argb: Int) = Color((argb shr 16 and 255) / 255f,
        (argb shr 8 and 255) / 255f, (argb and 255) / 255f, 1f)

    override fun render() {
        if (Gdx.input.justTouched()) {
            val tap = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))
            if (tap.y > 62f && tap.x > 104f) showStats = !showStats
            else if (tap.y < 9f) when {
                tap.x < 24f -> paused = !paused
                tap.x < 48f -> speed = if (speed >= 4f) 1f else speed * 2f
                tap.x < 72f -> { phase = 0f; paused = false }
                tap.x < 96f -> { index = (index + 1).coerceAtMost(plays.lastIndex); phase = 0f; paused = false }
                else -> labels = !labels
            } else if (tap.y > 65f && tap.x > 105f) labels = !labels
        }
        if (!paused) {
            phase += Gdx.graphics.deltaTime.coerceIn(0f, .05f) * speed
            if (phase >= duration) {
                phase = 0f
                if (index < plays.lastIndex) index++ else paused = true
            }
        }
        val p = plays[index]
        val until = (index + 1).coerceAtMost(plays.size)
        if (index == plays.lastIndex && phase > duration - .2f) showStats = true
        if (showStats && statsKey != "$until") {
            statsTexture?.dispose()
            statsTexture = makeStatsPanel(until)
            statsKey = "$until"
        }
        val controlKey = "${paused}:${speed}:${labels}"
        if (controlKey != controlsKey) {
            controls?.dispose()
            controls = makeControls()
            controlsKey = controlKey
        }
        val key = "$index:${p.awayScore}:${p.homeScore}"
        if (hudKey != key) {
            hud?.dispose()
            hud = makeHud(p)
            hudKey = key
        }
        val attack = if (p.away) awayColor else homeColor
        val defense = if (p.away) homeColor else awayColor
        Gdx.gl.glClearColor(.02f, .04f, .07f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        camera.update()
        shapes.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(.055f, .27f, .16f, 1f)
        shapes.rect(0f, 10f, 120f, 52f)
        for (x in 10..100 step 10) {
            shapes.color = if (x % 20 == 0) Color(.075f, .32f, .19f, 1f) else Color(.06f, .29f, .17f, 1f)
            shapes.rect(x.toFloat(), 10f, 10f, 52f)
        }
        shapes.color = awayColor; shapes.rect(0f, 10f, 10f, 52f)
        shapes.color = homeColor; shapes.rect(110f, 10f, 10f, 52f)
        for (i in 0 until 22) {
            val v = pos(i, phase, p)
            val team = if (i < 11) attack else defense
            val stride = if (phase > 1f) MathUtils.sin(phase * 10f + i) * .38f else 0f
            shapes.color = Color(0f, 0f, 0f, .33f)
            shapes.ellipse(v.x - .95f, v.y - 1.2f, 1.9f, .65f)
            shapes.color = Color(.16f, .19f, .23f, 1f)
            shapes.rect(v.x - .62f, v.y - 1.05f + stride, .42f, .75f)
            shapes.rect(v.x + .2f, v.y - 1.05f - stride, .42f, .75f)
            shapes.color = team
            shapes.rect(v.x - .72f, v.y - .3f, 1.44f, 1.3f)
            shapes.rect(v.x - 1.05f, v.y - .1f, .35f, .9f)
            shapes.rect(v.x + .7f, v.y - .1f, .35f, .9f)
            shapes.color = white; shapes.circle(v.x, v.y + 1f, .68f, 16)
            shapes.color = team; shapes.rect(v.x - .6f, v.y + 1.1f, 1.2f, .23f)
            if (i == carrier(p) && playType(p) !in setOf("INCOMPLETE", "PICK", "FIELD_GOAL", "PUNT")) {
                shapes.color = gold; shapes.circle(v.x, v.y - 1.8f, .35f, 14)
            }
        }
        val b = ball(p, phase)
        shapes.color = gold; shapes.ellipse(b.x - .5f, b.y - .24f, 1f, .48f)
        // A bright ring identifies the actual carrier only after possession is established.
        val kind = playType(p)
        val activeCarrier = when {
            phase < 1.9f -> 0
            kind == "INCOMPLETE" || kind == "FIELD_GOAL" || kind == "PUNT" -> -1
            kind == "PICK" && phase > 3.8f -> 18
            kind == "SACK" -> 0
            else -> carrier(p)
        }
        if (activeCarrier >= 0) {
            val v = pos(activeCarrier, phase, p)
            shapes.color = gold
            shapes.circle(v.x, v.y - 1.5f, .28f, 14)
        }
        shapes.color = Color(.025f, .048f, .085f, 1f)
        shapes.rect(0f, 0f, 120f, 9.5f)
        for (i in 0..4) {
            shapes.color = Color(.09f, .15f, .24f, 1f)
            shapes.rect(1f + i * 24f, 1f, 22f, 7.5f)
        }
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        shapes.color = Color(.86f, .94f, .89f, .55f)
        for (x in 10..110 step 5) shapes.line(x.toFloat(), 10f, x.toFloat(), 62f)
        for (x in 11..109) if (x % 5 != 0) {
            shapes.line(x.toFloat(), 29f, x.toFloat(), 30f)
            shapes.line(x.toFloat(), 43f, x.toFloat(), 44f)
        }
        shapes.color = Color(.20f, .77f, .98f, 1f)
        shapes.line(los(p), 10f, los(p), 62f)
        shapes.color = gold
        shapes.line((los(p) + direction(p) * p.distance).coerceIn(10f, 110f), 10f,
            (los(p) + direction(p) * p.distance).coerceIn(10f, 110f), 62f)
        shapes.end()
        batch.begin()
        yardNumbers?.let { batch.draw(it, 0f, 10f, 120f, 52f) }
        if (labels) {
            val quarterback = pos(0, phase, p)
            qbTag?.let { batch.draw(it, (quarterback.x - 4f).coerceIn(10f, 99f), (quarterback.y + 2.4f).coerceIn(12f, 57f), 11f, 2.4f) }
            val runner = pos(carrier(p), phase, p)
            skillTag?.let { batch.draw(it, (runner.x - 4f).coerceIn(10f, 99f), (runner.y + 2.4f).coerceIn(12f, 57f), 11f, 2.4f) }
        }
        hud?.let { batch.draw(it, 0f, 62f, 120f, 13f) }
        controls?.let { batch.draw(it, 0f, 0f, 120f, 9.5f) }
        if (showStats) statsTexture?.let { batch.draw(it, 13f, 12f, 94f, 49f) }
        batch.end()
    }

    override fun dispose() {
        statsTexture?.dispose(); hud?.dispose(); controls?.dispose(); yardNumbers?.dispose(); qbTag?.dispose(); skillTag?.dispose(); shapes.dispose(); batch.dispose()
    }
}
