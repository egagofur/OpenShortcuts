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
import app.openshortcuts.model.ShipLimitStyle
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
enum class ShipStamp(val label: String) { Signature("Signature"), Frame("Frame"), Stamp("Stamp"), Ticket("Ticket"), Off("Off") }

/** How a free text reads over the photo. */
enum class ShipTextStyle(val label: String) { Shadow("Shadow"), Plain("Plain"), Outline("Outline"), Box("Box") }

/**
 * A line of your own placed anywhere on the card, like a text sticker: [x]/[y] is its center as a
 * fraction of the card, [size] is in 1080-grid pixels, [rotation] in degrees.
 */
data class ShipText(
    val id: Long,
    val text: String,
    val font: String = ShipFonts.SYSTEM,
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 72f,
    val x: Float = 0.5f,
    val y: Float = 0.3f,
    val rotation: Float = 0f,
    val style: ShipTextStyle = ShipTextStyle.Shadow,
)

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
    val stamp: ShipStamp = ShipStamp.Signature,
    /** The name in the Signature and Frame marks; yours to change. */
    val stampText: String = "OpenShortcuts",
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
    /** The face for the headline and the big numbers (a [ShipFonts] key). */
    val font: String = ShipFonts.SYSTEM,
    /** Your own text stickers, drawn on top of everything. */
    val texts: List<ShipText> = emptyList(),
    /** The AI Limit card: which notice, at the top or bottom, and its button (null: the style's own, "": none). */
    val limitStyle: ShipLimitStyle = ShipLimitStyle.Card,
    val limitTop: Boolean = false,
    val limitAction: String? = null,
    /** When the limit resets, e.g. "3:40 PM"; blank works it out (a couple of hours on). */
    val resetAt: String = "",
) {
    /** Everything but the photo framing and text stickers: the overlay only needs redrawing when this changes. */
    fun overlayKey() = copy(zoom = 1f, panX = 0f, panY = 0f, dim = 0f, texts = emptyList())
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
        // Rounding can leave the photo a hair narrower than the card; never let the range invert.
        val left = ((w - dw) / 2f + spec.panX * w).coerceIn(min(w - dw, 0f), 0f)
        val top = ((h - dh) / 2f + spec.panY * h).coerceIn(min(h - dh, 0f), 0f)
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
        zone: ZoneId = ZoneId.systemDefault(), withPhoto: Boolean = true, withTexts: Boolean = true,
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
        if (spec.persona.layout == ShipLayout.Limit) {
            drawLimit(cv, spec, stats, copy, w.toFloat(), h.toFloat(), u, zone)
            if (withTexts) spec.texts.forEach { drawText(cv, it, w.toFloat(), h.toFloat()) }
            return out
        }
        val bottomReserve = (when (spec.stamp) { ShipStamp.Off -> 70f; ShipStamp.Frame -> 230f; ShipStamp.Signature -> 180f; else -> 150f }) + if (spec.logo != null && spec.logoCorner == ShipLogoCorner.Bottom) 130f else 0f
        val topReserve = if (spec.logo != null && spec.logoCorner != ShipLogoCorner.Bottom) 230f else 110f
        val safe = RectF(80 * u, topReserve * u, w - 80 * u, h - bottomReserve * u)
        when (spec.persona.layout) {
            ShipLayout.Stack -> drawStack(cv, spec, stats, copy, safe, u)
            ShipLayout.Recap -> drawRecap(cv, spec, stats, copy, safe, u)
            ShipLayout.Hackathon -> drawHackathon(cv, spec, stats, copy, safe, u)
            ShipLayout.Heatmap -> drawHeatmap(cv, spec, stats, copy, safe, u, zone)
        }
        spec.logo?.let { drawLogo(cv, it, spec.logoCorner, w, h, u, spec.stamp != ShipStamp.Off) }
        drawStamp(cv, spec, stats, zone, w, h, u)
        if (withTexts) spec.texts.forEach { drawText(cv, it, w.toFloat(), h.toFloat()) }
        return out
    }

    // ---- text stickers ----

    private fun stickerPaint(t: ShipText, u: Float) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textSize = t.size * u
        typeface = ShipFonts.typeface(t.font, 700)
        fontFeatureSettings = "'lnum' 1"
        color = t.color
        textAlign = Paint.Align.CENTER
    }

    /** The sticker's box before rotation, centered on its position, on a [w]×[h] card. */
    fun textBounds(t: ShipText, w: Float, h: Float): RectF {
        val u = w / 1080f
        val p = stickerPaint(t, u)
        val lines = t.text.ifEmpty { " " }.split('\n')
        val tw = lines.maxOf { p.measureText(it) }
        val lineH = p.fontSpacing
        val pad = if (t.style == ShipTextStyle.Box) t.size * u * 0.45f else t.size * u * 0.12f
        val cx = t.x * w; val cy = t.y * h
        val bh = lineH * lines.size
        return RectF(cx - tw / 2 - pad, cy - bh / 2 - pad, cx + tw / 2 + pad, cy + bh / 2 + pad)
    }

    /** Draws one text sticker; shared by the export and the editor's live layer. */
    fun drawText(cv: Canvas, t: ShipText, w: Float, h: Float) {
        if (t.text.isBlank()) return
        val u = w / 1080f
        val p = stickerPaint(t, u)
        val lines = t.text.split('\n')
        val lineH = p.fontSpacing
        val cx = t.x * w; val cy = t.y * h
        cv.save()
        cv.rotate(t.rotation, cx, cy)
        val top = cy - lineH * lines.size / 2f
        val fm = p.fontMetrics
        when (t.style) {
            ShipTextStyle.Box -> {
                val r = textBounds(t, w, h)
                val light = luminance(t.color) > 0.6f
                cv.drawRoundRect(r, t.size * u * 0.35f, t.size * u * 0.35f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.color })
                p.color = if (light) 0xFF0B0B0B.toInt() else 0xFFFFFFFF.toInt()
            }
            ShipTextStyle.Shadow -> p.setShadowLayer(t.size * u * 0.2f, 0f, t.size * u * 0.04f, 0x99000000.toInt())
            else -> Unit
        }
        lines.forEachIndexed { i, line ->
            val y = top + lineH * i - fm.ascent
            if (t.style == ShipTextStyle.Outline) {
                val stroke = Paint(p).apply {
                    style = Paint.Style.STROKE; strokeWidth = t.size * u * 0.1f; strokeJoin = Paint.Join.ROUND
                    color = if (luminance(t.color) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                cv.drawText(line, cx, y, stroke)
            }
            cv.drawText(line, cx, y, p)
        }
        cv.restore()
    }

    private fun luminance(c: Int): Float =
        (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f

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
            // Lining, even-width figures: Playfair's default old-style 0 reads like an o.
            fontFeatureSettings = "'lnum' 1, 'tnum' 1"
            letterSpacing = if (size > 60) -0.02f else 0f
            if (shadow) setShadowLayer(size * 0.22f, 0f, size * 0.04f, 0x99000000.toInt())
        }

    /** Shrinks [p] until [s] fits in [maxW]: wide faces (Poppins, a big number) never run into the next column. */
    private fun fit(p: Paint, s: String, maxW: Float): Paint {
        while (p.measureText(s) > maxW && p.textSize > 8f) p.textSize *= 0.95f
        return p
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
                val p = fit(label(30 * u * k, color), it, safe.width())
                if (centered) cv.center(it, safe.centerX(), top + 32 * u * k, p) else cv.drawText(it, safe.left, top + 32 * u * k, p)
            }
        }

    private fun headlineBlock(s: String, size: Float, safe: RectF, u: Float, centered: Boolean, face: Typeface): Block {
        // Line height from the face's own metrics: Poppins and Playfair stand taller than the system sans.
        val lineH = { k: Float ->
            val fm = text(size * u * k, 800, face = face).fontMetrics
            max(size * u * k * 1.12f, (fm.descent - fm.ascent) * 0.94f)
        }
        return Block({ k -> wrap(s, text(size * u * k, 800, face = face), safe.width()).size * lineH(k) }) { cv, top, k ->
            val p = text(size * u * k, 800, face = face)
            var y = top - p.fontMetrics.ascent * 0.96f
            wrap(s, p, safe.width()).forEach { line ->
                if (centered) cv.center(line, safe.centerX(), y, p) else cv.drawText(line, safe.left, y, p)
                y += lineH(k)
            }
        }
    }

    private fun subBlock(s: String, safe: RectF, u: Float, centered: Boolean): Block? = s.takeIf { it.isNotBlank() }?.let {
        Block({ k -> 40 * u * k }) { cv, top, k ->
            val p = fit(text(30 * u * k, 500, 0xD9FFFFFF.toInt()), it, safe.width())
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
                    val s = spec.languages.joinToString("  ·  ")
                    val p = fit(text(28 * u * k, 500, 0xD9FFFFFF.toInt(), mono).apply { letterSpacing = 0.04f }, s, safe.width())
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

    /** "✦ with Claude + Cursor": the AIs you picked, unless the words already name them. */
    private fun teamBlock(copy: Copy, safe: RectF, u: Float, centered: Boolean, accent: Int): Block? {
        if (copy.agents.isEmpty()) return null
        val said = listOf(copy.kicker, copy.headline, copy.sub).joinToString(" ")
        if (copy.agents.all { said.contains(it, ignoreCase = true) }) return null
        val s = "with " + copy.agents.joinToString(" + ")
        return Block({ k -> 50 * u * k }) { cv, top, k ->
            val star = 26 * u * k
            val p = fit(text(30 * u * k, 600, 0xF2FFFFFF.toInt()), s, safe.width() - star - 12 * u * k)
            val gap = 12 * u * k
            val total = star + gap + p.measureText(s)
            val x = if (centered) safe.centerX() - total / 2f else safe.left
            val cy = top + 25 * u * k
            drawSpark(cv, x + star / 2, cy, star / 2, accent)
            cv.drawText(s, x + star + gap, cy + 10.5f * u * k, p)
        }
    }

    /** A four-point spark: the card's sign for "an AI helped". */
    private fun drawSpark(cv: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        val q = r * 0.28f
        val path = Path().apply {
            moveTo(cx, cy - r); quadTo(cx + q, cy - q, cx + r, cy); quadTo(cx + q, cy + q, cx, cy + r)
            quadTo(cx - q, cy + q, cx - r, cy); quadTo(cx - q, cy - q, cx, cy - r); close()
        }
        cv.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
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
        val face = ShipFonts.typeface(spec.font, 800)
        val rows = copy.rows.map { (k, v) ->
            Block({ s -> 212 * u * s }) { c, top, s ->
                c.center(k, safe.centerX(), top + 32 * u * s, label(30 * u * s))
                c.center(v, safe.centerX(), top + 150 * u * s, fit(text(112 * u * s, 800, face = face), v, safe.width()))
            }
        }
        val blocks = listOfNotNull(kickerBlock(copy.kicker, spec.accent.argb, safe, u, true)) + rows + listOfNotNull(
            routeBlock(stats, spec.accent.argb, safe, u, 170f, 0.6f, true),
            headlineBlock(copy.headline, 52f, safe, u, true, face),
            subBlock(copy.sub, safe, u, true),
            teamBlock(copy, safe, u, true, spec.accent.argb),
            tagsBlock(spec, safe, u, true),
        )
        place(cv, blocks, safe, 18 * u, 0.5f)
    }

    /** A bottom-left summary with a 2×2 grid, like an end-of-week recap. */
    private fun drawRecap(cv: Canvas, spec: ShipSpec, stats: ShipStats, copy: Copy, safe: RectF, u: Float) {
        val face = ShipFonts.typeface(spec.font, 800)
        val kick = listOf(copy.kicker, copy.sub).filter { it.isNotBlank() }.joinToString("   ")
        val grid = Block({ k -> 340 * u * k }) { c, top, k ->
            copy.rows.take(4).forEachIndexed { i, (label, v) ->
                val x = safe.left + (i % 2) * safe.width() / 2f
                val y = top + (i / 2) * 175 * u * k
                c.drawText(label, x, y + 34 * u * k, fit(text(30 * u * k, 500, 0xE6FFFFFF.toInt()), label, safe.width() / 2f - 24 * u))
                c.drawText(v, x, y + 112 * u * k, fit(text(80 * u * k, 800, face = face), v, safe.width() / 2f - 24 * u))
            }
        }
        val blocks = listOfNotNull(
            routeBlock(stats, spec.accent.argb, safe, u, 200f, 1f, false),
            kickerBlock(kick, if (copy.kicker.isNotBlank()) spec.accent.argb else 0xE6FFFFFF.toInt(), safe, u, false),
            headlineBlock(copy.headline, 60f, safe, u, false, face),
            teamBlock(copy, safe, u, false, spec.accent.argb),
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
        val face = ShipFonts.typeface(spec.font, 800)
        val statsRow = Block({ k -> 170 * u * k }) { c, top, k ->
            val colW = safe.width() / 3f
            copy.rows.take(3).forEachIndexed { i, (label, v) ->
                val x = safe.left + i * colW
                c.drawText(label, x, top + 34 * u * k, fit(text(30 * u * k, 500, 0xE6FFFFFF.toInt()), label, colW - 20 * u))
                c.drawText(v, x, top + 112 * u * k, fit(text(72 * u * k, 800, face = face), v, colW - 20 * u))
            }
        }
        val team = buildList {
            add(spec.handle.takeIf { it.isNotBlank() }?.let { "@$it" } ?: "Me")
            addAll(copy.agents)
        }
        val teamBlock = Block({ k -> 50 * u * k }) { c, top, k -> c.drawText("Team  " + team.joinToString("  +  "), safe.left, top + 38 * u * k, fit(text(34 * u * k, 600), "Team  " + team.joinToString("  +  "), safe.width())) }
        val head = listOfNotNull(kickerBlock(copy.kicker, spec.accent.argb, safe, u, false), headlineBlock(copy.headline, 88f, safe, u, false, face), subBlock(copy.sub, safe, u, false))
        // Title at the top, the rest at the bottom: two groups, each fitted to its part.
        val split = safe.top + safe.height() * 0.42f
        place(cv, head, RectF(safe.left, safe.top, safe.right, split), 14 * u, 0f)
        place(cv, listOfNotNull(chart, statsRow, teamBlock, tagsBlock(spec, safe, u, false)), RectF(safe.left, split + 30 * u, safe.right, safe.bottom), 26 * u, 1f)
    }

    /** Sixteen weeks as a contribution grid, Monday on top, the newest week on the right. */
    private fun drawHeatmap(cv: Canvas, spec: ShipSpec, stats: ShipStats, copy: Copy, safe: RectF, u: Float, zone: ZoneId) {
        if (!spec.sticker) {
            val cy = safe.centerY()
            val r = max(safe.width(), safe.height()) * 0.62f
            val pool = Paint().apply { shader = RadialGradient(safe.centerX(), cy, r, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP) }
            cv.drawCircle(safe.centerX(), cy, r, pool)
        }
        val face = ShipFonts.typeface(spec.font, 800)
        val first = java.time.LocalDate.ofInstant(stats.from, zone)
        val last = java.time.LocalDate.ofInstant(stats.to, zone)
        val weeks = max(1, (java.time.temporal.ChronoUnit.DAYS.between(first, last) / 7 + 1).toInt()).coerceAtMost(53)
        val maxDay = max(1, stats.perDay.values.maxOrNull() ?: 1)
        val accent = spec.accent.argb
        fun level(n: Int): Int = when {
            n <= 0 -> 0
            n.toFloat() / maxDay <= 0.25f -> 1
            n.toFloat() / maxDay <= 0.5f -> 2
            n.toFloat() / maxDay <= 0.75f -> 3
            else -> 4
        }
        val alphas = intArrayOf(0, 90, 145, 200, 255)
        fun cellPaint(lv: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (lv == 0) 0x26FFFFFF else (accent and 0x00FFFFFF) or (alphas[lv] shl 24)
        }
        // The grid shrinks with everything else when a short card (feed, square) needs room.
        val grid = Block({ k -> val cell = safe.width() * k / (weeks + (weeks - 1) * 0.22f); 40 * u * k + 7 * cell + 6 * cell * 0.22f }) { c, top, k ->
            val cell = safe.width() * k / (weeks + (weeks - 1) * 0.22f)
            val gap = cell * 0.22f
            val labelH = 40 * u * k
            val monthP = label(min(24 * u * k, cell * 0.9f), 0xB3FFFFFF.toInt())
            var lastMonth = -1
            for (w in 0 until weeks) {
                val x = safe.left + w * (cell + gap)
                val weekStart = first.plusWeeks(w.toLong())
                if (weekStart.monthValue != lastMonth) {
                    // Only label a month when there's room before the next label.
                    if (lastMonth == -1 && weekStart.dayOfMonth > 21) { lastMonth = weekStart.monthValue } else {
                        c.drawText(weekStart.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH).uppercase(), x, top + labelH * 0.7f, monthP)
                        lastMonth = weekStart.monthValue
                    }
                }
                for (d in 0 until 7) {
                    val day = weekStart.plusDays(d.toLong())
                    if (day.isAfter(last)) continue
                    val y = top + labelH + d * (cell + gap)
                    c.drawRoundRect(RectF(x, y, x + cell, y + cell), cell * 0.26f, cell * 0.26f, cellPaint(level(stats.perDay[day] ?: 0)))
                }
            }
        }
        val legend = Block({ k -> 44 * u * k }) { c, top, k ->
            val sq = 22 * u * k; val g = 6 * u * k
            val p = label(22 * u * k, 0x99FFFFFF.toInt())
            val more = "MORE"
            var x = safe.right - p.measureText(more)
            c.drawText(more, x, top + sq * 0.85f, p)
            x -= g * 2 + 5 * sq + 4 * g
            c.drawText("LESS", x - g * 2 - p.measureText("LESS"), top + sq * 0.85f, p)
            for (lv in 0..4) { c.drawRoundRect(RectF(x, top, x + sq, top + sq), sq * 0.26f, sq * 0.26f, cellPaint(lv)); x += sq + g }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.ENGLISH)
            c.drawText("${fmt.format(first)} – ${fmt.format(last)}".uppercase(), safe.left, top + sq * 0.85f, p)
        }
        val statsGrid = Block({ k -> 310 * u * k }) { c, top, k ->
            copy.rows.take(4).forEachIndexed { i, (lbl, v) ->
                val x = safe.left + (i % 2) * safe.width() / 2f
                val y = top + (i / 2) * 160 * u * k
                c.drawText(lbl, x, y + 32 * u * k, text(28 * u * k, 500, 0xE6FFFFFF.toInt()))
                c.drawText(v, x, y + 108 * u * k, fit(text(76 * u * k, 800, face = face), v, safe.width() / 2f - 24 * u))
            }
        }
        val blocks = listOfNotNull(
            kickerBlock(copy.kicker, accent, safe, u, false),
            headlineBlock(copy.headline, 68f, safe, u, false, face),
            subBlock(copy.sub, safe, u, false),
            grid, legend, statsGrid,
            teamBlock(copy, safe, u, false, accent),
            tagsBlock(spec, safe, u, false),
        )
        place(cv, blocks, safe, 26 * u, 0.5f)
    }

    /** The reset time to show: yours, or about two hours from the card's moment, like "3:40 PM". */
    private fun resetLabel(spec: ShipSpec, stats: ShipStats, zone: ZoneId): String = spec.resetAt.trim().ifBlank {
        val t = stats.to.atZone(zone).plusMinutes(133)
        java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH).format(t.withMinute(t.minute / 10 * 10))
    }

    /** The speedometer icon these notices use: an open ring with a needle. */
    private fun drawGauge(cv: Canvas, cx: Float, cy: Float, r: Float, color: Int, u: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.22f; strokeCap = Paint.Cap.ROUND; this.color = color }
        cv.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 135f, 270f, false, p)
        cv.drawLine(cx, cy, cx + r * 0.5f, cy + r * 0.32f, p)
    }

    /** Words with link words underlined, wrapped to [maxW]; returns the height used (draws when [draw]). */
    private fun richText(cv: Canvas?, words: List<Pair<String, Boolean>>, x: Float, top: Float, maxW: Float, p: Paint, lineH: Float, underline: Int): Float {
        var cx = x; var line = 0
        val space = p.measureText(" ")
        words.forEach { (wd, link) ->
            val ww = p.measureText(wd)
            if (cx > x && cx + ww > x + maxW) { cx = x; line++ }
            val base = top + line * lineH + lineH * 0.74f
            if (cv != null) {
                cv.drawText(wd, cx, base, p)
                if (link) cv.drawRect(cx, base + lineH * 0.1f, cx + ww, base + lineH * 0.1f + max(1.5f, lineH * 0.045f), Paint().apply { color = underline })
            }
            cx += ww + space
        }
        return (line + 1) * lineH
    }

    /**
     * The AI Limit card: your photo, edge to edge, with a usage-limit notice laid over the top or bottom,
     * the way your AI app shows it. Four looks: plain words, a light card, an amber banner, a dark dialog.
     */
    private fun drawLimit(cv: Canvas, spec: ShipSpec, stats: ShipStats, copy: Copy, w: Float, h: Float, u: Float, zone: ZoneId) {
        val agent = copy.agents.firstOrNull() ?: "Claude"
        val reset = resetLabel(spec, stats, zone)
        val x = 64 * u
        val cw = w - 2 * x
        // App-style UI text: proportional figures, so "5-hour" and "8:30 AM" read like the real thing.
        val sans = { size: Float, wt: Int, col: Int -> text(size * u, wt, col, shadow = false).apply { letterSpacing = 0f; fontFeatureSettings = null } }
        val action = spec.limitAction ?: when (spec.limitStyle) {
            ShipLimitStyle.Banner -> "View your usage details"
            ShipLimitStyle.Text -> ""
            else -> "Try again"
        }
        val bottomReserve = when (spec.stamp) { ShipStamp.Off -> 70f; ShipStamp.Frame -> 240f; ShipStamp.Signature -> 190f; else -> 160f } * u
        // Clear of a story's own top bar (and the editor's buttons in the preview).
        fun topOf(height: Float) = if (spec.limitTop) 240 * u else h - bottomReserve - 30 * u - height

        when (spec.limitStyle) {
            ShipLimitStyle.Text -> {
                val t1 = text(66 * u, 800, 0xFFFFFFFF.toInt())
                val t2 = text(34 * u, 500, 0xE6FFFFFF.toInt())
                val lines = wrap("Usage limit reached.", t1, cw)
                val sub = "$agent is back at $reset." + if (action.isNotBlank()) "  $action" else ""
                val subLines = wrap(sub, t2, cw)
                val height = lines.size * 76 * u + 16 * u + subLines.size * 46 * u
                if (!spec.limitTop && !spec.sticker) {
                    cv.drawRect(0f, h * 0.5f, w, h, Paint().apply { shader = LinearGradient(0f, h * 0.5f, 0f, h, 0x00000000, 0xB3000000.toInt(), Shader.TileMode.CLAMP) })
                }
                var y = topOf(height)
                lines.forEach { cv.drawText(it, x, y + 62 * u, t1); y += 76 * u }
                y += 16 * u
                subLines.forEach { cv.drawText(it, x, y + 36 * u, t2); y += 46 * u }
            }
            ShipLimitStyle.Card -> {
                // Light card: gauge + title, the resume time, then View details, Auto-continue and a button.
                val pad = 44 * u
                val height = pad + 48 * u + 22 * u + 40 * u + 34 * u + 62 * u + pad
                val top = topOf(height)
                val r = RectF(x, top, x + cw, top + height)
                cv.drawRoundRect(r, 34 * u, 34 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); setShadowLayer(40 * u, 0f, 10 * u, 0x40000000) })
                cv.drawRoundRect(r, 34 * u, 34 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * u; color = 0x14000000 })
                var y = top + pad
                drawGauge(cv, x + pad + 17 * u, y + 24 * u, 15 * u, 0xFFC8483F.toInt(), u)
                cv.drawText("Usage limit reached", x + pad + 52 * u, y + 37 * u, sans(37f, 600, 0xFF1F1F1F.toInt()))
                y += 48 * u + 22 * u
                cv.drawText("Auto-resuming at $reset", x + pad, y + 31 * u, sans(31f, 400, 0xFF8C8C8C.toInt()))
                y += 40 * u + 34 * u
                val rowH = 62 * u
                val small = sans(27f, 500, 0xFF2A2A2A.toInt())
                var bx = x + pad
                val chipW = small.measureText("View details") + 40 * u
                cv.drawRoundRect(RectF(bx, y, bx + chipW, y + rowH), 14 * u, 14 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDEDEB.toInt() })
                cv.drawText("View details", bx + 20 * u, y + rowH * 0.64f, small)
                bx += chipW + 26 * u
                val btnW = if (action.isBlank()) 0f else small.measureText(action) + 40 * u
                val box = 30 * u
                val auto = "Auto-continue when limits reset"
                val roomForAuto = x + cw - pad - btnW - 20 * u - bx - box - 14 * u
                if (roomForAuto > 120 * u) {
                    val by = y + (rowH - box) / 2
                    cv.drawRoundRect(RectF(bx, by, bx + box, by + box), 7 * u, 7 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2F7BF6.toInt() })
                    val tick = Path().apply { moveTo(bx + box * 0.24f, by + box * 0.52f); lineTo(bx + box * 0.43f, by + box * 0.7f); lineTo(bx + box * 0.78f, by + box * 0.3f) }
                    cv.drawPath(tick, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4 * u; color = 0xFFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND })
                    var label = auto
                    val lp = sans(27f, 400, 0xFF2A2A2A.toInt())
                    while (lp.measureText(label) > roomForAuto && label.length > 4) label = label.dropLast(2).trimEnd() + "…"
                    cv.drawText(label, bx + box + 14 * u, y + rowH * 0.64f, lp)
                }
                if (btnW > 0f) {
                    val rx = x + cw - pad - btnW
                    val br = RectF(rx, y, rx + btnW, y + rowH)
                    cv.drawRoundRect(br, 14 * u, 14 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
                    cv.drawRoundRect(br, 14 * u, 14 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * u; color = 0xFFD6D6D3.toInt() })
                    cv.drawText(action, rx + 20 * u, y + rowH * 0.64f, small)
                }
            }
            ShipLimitStyle.Banner -> {
                // Amber banner: warning sign, the message with an underlined link, and a close ×.
                val pad = 34 * u
                val amber = 0xFFF0B45A.toInt()
                val p = sans(31f, 400, amber)
                val lineH = 46 * u
                val textX = x + pad + 40 * u + 22 * u
                val maxW = x + cw - pad - 50 * u - textX
                val words = "You've hit your limit for $agent messages. Limits will reset at $reset.".split(" ").map { it to false } +
                    action.split(" ").filter { it.isNotBlank() }.map { it to true }.let { l -> if (l.isEmpty()) l else l.dropLast(1) + (l.last().first + "." to true) }
                val textH = richText(null, words, textX, 0f, maxW, p, lineH, amber)
                val height = pad * 2 + textH
                val top = topOf(height)
                val r = RectF(x, top, x + cw, top + height)
                cv.drawRoundRect(r, 26 * u, 26 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF24A2F0B.toInt() })
                cv.drawRoundRect(r, 26 * u, 26 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * u; color = 0xFFB5822E.toInt() })
                val tx = x + pad; val ty = top + pad + 4 * u; val ts = 38 * u
                val tri = Path().apply { moveTo(tx + ts / 2, ty); lineTo(tx + ts, ty + ts * 0.88f); lineTo(tx, ty + ts * 0.88f); close() }
                val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * u; color = amber; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
                cv.drawPath(tri, stroke)
                cv.drawLine(tx + ts / 2, ty + ts * 0.32f, tx + ts / 2, ty + ts * 0.58f, stroke)
                cv.drawPoint(tx + ts / 2, ty + ts * 0.72f, stroke)
                richText(cv, words, textX, top + pad, maxW, p, lineH, amber)
                val cx = x + cw - pad - 16 * u; val cy = top + pad + 22 * u; val k = 12 * u
                cv.drawLine(cx - k, cy - k, cx + k, cy + k, stroke); cv.drawLine(cx + k, cy - k, cx - k, cy + k, stroke)
            }
            ShipLimitStyle.Dialog -> {
                // Dark dialog: gauge + "5-hour limit reached", the hint, and a light button bottom right.
                val pad = 46 * u
                val hint = sans(32f, 400, 0xFF9B9B9B.toInt())
                val hintText = if (spec.resetAt.isBlank()) "Try again after your 5-hour limit resets." else "Try again at $reset."
                val hintLines = wrap(hintText, hint, cw - pad * 2)
                val btnH = if (action.isBlank()) 0f else 70 * u
                val height = pad + 50 * u + 28 * u + hintLines.size * 46 * u + (if (btnH > 0) 40 * u + btnH else 0f) + pad
                val top = topOf(height)
                val r = RectF(x, top, x + cw, top + height)
                cv.drawRoundRect(r, 34 * u, 34 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF51B1B1B.toInt() })
                cv.drawRoundRect(r, 34 * u, 34 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * u; color = 0xFF2E2E2E.toInt() })
                var y = top + pad
                drawGauge(cv, x + pad + 18 * u, y + 25 * u, 16 * u, 0xFFE8685F.toInt(), u)
                cv.drawText("5-hour limit reached", x + pad + 56 * u, y + 38 * u, sans(38f, 500, 0xFFF2F2F2.toInt()))
                y += 50 * u + 28 * u
                hintLines.forEach { cv.drawText(it, x + pad, y + 34 * u, hint); y += 46 * u }
                if (btnH > 0) {
                    y += 40 * u
                    val bp = sans(32f, 500, 0xFF111111.toInt())
                    val bw = bp.measureText(action) + 52 * u
                    val br = RectF(x + cw - pad - bw, y, x + cw - pad, y + btnH)
                    cv.drawRoundRect(br, 18 * u, 18 * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF0F0EE.toInt() })
                    cv.drawText(action, br.left + 26 * u, br.top + btnH * 0.66f, bp)
                }
            }
        }
        spec.logo?.let { drawLogo(cv, it, if (spec.limitTop) ShipLogoCorner.Bottom else spec.logoCorner, w.toInt(), h.toInt(), u, spec.stamp != ShipStamp.Off) }
        drawStamp(cv, spec, stats, zone, w.toInt(), h.toInt(), u)
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

    /**
     * The OpenShortcuts mark at [size]: the app tile in [color] with the token (the blue word in a
     * sentence) cut out of it, so the photo shows through, the same shape as the app icon.
     */
    private fun drawMark(cv: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val layer = cv.saveLayer(x - 1, y - 1, x + size + 1, y + size + 1, null)
        cv.drawRoundRect(RectF(x, y, x + size, y + size), size * 0.23f, size * 0.23f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        // The 256-unit mark, scaled to fill two thirds of the tile.
        val s = size / 256f * 0.66f
        fun gx(v: Float) = x + size / 2 + (v - 128f) * s
        fun gy(v: Float) = y + size / 2 + (v - 128f) * s
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF000000.toInt(); blendMode = android.graphics.BlendMode.DST_OUT }
        val line = Paint(cut).apply { style = Paint.Style.STROKE; strokeWidth = 20f * s; strokeCap = Paint.Cap.ROUND; alpha = 0x6B }
        cv.drawLine(gx(52f), gy(82f), gx(204f), gy(82f), line)
        cv.drawLine(gx(52f), gy(128f), gx(82f), gy(128f), line)
        cv.drawLine(gx(52f), gy(174f), gx(164f), gy(174f), line)
        cv.drawRoundRect(RectF(gx(104f), gy(104f), gx(208f), gy(152f)), 24f * s, 24f * s, cut)
        cv.restoreToCount(layer)
    }

    private fun drawStamp(cv: Canvas, spec: ShipSpec, stats: ShipStats, zone: ZoneId, w: Int, h: Int, u: Float) {
        val stamp = spec.stamp
        val date = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy").format(stats.to.atZone(zone))
        val ink = 0xE6FFFFFF.toInt()
        val name = spec.stampText.trim().ifBlank { "OpenShortcuts" }
        val meta = listOfNotNull(
            // The AI Limit card isn't about commits, so its mark leaves the count out.
            if (stats.commits > 0 && spec.persona.layout != ShipLayout.Limit) "${stats.commits} commits" else null,
            spec.handle.takeIf { it.isNotBlank() }?.let { "@$it" },
            date,
        ).joinToString("  ·  ")
        when (stamp) {
            ShipStamp.Off -> Unit
            ShipStamp.Signature -> {
                // Like a phone camera's signature: the name, then a quiet line of details, bottom left.
                val x = 80 * u
                val y2 = h - 70 * u
                cv.drawText(name, x, y2 - 44 * u, text(40 * u, 500, 0xFFFFFFFF.toInt()).apply { letterSpacing = 0.01f })
                cv.drawText(meta.uppercase(), x, y2, text(20 * u, 500, 0xBFFFFFFF.toInt(), mono).apply { letterSpacing = 0.12f })
            }
            ShipStamp.Frame -> {
                // A white strip along the bottom, like a camera frame: name and details left, the mark right.
                val bar = 170 * u
                val top = h - bar
                cv.drawRect(0f, top, w.toFloat(), h.toFloat(), Paint().apply { color = 0xFFFFFFFF.toInt() })
                val x = 64 * u
                cv.drawText(name, x, top + 76 * u, text(40 * u, 600, 0xFF111111.toInt(), shadow = false).apply { letterSpacing = 0.01f })
                cv.drawText(meta.uppercase(), x, top + 120 * u, text(19 * u, 500, 0xFF8A8A8A.toInt(), mono, shadow = false).apply { letterSpacing = 0.12f })
                val mark = 40 * u
                val mx = w - 64 * u - mark
                drawMark(cv, mx, top + (bar - mark) / 2, mark, 0xFF111111.toInt())
                val div = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD5D5D5.toInt(); strokeWidth = 2 * u }
                cv.drawLine(mx - 28 * u, top + 52 * u, mx - 28 * u, top + bar - 52 * u, div)
                val tag = text(18 * u, 600, 0xFF6E6E6E.toInt(), monoBold, shadow = false).apply { letterSpacing = 0.18f; textAlign = Paint.Align.RIGHT }
                cv.drawText("STRACODE", mx - 52 * u, top + bar / 2 + 7 * u, tag)
            }
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
                cv.drawText("STRACODE Nº $no  ·  $date", x + 40 * u, y2, text(19 * u, 500, 0xB3FFFFFF.toInt(), mono).apply { letterSpacing = 0.14f })
            }
        }
    }
}
