package app.openshortcuts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.engine.AdapterResult
import app.openshortcuts.model.AdapterAction
import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.WidgetType
import app.openshortcuts.model.WorkspaceAdapter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a live_status card shows right after its start or stop action succeeded, before the next
 * fetch confirms it: the card flips at once instead of waiting on the network.
 */
object LiveOverrides {
    data class Override(val active: Boolean, val since: Long, val basis: Any?)

    private val map = mutableStateMapOf<String, Override>()

    private fun key(adapterId: String, widgetId: String) = "$adapterId/$widgetId"

    fun get(adapterId: String, widgetId: String) = map[key(adapterId, widgetId)]

    fun clear(adapterId: String, widgetId: String) { map.remove(key(adapterId, widgetId)) }

    /**
     * Records a successful [action] against every live_status card it starts or stops. [payloadOf]
     * gives the card's current data, so the override lasts until fresher data arrives. Returns true
     * when the action belongs to such a card (the card itself is then the confirmation).
     */
    fun note(adapter: WorkspaceAdapter, action: AdapterAction, payloadOf: (DashboardWidget) -> Any?): Boolean {
        var hit = false
        adapter.tabs.flatMap { it.widgets }.filter { it.type == WidgetType.LIVE_STATUS }.forEach { w ->
            val active = when (action.id) {
                w.inactiveActionId -> true
                w.activeActionId -> false
                else -> return@forEach
            }
            map[key(adapter.id, w.id)] = Override(active, System.currentTimeMillis(), payloadOf(w))
            hit = true
        }
        return hit
    }
}

private val timerStyle = TextStyle(
    fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum", letterSpacing = (-0.03).em,
)

/** "1:02:09" from milliseconds; hours aren't padded, minutes and seconds are. */
fun elapsedLabel(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

/** Strong ease-out (cubic-bezier(0.23, 1, 0.32, 1)): instant response, soft landing. */
private val easeOut = Motion.easeOut

/**
 * A start/stop card. Idle and running share one layout, so starting reads as the same card coming
 * alive: the dim 0:00:00 brightens and starts rolling, and the Start pill turns into a Stop pill
 * beside it (press and hold to stop when the widget asks for that).
 */
@Composable
fun LiveStatusWidget(
    widget: DashboardWidget,
    adapter: WorkspaceAdapter,
    payload: Any?,
    onRunAction: (AdapterAction) -> Unit,
    onDirectResult: (AdapterAction, AdapterResult) -> Unit,
) {
    val c = Theme.c
    val reduced = Theme.reduced
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val serverActive = AdapterEngine.extractBool(payload, widget.activePath)
    val override = LiveOverrides.get(adapter.id, widget.id)?.takeIf { o ->
        // Fresh data that agrees, or an override older than 15s, retires it.
        val fresh = o.basis !== payload
        !(fresh && (serverActive == o.active || System.currentTimeMillis() - o.since > 15_000))
    }
    LaunchedEffect(payload, override == null) {
        if (override == null) LiveOverrides.clear(adapter.id, widget.id)
    }
    val active = override?.active ?: serverActive

    val serverStart = widget.startedAtPath.takeIf { it.isNotBlank() }
        ?.let { AdapterEngine.parseInstantOrNull(AdapterEngine.extractString(payload, it))?.toEpochMilli() }
    var localStart by remember(widget.id) { mutableLongStateOf(0L) }
    LaunchedEffect(active) { if (active && localStart == 0L) localStart = System.currentTimeMillis() else if (!active) localStart = 0L }
    val startedAt = when {
        override?.active == true && (serverStart == null || override.basis === payload) -> override.since
        serverStart != null -> serverStart
        else -> localStart.takeIf { it > 0 } ?: System.currentTimeMillis()
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (active) LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000 - now % 1000 + 5)
        }
    }

    val title = AdapterEngine.extractString(payload, widget.activeTitlePath)
    val subtitle = AdapterEngine.extractString(payload, widget.subtitlePath)
    val action = adapter.actionById(if (active) widget.activeActionId else widget.inactiveActionId)
    val startTint = (adapter.actionById(widget.inactiveActionId)?.tint ?: widget.tint).color()
    val stopTint = (adapter.actionById(widget.activeActionId)?.tint ?: widget.tint).color()
    val counterColor by animateColorAsState(if (active) c.ink else c.ink3, tween(200, easing = easeOut), label = "counterInk")
    val pulse = if (active && !reduced) {
        rememberInfiniteTransition(label = "liveDot").animateFloat(
            0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "alpha",
        ).value
    } else 1f

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp).animateContentSize(if (reduced) tween(0) else tween(250, easing = easeOut))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).graphicsLayer { alpha = pulse }.clip(CircleShape)
                        .background(if (active) c.positive else c.ink3),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (active) widget.title.ifBlank { "Running" } else widget.inactiveText,
                    style = Theme.t.small, color = c.ink2,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (active && widget.holdToStop && action != null) {
                    EnterBlock(delayMs = 0) { Text("Hold Stop to finish", style = Theme.t.small, color = c.ink3, maxLines = 1) }
                }
            }
            Spacer(Modifier.height(2.dp))
            // The action sits beside the number it controls, centered on it, not on the whole card.
            Row(verticalAlignment = Alignment.CenterVertically) {
                RollingNumber(
                    if (active) elapsedLabel(now - startedAt) else "0:00:00", timerStyle, counterColor,
                    Modifier.weight(1f).semantics {
                        contentDescription = if (active) "Running for ${elapsedLabel(now - startedAt)}" else "Not running"
                    },
                )
                if (action != null) {
                    Spacer(Modifier.width(12.dp))
                    ActionPill(
                        active = active, hold = widget.holdToStop, startTint = startTint, stopTint = stopTint,
                        label = action.title,
                        onTap = { view.tick(); onRunAction(action) },
                        onHeld = { scope.launch { onDirectResult(action, AdapterEngine.runAction(adapter, action, emptyMap())) } },
                    )
                }
            }
            // The lines under the counter cascade in each time the card changes state.
            key(active) {
                val lines = buildList {
                    if (active) {
                        if (title.isNotBlank()) add(title to true)
                        if (subtitle.isNotBlank()) add(subtitle to false)
                    } else if (subtitle.isNotBlank()) add(subtitle to false)
                }
                lines.forEachIndexed { i, (text, strong) ->
                    EnterBlock(delayMs = 40 + 50 * i) {
                        Text(
                            text,
                            style = if (strong) Theme.t.label else Theme.t.small,
                            color = if (strong) c.ink else c.ink2,
                            maxLines = if (strong) 2 else 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** Fades in and rises 8dp over 250ms ease-out after [delayMs]; just fades under reduced motion. */
@Composable
private fun EnterBlock(delayMs: Int, content: @Composable () -> Unit) {
    val reduced = Theme.reduced
    val density = LocalDensity.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduced) delay(delayMs.toLong())
        p.animateTo(1f, tween(if (reduced) 150 else 250, easing = easeOut))
    }
    Box(Modifier.graphicsLayer {
        alpha = p.value
        if (!reduced) translationY = with(density) { 8.dp.toPx() } * (1f - p.value)
    }) { content() }
}

/**
 * The card's one control. Idle: a filled Start pill. Running: a quiet Stop pill; with [hold], a
 * press-and-hold whose colored copy is revealed left to right (slow, linear, while you decide) and
 * snaps back fast on release. The pill's content swaps with a short blur so the two states read
 * as one control changing, not two buttons trading places.
 */
@Composable
private fun ActionPill(
    active: Boolean,
    hold: Boolean,
    startTint: Color,
    stopTint: Color,
    label: String,
    onTap: () -> Unit,
    onHeld: () -> Unit,
) {
    val c = Theme.c
    val reduced = Theme.reduced
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val fill = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var done by remember(active) { mutableStateOf(false) }
    LaunchedEffect(active) { fill.snapTo(0f) }
    val press by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(160, easing = easeOut), label = "pillPress")
    val base by animateColorAsState(if (active) c.fill else startTint, tween(200, easing = easeOut), label = "pillBase")
    val holding = active && hold
    val shape = CircleShape

    Box(
        Modifier
            .height(44.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(shape)
            .background(base)
            .semantics(mergeDescendants = true) {
                contentDescription = if (holding) "$label, press and hold" else label
                onClick(label) { if (holding) onHeld() else onTap(); true }
            }
            .pointerInput(active, hold) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    if (!holding) {
                        val up = waitForUpOrCancellation()
                        pressed = false
                        if (up != null) onTap()
                        return@awaitEachGesture
                    }
                    if (done) { waitForUpOrCancellation(); pressed = false; return@awaitEachGesture }
                    view.tick()
                    val run = scope.launch {
                        fill.animateTo(1f, tween(((1f - fill.value) * 1000).toInt(), easing = LinearEasing))
                        done = true
                        view.confirm()
                        onHeld()
                    }
                    waitForUpOrCancellation()
                    pressed = false
                    if (!done) {
                        run.cancel()
                        scope.launch { fill.animateTo(0f, tween(200, easing = easeOut)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val content: @Composable (Color) -> Unit = { ink ->
            SwapContent(if (!active) "start" else if (holding) "hold" else "stop") { state ->
                Row(Modifier.padding(start = 16.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (state == "start") "playFill" else "stopFill", if (state == "start") ink else if (ink == Color.White) ink else stopTint, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (state == "start") "Start" else if (done) "Stopping…" else "Stop",
                        style = Theme.t.label, color = ink, maxLines = 1,
                    )
                }
            }
        }
        content(if (active) c.ink else Color.White)
        if (holding) {
            // A colored copy of the pill, revealed from the left as the hold progresses.
            Box(
                Modifier
                    .matchParentSize()
                    .drawWithContent {
                        clipRect(right = size.width * fill.value) { this@drawWithContent.drawContent() }
                    }
                    .background(stopTint),
                contentAlignment = Alignment.Center,
            ) { content(Color.White) }
        }
    }
}

/** Swaps keyed content: the new state fades in from 0.9 scale and 2dp blur; the old leaves faster. */
@Composable
private fun SwapContent(state: String, content: @Composable (String) -> Unit) {
    val reduced = Theme.reduced
    val blur = with(LocalDensity.current) { 2.dp.toPx() }
    androidx.compose.animation.AnimatedContent(
        targetState = state,
        transitionSpec = {
            if (reduced) {
                androidx.compose.animation.fadeIn(tween(150)) togetherWith androidx.compose.animation.fadeOut(tween(100))
            } else {
                (androidx.compose.animation.fadeIn(tween(200, easing = easeOut)) +
                    androidx.compose.animation.scaleIn(tween(200, easing = easeOut), initialScale = 0.9f)) togetherWith
                    (androidx.compose.animation.fadeOut(tween(150, easing = easeOut)) +
                        androidx.compose.animation.scaleOut(tween(150, easing = easeOut), targetScale = 0.9f)) using
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ -> tween(200, easing = easeOut) }
            }
        },
        contentAlignment = Alignment.Center,
        label = "pillSwap",
    ) { s ->
        val t = transition.animateFloat(label = "pillBlur") { if (it == androidx.compose.animation.EnterExitState.Visible) 0f else 1f }
        Box(Modifier.graphicsLayer {
            val r = if (reduced) 0f else blur * t.value
            renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
        }) { content(s) }
    }
}

/**
 * A number whose characters change one at a time: the old digit lifts away while the new one rises
 * into place, with a light blur so the two read as one moving digit. Under reduced motion digits
 * simply cross-fade.
 */
@Composable
fun RollingNumber(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val reduced = Theme.reduced
    Row(modifier) {
        // Key slots from the right so seconds stay in the same slot when hours gain a digit.
        text.forEachIndexed { i, ch ->
            key(text.length - i) { RollingChar(ch, style, color, reduced) }
        }
    }
}

@Composable
private fun RollingChar(ch: Char, style: TextStyle, color: Color, reduced: Boolean) {
    val density = LocalDensity.current
    val shift = with(density) { (style.fontSize.value * 0.4f).dp.toPx() }
    val maxBlur = with(density) { 2.dp.toPx() }
    var shown by remember { mutableStateOf(ch) }
    var previous by remember { mutableStateOf<Char?>(null) }
    val roll = remember { Animatable(1f) }
    LaunchedEffect(ch) {
        if (ch == shown) return@LaunchedEffect
        previous = shown
        shown = ch
        roll.snapTo(0f)
        roll.animateTo(1f, tween(if (reduced) 150 else 240, easing = easeOut))
        previous = null
    }
    Box(contentAlignment = Alignment.Center) {
        val old = previous
        if (old != null) {
            Text(old.toString(), style = style, color = color, modifier = Modifier.graphicsLayer {
                val p = roll.value
                alpha = 1f - p
                if (!reduced) {
                    translationY = -shift * p
                    val r = maxBlur * p
                    renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
                }
            })
        }
        Text(shown.toString(), style = style, color = color, modifier = Modifier.graphicsLayer {
            val p = roll.value
            alpha = p
            if (!reduced) {
                translationY = shift * (1f - p)
                val r = maxBlur * (1f - p)
                renderEffect = if (r > 0.5f) BlurEffect(r, r) else null
            }
        })
    }
}
