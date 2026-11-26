package app.openshortcuts.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import app.openshortcuts.model.ShipLayout
import app.openshortcuts.model.ShipPeriod
import app.openshortcuts.model.ShipPersona
import app.openshortcuts.model.ShipStats
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A look applied to the photo before the stats go on top. */
enum class ShipFilter(val label: String) { Natural("Natural"), Mono("Mono"), Film("Film"), Terminal("Terminal"), Blueprint("Blueprint"), Night("Night") }

/** Output shapes, at the width Instagram and friends show natively. */
enum class ShipAspect(val label: String, val w: Int, val h: Int) {
    Story("Story 9:16", 1080, 1920), Feed("Feed 4:5", 1080, 1350), Square("Square", 1080, 1080),
}

/** The route line's color. Bright enough to read over any photo. */
enum class ShipAccent(val label: String, val argb: Int) {
    Mint("Mint", 0xFF6EE7B7.toInt()), Electric("Electric", 0xFF8EA8FF.toInt()), Amber("Amber", 0xFFFFC14D.toInt()),
    Pink("Pink", 0xFFFF8FC7.toInt()), White("White", 0xFFFFFFFF.toInt()),
}

/** Where your own logo sits on the card. */
enum class ShipLogoCorner(val label: String) { TopLeft("Top left"), TopRight("Top right"), Bottom("Bottom") }

/** How languages show on the card. */
enum class ShipTagStyle(val label: String) { Text("Text"), Outline("Outline"), Off("Off") }

/** The "made with" mark. Designed to be worth keeping. */
enum class ShipStamp(val label: String) { Stamp("Stamp"), Ticket("Ticket"), Minimal("Minimal"), Off("Off") }

data class ShipSpec(
    val persona: ShipPersona = ShipPersona.Classic,
    val filter: ShipFilter = ShipFilter.Natural,
    val aspect: ShipAspect = ShipAspect.Story,
    val accent: ShipAccent = ShipAccent.Mint,
    val period: ShipPeriod = ShipPeriod.Today,
    /** Which of the persona's ready-made headlines; ignored once [headline] is written by hand. */
    val headlineIdx: Int = 0,
    /** Lines written by hand (taken literally); null uses the persona's words. */
    val kicker: String? = null,
    val headline: String? = null,
    val sub: String? = null,
    val handle: String = "",
    val languages: List<String> = emptyList(),
    val tagStyle: ShipTagStyle = ShipTagStyle.Text,
    /** AIs you shipped with, as you picked them; null uses the ones found in your commits. */
    val agents: List<String>? = null,
    val showAi: Boolean = true,
    val stamp: ShipStamp = ShipStamp.Stamp,
    /** No photo: a transparent PNG to lay over a story as a sticker. */
    val sticker: Boolean = false,
    /** How much to darken the photo, 0..0.7, so the numbers read over busy pictures. */
    val dim: Float = 0.2f,
    /** Photo framing: zoom past "cover" (1..4) and pan, in card widths/heights from center. */
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Your logo (kept on the phone only) and where it goes. */
    val logo: Bitmap? = null,
    val logoCorner: ShipLogoCorner = ShipLogoCorner.TopLeft,
) {
    /** Everything but the photo framing: the overlay only needs redrawing when this changes. */
    fun overlayKey() = copy(zoom = 1f, panX = 0f, panY = 0f, dim = 0f)
}

/**
 * Draws a Ship Card with plain Canvas calls at any size. Layout happens on a 1080-wide grid and is
 * fitted to the card's height, so Story, Feed and Square all hold together. The photo and the
 * overlay can be drawn apart: the editor moves the photo on the GPU and redraws text only when it changes.
 */
object ShipCardRenderer {

    // ---- photo ----

    /** How far the photo may pan each way (in card widths/heights) at [zoom], so it always covers the card. */
    fun panLimits(photoW: Int, photoH: Int, cardW: Int, cardH: Int, zoom: Float): Pair<Float, Float> {
        if (photoW <= 0 || photoH <= 0) return 0f to 0f
        val s = max(cardW.toFloat() / photoW, cardH.toFloat() / photoH) * zoom
        return ((photoW * s / cardW - 1f) / 2f).coerceAtLeast(0f) to ((photoH * s / cardH - 1f) / 2f).coerceAtLeast(0f)
    }

    /** Where the photo lands on a [w]×[h] card for [spec]'s framing. */
    fun photoRect(photoW: Int, photoH: Int, w: Float, h: Float, spec: ShipSpec): RectF {
        val s = max(w / photoW, h / photoH) * spec.zoom.coerceIn(1f, 4f)
        val dw = photoW * s
        val dh = photoH * s
        val left = ((w - dw) / 2f + spec.panX * w).coerceIn(w - dw, 0f)
        val top = ((h - dh) / 2f + spec.panY * h).coerceIn(h - dh, 0f)
        return RectF(left, top, left + dw, top + dh)
    }

    /** Decodes [uri] just large enough to cover [w]×[h] (EXIF rotation applied), as a software bitmap. */
    fun loadPhoto(ctx: Context, uri: Uri, w: Int, h: Int): Bitmap? = runCatching {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            val s = max(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
            dec.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            dec.isMutableRequired = false
        }
    }.getOrNull()

    /** The filter as a 4×5 color matrix, shared by the export and the live preview. */
    fun matrixOf(f: ShipFilter): FloatArray? = when (f) {
        ShipFilter.Natural -> null
        ShipFilter.Mono -> ColorMatrix().apply { setSaturation(0f); postConcat(contrast(1.18f)) }.array
        ShipFilter.Film -> ColorMatrix(floatArrayOf(
            1.08f, 0.04f, 0f, 0f, 8f,
            0.02f, 1.0f, 0.02f, 0f, 2f,
            0f, 0.04f, 0.88f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f,
        )).apply { postConcat(ColorMatrix().apply { setSaturation(0.85f) }) }.array
        ShipFilter.Terminal -> duotone(0xFF02140A.toInt(), 0xFF7CFFB2.toInt()).array
        ShipFilter.Blueprint -> duotone(0xFF06152E.toInt(), 0xFF9CC2FF.toInt()).array
        ShipFilter.Night -> ColorMatrix(floatArrayOf(
            0.72f, 0f, 0f, 0f, -6f,
            0f, 0.76f, 0f, 0f, -2f,
            0f, 0f, 0.92f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f,
        )).apply { postConcat(ColorMatrix().apply { setSaturation(0.7f) }) }.array
    }

    private fun contrast(c: Float): ColorMatrix {
        val t = (1f - c) * 128f
        return ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f))
    }

    /** Maps luminance from [dark] to [light]. */
    private fun duotone(dark: Int, light: Int): ColorMatrix {
        fun ch(c: Int, shift: Int) = ((c shr shift) and 0xFF).toFloat()
        val row = { shift: Int ->
            val d = ch(dark, shift); val l = ch(light, shift); val k = (l - d) / 255f
            floatArrayOf(0.299f * k, 0.587f * k, 0.114f * k, 0f, d)
        }
        return ColorMatrix(row(16) + row(8) + row(0) + floatArrayOf(0f, 0f, 0f, 1f, 0f)).apply { postConcat(contrast(1.08f)) }
    }

    // ---- whole card ----

    /**
     * The full card. With [withPhoto] false only the overlay is drawn (effects, scrims, text) on a
     * transparent bitmap, for the editor to lay over its live photo.
     */
    fun render(
        spec: ShipSpec, stats: ShipStats, photo: Bitmap?, scale: Float = 1f,
        zone: ZoneId = ZoneId.systemDefault(), withPhoto: Boolean = true,
    ): Bitmap {
        val w = (spec.aspect.w * scale).toInt()
        val h = (spec.aspect.h * scale).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val u = w / 1080f
        if (!spec.sticker) {
            if (withPhoto) drawPhoto(cv, spec, photo, w, h)
            drawEffects(cv, spec, w, h, u, blend = withPhoto)
        }
        val copy = copyFor(spec, stats, zone)
        val bottomReserve = (if (spec.stamp == ShipStamp.Off) 70f else 150f) + if (spec.logo != null && spec.logoCorner == ShipLogoCorner.Bottom) 130f else 0f
        val topReserve = if (spec.logo != null && spec.logoCorner != ShipLogoCorner.Bottom) 230f else 110f
        val safe = RectF(80 * u, topReserve * u, w - 80 * u, h - bottomReserve * u)
        when (spec.persona.layout) {
            ShipLayout.Stack -> drawStack(cv, spec, stats, copy, safe, u)
            ShipLayout.Recap -> drawRecap(cv, spec, stats, copy, safe, u)
            ShipLayout.Hackathon -> drawHackathon(cv, spec, stats, copy, safe, u)
        }
        spec.logo?.let { drawLogo(cv, it, spec.logoCorner, w, h, u, spec.stamp != ShipStamp.Off) }
        drawStamp(cv, spec.stamp, stats, zone, w, h, u)
        return out
    }

    private fun drawPhoto(cv: Canvas, spec: ShipSpec, photo: Bitmap?, w: Int, h: Int) {
        if (photo == null) {
            // No photo yet: a deep gradient that still looks finished.
            val p = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF0E1116.toInt(), 0xFF1D2433.toInt(), Shader.TileMode.CLAMP) }
            cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            return
        }
        val dst = photoRect(photo.width, photo.height, w.toFloat(), h.toFloat(), spec)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = matrixOf(spec.filter)?.let { ColorMatrixColorFilter(it) }
        }
        cv.drawBitmap(photo, Rect(0, 0, photo.width, photo.height), dst, p)
        if (spec.dim > 0f) cv.drawColor(((spec.dim.coerceIn(0f, 0.7f) * 255).toInt() shl 24))
    }

    /** Texture and the top/bottom scrims. [blend] grain into the photo (export) or just lay it on top (preview). */
    private fun drawEffects(cv: Canvas, spec: ShipSpec, w: Int, h: Int, u: Float, blend: Boolean) {
        when (spec.filter) {
            ShipFilter.Film -> grain(cv, w, h, if (blend) 0.10f else 0.06f, blend)
            ShipFilter.Terminal -> scanlines(cv, w, h, u)
            ShipFilter.Blueprint -> grid(cv, w, h, u)
            else -> Unit
        }
        val top = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h * 0.30f, 0x80000000.toInt(), 0x00000000, Shader.TileMode.CLAMP) }
        cv.drawRect(0f, 0f, w.toFloat(), h * 0.30f, top)
        val bottom = Paint().apply { shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP) }
        cv.drawRect(0f, h * 0.45f, w.toFloat(), h.toFloat(), bottom)
    }

    private val noise: Bitmap by lazy {
        val n = 256
        val rnd = Random(7)
        Bitmap.createBitmap(IntArray(n * n) { val v = rnd.nextInt(256); (v shl 16) or (v shl 8) or v or (0xFF shl 24) }, n, n, Bitmap.Config.ARGB_8888)
    }

    private fun grain(cv: Canvas, w: Int, h: Int, alpha: Float, blend: Boolean) {
        val p = Paint().apply {
            shader = android.graphics.BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            this.alpha = (alpha * 255).toInt()
            if (blend) blendMode = android.graphics.BlendMode.OVERLAY
        }
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
    }

    private fun scanlines(cv: Canvas, w: Int, h: Int, u: Float) {
        val p = Paint().apply { color = 0x2E000000 }
        val step = max(3f, 6 * u)
        var y = 0f
        while (y < h) { cv.drawRect(0f, y, w.toFloat(), y + step / 2, p); y += step }
    }

    private fun grid(cv: Canvas, w: Int, h: Int, u: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x229CC2FF; strokeWidth = max(1f, 1.5f * u) }
        val step = 72 * u
        var x = 0f
        while (x < w) { cv.drawLine(x, 0f, x, h.toFloat(), p); x += step }
        var y = 0f
        while (y < h) { cv.drawLine(0f, y, w.toFloat(), y, p); y += step }
    }

    // ---- type ----

    private val sans = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private fun weight(wt: Int) = Typeface.create(sans, wt, false)
    private val mono = Typeface.MONOSPACE
    private val monoBold = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private fun text(size: Float, wt: Int = 700, color: Int = 0xFFFFFFFF.toInt(), face: Typeface? = null, shadow: Boolean = true) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            typeface = face ?: weight(wt)
            this.color = color
            fontFeatureSettings = "tnum"
            letterSpacing = if (size > 60) -0.02f else 0f
            if (shadow) setShadowLayer(size * 0.22f, 0f, size * 0.04f, 0x99000000.toInt())
        }

    private fun label(size: Float, color: Int = 0xCCFFFFFF.toInt()) = text(size, 500, color, mono).apply { letterSpacing = 0.12f }

    private fun Canvas.center(s: String, cx: Float, y: Float, p: Paint) = drawText(s, cx - p.measureText(s) / 2f, y, p)

    /** Breaks [text] into lines no wider than [maxW]. */
    private fun wrap(text: String, p: Paint, maxW: Float): List<String> {
        val out = ArrayList<String>()
        var line = ""
        text.split(" ").forEach { word ->
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) <= maxW || line.isEmpty()) line = next else { out += line; line = word }
        }
        if (line.isNotEmpty()) out += line
        return out
    }

    // ---- words ----

    /** A persona's words with {tokens} filled in, ready to draw. */
    private class Copy(val kicker: String, val headline: String, val sub: String, val rows: List<Pair<String, String>>, val agents: List<String>)

    private fun copyFor(spec: ShipSpec, stats: ShipStats, zone: ZoneId): Copy {
        val p = spec.persona
        val agents = if (!spec.showAi) emptyList() else spec.agents ?: stats.agents.map { it.first.label }
        val perDay = spec.period == ShipPeriod.Week
        fun f(t: String) = ShipPersona.fill(t, stats, spec.handle, zone, agents, perDay)
        // Without AI numbers (none found, or hidden) an AI row shows a stat the card isn't already showing.
        val noAiShare = !spec.showAi || stats.aiShare <= 0f
        val used = p.rows.map { it.second }.toMutableSet()
        val spare = listOf("Active days" to "{days}", "Repos" to "{repos}", "Last commit" to "{last}", "Span" to "{span}")
        val rows = p.rows.map { (k, v) ->
            if (noAiShare && v.contains("{ai}")) {
                val alt = spare.first { it.second !in used }
                used += alt.second
                (if (k == k.uppercase()) alt.first.uppercase() else alt.first) to f(alt.second)
            } else k to f(v)
        }
        fun clean(s: String) = if (agents.isEmpty()) s.replace(Regex("\\s*(with|\\+)\\s*AI\\b"), "").trim() else s
        val headline = spec.headline ?: p.headlines.getOrElse(spec.headlineIdx) { p.headline }
        return Copy(
            clean(spec.kicker ?: f(p.kicker)),
            clean(if (spec.headline != null) headline else f(headline)),
            clean(spec.sub ?: f(p.sub)),
            rows, agents,
        )
    }

    /** The persona's words as they'd read on the card, for the editor to show and offer. */
    fun filledLines(spec: ShipSpec, stats: ShipStats, zone: ZoneId = ZoneId.systemDefault()): Triple<String, String, String> =
        copyFor(spec, stats, zone).let { Triple(it.kicker, it.headline, it.sub) }

    fun filledHeadlines(spec: ShipSpec, stats: ShipStats, zone: ZoneId = ZoneId.systemDefault()): List<String> {
        val agents = if (!spec.showAi) emptyList() else spec.agents ?: stats.agents.map { it.first.label }
        return spec.persona.headlines.map { ShipPersona.fill(it, stats, spec.handle, zone, agents, spec.period == ShipPeriod.Week) }
    }

    // ---- layout: blocks measured at full size, then scaled to fit the safe area ----

    private class Block(val height: (k: Float) -> Float, val draw: (cv: Canvas, top: Float, k: Float) -> Unit)

    /** Fits [blocks] (with [gap] between) into [safe]; [align] 0 = top, 0.5 = middle, 1 = bottom. */
    private fun place(cv: Canvas, blocks: List<Block>, safe: RectF, gap: Float, align: Float) {
        fun total(k: Float) = blocks.sumOf { it.height(k).toDouble() }.toFloat() + gap * k * (blocks.size - 1).coerceAtLeast(0)
        var k = 1f
        // Shrink until it fits; wrapped text gets fewer lines as it shrinks, so re-measure each step.
        while (k > 0.45f && total(k) > safe.height()) k -= 0.04f
        var y = safe.top + (safe.height() - total(k)) * align
        blocks.forEach { b -> b.draw(cv, y, k); y += b.height(k) + gap * k }
    }

    private fun kickerBlock(s: String, color: Int, safe: RectF, u: Float, centered: Boolean): Block? =
        s.takeIf { it.isNotBlank() }?.let {
            Block({ k -> 40 * u * k }) { cv, top, k ->
                val p = label(30 * u * k, color)
                if (centered) cv.center(it, safe.centerX(), top + 32 * u * k, p) else cv.drawText(it, safe.left, top + 32 * u * k, p)
            }
        }

    private fun headlineBlock(s: String, size: Float, safe: RectF, u: Float, centered: Boolean): Block {
        val lineH = { k: Float -> size * u * k * 1.14f }
        return Block({ k -> wrap(s, text(size * u * k, 800), safe.width()).size * lineH(k) }) { cv, top, k ->
            val p = text(size * u * k, 800)
            var y = top + size * u * k * 0.92f
            wrap(s, p, safe.width()).forEach { line ->
                if (centered) cv.center(line, safe.centerX(), y, p) else cv.drawText(line, safe.left, y, p)
                y += lineH(k)
            }
        }
    }

    private fun subBlock(s: String, safe: RectF, u: Float, centered: Boolean): Block? = s.takeIf { it.isNotBlank() }?.let {
        Block({ k -> 40 * u * k }) { cv, top, k ->
            val p = text(30 * u * k, 500, 0xD9FFFFFF.toInt())
            if (centered) cv.center(it, safe.centerX(), top + 30 * u * k, p) else cv.drawText(it, safe.left, top + 30 * u * k, p)
        }
    }

    private fun routeBlock(stats: ShipStats, accent: Int, safe: RectF, u: Float, height: Float, widthFrac: Float, centered: Boolean): Block =
        Block({ k -> height * u * k }) { cv, top, k ->
            val bw = safe.width() * widthFrac
            val left = if (centered) safe.centerX() - bw / 2 else safe.left
            drawRoute(cv, stats, RectF(left, top + 14 * u * k, left + bw, top + height * u * k - 14 * u * k), accent, u * k)
        }

    private fun tagsBlock(spec: ShipSpec, safe: RectF, u: Float, centered: Boolean): Block? {
        if (spec.languages.isEmpty() || spec.tagStyle == ShipTagStyle.Off) return null
        return Block({ k -> 56 * u * k }) { cv, top, k ->
            when (spec.tagStyle) {
                ShipTagStyle.Text -> {
                    val p = text(28 * u * k, 500, 0xD9FFFFFF.toInt(), mono).apply { letterSpacing = 0.04f }
                    val s = spec.languages.joinToString("  ·  ")
                    if (centered) cv.center(s, safe.centerX(), top + 38 * u * k, p) else cv.drawText(s, safe.left, top + 38 * u * k, p)
                }
                ShipTagStyle.Outline -> {
                    val p = text(26 * u * k, 600, shadow = false)
                    val pad = 20 * u * k; val gap = 12 * u * k; val hgt = 50 * u * k
                    val widths = spec.languages.map { p.measureText(it) + pad * 2 }
                    var x = if (centered) safe.centerX() - (widths.sum() + gap * (widths.size - 1)) / 2f else safe.left
                    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * u * k; color = 0x99FFFFFF.toInt() }
                    spec.languages.forEachIndexed { i, l ->
                        val r = RectF(x, top, x + widths[i], top + hgt)
                        cv.drawRoundRect(r, hgt / 2, hgt / 2, stroke)
                        cv.drawText(l, x + pad, top + hgt * 0.68f, p)
                        x += widths[i] + gap
                    }
                }
                ShipTagStyle.Off -> Unit
            }
        }
    }

    // ---- the route: cumulative commits over the window, drawn as a glowing line ----

    private fun drawRoute(cv: Canvas, stats: ShipStats, box: RectF, accent: Int, u: Float) {
        val span = (stats.to.toEpochMilli() - stats.from.toEpochMilli()).coerceAtLeast(1)
        val pts = ArrayList<Pair<Float, Float>>()
        pts += box.left to box.bottom
        stats.times.forEachIndexed { i, t ->
            val x = box.left + box.width() * ((t.toEpochMilli() - stats.from.toEpochMilli()).toFloat() / span).coerceIn(0f, 1f)
            val y = box.bottom - box.height() * ((i + 1).toFloat() / max(1, stats.times.size))
            pts += x to y
        }
        pts += box.right to (pts.last().second)
        val path = Path().apply {
            moveTo(pts[0].first, pts[0].second)
            for (i in 1 until pts.size) {
                val (px, py) = pts[i - 1]
                val (x, y) = pts[i]
                val mx = (px + x) / 2f
                cubicTo(mx, py, mx, y, x, y)
            }
        }
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 14 * u; color = accent; alpha = 90
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            maskFilter = BlurMaskFilter(max(1f, 16 * u), BlurMaskFilter.Blur.NORMAL)
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 7 * u; color = accent
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        cv.drawPath(path, glow)
        cv.drawPath(path, line)
        val (ex, ey) = pts[pts.size - 2]
        cv.drawCircle(ex, ey, 11 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        cv.drawCircle(ex, ey, 5 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B0B0B.toInt() })
    }

    // ---- layouts ----

    /** Big stacked numbers down the middle, like a finish screen. */
    private fun drawStack(cv: Canvas, spec: ShipSpec, stats: ShipStats, copy: Copy, safe: RectF, u: Float) {
        if (!spec.sticker) {
            // A soft dark pool behind the stack keeps the numbers readable over any photo.
            val cy = safe.centerY()
            val r = min(safe.width() * 0.7f, safe.height() * 0.55f)
            val pool = Paint().apply { shader = RadialGradient(safe.centerX(), cy, r, 0x8C000000.toInt(), 0x00000000, Shader.TileMode.CLAMP) }
            cv.save(); cv.scale(1f, 1.4f, safe.centerX(), cy); cv.drawCircle(safe.centerX(), cy, r, pool); cv.restore()
        }
        val rows = copy.rows.map { (k, v) ->
            Block({ s -> 212 * u * s }) { c, top, s ->
                c.center(k, safe.centerX(), top + 32 * u * s, label(30 * u * s))
                c.center(v, safe.centerX(), top + 150 * u * s, text(112 * u * s, 800))
            }
        }
        val blocks = listOfNotNull(kickerBlock(copy.kicker, spec.accent.argb, safe, u, true)) + rows + listOfNotNull(
            routeBlock(stats, spec.accent.argb, safe, u, 170f, 0.6f, true),
            headlineBlock(copy.headline, 52f, safe, u, true),
            subBlock(copy.sub, safe, u, true),
            tagsBlock(spec, safe, u, true),
        )
        place(cv, blocks, safe, 18 * u, 0.5f)
    }

    /** A bottom-left summary with a 2×2 grid, like an end-of-week recap. */
    private fun drawRecap(cv: Canvas, spec: ShipSpec, stats: ShipStats, copy: Copy, safe: RectF, u: Float) {
        val kick = listOf(copy.kicker, copy.sub).filter { it.isNotBlank() }.joinToString("   ")
        val grid = Block({ k -> 340 * u * k }) { c, top, k ->
            copy.rows.take(4).forEachIndexed { i, (label, v) ->
                val x = safe.left + (i % 2) * safe.width() / 2f
                val y = top + (i / 2) * 175 * u * k
                c.drawText(label, x, y + 34 * u * k, text(30 * u * k, 500, 0xE6FFFFFF.toInt()))
                c.drawText(v, x, y + 112 * u * k, text(80 * u * k, 800))
            }
        }
        val blocks = listOfNotNull(
            routeBlock(stats, spec.accent.argb, safe, u, 200f, 1f, false),
            kickerBlock(kick, if (copy.kicker.isNotBlank()) spec.accent.argb else 0xE6FFFFFF.toInt(), safe, u, false),
            headlineBlock(copy.headline, 60f, safe, u, false),
            grid,
            tagsBlock(spec, safe, u, false),
        )
        place(cv, blocks, safe, 22 * u, 1f)
    }

    /** Event name up top, a commits-per-hour histogram, and the team (you plus your AIs). */
    private fun drawHackathon(cv: Canvas, spec: ShipSpec, stats: ShipStats, copy: Copy, safe: RectF, u: Float) {
        if (!spec.sticker) {
            val pool = Paint().apply { shader = LinearGradient(0f, 0f, 0f, safe.top + 520 * u, 0x8C000000.toInt(), 0x00000000, Shader.TileMode.CLAMP) }
            cv.drawRect(0f, 0f, cv.width.toFloat(), safe.top + 520 * u, pool)
        }
        val hours = max(1, ((stats.to.toEpochMilli() - stats.from.toEpochMilli()) / 3_600_000L).toInt())
        val buckets = IntArray(min(hours, 72))
        stats.times.forEach { t ->
            val i = ((t.toEpochMilli() - stats.from.toEpochMilli()) / 3_600_000L).toInt()
            if (i in buckets.indices) buckets[i]++
        }
        // Commits per hour: the 3am spike tells the story.
        val chart = Block({ k -> 240 * u * k }) { c, top, k ->
            val box = RectF(safe.left, top, safe.right, top + 240 * u * k)
            val maxB = max(1, buckets.maxOrNull() ?: 1)
            val bw = box.width() / buckets.size
            val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.accent.argb }
            val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            buckets.forEachIndexed { i, n ->
                val x = box.left + i * bw
                val bh = if (n == 0) 4 * u * k else box.height() * n / maxB
                c.drawRoundRect(RectF(x + bw * 0.18f, box.bottom - bh, x + bw * 0.82f, box.bottom), bw * 0.3f, bw * 0.3f, if (n == 0) empty else bar)
            }
        }
        val statsRow = Block({ k -> 170 * u * k }) { c, top, k ->
            val colW = safe.width() / 3f
            copy.rows.take(3).forEachIndexed { i, (label, v) ->
                val x = safe.left + i * colW
                c.drawText(label, x, top + 34 * u * k, text(30 * u * k, 500, 0xE6FFFFFF.toInt()))
                c.drawText(v, x, top + 112 * u * k, text(72 * u * k, 800))
            }
        }
        val team = buildList {
            add(spec.handle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Me")
            addAll(copy.agents)
        }
        val teamBlock = Block({ k -> 50 * u * k }) { c, top, k -> c.drawText("Team  " + team.joinToString("  +  "), safe.left, top + 38 * u * k, text(34 * u * k, 600)) }
        val head = listOfNotNull(kickerBlock(copy.kicker, spec.accent.argb, safe, u, false), headlineBlock(copy.headline, 88f, safe, u, false), subBlock(copy.sub, safe, u, false))
        // Title at the top, the rest at the bottom: two groups, each fitted to its part.
        val split = safe.top + safe.height() * 0.42f
        place(cv, head, RectF(safe.left, safe.top, safe.right, split), 14 * u, 0f)
        place(cv, listOfNotNull(chart, statsRow, teamBlock, tagsBlock(spec, safe, u, false)), RectF(safe.left, split + 30 * u, safe.right, safe.bottom), 26 * u, 1f)
    }

    // ---- logo and stamp ----

    /** Your logo, kept to a 96-unit tall badge so any shape fits. */
    private fun drawLogo(cv: Canvas, logo: Bitmap, corner: ShipLogoCorner, w: Int, h: Int, u: Float, stamp: Boolean) {
        val s = min(96 * u / logo.height, 300 * u / logo.width)
        val lw = logo.width * s
        val lh = logo.height * s
        val (x, y) = when (corner) {
            ShipLogoCorner.TopLeft -> 80 * u to 100 * u
            ShipLogoCorner.TopRight -> (w - 80 * u - lw) to 100 * u
            ShipLogoCorner.Bottom -> (w - lw) / 2f to (h - (if (stamp) 150 else 70) * u - lh)
        }
        cv.drawBitmap(logo, Rect(0, 0, logo.width, logo.height), RectF(x, y, x + lw, y + lh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    /** The OpenShortcuts mark: a rounded square with a play notch cut out, drawn at [size]. */
    private fun drawMark(cv: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val layer = cv.saveLayer(x - 1, y - 1, x + size + 1, y + size + 1, null)
        cv.drawRoundRect(RectF(x, y, x + size, y + size), size * 0.28f, size * 0.28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF000000.toInt(); blendMode = android.graphics.BlendMode.DST_OUT }
        val tri = Path().apply {
            moveTo(x + size * 0.38f, y + size * 0.28f); lineTo(x + size * 0.72f, y + size * 0.5f); lineTo(x + size * 0.38f, y + size * 0.72f); close()
        }
        cv.drawPath(tri, cut)
        cv.restoreToCount(layer)
    }

    private fun drawStamp(cv: Canvas, stamp: ShipStamp, stats: ShipStats, zone: ZoneId, w: Int, h: Int, u: Float) {
        val date = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy").format(stats.to.atZone(zone))
        val ink = 0xE6FFFFFF.toInt()
        when (stamp) {
            ShipStamp.Off -> Unit
            ShipStamp.Stamp -> {
                // A thin outlined pill: mark · SHIPPED WITH OPENSHORTCUTS | date
                val p = text(21 * u, 600, ink, monoBold, shadow = false).apply { letterSpacing = 0.16f }
                val left = "SHIPPED WITH OPENSHORTCUTS"
                val tw = p.measureText(left)
                val dw = p.measureText(date)
                val mark = 22 * u
                val padX = 22 * u
                val gap = 14 * u
                val pillW = padX + mark + gap + tw + gap * 1.6f + dw + padX
                val pillH = 54 * u
                val x = (w - pillW) / 2f
                val y = h - 66 * u - pillH
                val r = RectF(x, y, x + pillW, y + pillH)
                cv.drawRoundRect(r, pillH / 2, pillH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 })
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f * u; color = 0x8CFFFFFF.toInt() }
                cv.drawRoundRect(r, pillH / 2, pillH / 2, stroke)
                drawMark(cv, x + padX, y + (pillH - mark) / 2, mark, ink)
                val ty = y + pillH / 2 + 7.5f * u
                cv.drawText(left, x + padX + mark + gap, ty, p)
                val divX = x + padX + mark + gap + tw + gap * 0.8f
                cv.drawLine(divX, y + 15 * u, divX, y + pillH - 15 * u, stroke)
                cv.drawText(date, divX + gap * 0.8f, ty, p.apply { color = 0xB3FFFFFF.toInt() })
            }
            ShipStamp.Ticket -> {
                // Two mono lines bottom-left, like a receipt: the brand, then a card number and date.
                val no = "%04d".format((stats.to.atZone(zone).dayOfYear * 7 + stats.commits) % 10000)
                val x = 80 * u
                val y2 = h - 64 * u
                val y1 = y2 - 34 * u
                drawMark(cv, x, y1 - 24 * u, 26 * u, ink)
                cv.drawText("OPENSHORTCUTS", x + 40 * u, y1, text(24 * u, 700, ink, monoBold).apply { letterSpacing = 0.22f })
                cv.drawText("SHIP CARD Nº $no  ·  $date", x + 40 * u, y2, text(19 * u, 500, 0xB3FFFFFF.toInt(), mono).apply { letterSpacing = 0.14f })
            }
            ShipStamp.Minimal -> {
                val p = text(22 * u, 500, 0xB3FFFFFF.toInt(), mono).apply { letterSpacing = 0.1f }
                cv.center("openshortcuts", w / 2f, h - 64 * u, p)
            }
        }
    }
}
