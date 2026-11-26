package app.openshortcuts.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.model.Tint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Tour {
    enum class Step(val anchor: String?, val title: String, val message: String, val glyph: String, val tint: Tint, val gotIt: Boolean = false) {
        Welcome(null, "", "", "layers", Tint.Blue),
        NewShortcut("railPlus", "Make your first shortcut", "Tap +, then choose Start from scratch.", "plus", Tint.Blue),
        AddAction("search", "Add an action", "Search for an action, like Do Not Disturb, then tap it to add it.", "search", Tint.Blue),
        EditToken("firstAction", "Make it yours", "Blue words are settings. Tap one to change it, like On or Off.", "sliders", Tint.Indigo, gotIt = true),
        Run("run", "Try it", "Tap ▶ to run your shortcut now.", "playFill", Tint.Green),
        GoBack("railBack", "Now make it automatic", "Tap Back to return to your shortcuts.", "chevronLeft", Tint.Orange),
        OpenAutomation("tabAutomation", "Open Automation", "Shortcuts here run on their own, at a set time or when something happens.", "clock", Tint.Orange),
        NewAutomation("railPlus", "Choose when it runs", "Tap +, then choose a moment, like a time of day or plugging in the charger.", "plus", Tint.Orange),
        PickShortcut("runCard", "Choose your shortcut", "Tap Shortcut, then choose the one you just made.", "layers", Tint.Purple),
        AllSet(null, "", "", "check", Tint.Green),
        Finished(null, "", "", "check", Tint.Green),
    }

    var step by mutableStateOf(Step.Finished); private set
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context, freshInstall: Boolean) {
        val p = ctx.getSharedPreferences("tour", Context.MODE_PRIVATE)
        prefs = p
        if (!p.contains("step")) p.edit().putString("step", (if (freshInstall) Step.Welcome else Step.Finished).name).apply()
        // The marketplace is offered once to new installs; people updating already have their workspaces.
        if (!freshInstall && !p.contains("market_offered")) p.edit().putBoolean("market_offered", true).apply()
        step = runCatching { Step.valueOf(p.getString("step", null)!!) }.getOrDefault(Step.Finished)
    }

    private fun set(s: Step) { step = s; prefs?.edit()?.putString("step", s.name)?.apply() }

    fun done(from: Step) { if (step == from) set(Step.entries[from.ordinal + 1]) }

    fun start() = set(Step.NewShortcut)
    fun restart() = set(Step.Welcome)
    fun skip() = set(Step.Finished)
    fun finish() = set(Step.Finished)

    val anchors = mutableStateMapOf<String, Rect>()
}

fun Modifier.tipAnchor(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { Tour.anchors.remove(id) } }
    onGloballyPositioned { Tour.anchors[id] = it.boundsInWindow() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TourOverlay() {
    val reduced = Theme.reduced
    var shown by remember { mutableStateOf<Tour.Step?>(null) }
    val k = remember { Animatable(0f) }
    LaunchedEffect(Tour.step) {
        val next = Tour.step.takeIf { it.anchor != null }
        if (shown != null && shown != next) k.animateTo(0f, tween(140, easing = Motion.easeOut))
        shown = null
        if (next == null) return@LaunchedEffect
        delay(320)
        shown = next
        k.snapTo(0f)
        k.animateTo(1f, if (reduced) tween(180) else spring(dampingRatio = 0.86f, stiffness = 420f))
    }
    val step = shown ?: return
    val anchor = Tour.anchors[step.anchor!!] ?: return
    if (WindowInsets.isImeVisible) return
    val c = Theme.c
    val density = LocalDensity.current
    val ping = if (reduced) 0f else rememberInfiniteTransition(label = "halo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600, easing = { t -> if (t < 0.75f) Motion.easeOut.transform(t / 0.75f) else 1f }), RepeatMode.Restart), label = "halo",
    ).value

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val margin = with(density) { 12.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val cardW = minOf(with(density) { 300.dp.toPx() }, w - 2 * margin)
        var cardH by remember(step) { mutableStateOf(0) }
        val below = anchor.center.y < h / 2
        val x = (anchor.center.x - cardW / 2).coerceIn(margin, w - margin - cardW)
        val y = if (below) anchor.bottom + gap else anchor.top - gap - cardH
        val arrowX = (anchor.center.x - x).coerceIn(with(density) { 22.dp.toPx() }, cardW - with(density) { 22.dp.toPx() })

        Box(
            Modifier.fillMaxSize().drawBehind {
                val r = minOf(anchor.width, anchor.height) / 2
                val base = 5.dp.toPx()
                drawRoundRect(
                    c.accent.copy(alpha = 0.35f * k.value),
                    topLeft = Offset(anchor.left - base, anchor.top - base), size = Size(anchor.width + 2 * base, anchor.height + 2 * base),
                    cornerRadius = CornerRadius(r + base), style = Stroke(1.5.dp.toPx()),
                )
                if (!reduced) {
                    val grow = base + 12.dp.toPx() * ping
                    drawRoundRect(
                        c.accent.copy(alpha = 0.5f * (1f - ping) * k.value),
                        topLeft = Offset(anchor.left - grow, anchor.top - grow), size = Size(anchor.width + 2 * grow, anchor.height + 2 * grow),
                        cornerRadius = CornerRadius(r + grow), style = Stroke(2.dp.toPx()),
                    )
                }
            },
        )

        Column(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(with(density) { cardW.toDp() })
                .onSizeChanged { cardH = it.height }
                .graphicsLayer {
                    val v = k.value
                    val s = if (reduced) 1f else 0.94f + 0.06f * v
                    scaleX = s; scaleY = s
                    alpha = if (cardH == 0) 0f else v.coerceIn(0f, 1f)
                    translationY = if (reduced) 0f else (1f - v) * 6.dp.toPx() * (if (below) -1 else 1)
                    transformOrigin = TransformOrigin(arrowX / cardW, if (below) 0f else 1f)
                }
                .drawBehind {
                    val aw = 18.dp.toPx(); val ah = 9.dp.toPx()
                    val p = Path().apply {
                        if (below) { moveTo(arrowX - aw / 2, 0f); lineTo(arrowX, -ah); lineTo(arrowX + aw / 2, 0f) }
                        else { moveTo(arrowX - aw / 2, size.height); lineTo(arrowX, size.height + ah); lineTo(arrowX + aw / 2, size.height) }
                        close()
                    }
                    drawPath(p, c.raised)
                }
                .shadow(if (c.dark) 0.dp else 18.dp, RoundedCornerShape(18.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .clip(RoundedCornerShape(18.dp))
                .background(c.raised)
                .border(1.dp, c.hairline, RoundedCornerShape(18.dp))
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                val icon = remember(step) { Animatable(if (reduced) 1f else 0f) }
                LaunchedEffect(step) { if (!reduced) { delay(70); icon.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 520f)) } }
                Box(Modifier.graphicsLayer { val v = 0.7f + 0.3f * icon.value; scaleX = v; scaleY = v; alpha = icon.value.coerceIn(0f, 1f) }) {
                    IconTile(step.glyph, step.tint.color(), size = 30.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = Theme.t.label, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(step.message, style = Theme.t.small, color = c.ink2)
                    if (step.gotIt) {
                        Spacer(Modifier.height(8.dp))
                        Text("Got It", style = Theme.t.label, color = c.accent,
                            modifier = Modifier.heightIn(min = 32.dp).pressable(label = "Got it") { Tour.done(step) }.padding(vertical = 4.dp))
                    }
                }
                Box(Modifier.size(36.dp).pressable(scale = 0.9f, label = "End tour") { Tour.skip() }, contentAlignment = Alignment.Center) {
                    Glyph("x", c.ink3, size = 15.dp)
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit, onNotNow: () -> Unit) {
    val c = Theme.c
    val reduced = Theme.reduced
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rows = listOf(
        Triple("layers" to Tint.Blue, "Shortcuts", "Put steps together, like ending a WhatsApp call and turning on Do Not Disturb, and run them with one tap."),
        Triple("clock" to Tint.Orange, "Automation", "Shortcuts can run on their own: at bedtime, when you plug in, or when a call runs late."),
        Triple("lock" to Tint.Green, "Stays on your phone", "Your shortcuts run right here on this phone. No account, no tracking."),
    )
    val parts = remember { List(6) { Animatable(if (reduced) 1f else 0f) } }
    val icons = remember { List(rows.size) { Animatable(if (reduced) 1f else 0f) } }
    val leave = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        val at = listOf(80L, 160L, 300L, 380L, 450L, 620L)
        parts.forEachIndexed { i, a ->
            scope.launch { delay(at[i]); a.animateTo(1f, spring(dampingRatio = if (i == 0) 0.72f else 1f, stiffness = if (i == 0) 320f else 240f)) }
        }
        icons.forEachIndexed { i, a ->
            scope.launch { delay(at[i + 2] + 90); a.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f)) }
        }
    }
    fun go(then: () -> Unit) {
        if (leave.isRunning || leave.value > 0f) return
        scope.launch { leave.animateTo(1f, tween(if (reduced) 150 else 240, easing = Motion.easeOut)); then() }
    }
    fun Modifier.rise(a: Animatable<Float, *>, dist: Float = 16f) = graphicsLayer {
        val v = a.value.coerceIn(0f, 1.2f)
        alpha = v.coerceIn(0f, 1f)
        if (!reduced) {
            translationY = (1f - v) * dist.dp.toPx()
            if (android.os.Build.VERSION.SDK_INT >= 31 && v < 1f) {
                val b = (1f - v.coerceIn(0f, 1f)) * 6.dp.toPx()
                if (b > 0.5f) renderEffect = androidx.compose.ui.graphics.BlurEffect(b, b)
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                alpha = 1f - leave.value
                if (!reduced) { val s = 1f + 0.03f * leave.value; scaleX = s; scaleY = s }
            }
            .background(c.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        val header: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.graphicsLayer {
                        val v = parts[0].value
                        alpha = v.coerceIn(0f, 1f)
                        if (!reduced) { val s = 0.82f + 0.18f * v; scaleX = s; scaleY = s; rotationZ = (1f - v) * -8f }
                    },
                ) { IconTile("layers", Tint.Blue.color(), size = 64.dp) }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Welcome to\nOpenShortcuts", style = Theme.t.large.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.rise(parts[1], 12f),
                )
            }
        }
        val features: @Composable () -> Unit = {
            rows.forEachIndexed { i, (look, title, text) ->
                Row(Modifier.fillMaxWidth().padding(vertical = if (isShort) 6.dp else 10.dp).rise(parts[i + 2]), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(44.dp).graphicsLayer {
                            val v = icons[i].value
                            alpha = v.coerceIn(0f, 1f)
                            if (!reduced) { val s = 0.6f + 0.4f * v; scaleX = s; scaleY = s }
                        },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(look.first, look.second.color(), size = 34.dp) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = Theme.t.label.copy(fontSize = 16.sp), color = c.ink)
                        Text(text, style = if (isShort) Theme.t.small.copy(fontSize = 14.sp, lineHeight = 19.sp) else Theme.t.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = c.ink2)
                    }
                }
            }
        }
        val footer: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).rise(parts[5], 10f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Learn by making a real shortcut. It takes about two minutes.", style = Theme.t.small, color = c.ink2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Continue", Modifier.fillMaxWidth()) { go(onContinue) }
                Text("Not Now", style = Theme.t.label, color = c.accent,
                    modifier = Modifier.heightIn(min = 44.dp).pressable(label = "Not now") { go(onNotNow) }.padding(top = 12.dp))
            }
        }
        if (isShort) {
            Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(0.9f).fillMaxHeight(), contentAlignment = Alignment.Center) { header() }
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1.1f).fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.Center) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { features() }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.rise(parts[5], 10f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuietButton("Not Now", Modifier.weight(1f), color = c.ink) { go(onNotNow) }
                        PrimaryButton("Continue", Modifier.weight(1f)) { go(onContinue) }
                    }
                }
            }
        } else Column(
            Modifier.widthIn(max = 460.dp).fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(56.dp))
                header()
                Spacer(Modifier.height(36.dp))
                features()
                Spacer(Modifier.height(16.dp))
            }
            footer()
        }
    }
}

@Composable
fun AllSetDialog(onDone: () -> Unit) {
    val c = Theme.c
    val view = androidx.compose.ui.platform.LocalView.current
    val reduced = Theme.reduced
    val circle = remember { Animatable(if (reduced) 1f else 0f) }
    val draw = remember { Animatable(if (reduced) 1f else 0f) }
    val words = remember { Animatable(if (reduced) 1f else 0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (reduced) { view.confirm(); return@LaunchedEffect }
        scope.launch { delay(60); circle.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 360f)) }
        scope.launch { delay(230); draw.animateTo(1f, tween(300, easing = Motion.easeOut)); view.confirm() }
        scope.launch { delay(330); words.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f)) }
    }
    DuoDialog(onDone) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).graphicsLayer {
                    val v = circle.value
                    val s = 0.7f + 0.3f * v; scaleX = s; scaleY = s; alpha = v.coerceIn(0f, 1f)
                }.clip(CircleShape).background(c.positive).drawBehind {
                    val p = Path().apply {
                        moveTo(size.width * 0.29f, size.height * 0.52f)
                        lineTo(size.width * 0.44f, size.height * 0.66f)
                        lineTo(size.width * 0.72f, size.height * 0.37f)
                    }
                    val m = androidx.compose.ui.graphics.PathMeasure().apply { setPath(p, false) }
                    val seg = Path()
                    m.getSegment(0f, m.length * draw.value, seg, true)
                    drawPath(seg, Color.White, style = Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                },
            )
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.graphicsLayer { alpha = words.value.coerceIn(0f, 1f); if (!reduced) translationY = (1f - words.value) * 8.dp.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("You're All Set", style = Theme.t.title, color = c.ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your automation now runs by itself, with nothing to approve. Switch it on or off in Automation, and allow anything it needs in Setup.",
                    style = Theme.t.body, color = c.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDone)
            }
        }
    }
}
