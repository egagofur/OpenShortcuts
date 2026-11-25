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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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

/**
 * First-run tour. A welcome screen, then Apple TipKit-style tips that point at the real controls
 * and move on when you do the thing they describe — you learn by building, not by reading slides.
 */
object Tour {
    enum class Step(val anchor: String?, val title: String, val message: String, val glyph: String, val tint: Tint, val gotIt: Boolean = false) {
        Welcome(null, "", "", "layers", Tint.Blue),
        NewShortcut("railPlus", "Make your first shortcut", "Tap + and choose Start from scratch.", "plus", Tint.Blue),
        AddAction("search", "Add an action", "Search for something to do, like Do Not Disturb, and tap it.", "search", Tint.Blue),
        EditToken("firstAction", "Make it yours", "Blue words are settings. Tap one to change it, like On or Off.", "sliders", Tint.Indigo, gotIt = true),
        Run("run", "Try it", "Tap ▶ to run your shortcut now.", "playFill", Tint.Green),
        GoBack("railBack", "Now make it automatic", "Go back to your shortcuts.", "chevronLeft", Tint.Orange),
        OpenAutomation("tabAutomation", "Automation", "Open Automation to make shortcuts run by themselves.", "clock", Tint.Orange),
        NewAutomation("railPlus", "Choose what starts it", "Tap + and pick a trigger, like Time of Day or Charger.", "plus", Tint.Orange),
        PickShortcut("runCard", "Pick your shortcut", "Tap Shortcut and choose the one you just made.", "layers", Tint.Purple),
        AllSet(null, "", "", "check", Tint.Green),
        Finished(null, "", "", "check", Tint.Green),
    }

    var step by mutableStateOf(Step.Finished); private set
    private var prefs: android.content.SharedPreferences? = null

    /** On a fresh install the tour starts; people who already use the app aren't interrupted. */
    fun init(ctx: Context, freshInstall: Boolean) {
        val p = ctx.getSharedPreferences("tour", Context.MODE_PRIVATE)
        prefs = p
        if (!p.contains("step")) p.edit().putString("step", (if (freshInstall) Step.Welcome else Step.Finished).name).apply()
        step = runCatching { Step.valueOf(p.getString("step", null)!!) }.getOrDefault(Step.Finished)
    }

    private fun set(s: Step) { step = s; prefs?.edit()?.putString("step", s.name)?.apply() }

    /** Move on only if the tour is at [from], so stray events don't skip ahead. */
    fun done(from: Step) { if (step == from) set(Step.entries[from.ordinal + 1]) }

    fun start() = set(Step.NewShortcut)
    fun restart() = set(Step.Welcome)
    fun skip() = set(Step.Finished)
    fun finish() = set(Step.Finished)

    /** Where each tip's control is on screen, in window pixels. */
    val anchors = mutableStateMapOf<String, Rect>()
}

/** Marks a control a tip can point at. */
fun Modifier.tipAnchor(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { Tour.anchors.remove(id) } }
    onGloballyPositioned { Tour.anchors[id] = it.boundsInWindow() }
}

/** Draws the current tip over the app. Touches pass through everywhere except the tip itself. */
@Composable
fun TourOverlay() {
    val step = Tour.step
    val id = step.anchor ?: return
    val anchor = Tour.anchors[id] ?: return
    val c = Theme.c
    val reduced = Theme.reduced
    val density = LocalDensity.current
    // A beat after the screen settles, so the tip doesn't chase a sliding control.
    var ready by remember(step) { mutableStateOf(false) }
    LaunchedEffect(step) { delay(380); ready = true }
    if (!ready) return

    val k = remember(step) { Animatable(0f) }
    LaunchedEffect(step) { k.animateTo(1f, if (reduced) tween(150) else spring(dampingRatio = 0.85f, stiffness = 380f)) }
    val pulse = if (reduced) 0.5f else rememberInfiniteTransition(label = "halo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "halo",
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

        // A soft ring around the control: "this one".
        Box(
            Modifier.fillMaxSize().drawBehind {
                val grow = 6.dp.toPx() + 8.dp.toPx() * pulse
                drawRoundRect(
                    c.accent.copy(alpha = 0.55f * (1f - pulse) * k.value),
                    topLeft = Offset(anchor.left - grow, anchor.top - grow),
                    size = Size(anchor.width + 2 * grow, anchor.height + 2 * grow),
                    cornerRadius = CornerRadius(minOf(anchor.width, anchor.height) / 2 + grow),
                    style = Stroke(2.dp.toPx()),
                )
            },
        )

        Column(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(with(density) { cardW.toDp() })
                .onSizeChanged { cardH = it.height }
                .graphicsLayer {
                    // Grows out of the side the arrow points to, like a TipKit popover.
                    val s = 0.92f + 0.08f * k.value
                    scaleX = s; scaleY = s; alpha = if (cardH == 0) 0f else k.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(arrowX / cardW, if (below) 0f else 1f)
                }
                .drawBehind {
                    // The arrow, same material as the card.
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
                IconTile(step.glyph, step.tint.color(), size = 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(step.title, style = Theme.t.label, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(step.message, style = Theme.t.small, color = c.ink2)
                    if (step.gotIt) {
                        Spacer(Modifier.height(8.dp))
                        Text("Got It", style = Theme.t.label, color = c.accent,
                            modifier = Modifier.pressable(label = "Got it") { Tour.done(step) }.padding(vertical = 4.dp))
                    }
                }
                Box(Modifier.size(36.dp).pressable(scale = 0.9f, label = "End tour") { Tour.skip() }, contentAlignment = Alignment.Center) {
                    Glyph("x", c.ink3, size = 15.dp)
                }
            }
        }
    }
}

/** Apple's first-launch screen: big title, three features that arrive one after another, Continue. */
@Composable
fun WelcomeScreen(onContinue: () -> Unit, onNotNow: () -> Unit) {
    val c = Theme.c
    val reduced = Theme.reduced
    val rows = listOf(
        Triple("layers" to Tint.Blue, "Shortcuts", "Chain steps — end a WhatsApp call, turn on Do Not Disturb, lock the phone — and run them with one tap."),
        Triple("clock" to Tint.Orange, "Automation", "Let shortcuts run by themselves: at bedtime, when you plug in, when a call runs late."),
        Triple("lock" to Tint.Green, "Stays on your phone", "Your shortcuts run right here on this phone. No account, no tracking."),
    )
    val appear = remember { List(rows.size + 2) { Animatable(if (reduced) 1f else 0f) } }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (!reduced) appear.forEachIndexed { i, a ->
            // Short, uneven gaps read as one gesture rather than a slideshow; each rise overlaps the next.
            delay(if (i == 0) 120 else listOf(140L, 90L, 80L, 120L)[minOf(i - 1, 3)])
            scope.launch { a.animateTo(1f, spring(dampingRatio = 1f, stiffness = 260f)) }
        }
    }
    fun Modifier.rise(i: Int) = graphicsLayer { alpha = appear[i].value; translationY = (1f - appear[i].value) * 18.dp.toPx() }

    Box(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (isShort) 16.dp else 56.dp))
                Box(Modifier.rise(0)) { IconTile("layers", Tint.Blue.color(), size = 64.dp) }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Welcome to\nOpenShortcuts", style = Theme.t.large.copy(fontSize = 34.sp, lineHeight = 38.sp),
                    color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.rise(0),
                )
                Spacer(Modifier.height(if (isShort) 18.dp else 36.dp))
                rows.forEachIndexed { i, (look, title, text) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).rise(i + 1), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Glyph(look.first, look.second.color(), size = 34.dp) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, style = Theme.t.label.copy(fontSize = 16.sp), color = c.ink)
                            Text(text, style = Theme.t.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = c.ink2)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).rise(rows.size + 1), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("A two-minute tour shows you how, by building a real shortcut.", style = Theme.t.small, color = c.ink2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Continue", Modifier.fillMaxWidth(), onClick = onContinue)
                Text("Not Now", style = Theme.t.label, color = c.accent,
                    modifier = Modifier.heightIn(min = 44.dp).pressable(label = "Not now", onClick = onNotNow).padding(top = 12.dp))
            }
        }
    }
}

/** The end of the tour: one check, one sentence, Done. */
@Composable
fun AllSetDialog(onDone: () -> Unit) {
    val c = Theme.c
    val reduced = Theme.reduced
    val pop = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, if (reduced) tween(0) else spring(dampingRatio = 0.55f, stiffness = 380f)) }
    DuoDialog(onDone) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(64.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
                    .clip(CircleShape).background(c.positive),
                contentAlignment = Alignment.Center,
            ) { Glyph("check", Color.White, size = 34.dp) }
            Spacer(Modifier.height(14.dp))
            Text("You're All Set", style = Theme.t.title, color = c.ink)
            Spacer(Modifier.height(6.dp))
            Text(
                "Your automation runs by itself — no notification to approve. Turn it on or off in Automation, and find permissions in Setup.",
                style = Theme.t.body, color = c.ink2, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDone)
        }
    }
}

