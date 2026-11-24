package app.openshortcuts.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/** Press feedback fires on touch-down; commit on release. One scale value across the app. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    scale: Float = 0.97f,
    label: String? = null,
    role: Role = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduced = Theme.reduced
    val s by animateFloatAsState(
        targetValue = if (pressed && !reduced) scale else 1f,
        animationSpec = if (pressed) tween(150, easing = Motion.easeOut) else Motion.settle(),
        label = "press",
    )
    graphicsLayer { scaleX = s; scaleY = s; alpha = if (enabled) 1f else 0.4f }
        .combinedClickable(
            interactionSource = src, indication = null, enabled = enabled, role = role,
            onClickLabel = label, onClick = onClick, onLongClick = onLongClick,
        )
}

fun View.confirm() = performHapticFeedback(
    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
)

fun View.tick() = performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

/** Round, slightly raised control used on the rail and in headers. */
@Composable
fun GlassCircle(
    glyph: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.glass,
    tint: Color = Theme.c.ink,
    fill: Color = Theme.c.raised,
    onClick: () -> Unit,
) {
    val c = Theme.c
    Box(
        modifier
            .size(size)
            .pressable(scale = 0.94f, label = label, onClick = onClick)
            .shadow(if (c.dark) 0.dp else 8.dp, CircleShape, ambientColor = c.shadow, spotColor = c.shadow)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, c.hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, tint, size = size * 0.46f, label = label)
    }
}

@Composable
fun IconTile(glyph: String, color: Color, size: Dp = Dimens.tile, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.27f)).background(color),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, Color.White, size = size * 0.66f) }
}

/** Blue parameter token, the heart of the Shortcuts sentence UI. */
@Composable
fun TokenPill(text: String, modifier: Modifier = Modifier, glyph: String? = null, glyphColor: Color? = null, onClick: () -> Unit) {
    val c = Theme.c
    Row(
        modifier
            .pressable(scale = 0.95f, label = "Edit $text", onClick = onClick)
            .clip(RoundedCornerShape(9.dp))
            .background(c.accentWash)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (glyph != null) IconTile(glyph, glyphColor ?: c.accent, size = 20.dp)
        Text(text, style = Theme.t.token, color = c.accent, maxLines = 1)
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.cardRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.clip(shape).background(Theme.c.card), content = content)
}

/** Small grey "remove" circle that sits at the trailing edge of an action card. */
@Composable
fun RemoveDot(label: String, onClick: () -> Unit) {
    val c = Theme.c
    // 44dp hit area around a 24dp visual.
    Box(Modifier.size(44.dp).pressable(scale = 0.9f, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
            Glyph("x", c.ink3, size = 15.dp)
        }
    }
}

// --- Rail ---------------------------------------------------------------------

@Composable
fun rememberClock(): String {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Calendar.getInstance()
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }
    val fmt = if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm"
    return DateFormat.format(fmt, now).toString()
}

data class BatteryState(val level: Int, val charging: Boolean)

@Composable
fun rememberBattery(): BatteryState {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(100, false)) }
    DisposableEffect(ctx) {
        fun read(i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            state = BatteryState(
                if (level >= 0) level * 100 / scale else 0,
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            )
        }
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) = read(i) }
        read(ctx.registerReceiver(r, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        onDispose { ctx.unregisterReceiver(r) }
    }
    return state
}

@Composable
fun BatteryRing(state: BatteryState) {
    val c = Theme.c
    val arc = when {
        state.charging -> c.positive
        state.level <= 20 -> c.danger
        else -> c.ink
    }
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(38.dp)) {
            val w = 3.dp.toPx()
            val inset = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(c.fill, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            drawArc(arc, -90f, 360f * state.level / 100f, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (state.charging) {
            Glyph("boltFill", c.positive, size = 10.dp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp))
        }
        Text(
            "${state.level}",
            style = Theme.t.small.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (state.charging) 6.dp else 0.dp),
        )
    }
}

/** Screens at least this tall are phones, not the folded cover: thumbs live at the bottom. */
private val TALL = 640.dp

/**
 * The right-hand rail from the folded iPhone Duo: status at the top, round controls under it.
 * On tall phones the controls drop to the bottom of the rail, level with the bottom sheet,
 * with the primary one (first in the list) nearest the thumb.
 */
@Composable
fun Rail(vararg controls: @Composable () -> Unit) {
    val c = Theme.c
    BoxWithConstraints(Modifier.width(Dimens.rail).fillMaxHeight()) {
        val tall = maxHeight >= TALL
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom))
                .padding(top = 14.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Glyph("home", c.ink, size = 17.dp)
            Spacer(Modifier.height(4.dp))
            Text(rememberClock(), style = Theme.t.clock, color = c.ink)
            Spacer(Modifier.height(6.dp))
            BatteryRing(rememberBattery())
            Spacer(if (tall) Modifier.weight(1f) else Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                (if (tall) controls.reversed() else controls.toList()).forEach { it() }
            }
        }
    }
}

// --- Dialog -------------------------------------------------------------------

/** Centered card that materialises from 0.96 scale. */
@Composable
fun DuoDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val reduced = Theme.reduced
        val scale = remember { Animatable(if (reduced) 1f else 0.96f) }
        val alpha = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            launch { scale.animateTo(1f, tween(250, easing = Motion.easeOut)) }
            alpha.animateTo(1f, tween(200, easing = Motion.easeOut))
        }
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
                .clip(RoundedCornerShape(30.dp))
                .background(Theme.c.card)
                .padding(20.dp),
            content = content,
        )
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, color: Color = Theme.c.accent, onClick: () -> Unit) {
    Box(
        modifier
            .height(50.dp)
            .pressable(label = text, onClick = onClick)
            .clip(RoundedCornerShape(25.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Theme.t.label.copy(fontSize = 17.sp), color = Color.White) }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = Theme.c.accent, onClick: () -> Unit) {
    Box(
        modifier.height(50.dp).pressable(label = text, onClick = onClick).clip(RoundedCornerShape(25.dp)).background(Theme.c.fill),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Theme.t.label.copy(fontSize = 17.sp), color = color) }
}

@Composable
fun rememberHaptics(): View = LocalView.current

/** Top inset that ignores the hidden status bar (the rail shows time instead). */
val WindowInsets.Companion.topBar: WindowInsets
    @Composable get() = WindowInsets.statusBars.union(WindowInsets.displayCutout).union(WindowInsets.navigationBars)
