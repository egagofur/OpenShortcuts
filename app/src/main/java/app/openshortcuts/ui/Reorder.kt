package app.openshortcuts.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Where a row dragged from [from] by [dy] pixels lands, given rows [slot] pixels apart. */
fun reorderTarget(from: Int, dy: Float, slot: Float, size: Int): Int =
    if (slot <= 0f || size == 0) from else (from + (dy / slot).roundToInt()).coerceIn(0, size - 1)

/**
 * A short vertical list whose rows reorder by press-and-hold then drag. Rows should share one height;
 * the others slide aside as the held row passes them, and [onMove] fires once on release.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onMove: (from: Int, to: Int) -> Unit,
    row: @Composable (item: T, dragging: Boolean) -> Unit,
) {
    val view = LocalView.current
    val gap = 8.dp
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    var rowHeight by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var dy by remember { mutableFloatStateOf(0f) }
    val slot = rowHeight + gapPx
    val latestItems by rememberUpdatedState(items)
    val target = dragging?.let { reorderTarget(it, dy, slot, items.size) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        items.forEachIndexed { i, item ->
            key(keyOf(item)) {
                val from = dragging
                val shift = when {
                    from == null || target == null || i == from -> 0f
                    i in (from + 1)..target -> -slot
                    i in target until from -> slot
                    else -> 0f
                }
                val animatedShift by animateFloatAsState(shift, tween(180, easing = Motion.easeOut), label = "reorderShift")
                val itemKey = keyOf(item)
                Box(
                    Modifier
                        .zIndex(if (i == from) 1f else 0f)
                        .onSizeChanged { if (it.height > 0) rowHeight = it.height }
                        .graphicsLayer {
                            translationY = if (i == from) dy else animatedShift
                            val lifted = i == from
                            scaleX = if (lifted) 1.02f else 1f
                            scaleY = if (lifted) 1.02f else 1f
                        }
                        .pointerInput(itemKey) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = latestItems.indexOfFirst { keyOf(it) == itemKey }.takeIf { it >= 0 }
                                    dy = 0f
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dy += amount.y
                                },
                                onDragEnd = {
                                    val f = dragging
                                    val t = f?.let { reorderTarget(it, dy, slot, latestItems.size) }
                                    dragging = null
                                    dy = 0f
                                    if (f != null && t != null && t != f) {
                                        view.tick()
                                        onMove(f, t)
                                    }
                                },
                                onDragCancel = {
                                    dragging = null
                                    dy = 0f
                                },
                            )
                        },
                ) { row(item, i == from) }
            }
        }
    }
}
