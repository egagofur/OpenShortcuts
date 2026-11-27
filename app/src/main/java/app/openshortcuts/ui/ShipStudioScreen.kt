package app.openshortcuts.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.openshortcuts.engine.ShipData
import app.openshortcuts.engine.ShipSource
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.SHIP_AI_NAMES
import app.openshortcuts.model.SHIP_LANGUAGES
import app.openshortcuts.model.ShipPeriod
import app.openshortcuts.model.ShipPersona
import app.openshortcuts.model.ShipStats
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOGO_FILE = "ship_logo.png"

/** Export is rendered at twice the 1080 grid: 2160×3840 for a story, sharp on any screen. */
private const val EXPORT_SCALE = 2f

/** The editor's tools, along the bottom like a photo editor. */
private enum class Tool(val label: String, val glyph: String) {
    Style("Style", "palette"), Text("Text", "type"), Photo("Photo", "image"), Tags("Tags", "tag"), Brand("Brand", "stamp"),
}

/** Photo framing, kept apart from the rest so a pinch never touches the text overlay. */
private data class Frame(val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f)

/** A card's settings as plain values (everything but the logo bitmap, which lives in its own file). */
private fun specToMap(s: ShipSpec): Map<String, Any?> = mapOf(
    "persona" to s.persona.ordinal, "filter" to s.filter.ordinal, "aspect" to s.aspect.ordinal, "accent" to s.accent.ordinal,
    "period" to s.period.ordinal, "headlineIdx" to s.headlineIdx, "kicker" to s.kicker, "headline" to s.headline, "sub" to s.sub,
    "handle" to s.handle, "languages" to ArrayList(s.languages), "tagStyle" to s.tagStyle.ordinal,
    "agents" to s.agents?.let { ArrayList(it) }, "showAi" to s.showAi, "stamp" to s.stamp.ordinal, "sticker" to s.sticker,
    "dim" to s.dim, "logoCorner" to s.logoCorner.ordinal, "font" to s.font, "texts" to textsToJson(s.texts),
    "limitStyle" to s.limitStyle.ordinal, "limitTop" to s.limitTop, "limitAction" to s.limitAction, "resetAt" to s.resetAt,
    "stampText" to s.stampText,
)

/** Reads [specToMap]'s values back; anything missing or out of range keeps its default, so old saves still open. */
@Suppress("UNCHECKED_CAST")
private fun specFromMap(m: Map<String, Any?>): ShipSpec {
    val d = ShipSpec()
    fun int(k: String) = (m[k] as? Number)?.toInt()
    fun <E> pick(k: String, all: List<E>, def: E): E = int(k)?.let { all.getOrNull(it) } ?: def
    fun str(k: String) = m[k] as? String
    fun list(k: String): List<String>? = (m[k] as? List<*>)?.map { it.toString() }
    return ShipSpec(
        persona = pick("persona", ShipPersona.entries, d.persona), filter = pick("filter", ShipFilter.entries, d.filter),
        aspect = pick("aspect", ShipAspect.entries, d.aspect), accent = pick("accent", ShipAccent.entries, d.accent),
        period = pick("period", ShipPeriod.entries, d.period), headlineIdx = int("headlineIdx") ?: 0,
        kicker = str("kicker"), headline = str("headline"), sub = str("sub"),
        handle = str("handle").orEmpty(), languages = list("languages").orEmpty(), tagStyle = pick("tagStyle", ShipTagStyle.entries, d.tagStyle),
        agents = list("agents"), showAi = m["showAi"] as? Boolean ?: true, stamp = pick("stamp", ShipStamp.entries, d.stamp),
        sticker = m["sticker"] as? Boolean ?: false, dim = (m["dim"] as? Number)?.toFloat() ?: d.dim,
        logoCorner = pick("logoCorner", ShipLogoCorner.entries, d.logoCorner),
        font = str("font") ?: ShipFonts.SYSTEM, texts = textsFromJson(str("texts")),
        limitStyle = pick("limitStyle", app.openshortcuts.model.ShipLimitStyle.entries, d.limitStyle),
        limitTop = m["limitTop"] as? Boolean ?: false, limitAction = str("limitAction"), resetAt = str("resetAt").orEmpty(),
        stampText = str("stampText") ?: d.stampText,
    )
}

private fun specToJson(s: ShipSpec): org.json.JSONObject = org.json.JSONObject().apply {
    specToMap(s).forEach { (k, v) -> put(k, when (v) { null -> org.json.JSONObject.NULL; is List<*> -> org.json.JSONArray(v); else -> v }) }
}

private fun specFromJson(o: org.json.JSONObject): ShipSpec = specFromMap(o.keys().asSequence().associateWith { k ->
    when (val v = o.get(k)) { org.json.JSONObject.NULL -> null; is org.json.JSONArray -> (0 until v.length()).map { v.getString(it) }; else -> v }
})

/** Saves a card's settings across process death. */
private val SpecSaver = Saver<ShipSpec, String>(save = { specToJson(it).toString() }, restore = { specFromJson(org.json.JSONObject(it)) })

/**
 * Each card remembers how you left it (its settings, photo and framing) until you reset it, in the
 * app's private storage. The photo is copied in, so it's still there next time.
 */
private object ShipCardStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "ship_cards.json")
    private fun all(ctx: Context) = runCatching { org.json.JSONObject(file(ctx).readText()) }.getOrElse { org.json.JSONObject() }

    fun photoFile(ctx: Context, key: String): File =
        File(File(ctx.filesDir, "ship_photos").apply { mkdirs() }, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg")

    class Saved(val spec: ShipSpec, val frame: Frame, val photo: File?)

    fun load(ctx: Context, key: String): Saved? = runCatching {
        val o = all(ctx).optJSONObject(key) ?: return null
        val f = o.optJSONArray("frame")
        Saved(
            specFromJson(o.getJSONObject("spec")),
            if (f != null && f.length() == 3) Frame(f.getDouble(0).toFloat(), f.getDouble(1).toFloat(), f.getDouble(2).toFloat()) else Frame(),
            photoFile(ctx, key).takeIf { o.optBoolean("photo") && it.exists() },
        )
    }.getOrNull()

    @Synchronized
    fun save(ctx: Context, key: String, spec: ShipSpec, frame: Frame, hasPhoto: Boolean) {
        val root = all(ctx)
        root.put(key, org.json.JSONObject().put("spec", specToJson(spec))
            .put("frame", org.json.JSONArray(listOf(frame.zoom.toDouble(), frame.panX.toDouble(), frame.panY.toDouble())))
            .put("photo", hasPhoto))
        val tmp = File(ctx.filesDir, "ship_cards.json.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(file(ctx))
    }

    /** Copies a picked or taken photo in, so the card still has it after the picker's access ends. */
    fun keepPhoto(ctx: Context, key: String, uri: Uri): Uri? = runCatching {
        val dst = photoFile(ctx, key)
        ctx.contentResolver.openInputStream(uri)?.use { input -> dst.outputStream().use { input.copyTo(it) } } ?: return null
        Uri.fromFile(dst).buildUpon().appendQueryParameter("v", System.currentTimeMillis().toString()).build()
    }.getOrNull()
}

private fun textsToJson(texts: List<ShipText>): String = org.json.JSONArray(texts.map { t ->
    org.json.JSONObject().put("id", t.id).put("text", t.text).put("font", t.font).put("color", t.color).put("size", t.size.toDouble())
        .put("x", t.x.toDouble()).put("y", t.y.toDouble()).put("rotation", t.rotation.toDouble()).put("style", t.style.name)
}).toString()

private fun textsFromJson(json: String?): List<ShipText> = runCatching {
    val a = org.json.JSONArray(json ?: return emptyList())
    (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        ShipText(
            id = o.getLong("id"), text = o.getString("text"), font = o.optString("font", ShipFonts.SYSTEM), color = o.getInt("color"),
            size = o.getDouble("size").toFloat(), x = o.getDouble("x").toFloat(), y = o.getDouble("y").toFloat(),
            rotation = o.optDouble("rotation", 0.0).toFloat(), style = runCatching { ShipTextStyle.valueOf(o.getString("style")) }.getOrDefault(ShipTextStyle.Shadow),
        )
    }
}.getOrDefault(emptyList())

/** Is [pos] (card pixels) on sticker [t]? Tested in the sticker's own rotated frame, with a little slack for fingers. */
private fun hits(t: ShipText, pos: Offset, w: Float, h: Float, slack: Float): Boolean {
    val r = ShipCardRenderer.textBounds(t, w, h)
    val rad = Math.toRadians(-t.rotation.toDouble())
    val dx = pos.x - r.centerX(); val dy = pos.y - r.centerY()
    val lx = (dx * kotlin.math.cos(rad) - dy * kotlin.math.sin(rad)).toFloat()
    val ly = (dx * kotlin.math.sin(rad) + dy * kotlin.math.cos(rad)).toFloat()
    return kotlin.math.abs(lx) <= r.width() / 2 + slack && kotlin.math.abs(ly) <= r.height() / 2 + slack
}

private val FrameSaver = Saver<Frame, List<Float>>(save = { listOf(it.zoom, it.panX, it.panY) }, restore = { Frame(it[0], it[1], it[2]) })

/**
 * Make a Ship Card: a full-screen editor with the card edge to edge, your commit stats on a photo
 * (taken here or picked), and a tool bar along the bottom. The photo moves live on the GPU; the
 * words are drawn once per change. Export renders the whole card again at full resolution.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShipStudioScreen(adapterId: String, widgetId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val adapter = remember(adapterId) { AdapterStore.get(adapterId) }
    val widget = remember(adapter, widgetId) { adapter?.tabs?.flatMap { it.widgets }?.firstOrNull { it.id == widgetId } }

    var data by remember { mutableStateOf<ShipData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val cardKey = "$adapterId/$widgetId"
    val saved = remember(cardKey) { ShipCardStore.load(ctx, cardKey) }
    var spec by rememberSaveable(stateSaver = SpecSaver) { mutableStateOf(saved?.spec ?: ShipSpec()) }
    var frame by rememberSaveable(stateSaver = FrameSaver) { mutableStateOf(saved?.frame ?: Frame()) }
    var hackHours by rememberSaveable { mutableStateOf(36) }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(saved?.photo?.let { Uri.fromFile(it) }) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
    var overlay by remember { mutableStateOf<ImageBitmap?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    /** The text sticker being edited, if any. */
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var fontsVersion by remember { mutableStateOf(0) }
    remember { ShipFonts.init(ctx) }

    BackHandler(enabled = tool != null) { tool = null }

    LaunchedEffect(adapter, widget) {
        if (adapter == null || widget == null) { error = "This card's workspace is gone."; return@LaunchedEffect }
        ShipSource.load(adapter, widget)
            .onSuccess { d -> data = d; if (spec.handle.isBlank()) spec = spec.copy(handle = d.handle) }
            .onFailure { error = it.message ?: "Couldn't load your commits." }
    }
    // Your logo is kept in the app's private storage; it never leaves the phone.
    LaunchedEffect(Unit) {
        val f = File(ctx.filesDir, LOGO_FILE)
        if (f.exists()) withContext(Dispatchers.IO) { android.graphics.BitmapFactory.decodeFile(f.path) }?.let { spec = spec.copy(logo = it) }
    }

    val zone = remember { ZoneId.systemDefault() }
    fun stats(): ShipStats {
        val (from, to) = ShipStats.window(spec.period, Instant.now(), zone, hackHours)
        return ShipStats.from(data?.commits.orEmpty(), from, to, zone)
    }

    // The photo is decoded once at story size for the live preview; export decodes again at full size.
    LaunchedEffect(photoUri) {
        photo = photoUri?.let { uri -> withContext(Dispatchers.IO) { ShipCardRenderer.loadPhoto(ctx, uri, 1080, 1920) } }
    }
    // The words, route and stamp: redrawn only when something other than the framing changes.
    LaunchedEffect(spec.overlayKey(), data, hackHours) {
        overlay = withContext(Dispatchers.Default) { ShipCardRenderer.render(spec, stats(), null, 0.5f, zone, withPhoto = false, withTexts = false).asImageBitmap() }
    }

    fun usePhoto(uri: Uri) {
        scope.launch {
            val kept = withContext(Dispatchers.IO) { ShipCardStore.keepPhoto(ctx, cardKey, uri) }
            photoUri = kept ?: uri; frame = Frame(); spec = spec.copy(sticker = false)
        }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let(::usePhoto)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) usePhoto(uri)
    }
    // How you left it is how it opens next time.
    LaunchedEffect(spec, frame, photoUri) {
        kotlinx.coroutines.delay(400)
        val has = photoUri?.scheme == "file"
        withContext(Dispatchers.IO) { ShipCardStore.save(ctx, cardKey, spec, frame, has) }
    }
    val pickLogo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                ShipCardRenderer.loadPhoto(ctx, uri, 600, 600)?.also { b ->
                    File(ctx.filesDir, LOGO_FILE).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            if (bmp != null) spec = spec.copy(logo = bmp) else note = "Couldn't read that image."
        }
    }

    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            withContext(Dispatchers.IO) { ShipFonts.import(ctx, uri) }
                .onSuccess { f ->
                    fontsVersion++
                    val sel = selected
                    spec = if (sel != null) spec.copy(texts = spec.texts.map { if (it.id == sel) it.copy(font = f.key) else it }) else spec.copy(font = f.key)
                    note = "Added ${f.label}. It stays on this phone."
                }
                .onFailure { note = it.message ?: "Couldn't add that font." }
        }
    }

    fun updateText(id: Long, f: (ShipText) -> ShipText) { spec = spec.copy(texts = spec.texts.map { if (it.id == id) f(it) else it }) }

    fun export(share: Boolean) {
        if (busy != null) return
        busy = if (share) "Preparing…" else "Saving…"
        scope.launch {
            val full = spec.copy(zoom = frame.zoom, panX = frame.panX, panY = frame.panY)
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    val w = (full.aspect.w * EXPORT_SCALE).toInt(); val h = (full.aspect.h * EXPORT_SCALE).toInt()
                    // Decode enough pixels for the zoomed-in framing, never more than the original has.
                    // Enough pixels for the zoomed framing, capped near 24 MP so a 50 MP photo can't run the phone out of memory.
                    val z = full.zoom.coerceAtMost(kotlin.math.sqrt(24_000_000f / (w * h)).coerceAtLeast(1f))
                    val src = if (full.sticker) null else photoUri?.let { ShipCardRenderer.loadPhoto(ctx, it, (w * z).toInt(), (h * z).toInt()) }
                    val bmp = ShipCardRenderer.render(full, stats(), src, EXPORT_SCALE, zone)
                    withContext(Dispatchers.IO) { if (share) shareImage(ctx, bmp, full.sticker) else saveImage(ctx, bmp, full.sticker) }
                }
            }
            busy = null
            result.onSuccess { view.confirm(); if (!share) note = "Saved to Photos › Pictures/OpenShortcuts" }
                .onFailure { note = "Couldn't ${if (share) "share" else "save"}: ${it.message}" }
        }
    }

    fun reframe(f: Frame) {
        val p = photo ?: return
        val z = f.zoom.coerceIn(1f, 4f)
        val (mx, my) = ShipCardRenderer.panLimits(p.width, p.height, spec.aspect.w, spec.aspect.h, z)
        frame = Frame(z, f.panX.coerceIn(-mx, mx), f.panY.coerceIn(-my, my))
    }

    val typing = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Card above, the open tool's panel and the tool bar below; the keyboard pushes all of it up.
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom))) {
            // A story runs edge to edge under the floating bar, like Instagram's editor; feed and square
            // cards start below it so the bar never covers their first line.
            // The card starts below the status bar so the clock and battery never sit on it. A story runs
            // under the floating bar, like Instagram's editor; feed and square start below it.
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top))
                    .then(if (spec.aspect == ShipAspect.Story) Modifier else Modifier.padding(top = 60.dp)),
                contentAlignment = Alignment.TopCenter,
            ) {
                val ratio = spec.aspect.w.toFloat() / spec.aspect.h
                val cardW = min(maxWidth.value, maxHeight.value * ratio).dp
                val edge = cardW.value >= maxWidth.value - 1
                Box(
                    Modifier.size(cardW, (cardW.value / ratio).dp)
                        .clip(RoundedCornerShape(if (edge) 0.dp else 18.dp))
                        .background(if (spec.sticker) Color(0xFF2A2F3A) else Color.Black)
                        .pointerInput(spec.aspect) {
                            // One finger or two: on a text sticker it moves, scales and turns the sticker;
                            // anywhere else it frames the photo. A tap selects a sticker or closes the panel.
                            var lastTap = 0L
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val w = size.width.toFloat(); val h = size.height.toFloat()
                                val hit = spec.texts.lastOrNull { hits(it, down.position, w, h, 12.dp.toPx()) }
                                var moved = false
                                var travel = Offset.Zero
                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan(); val zoom = event.calculateZoom(); val rot = event.calculateRotation()
                                    travel += pan
                                    if (!moved && (travel.getDistance() > viewConfiguration.touchSlop || event.changes.size > 1)) moved = true
                                    if (moved) {
                                        if (hit != null) {
                                            if (selected != hit.id) selected = hit.id
                                            updateText(hit.id) {
                                                it.copy(
                                                    x = (it.x + pan.x / w).coerceIn(0.02f, 0.98f), y = (it.y + pan.y / h).coerceIn(0.02f, 0.98f),
                                                    size = (it.size * zoom).coerceIn(18f, 420f), rotation = (it.rotation + rot) % 360f,
                                                )
                                            }
                                        } else if (photo != null && !spec.sticker) {
                                            reframe(Frame(frame.zoom * zoom, frame.panX + pan.x / w, frame.panY + pan.y / h))
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (!moved) {
                                    val now = System.currentTimeMillis()
                                    when {
                                        hit != null -> { view.tick(); selected = hit.id; tool = Tool.Text }
                                        now - lastTap < 300 && photo != null -> reframe(Frame())
                                        else -> { selected = null; tool = null }
                                    }
                                    lastTap = now
                                }
                            }
                        },
                ) {
                    LivePhoto(photo, spec, frame)
                    overlay?.let { Image(it, "Stracode preview", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
                    LiveTexts(spec.texts, selected.takeIf { tool == Tool.Text })
                }
            }
            AnimatedContent(tool, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "panel") { t ->
                if (t != null) Panel(if (typing) 0.26f else 0.42f) {
                    when (t) {
                        Tool.Style -> StylePanel(spec, hackHours, data, error, adapter?.name.orEmpty(), stats(), { spec = it }) { hackHours = it }
                        Tool.Text -> TextPanel(
                            spec, stats(), zone, selected, fontsVersion, onChange = { spec = it },
                            onSelect = { selected = it },
                            onImportFont = { pickFont.launch(arrayOf("*/*")) },
                            onRemoveFont = { key ->
                                ShipFonts.delete(key); fontsVersion++
                                spec = spec.copy(font = if (spec.font == key) ShipFonts.SYSTEM else spec.font,
                                    texts = spec.texts.map { if (it.font == key) it.copy(font = ShipFonts.SYSTEM) else it })
                            },
                        )
                        Tool.Photo -> PhotoPanel(
                            spec, hasPhoto = photo != null, onChange = { spec = it },
                            onCamera = {
                                val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
                                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "camera_${System.currentTimeMillis()}.jpg"))
                                cameraUri = uri
                                takePhoto.launch(uri)
                            },
                            onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        )
                        Tool.Tags -> TagsPanel(spec, stats()) { spec = it }
                        Tool.Brand -> BrandPanel(
                            spec, onChange = { spec = it },
                            onPickLogo = { pickLogo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onRemoveLogo = { File(ctx.filesDir, LOGO_FILE).delete(); spec = spec.copy(logo = null) },
                        )
                    }
                }
            }
            // While typing, the keyboard takes the tool bar's place so the card stays big enough to read.
            if (!typing) ToolBar(tool) { t -> view.tick(); tool = if (tool == t) null else t; if (tool != Tool.Text) selected = null }
        }
        // Floating over the card's top edge, clear of the status bar.
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassCircle("chevronLeft", "Back", onClick = onBack)
            Spacer(Modifier.weight(1f))
            note?.let {
                Text(it, style = Theme.t.small, color = Color.White, maxLines = 2,
                    modifier = Modifier.weight(3f).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 6.dp))
                LaunchedEffect(it) { kotlinx.coroutines.delay(3500); note = null }
                Spacer(Modifier.width(8.dp))
            }
            TopPill(if (busy == "Saving…") "Saving…" else "Save", filled = false) { export(share = false) }
            Spacer(Modifier.width(8.dp))
            TopPill(if (busy == "Preparing…") "Preparing…" else "Share", filled = true) { export(share = true) }
        }
    }
}

/** The photo, filtered and framed, drawn straight on the GPU so pinch and drag follow the finger. */
@Composable
private fun LivePhoto(photo: Bitmap?, spec: ShipSpec, frame: Frame) {
    val image = remember(photo) { photo?.asImageBitmap() }
    val filter = remember(spec.filter) { ShipCardRenderer.matrixOf(spec.filter)?.let { ColorFilter.colorMatrix(ColorMatrix(it)) } }
    Canvas(Modifier.fillMaxSize()) {
        if (spec.sticker) return@Canvas
        if (image == null || photo == null) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF0E1116), Color(0xFF1D2433)), Offset.Zero, Offset(size.width, size.height)))
            return@Canvas
        }
        val r = ShipCardRenderer.photoRect(photo.width, photo.height, size.width, size.height, spec.copy(zoom = frame.zoom, panX = frame.panX, panY = frame.panY))
        drawImage(
            image, srcOffset = IntOffset.Zero, srcSize = IntSize(photo.width, photo.height),
            dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(r.width().toInt(), r.height().toInt()),
            colorFilter = filter,
        )
        if (spec.dim > 0f) drawRect(Color.Black.copy(alpha = spec.dim.coerceIn(0f, 0.7f)))
    }
}

/** Text stickers drawn live with the export's own code, so moving one never redraws the rest. */
@Composable
private fun LiveTexts(texts: List<ShipText>, selected: Long?) {
    if (texts.isEmpty()) return
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            texts.forEach { ShipCardRenderer.drawText(nc, it, size.width, size.height) }
            texts.firstOrNull { it.id == selected }?.let { t ->
                val r = ShipCardRenderer.textBounds(t, size.width, size.height)
                nc.save()
                nc.rotate(t.rotation, r.centerX(), r.centerY())
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.5.dp.toPx(); color = 0xE6FFFFFF.toInt()
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
                }
                nc.drawRoundRect(r, 8.dp.toPx(), 8.dp.toPx(), p)
                nc.restore()
            }
        }
    }
}

@Composable
private fun TopPill(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).pressable(scale = 0.96f, label = text, onClick = onClick)
            .clip(CircleShape).background(if (filled) Color.White else Color(0x66000000))
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Theme.t.label, color = if (filled) Color.Black else Color.White, maxLines = 1) }
}

@Composable
private fun ToolBar(open: Tool?, onTool: (Tool) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.Black).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        Tool.entries.forEach { t ->
            val on = t == open
            Column(
                Modifier.width(64.dp).pressable(scale = 0.96f, label = t.label, role = androidx.compose.ui.semantics.Role.Tab) { onTool(t) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Glyph(t.glyph, if (on) Color.White else Color(0x99FFFFFF), 22.dp)
                Spacer(Modifier.height(4.dp))
                Text(t.label, style = Theme.t.small, color = if (on) Color.White else Color(0x99FFFFFF), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Panel(heightFrac: Float, content: @Composable ColumnScope.() -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * heightFrac).dp
    Column(
        Modifier.fillMaxWidth().heightIn(max = maxH)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(Theme.c.card)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}

// ---- panels ----

@Composable
private fun StylePanel(
    spec: ShipSpec, hackHours: Int, data: ShipData?, error: String?, source: String, stats: ShipStats,
    onChange: (ShipSpec) -> Unit, onHours: (Int) -> Unit,
) {
    val c = Theme.c
    if (data == null && error == null) SkeletonLine(0.45f)
    else Text(error ?: "$source · ${stats.commits} commits", style = Theme.t.small, color = if (error != null) c.danger else c.ink2)
    Section("Style")
    Chips(ShipPersona.entries.map { it.label }, spec.persona.ordinal) {
        val p = ShipPersona.entries[it]
        // A new style changes the layout; words you wrote yourself stay (reset brings the style's back).
        onChange(spec.copy(
            persona = p, headlineIdx = 0,
            period = when {
                p == ShipPersona.Hackathon -> ShipPeriod.Hackathon
                p == ShipPersona.Heatmap -> ShipPeriod.Months
                spec.period == ShipPeriod.Hackathon || spec.period == ShipPeriod.Months -> ShipPeriod.Today
                else -> spec.period
            },
        ))
    }
    if (spec.persona.layout == app.openshortcuts.model.ShipLayout.Limit) {
        // AI Limit comes in four looks; they're picked right here, with the style.
        Section("Look")
        Chips(app.openshortcuts.model.ShipLimitStyle.entries.map { it.label }, spec.limitStyle.ordinal) {
            onChange(spec.copy(limitStyle = app.openshortcuts.model.ShipLimitStyle.entries[it], limitAction = null))
        }
        Spacer(Modifier.height(8.dp))
        Chips(listOf("Bottom", "Top"), if (spec.limitTop) 1 else 0) { onChange(spec.copy(limitTop = it == 1)) }
        Spacer(Modifier.height(6.dp))
        Text("Button, reset time and wording are in Text.", style = Theme.t.small, color = c.ink3)
    }
    // Only settings that change this style's card are shown: the AI Limit notice has no commit numbers
    // or route line, and the heatmap always covers sixteen weeks.
    val layout = spec.persona.layout
    when (layout) {
        app.openshortcuts.model.ShipLayout.Limit -> Unit
        app.openshortcuts.model.ShipLayout.Heatmap -> {
            Spacer(Modifier.height(10.dp))
            Text("Covers the last 16 weeks.", style = Theme.t.small, color = c.ink3)
        }
        else -> {
            Section("Period")
            val periods = ShipPeriod.entries.filter { it != ShipPeriod.Months }
            Chips(periods.map { it.label }, periods.indexOf(spec.period)) { onChange(spec.copy(period = periods[it])) }
            if (spec.period == ShipPeriod.Hackathon) {
                Spacer(Modifier.height(8.dp))
                val hs = listOf(24, 36, 48, 72)
                Chips(hs.map { "${it}h" }, hs.indexOf(hackHours)) { onHours(hs[it]) }
            }
        }
    }
    Section("Size")
    Chips(ShipAspect.entries.map { it.label }, spec.aspect.ordinal) { onChange(spec.copy(aspect = ShipAspect.entries[it])) }
    if (layout != app.openshortcuts.model.ShipLayout.Limit) Section(if (layout == app.openshortcuts.model.ShipLayout.Heatmap) "Color" else "Line color")
    if (layout != app.openshortcuts.model.ShipLayout.Limit) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ShipAccent.entries.forEach { a ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(a.argb))
                    .then(if (a == spec.accent) Modifier.border(3.dp, c.ink, CircleShape) else Modifier)
                    .pressable(scale = 0.96f, label = a.label) { onChange(spec.copy(accent = a)) },
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    // Back to the style as designed: its words and look. Your name, tags, logo, photo and stickers stay.
    QuietButton("Reset to the style", Modifier.fillMaxWidth()) {
        val d = ShipSpec()
        onChange(spec.copy(
            kicker = null, headline = null, sub = null, headlineIdx = 0, font = d.font, filter = d.filter, accent = d.accent,
            dim = d.dim, stamp = d.stamp, stampText = d.stampText, tagStyle = d.tagStyle,
            limitStyle = d.limitStyle, limitTop = d.limitTop, limitAction = d.limitAction, resetAt = d.resetAt,
        ))
    }
}

/** Text stickers like Phonto (add, type, font, color, style; move them on the card), then the card's own words. */
@Composable
private fun TextPanel(
    spec: ShipSpec, stats: ShipStats, zone: ZoneId, selected: Long?, fontsVersion: Int,
    onChange: (ShipSpec) -> Unit, onSelect: (Long?) -> Unit, onImportFont: () -> Unit, onRemoveFont: (String) -> Unit,
) {
    val c = Theme.c
    val fonts = remember(fontsVersion) { ShipFonts.all() }
    val sticker = spec.texts.firstOrNull { it.id == selected }
    if (sticker != null) {
        fun edit(f: (ShipText) -> ShipText) = onChange(spec.copy(texts = spec.texts.map { if (it.id == sticker.id) f(it) else it }))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text sticker", style = Theme.t.label, color = c.ink, modifier = Modifier.weight(1f))
            Chip("Done", true) { onSelect(null) }
        }
        Spacer(Modifier.height(10.dp))
        Field(sticker.text, { v -> edit { it.copy(text = v.take(80)) } }, "Type something", autofocus = sticker.text.isEmpty())
        Spacer(Modifier.height(6.dp))
        Text("Drag it on the card. Pinch to resize, twist to turn.", style = Theme.t.small, color = c.ink3)
        Section("Font")
        FontChips(fonts, sticker.font, onImportFont, onRemoveFont) { k -> edit { it.copy(font = k) } }
        Section("Color")
        ColorDots(sticker.color, spec.accent.argb) { col -> edit { it.copy(color = col) } }
        Section("Style")
        Chips(ShipTextStyle.entries.map { it.label }, sticker.style.ordinal) { i -> edit { it.copy(style = ShipTextStyle.entries[i]) } }
        Section("Size")
        val sizes = listOf(44f, 72f, 110f, 160f)
        Chips(listOf("S", "M", "L", "XL"), sizes.indexOfFirst { kotlin.math.abs(it - sticker.size) < 1f }) { i -> edit { it.copy(size = sizes[i]) } }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Duplicate", Modifier.weight(1f)) {
                val copy = sticker.copy(id = System.nanoTime(), x = (sticker.x + 0.04f).coerceAtMost(0.98f), y = (sticker.y + 0.04f).coerceAtMost(0.98f))
                onChange(spec.copy(texts = spec.texts + copy)); onSelect(copy.id)
            }
            QuietButton("Delete", Modifier.weight(1f), color = c.danger) { onChange(spec.copy(texts = spec.texts.filter { it.id != sticker.id })); onSelect(null) }
        }
        return
    }

    QuietButton("+ Add text", Modifier.fillMaxWidth()) {
        val t = ShipText(id = System.nanoTime(), text = "", font = spec.font, color = 0xFFFFFFFF.toInt(), y = 0.22f + 0.06f * (spec.texts.size % 5))
        onChange(spec.copy(texts = spec.texts + t)); onSelect(t.id)
    }
    if (spec.texts.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.texts.forEach { t -> Chip(t.text.ifBlank { "Empty text" }.take(18), false) { onSelect(t.id) } }
        }
    }

    val (kicker, headline, sub) = ShipCardRenderer.filledLines(spec, stats, zone)
    val options = ShipCardRenderer.filledHeadlines(spec, stats, zone)
    if (spec.persona.layout == app.openshortcuts.model.ShipLayout.Limit) {
        // The AI-limit notice over your photo: which look, where, and its button.
        Section("Notice")
        Chips(app.openshortcuts.model.ShipLimitStyle.entries.map { it.label }, spec.limitStyle.ordinal) {
            onChange(spec.copy(limitStyle = app.openshortcuts.model.ShipLimitStyle.entries[it], limitAction = null))
        }
        Section("Position")
        Chips(listOf("Bottom", "Top"), if (spec.limitTop) 1 else 0) { onChange(spec.copy(limitTop = it == 1)) }
        Section(if (spec.limitStyle == app.openshortcuts.model.ShipLimitStyle.Banner) "Link" else "Button")
        val presets = listOf("Default", "Upgrade to Max", "Hide")
        val sel = when (spec.limitAction) { null -> 0; "Upgrade to Max" -> 1; "" -> 2; else -> -1 }
        Chips(presets, sel) { onChange(spec.copy(limitAction = when (it) { 0 -> null; 1 -> "Upgrade to Max"; else -> "" })) }
        Spacer(Modifier.height(8.dp))
        Field(spec.limitAction.orEmpty(), { onChange(spec.copy(limitAction = it.take(30).ifEmpty { null })) }, "Or type your own, e.g. Touch grass", autofocus = false)
        Section("Resets at")
        Field(spec.resetAt, { onChange(spec.copy(resetAt = it.take(12))) }, "e.g. 3:40 PM (blank: about two hours on)", autofocus = false)
        Spacer(Modifier.height(6.dp))
        Text("The AI named in the notice is your first AI teammate (Tags).", style = Theme.t.small, color = c.ink3)
        Section("Your name")
        Field(spec.handle, { onChange(spec.copy(handle = it.removePrefix("@").take(30))) }, "e.g. ega", autofocus = false)
        return
    }
    Section("Card font")
    FontChips(fonts, spec.font, onImportFont, onRemoveFont) { onChange(spec.copy(font = it)) }
    Section("Headline")
    options.forEachIndexed { i, line ->
        val on = spec.headline == null && spec.headlineIdx == i
        Box(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp))
                .background(if (on) c.ink else c.fill)
                .pressable(scale = 0.98f, label = line) { onChange(spec.copy(headlineIdx = i, headline = null)) }
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) { Text(line, style = Theme.t.label, color = if (on) c.bg else c.ink) }
    }
    Section("Or write your own")
    Field(headline, { onChange(spec.copy(headline = it.take(70))) }, "Your headline", autofocus = false)
    Spacer(Modifier.height(8.dp))
    // Tap a stat to add it to your headline as it reads now.
    val facts = listOf(
        "${stats.commits} commits", "${(stats.aiShare * 100).toInt()}% AI", ShipStats.spanLabel(stats.span), "${stats.repos} repos",
        "${stats.activeDays} days",
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { f -> Chip("+ $f", false) { onChange(spec.copy(headline = (headline.trimEnd() + " " + f).trim().take(70))) } }
    }
    Section("Your name")
    Field(spec.handle, { onChange(spec.copy(handle = it.removePrefix("@").take(30))) }, "e.g. ega", autofocus = false)
    Section("Small line on top")
    Field(kicker, { onChange(spec.copy(kicker = it.take(50))) }, "e.g. VIBE CODING SESSION", autofocus = false)
    Section("Under the headline")
    Field(sub, { onChange(spec.copy(sub = it.take(60))) }, "e.g. @you", autofocus = false)
    if (spec.kicker != null || spec.headline != null || spec.sub != null) {
        Spacer(Modifier.height(10.dp))
        QuietButton("Reset to the style's words", Modifier.fillMaxWidth()) { onChange(spec.copy(kicker = null, headline = null, sub = null)) }
    }
}

/** Each font shown in itself; your imported ones last, then "Import font". */
@Composable
private fun FontChips(fonts: List<ShipFonts.Face>, selected: String, onImport: () -> Unit, onRemove: (String) -> Unit, onPick: (String) -> Unit) {
    val c = Theme.c
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fonts.forEach { f ->
            val on = f.key == selected
            val family = remember(f.key) { androidx.compose.ui.text.font.FontFamily(ShipFonts.typeface(f.key, 700)) }
            Box(
                Modifier.heightIn(min = 40.dp).pressable(scale = 0.96f, label = f.label) { onPick(f.key) }
                    .clip(RoundedCornerShape(20.dp)).background(if (on) c.ink else c.fill)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(f.label, style = Theme.t.label.copy(fontFamily = family), color = if (on) c.bg else c.ink, maxLines = 1) }
        }
        Chip("+ Import font", false, onImport)
    }
    if (selected.startsWith("file:")) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Imported fonts stay on this phone.", style = Theme.t.small, color = c.ink3, modifier = Modifier.weight(1f))
            Chip("Remove font", false) { onRemove(selected) }
        }
    }
}

@Composable
private fun ColorDots(selected: Int, accent: Int, onPick: (Int) -> Unit) {
    val colors = listOf(0xFFFFFFFF.toInt(), 0xFF0B0B0B.toInt(), accent, 0xFFFFD84D.toInt(), 0xFFFF6B6B.toInt(), 0xFF8EA8FF.toInt(), 0xFF6EE7B7.toInt(), 0xFFFF8FC7.toInt())
        .distinct()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { col ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(col))
                    .border(if (col == selected) 3.dp else 1.dp, if (col == selected) Theme.c.ink else Theme.c.hairline, CircleShape)
                    .pressable(scale = 0.96f, label = "Color") { onPick(col) },
            )
        }
    }
}

@Composable
private fun PhotoPanel(spec: ShipSpec, hasPhoto: Boolean, onChange: (ShipSpec) -> Unit, onCamera: () -> Unit, onGallery: () -> Unit) {
    val c = Theme.c
    // Two clear sources first, then the card's setup as its own section: the same 16dp rhythm as every
    // other section, so the buttons don't sit on the chips.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PhotoSource("camera", "Take photo", Modifier.weight(1f), onCamera)
        PhotoSource("image", "Choose photo", Modifier.weight(1f), onGallery)
    }
    if (hasPhoto && !spec.sticker) {
        Spacer(Modifier.height(10.dp))
        Text("Pinch and drag on the card to frame it. Double-tap resets.", style = Theme.t.small, color = c.ink3)
    }
    Section("Card")
    Chips(listOf("With photo", "Sticker, no photo"), if (spec.sticker) 1 else 0) { onChange(spec.copy(sticker = it == 1)) }
    if (!spec.sticker) {
        Section("Filter")
        Chips(ShipFilter.entries.map { it.label }, spec.filter.ordinal) { onChange(spec.copy(filter = ShipFilter.entries[it])) }
        Section("Dim photo")
        val dims = listOf(0f, 0.2f, 0.4f, 0.6f)
        Chips(listOf("Off", "Low", "Medium", "High"), dims.indexOfFirst { kotlin.math.abs(it - spec.dim) < 0.01f }) { onChange(spec.copy(dim = dims[it])) }
    }
}

/** A tall, icon-first button for a photo source; presses to 0.96 like every control in the editor. */
@Composable
private fun PhotoSource(glyph: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Theme.c
    Column(
        modifier.heightIn(min = 84.dp).pressable(scale = 0.96f, label = label, onClick = onClick)
            .clip(RoundedCornerShape(20.dp)).background(c.fill)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(glyph, c.ink, 24.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, style = Theme.t.label, color = c.ink, maxLines = 1)
    }
}

/** Languages and AI teammates: search the list or type your own. */
@Composable
private fun TagsPanel(spec: ShipSpec, stats: ShipStats, onChange: (ShipSpec) -> Unit) {
    val c = Theme.c
    Section("Languages and tools")
    Chips(ShipTagStyle.entries.map { it.label }, spec.tagStyle.ordinal) { onChange(spec.copy(tagStyle = ShipTagStyle.entries[it])) }
    Spacer(Modifier.height(10.dp))
    SearchPicker(spec.languages, SHIP_LANGUAGES, "Search or type a language", max = 4) { onChange(spec.copy(languages = it)) }

    Section("AI teammates")
    val detected = stats.agents.map { it.first.label }
    val agents = spec.agents ?: detected
    SettingsGroup(surface = c.fill) {
        SettingsRow("Show AI teammates", trailing = { DuoSwitch(spec.showAi) { onChange(spec.copy(showAi = it)) } })
    }
    if (spec.showAi) {
        Spacer(Modifier.height(8.dp))
        if (detected.isNotEmpty()) Text("Found in your commits: ${detected.joinToString(", ")}", style = Theme.t.small, color = c.ink3)
        else Text("No AI co-authors in these commits. Add the ones you used.", style = Theme.t.small, color = c.ink3)
        Spacer(Modifier.height(8.dp))
        SearchPicker(agents, SHIP_AI_NAMES, "Search or type an AI", max = 5) { onChange(spec.copy(agents = it)) }
    }
}

/** Selected items as removable pills, a search field, and matching suggestions (or "Add" what you typed). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPicker(selected: List<String>, all: List<String>, hint: String, max: Int, onChange: (List<String>) -> Unit) {
    val c = Theme.c
    var query by remember { mutableStateOf("") }
    if (selected.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            selected.forEach { s -> Chip("$s  ✕", true) { onChange(selected - s) } }
        }
        Spacer(Modifier.height(10.dp))
    }
    Field(query, { query = it.take(30) }, hint, autofocus = false)
    Spacer(Modifier.height(8.dp))
    val q = query.trim()
    val matches = all.filter { it !in selected && (q.isEmpty() || it.contains(q, ignoreCase = true)) }.take(if (q.isEmpty()) 10 else 12)
    val canAdd = selected.size < max
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (q.isNotEmpty() && all.none { it.equals(q, ignoreCase = true) } && selected.none { it.equals(q, ignoreCase = true) }) {
            Chip("+ Add “$q”", false) { if (canAdd) { onChange(selected + q); query = "" } }
        }
        matches.forEach { m -> Chip(m, false) { if (canAdd) { onChange(selected + m); query = "" } } }
    }
    if (!canAdd) { Spacer(Modifier.height(6.dp)); Text("Up to $max. Remove one to add another.", style = Theme.t.small, color = c.ink3) }
}

@Composable
private fun BrandPanel(spec: ShipSpec, onChange: (ShipSpec) -> Unit, onPickLogo: () -> Unit, onRemoveLogo: () -> Unit) {
    val c = Theme.c
    Section("Made-with mark")
    Chips(ShipStamp.entries.map { it.label }, spec.stamp.ordinal) { onChange(spec.copy(stamp = ShipStamp.entries[it])) }
    if (spec.stamp == ShipStamp.Signature || spec.stamp == ShipStamp.Frame) {
        Spacer(Modifier.height(8.dp))
        Field(spec.stampText, { onChange(spec.copy(stampText = it.take(28))) }, "OpenShortcuts", autofocus = false)
    }
    Section("Your logo")
    Text("Stays on this phone. Nothing is uploaded.", style = Theme.t.small, color = c.ink3)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietButton(if (spec.logo == null) "Add logo" else "Change logo", Modifier.weight(1f), onClick = onPickLogo)
        if (spec.logo != null) QuietButton("Remove", Modifier.weight(1f), color = c.danger, onClick = onRemoveLogo)
    }
    if (spec.logo != null) {
        Spacer(Modifier.height(8.dp))
        Chips(ShipLogoCorner.entries.map { it.label }, spec.logoCorner.ordinal) { onChange(spec.copy(logoCorner = ShipLogoCorner.entries[it])) }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = Theme.t.small, color = Theme.c.ink2)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Chips(labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> Chip(l, i == selected) { onPick(i) } }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    Box(
        Modifier.heightIn(min = 40.dp).pressable(scale = 0.96f, label = text, onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) c.ink else c.fill)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Theme.t.label, color = if (on) c.bg else c.ink, maxLines = 1) }
}

// ---- export ----

private fun encode(bmp: Bitmap, sticker: Boolean, out: java.io.OutputStream) {
    // A sticker keeps its transparency as PNG; a photo card is JPEG at full quality.
    if (sticker) bmp.compress(Bitmap.CompressFormat.PNG, 100, out) else bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
}

private fun saveImage(ctx: Context, bmp: Bitmap, sticker: Boolean) {
    val name = "ship_${System.currentTimeMillis()}.${if (sticker) "png" else "jpg"}"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, if (sticker) "image/png" else "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenShortcuts")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = ctx.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no media store")
    resolver.openOutputStream(uri)?.use { encode(bmp, sticker, it) } ?: error("can't write")
    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
}

private suspend fun shareImage(ctx: Context, bmp: Bitmap, sticker: Boolean) {
    val dir = File(ctx.cacheDir, "shared").apply { mkdirs() }
    val f = File(dir, "ship_card.${if (sticker) "png" else "jpg"}")
    f.outputStream().use { encode(bmp, sticker, it) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (sticker) "image/png" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri("", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        ctx.startActivity(Intent.createChooser(send, "Share Stracode card").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Opens the Ship Card studio for (workspace id, widget id). Provided by the app's navigation. */
val LocalOpenShipCard = androidx.compose.runtime.staticCompositionLocalOf<(String, String) -> Unit> { { _, _ -> } }

/** The share_card widget on a dashboard: a tap into the studio. */
@Composable
fun ShipCardTile(widget: app.openshortcuts.model.DashboardWidget, adapterId: String) {
    val c = Theme.c
    val open = LocalOpenShipCard.current
    val view = LocalView.current
    Card(Modifier.fillMaxWidth().pressable(scale = 0.97f, label = "Open Stracode") { view.tick(); open(adapterId, widget.id) }) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile("laptopRun", widget.tint.color(), 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(widget.title.ifBlank { "Stracode" }, style = Theme.t.label, color = c.ink)
                Text("Your commits as a card to share, like a run", style = Theme.t.small, color = c.ink2)
            }
            Glyph("chevronRight", c.ink3, 18.dp)
        }
    }
}
