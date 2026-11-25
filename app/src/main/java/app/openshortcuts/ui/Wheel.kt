package app.openshortcuts.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * iOS-style switch: 51×31 track, white thumb with a soft shadow. The thumb stretches while the
 * finger is down (as on iOS) and slides with a critically damped spring.
 */
@Composable
fun IosSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?, label: String? = null) {
    val c = Theme.c
    val view = LocalView.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val reduced = Theme.reduced
    val track by animateColorAsState(if (checked) c.positive else c.fill, tween(200, easing = Motion.easeOut), label = "track")
    val stretch by animateDpAsState(if (pressed && !reduced) 7.dp else 0.dp, if (reduced) tween(0) else Motion.settle(), label = "stretch")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, if (reduced) tween(0) else Motion.settle(), label = "thumb")
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .then(
                if (onChange != null) Modifier.toggleable(
                    value = checked, interactionSource = src, indication = null, role = Role.Switch,
                    onValueChange = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onChange(it) },
                ) else Modifier,
            )
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        // When on, the stretch grows leftwards so the thumb stays pinned to the right edge.
        Box(
            Modifier
                .offset(x = 2.dp + x - if (checked) stretch else 0.dp)
                .size(width = 27.dp + stretch, height = 27.dp)
                .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private val ROW = 40.dp
private const val VISIBLE = 5

/**
 * One wheel of an iOS picker: snaps row by row, ticks once per row, rows fade and shrink away
 * from the centre band. Loops by repeating the values.
 */
@Composable
private fun Wheel(values: List<Int>, selected: Int, format: (Int) -> String, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Theme.c
    val view = LocalView.current
    val loops = 200
    val start = loops / 2 * values.size + values.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = start - VISIBLE / 2)
    var centre by remember { mutableIntStateOf(start) }
    LaunchedEffect(state) {
        snapshotFlow {
            val first = state.firstVisibleItemIndex
            val off = state.firstVisibleItemScrollOffset
            val rowPx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            first + VISIBLE / 2 + if (off > rowPx / 2) 1 else 0
        }.collect { i ->
            if (i != centre) { centre = i; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            onSelect(values[i % values.size])
        }
    }
    LazyColumn(
        modifier.height(ROW * VISIBLE),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(loops * values.size) { i ->
            val v = values[i % values.size]
            val d = abs(i - centre).toFloat()
            Box(
                Modifier.fillMaxWidth().height(ROW).graphicsLayer {
                    alpha = (1f - d * 0.32f).coerceAtLeast(0.15f)
                    val s = (1f - d * 0.06f).coerceAtLeast(0.8f); scaleX = s; scaleY = s
                },
                contentAlignment = Alignment.Center,
            ) {
                Text(format(v), style = Theme.t.title.copy(fontSize = 23.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                    fontFeatureSettings = "tnum"), color = c.ink, textAlign = TextAlign.Center)
            }
        }
    }
}

/** iOS wheel time picker in a Duo dialog. Minutes in steps of 1, 24-hour. */
@Composable
fun TimeWheelDialog(title: String, minutes: Int, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    val c = Theme.c
    var h by remember { mutableIntStateOf(minutes / 60) }
    var m by remember { mutableIntStateOf(minutes % 60) }
    DuoDialog(onDismiss) {
        DialogTitle(title)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Selection band behind the centre row.
            Box(Modifier.fillMaxWidth().height(ROW).clip(RoundedCornerShape(10.dp)).background(c.fill))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Wheel((0..23).toList(), h, { "%02d".format(it) }, { h = it }, Modifier.width(72.dp))
                Text(":", style = Theme.t.title, color = c.ink, modifier = Modifier.padding(horizontal = 6.dp))
                Wheel((0..59).toList(), m, { "%02d".format(it) }, { m = it }, Modifier.width(72.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietButton("Cancel", Modifier.weight(1f), color = c.ink, onClick = onDismiss)
            PrimaryButton("Done", Modifier.weight(1f)) { onDone(h * 60 + m) }
        }
    }
}
