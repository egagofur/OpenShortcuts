package app.openshortcuts.ui

import android.content.Intent
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** Set by the app while you're below the first screen: the rail's house takes you back there. */
val LocalGoHome = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The house glyph. Below Home it goes back with the push animation in reverse; on Home it just nods. */
@Composable
fun RailHome() {
    val c = Theme.c
    val view = LocalView.current
    val goHome = LocalGoHome.current
    val scope = rememberCoroutineScope()
    val nod = remember { Animatable(0f) }
    val reduced = Theme.reduced
    Box(
        Modifier.size(40.dp, 30.dp).pressable(scale = 0.88f, label = if (goHome != null) "Go to Home" else "Home") {
            if (goHome != null) { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); goHome() }
            else if (!reduced) scope.launch {
                // Already home: a small shake says "you're here".
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                nod.snapTo(0f)
                nod.animateTo(0f, androidx.compose.animation.core.keyframes { durationMillis = 320; -9f at 60; 7f at 140; -3f at 220 })
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Glyph("home", if (goHome != null) c.accent else c.ink, size = 17.dp, modifier = Modifier.graphicsLayer { rotationZ = nod.value })
    }
}

/** Time; tap flips to the date for a few seconds, hold opens the clock's alarms. */
@Composable
fun RailClock() {
    val c = Theme.c
    val ctx = LocalContext.current
    val view = LocalView.current
    var showDate by remember { mutableStateOf(false) }
    LaunchedEffect(showDate) { if (showDate) { delay(3_000); showDate = false } }
    val reduced = Theme.reduced
    val time = rememberClock()
    val date = remember(time) { DateFormat.format("EEE d", Calendar.getInstance()).toString() }
    Box(
        Modifier.heightIn(min = 30.dp).pressable(
            scale = 0.94f, label = if (showDate) "Show time" else "Show date",
            onLongClick = {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                runCatching { ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
        ) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); showDate = !showDate },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            showDate, label = "clock",
            transitionSpec = {
                // Rolls like a flip counter: the new value comes from below, the old one leaves upward.
                val d = if (targetState) 1 else -1
                if (reduced) fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                else (slideInVertically(spring(dampingRatio = 0.85f, stiffness = 500f)) { it * d } + fadeIn(tween(150))) togetherWith
                    (slideOutVertically(tween(160, easing = Motion.easeOut)) { -it * d } + fadeOut(tween(110)))
            },
        ) { d ->
            Text(
                if (d) date else time,
                style = if (d) Theme.t.clock.copy(fontSize = 14.sp, lineHeight = 22.sp) else Theme.t.clock,
                color = if (d) c.accent else c.ink, maxLines = 1,
            )
        }
    }
}

/**
 * The battery ring as a control: tap replays the fill and counts the number up, and a small panel
 * grows out of the ring with what the phone knows about the battery.
 */
@Composable
fun RailBattery() {
    val ctx = LocalContext.current
    val view = LocalView.current
    val state = rememberBattery()
    var replay by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    Box(
        Modifier.pressable(scale = 0.92f, label = "Battery details") {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            replay++
            open = !open
        },
    ) {
        BatteryRing(state, replay)
        if (open) BatteryPanel(state, onDismiss = { open = false }) {
            open = false
            runCatching { ctx.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** Puts the panel to the left of the ring, centred on it, kept inside the window. */
private class LeftOf(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left - gap - popupContentSize.width).coerceAtLeast(gap)
        val y = (anchorBounds.center.y - popupContentSize.height / 2).coerceIn(gap, (windowSize.height - popupContentSize.height - gap).coerceAtLeast(gap))
        return IntOffset(x, y)
    }
}

@Composable
private fun BatteryPanel(state: BatteryState, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = Theme.reduced
    val gap = with(LocalDensity.current) { 10.dp.roundToPx() }
    val k = remember { Animatable(0f) }
    LaunchedEffect(Unit) { k.animateTo(1f, if (reduced) tween(150) else spring(dampingRatio = 0.82f, stiffness = 420f)) }
    fun close() = scope.launch { k.animateTo(0f, tween(130, easing = Motion.easeOut)); onDismiss() }

    val toFull = remember(state) {
        if (!state.charging || state.level >= 100) -1L
        else ctx.getSystemService(BatteryManager::class.java).computeChargeTimeRemaining()
    }
    val status = when {
        state.charging && state.level >= 100 -> "Fully charged"
        state.charging && toFull > 0 -> "Charging · full in ${durationLabel(toFull)}"
        state.charging -> "Charging" + when (state.plug) { BatteryManager.BATTERY_PLUGGED_WIRELESS -> " wirelessly"; BatteryManager.BATTERY_PLUGGED_USB -> " over USB"; else -> "" }
        state.level <= 20 -> "Low battery"
        else -> "On battery"
    }

    Popup(popupPositionProvider = LeftOf(gap), onDismissRequest = { close() }, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                // Grows out of the ring it came from: origin on the panel's right edge, middle.
                .graphicsLayer {
                    val s = 0.9f + 0.1f * k.value
                    scaleX = s; scaleY = s; alpha = k.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(1f, 0.5f)
                }
                .width(220.dp)
                .shadow(if (c.dark) 0.dp else 20.dp, RoundedCornerShape(22.dp), ambientColor = c.shadow, spotColor = c.shadow)
                .clip(RoundedCornerShape(22.dp))
                .background(c.raised)
                .border(1.dp, c.hairline, RoundedCornerShape(22.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.level}", style = Theme.t.large.copy(fontFeatureSettings = "tnum"), color = c.ink)
                Text("%", style = Theme.t.title, color = c.ink2, modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
                Spacer(Modifier.weight(1f))
                if (state.tempTenths > 0) Text("${state.tempTenths / 10}°C", style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(bottom = 6.dp))
            }
            Text(status, style = Theme.t.small, color = if (state.charging) c.positive else if (state.level <= 20) c.danger else c.ink2)
            Spacer(Modifier.height(10.dp))
            // A plain bar version of the ring, so the number has something to sit on.
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.fill)) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth((state.level / 100f) * k.value.coerceIn(0f, 1f)).clip(CircleShape)
                        .background(if (state.charging) c.positive else if (state.level <= 20) c.danger else c.ink),
                )
            }
            Spacer(Modifier.height(12.dp))
            PanelLink("Battery usage", "battery") { onOpen(Intent.ACTION_POWER_USAGE_SUMMARY) }
            PanelLink("Battery Saver", "bolt") { onOpen(Settings.ACTION_BATTERY_SAVER_SETTINGS) }
        }
    }
}

@Composable
private fun PanelLink(label: String, glyph: String, onClick: () -> Unit) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).pressable(scale = 0.97f, label = label, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(glyph, c.accent, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = Theme.t.body, color = c.ink, modifier = Modifier.weight(1f))
        Glyph("chevronRight", c.ink3, size = 14.dp)
    }
}

private fun durationLabel(ms: Long): String {
    val m = (ms / 60_000).toInt().coerceAtLeast(1)
    return if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"
}
