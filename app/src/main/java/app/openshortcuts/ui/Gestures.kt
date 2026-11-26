package app.openshortcuts.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun SwipeToDelete(
    label: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.cardRadius),
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Theme.c
    val view = LocalView.current
    val reduced = Theme.reduced
    val scope = rememberCoroutineScope()
    val flick = with(LocalDensity.current) { 700.dp.toPx() }
    var width by remember { mutableFloatStateOf(1f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<Job?>(null) }
    val threshold = width * 0.38f
    fun offset() = if (raw > 0f) raw * 0.2f else raw
    val revealed by remember { androidx.compose.runtime.derivedStateOf { raw < 0f } }

    val trash by animateFloatAsState(
        if (armed) 1.15f else 1f,
        if (reduced) tween(0) else spring(dampingRatio = 0.5f, stiffness = 600f), label = "trash",
    )

    val drag = rememberDraggableState { d ->
        raw += d
        val now = raw < -threshold
        if (now != armed) { armed = now; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics { customActions = listOf(CustomAccessibilityAction("Delete $label") { onDelete(); true }) },
    ) {
        if (revealed) Box(
            Modifier.matchParentSize().clip(shape).background(if (armed) c.danger else c.danger.copy(alpha = 0.75f)),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Glyph(
                "trash", Color.White, size = 22.dp,
                modifier = Modifier.padding(end = 26.dp).graphicsLayer {
                    val reveal = (-offset() / threshold).coerceIn(0f, 1f)
                    alpha = reveal; scaleX = trash * (0.6f + 0.4f * reveal); scaleY = scaleX
                },
            )
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset() }
                .draggable(
                    drag, Orientation.Horizontal, enabled = enabled,
                    onDragStarted = { release?.cancel() },
                    onDragStopped = { v ->
                        val gone = raw < -threshold || v < -flick
                        release = scope.launch {
                            if (gone) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                animate(raw, -width * 1.05f, initialVelocity = v.coerceAtMost(0f),
                                    animationSpec = if (reduced) tween(120) else spring(dampingRatio = 1f, stiffness = 500f)) { x, _ -> raw = x }
                                onDelete()
                                raw = 0f; armed = false
                            } else {
                                armed = false
                                animate(raw, 0f, initialVelocity = v,
                                    animationSpec = if (reduced) tween(120) else spring(dampingRatio = 0.75f, stiffness = 520f)) { x, _ -> raw = x }
                            }
                        }
                    },
                ),
            content = content,
        )
    }
}

@Composable
fun BoxScope.UndoBar(text: String?, action: String, onAction: () -> Unit) {
    val c = Theme.c
    var last by remember { mutableStateOf(text.orEmpty()) }
    if (text != null) last = text
    AnimatedVisibility(
        visible = text != null,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = if (Theme.reduced) fadeIn(tween(150)) else slideInVertically(Motion.fling()) { it } + fadeIn(tween(150)),
        exit = if (Theme.reduced) fadeOut(tween(120)) else slideOutVertically(tween(200, easing = Motion.easeOut)) { it } + fadeOut(tween(150)),
    ) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Bottom)).padding(16.dp)
                .widthIn(max = 420.dp)
                .glass(com.kyant.shapes.RoundedRectangle(24.dp))
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(last, style = Theme.t.label, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier.heightIn(min = 40.dp).pressable(label = action, onClick = onAction)
                    .clip(RoundedCornerShape(20.dp)).background(c.accent).padding(horizontal = 16.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(action, style = Theme.t.label, color = Color.White, maxLines = 1) }
        }
    }
}

@Composable
fun SettingsGroup(
    title: String? = null,
    footer: String? = null,
    surface: Color = Theme.c.bg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Theme.c
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title.uppercase(), style = Theme.t.small.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(0.6f, androidx.compose.ui.unit.TextUnitType.Sp)),
                color = c.ink2, modifier = Modifier.padding(start = 14.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surface), content = content)
        if (footer != null) {
            Text(footer, style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp))
        }
    }
}

@Composable
fun SettingsDivider(inset: androidx.compose.ui.unit.Dp = 58.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(1.dp).background(Theme.c.hairline))
}

@Composable
fun SettingsRow(
    title: String,
    glyph: String? = null,
    tint: Color = Theme.c.accent,
    subtitle: String? = null,
    titleColor: Color = Theme.c.ink,
    actionLabel: String = title,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.pressable(scale = 0.985f, label = actionLabel, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (glyph != null) IconTile(glyph, tint, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.t.body, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = Theme.t.small, color = c.ink2)
        }
        trailing()
    }
}

@Composable
fun DuoSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)? = null) = IosSwitch(checked, onChange)
