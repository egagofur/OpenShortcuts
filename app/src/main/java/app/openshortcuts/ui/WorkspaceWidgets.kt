package app.openshortcuts.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.engine.WorkspaceItem
import app.openshortcuts.model.AdapterAction
import app.openshortcuts.model.AdapterRowAction
import app.openshortcuts.model.DashboardActionRef
import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.Store
import app.openshortcuts.model.Tint
import app.openshortcuts.model.WorkspaceAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SkeletonCard(heightDp: Int = 78) {
    val c = Theme.c
    val reduced = Theme.reduced
    val alpha = if (!reduced) {
        rememberInfiniteTransition(label = "skel").animateFloat(
            0.35f, 0.72f,
            infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "a",
        ).value
    } else 0.5f

    Card(Modifier.fillMaxWidth().height(heightDp.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c.fill.copy(alpha = alpha)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.68f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c.fill.copy(alpha = alpha)))
                Box(Modifier.fillMaxWidth(0.42f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(c.fill.copy(alpha = alpha * 0.8f)))
            }
        }
    }
}

@Composable
fun ErrorWidgetCard(
    title: String,
    error: String,
    onRetry: () -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    val c = Theme.c
    val isAuth = error.contains("401") || error.contains("403") || error.contains("Unauthorized", ignoreCase = true)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile("bolt", c.danger, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title.ifBlank { "Couldn't load" }, style = Theme.t.label, color = c.ink, maxLines = 1)
                Text(error, style = Theme.t.small, color = c.danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            if (isAuth && onSettings != null) {
                TokenPill("Token", onClick = onSettings)
            } else {
                TokenPill("Retry", onClick = onRetry)
            }
        }
    }
}

@Composable
fun EmptyWidgetCard(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile("check", c.ink3, size = 30.dp)
            Spacer(Modifier.width(12.dp))
            Text(message, style = Theme.t.small, color = c.ink2, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.width(8.dp))
                TokenPill(actionLabel, onClick = onAction)
            }
        }
    }
}

@Composable
fun StaggeredBlock(index: Int, content: @Composable () -> Unit) {
    val reduced = Theme.reduced
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    val slide = remember { Animatable(if (reduced) 0f else 12f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay((index.coerceAtMost(5) * 30L))
            launch { alpha.animateTo(1f, tween(200, easing = Motion.easeOut)) }
            slide.animateTo(0f, tween(220, easing = Motion.easeOut))
        }
    }
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = slide.value * density
        },
    ) { content() }
}

@Composable
fun StatGridWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val metrics = widget.metrics
    if (metrics.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (widget.title.isNotBlank()) {
            Text(widget.title, style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 4.dp))
        }
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val rawVal = AdapterEngine.extractString(payload, m.valuePath).ifBlank { "—" }
                    val numDouble = rawVal.toDoubleOrNull()
                    val numVal = numDouble?.let {
                        if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it)
                    } ?: rawVal
                    val targetDouble = if (m.targetPath.isNotBlank()) AdapterEngine.extractNumber(payload, m.targetPath) else null
                    val targetStr = targetDouble?.let {
                        val tFmt = if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.1f", it)
                        val pct = if (numDouble != null && it > 0) " · ${(numDouble / it * 100).toInt()}%" else ""
                        "of $tFmt${m.suffix}$pct"
                    }.orEmpty()
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(m.tint.color()))
                                Spacer(Modifier.width(6.dp))
                                Text(m.label, style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "$numVal${m.suffix}",
                                style = Theme.t.large.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"),
                                color = c.ink,
                                maxLines = 1,
                                softWrap = false,
                            )
                            if (targetStr.isNotEmpty()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    targetStr,
                                    style = Theme.t.small.copy(fontFeatureSettings = "tnum"),
                                    color = c.ink3,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun ProgressRingWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val view = LocalView.current
    val reduced = Theme.reduced
    val cur = AdapterEngine.extractNumber(payload, widget.currentPath) ?: 0.0
    val goal = (AdapterEngine.extractNumber(payload, widget.targetPath) ?: 100.0).coerceAtLeast(1.0)
    val frac = (cur / goal).toFloat().coerceIn(0f, 1f)
    val sweep = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(cur, goal) {
        if (!reduced) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(650, easing = Motion.easeOut))
        }
    }
    val arcColor = if (frac >= 1f) c.positive else widget.tint.color()
    val fmt = { d: Double -> if (d % 1.0 == 0.0) d.toInt().toString() else String.format("%.1f", d) }
    var showBreakdown by remember { mutableStateOf(false) }

    Card(
        Modifier
            .fillMaxWidth()
            .pressable(
                label = "${widget.title} breakdown",
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    showBreakdown = true
                },
                onClick = {
                    view.tick()
                    showBreakdown = true
                },
            ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(52.dp)) {
                    val w = 5.dp.toPx()
                    val inset = w / 2
                    val sz = Size(size.width - w, size.height - w)
                    drawArc(c.fill, 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                    drawArc(arcColor, -90f, 360f * frac * sweep.value, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text(
                    "${(frac * 100 * sweep.value).toInt()}%",
                    style = Theme.t.small.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"),
                    color = c.ink,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(widget.title.ifBlank { "Progress" }, style = Theme.t.label, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${fmt(cur)}${widget.unit} of ${fmt(goal)}${widget.unit}",
                    style = Theme.t.body.copy(fontFeatureSettings = "tnum"),
                    color = c.ink2,
                )
                Text("Hold or tap for breakdown", style = Theme.t.small.copy(fontSize = 11.sp), color = c.ink3)
            }
            Glyph("chevronRight", c.ink3, size = 16.dp)
        }
    }

    if (showBreakdown) {
        val diff = goal - cur
        val pct = (cur / goal * 100).toInt()
        DuoDialog(onDismiss = { showBreakdown = false }) {
            DialogTitle(widget.title.ifBlank { "Target Breakdown" }, "$pct% completed")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BreakdownRow("Logged", "${fmt(cur)}${widget.unit}", c.ink)
                    BreakdownRow("Target", "${fmt(goal)}${widget.unit}", c.ink2)
                    BreakdownRow(
                        if (diff >= 0) "Remaining" else "Overtime",
                        "${fmt(kotlin.math.abs(diff))}${widget.unit}",
                        if (diff >= 0) c.accent else c.positive,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) { showBreakdown = false }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, valueColor: Color) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Theme.t.small, color = c.ink3)
        Text(value, style = Theme.t.label.copy(fontFeatureSettings = "tnum"), color = valueColor)
    }
}

@Composable
fun LiveStatusWidget(
    widget: DashboardWidget,
    adapter: WorkspaceAdapter,
    payload: Any?,
    onRunAction: (AdapterAction) -> Unit,
) {
    val c = Theme.c
    val reduced = Theme.reduced
    val active = AdapterEngine.extractBool(payload, widget.activePath)
    val activeTitle = AdapterEngine.extractString(payload, widget.activeTitlePath).ifBlank { "Active" }
    val pulse = if (active && !reduced) {
        rememberInfiniteTransition(label = "liveDot").animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "alpha",
        ).value
    } else 1f

    val actionId = if (active) widget.activeActionId else widget.inactiveActionId
    val action = adapter.actionById(actionId)

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer { alpha = pulse }
                    .clip(CircleShape)
                    .background(if (active) c.positive else c.ink3),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (active) widget.title.ifBlank { "Running" } else widget.inactiveText,
                    style = Theme.t.label,
                    color = c.ink,
                )
                if (active) {
                    Text(activeTitle, style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (action != null) {
                Box(
                    Modifier
                        .pressable(scale = 0.95f, label = action.title) { onRunAction(action) }
                        .clip(RoundedCornerShape(16.dp))
                        .background(action.tint.color())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(action.title, style = Theme.t.small, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ActionGridWidget(
    widget: DashboardWidget,
    adapter: WorkspaceAdapter,
    runs: RunController,
    onRunAdapterAction: (AdapterAction) -> Unit,
) {
    val c = Theme.c
    val view = LocalView.current
    if (widget.actionRefs.isEmpty()) return
    val cols = if (isShort) 3 else 2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (widget.title.isNotBlank()) {
            Text(widget.title, style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 4.dp))
        }
        widget.actionRefs.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { ref ->
                    ActionRefTile(ref, adapter, runs, Modifier.weight(1f)) {
                        view.tick()
                        val act = adapter.actionById(ref.actionId)
                        if (act != null) {
                            onRunAdapterAction(act)
                        } else if (!ref.shortcutName.isNullOrBlank()) {
                            val s = Store.library.value.shortcuts.firstOrNull { it.name.equals(ref.shortcutName, ignoreCase = true) }
                            if (s != null) runs.run(s)
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ActionRefTile(
    ref: DashboardActionRef,
    adapter: WorkspaceAdapter,
    runs: RunController,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val s = ref.shortcutName?.let { name -> Store.library.value.shortcuts.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    val running = s != null && runs.running == s.id
    val breathe = if (running && !Theme.reduced) {
        rememberInfiniteTransition(label = "tileRun").animateFloat(
            1f, 0.82f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "b",
        ).value
    } else 1f

    Box(
        modifier
            .height(100.dp)
            .graphicsLayer { alpha = breathe }
            .pressable(label = ref.title, onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(ref.tint.color())
            .padding(14.dp),
    ) {
        Glyph(if (running) "timer" else ref.glyph, Color.White, size = 24.dp, modifier = Modifier.align(Alignment.TopStart))
        Text(
            ref.title,
            style = Theme.t.label,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceBarWidget(
    widget: DashboardWidget,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val c = Theme.c
    val view = LocalView.current
    val current = selected.ifBlank { widget.choices.firstOrNull().orEmpty() }
    Card(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(widget.prefix, style = Theme.t.action, color = c.ink)
            TokenPill(current.ifBlank { "All" }) {
                if (widget.choices.isNotEmpty()) {
                    view.tick()
                    val idx = widget.choices.indexOf(current)
                    onSelect(widget.choices[(idx + 1) % widget.choices.size])
                }
            }
        }
    }
}

@Composable
fun NoteCardWidget(widget: DashboardWidget) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            IconTile(widget.glyph, widget.tint.color(), size = 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (widget.title.isNotBlank()) {
                    Text(widget.title, style = Theme.t.label, color = c.ink)
                    Spacer(Modifier.height(4.dp))
                }
                Text(widget.body, style = Theme.t.small, color = c.ink2)
            }
        }
    }
}

@Composable
fun ProfileCardWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val view = LocalView.current
    val name = AdapterEngine.extractString(payload, widget.titlePath.ifBlank { "name" }).ifBlank { widget.title.ifBlank { "Profile" } }
    val handle = AdapterEngine.extractString(payload, widget.subtitlePath.ifBlank { "username" })
    val email = AdapterEngine.extractString(payload, widget.metaPath.ifBlank { "email" })
    val state = AdapterEngine.extractString(payload, widget.badgePath.ifBlank { "state" })
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "U" }
    var showInfo by remember { mutableStateOf(false) }

    Card(
        Modifier
            .fillMaxWidth()
            .pressable(
                label = "$name profile",
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    showInfo = true
                },
                onClick = {
                    view.tick()
                    showInfo = true
                },
            ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(widget.tint.color()),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials, style = Theme.t.title.copy(fontSize = 18.sp), color = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = Theme.t.label.copy(fontSize = 16.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (handle.isNotBlank()) {
                    Text("@$handle", style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (email.isNotBlank()) {
                    Text(email, style = Theme.t.small, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (state.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(c.positive.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(state, style = Theme.t.small, color = c.positive, maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (showInfo) {
        val id = AdapterEngine.extractString(payload, "id")
        val bio = AdapterEngine.extractString(payload, "bio")
        val jobTitle = AdapterEngine.extractString(payload, "job_title")
        val created = AdapterEngine.formatIfTimestamp(AdapterEngine.extractString(payload, "created_at"))
        DuoDialog(onDismiss = { showInfo = false }) {
            DialogTitle(name, if (handle.isNotBlank()) "@$handle" else null)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (id.isNotBlank()) BreakdownRow("User ID", "#$id", c.ink)
                    if (email.isNotBlank()) BreakdownRow("Email", email, c.ink2)
                    if (jobTitle.isNotBlank()) BreakdownRow("Role", jobTitle, c.ink2)
                    if (state.isNotBlank()) BreakdownRow("State", state, c.positive)
                    if (created.isNotBlank()) BreakdownRow("Joined", created, c.ink2)
                    if (bio.isNotBlank()) BreakdownRow("Bio", bio, c.ink2)
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) { showInfo = false }
        }
    }
}

@Composable
fun HeatmapChartWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val view = LocalView.current
    val isDateMap = remember(payload) {
        val obj = payload as? org.json.JSONObject
        obj != null && obj.keys().asSequence().take(5).any { it.matches(DATE_KEY) }
    }
    val dateField = widget.metaPath.ifBlank { "created_at" }
    // Events grouped by day, and per-day counts: built once per payload on a background thread.
    val byDate by produceState<Map<java.time.LocalDate, List<org.json.JSONObject>>>(emptyMap(), payload, isDateMap, dateField) {
        value = if (isDateMap) emptyMap()
        else withContext(Dispatchers.Default) { eventsByDate(AdapterEngine.extractList(payload, widget.itemsPath), dateField) }
    }
    val countsByDate = remember(payload, byDate, isDateMap, widget.seedData) {
        val map = LinkedHashMap<String, Int>(widget.seedData)
        if (isDateMap) {
            val obj = payload as org.json.JSONObject
            obj.keys().forEach { k ->
                if (k.matches(DATE_KEY)) {
                    val v = obj.optInt(k, 0)
                    if (v > 0) map[k] = maxOf(map[k] ?: 0, v)
                }
            }
        } else {
            byDate.forEach { (d, list) -> map[d.toString()] = maxOf(map[d.toString()] ?: 0, list.size) }
        }
        map
    }
    val today = remember { java.time.LocalDate.now() }
    val maxCount = remember(countsByDate) { (countsByDate.values.maxOrNull() ?: 1).coerceAtLeast(1) }
    val total = remember(countsByDate) { countsByDate.values.sum() }
    var selectedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var inspectDate by remember { mutableStateOf<java.time.LocalDate?>(null) }

    val baseColor = widget.tint.color()
    fun cellColor(cnt: Int): Color = when {
        cnt <= 0 -> c.fill.copy(alpha = 0.55f)
        cnt <= maxCount * 0.25 -> baseColor.copy(alpha = 0.28f)
        cnt <= maxCount * 0.50 -> baseColor.copy(alpha = 0.52f)
        cnt <= maxCount * 0.75 -> baseColor.copy(alpha = 0.76f)
        else -> baseColor
    }

    Card(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val visibleWeeks = ((maxWidth - 24.dp) / 15.dp).toInt().coerceAtLeast(18)
            val weeksCount = if (isDateMap || widget.seedData.isNotEmpty()) maxOf(visibleWeeks, 52) else visibleWeeks
            val currentMonday = remember(today) { today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }
            val startMonday = remember(currentMonday, weeksCount) { currentMonday.minusWeeks((weeksCount - 1).toLong()) }
            val weeks = remember(startMonday, weeksCount) {
                (0 until weeksCount).map { w ->
                    val mon = startMonday.plusWeeks(w.toLong())
                    (0..6).map { d -> mon.plusDays(d.toLong()) }
                }
            }

            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(widget.title.ifBlank { "Activity" }, style = Theme.t.label, color = c.ink, maxLines = 1)
                        val sel = selectedDate
                        Text(
                            if (sel != null) "$sel · ${countsByDate[sel.toString()] ?: 0} events"
                            else "Last $weeksCount weeks",
                            style = Theme.t.small,
                            color = if (sel != null) baseColor else c.ink3,
                            maxLines = 1,
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$total", style = Theme.t.title.copy(fontFeatureSettings = "tnum"), color = baseColor, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(4.dp))
                        Text("events", style = Theme.t.small, color = c.ink2, maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(
                        Modifier.padding(top = 18.dp, end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        listOf("M", "", "W", "", "F", "", "S").forEach { lbl ->
                            Box(Modifier.height(12.dp), contentAlignment = Alignment.CenterStart) {
                                if (lbl.isNotEmpty()) Text(lbl, style = Theme.t.small.copy(fontSize = 10.sp), color = c.ink3)
                            }
                        }
                    }
                    val density = LocalDensity.current
                    val cellPx = with(density) { 12.dp.toPx() }
                    val gapPx = with(density) { 3.dp.toPx() }
                    val cornerPx = with(density) { 3.dp.toPx() }
                    val colStep = cellPx + gapPx
                    val totalWidthDp = 15.dp * weeksCount
                    val emptyCellColor = c.fill.copy(alpha = 0.55f)
                    val selStrokeColor = c.ink

                    Column(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    ) {
                        Row(Modifier.width(totalWidthDp).height(16.dp)) {
                            weeks.forEachIndexed { wIdx, week ->
                                val prevMonth = if (wIdx > 0) weeks[wIdx - 1].first().month else null
                                val showMonth = wIdx == 0 || (week.first().month != prevMonth && wIdx > 1)
                                val monthLbl = if (showMonth) week.first().month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) else ""
                                Box(Modifier.width(15.dp), contentAlignment = Alignment.CenterStart) {
                                    if (monthLbl.isNotEmpty()) {
                                        Text(monthLbl, style = Theme.t.small.copy(fontSize = 10.sp), color = c.ink3, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Canvas(
                            Modifier
                                .width(totalWidthDp)
                                .height(102.dp) // 7 * 12dp + 6 * 3dp = 102dp
                                .pointerInput(weeks, today) {
                                    detectTapGestures(
                                        onTap = { pos ->
                                            val wIdx = (pos.x / colStep).toInt()
                                            val dIdx = (pos.y / colStep).toInt()
                                            val d = weeks.getOrNull(wIdx)?.getOrNull(dIdx)
                                            if (d != null && !d.isAfter(today)) {
                                                view.tick()
                                                selectedDate = if (selectedDate == d) null else d
                                            }
                                        },
                                        onLongPress = { pos ->
                                            val wIdx = (pos.x / colStep).toInt()
                                            val dIdx = (pos.y / colStep).toInt()
                                            val d = weeks.getOrNull(wIdx)?.getOrNull(dIdx)
                                            if (d != null && !d.isAfter(today)) {
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                selectedDate = d
                                                inspectDate = d
                                            }
                                        },
                                    )
                                },
                        ) {
                            val r = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
                            val sz = Size(cellPx, cellPx)
                            weeks.forEachIndexed { wIdx, week ->
                                val x = wIdx * colStep
                                week.forEachIndexed { dIdx, d ->
                                    if (!d.isAfter(today)) {
                                        val y = dIdx * colStep
                                        val cnt = countsByDate[d.toString()] ?: 0
                                        val col = if (cnt <= 0) emptyCellColor else cellColor(cnt)
                                        drawRoundRect(col, Offset(x, y), sz, r)
                                        if (selectedDate == d) {
                                            drawRoundRect(selStrokeColor, Offset(x, y), sz, r, style = Stroke(1.5.dp.toPx()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Less", style = Theme.t.small.copy(fontSize = 10.sp), color = c.ink3)
                        Spacer(Modifier.width(2.dp))
                        listOf(0f, 0.28f, 0.52f, 0.76f, 1f).forEach { a ->
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(if (a == 0f) c.fill.copy(alpha = 0.55f) else baseColor.copy(alpha = a)),
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        Text("More", style = Theme.t.small.copy(fontSize = 10.sp), color = c.ink3)
                    }
                    Text("Pushes, MRs & comments", style = Theme.t.small.copy(fontSize = 10.sp), color = c.ink3)
                }
            }
        }
    }

    inspectDate?.let { d ->
        val dayEvents = byDate[d].orEmpty()
        val dayCount = countsByDate[d.toString()] ?: dayEvents.size
        DuoDialog(onDismiss = { inspectDate = null }) {
            DialogTitle("$d", "$dayCount contributions")
            if (dayEvents.isEmpty()) {
                Text(
                    if (dayCount > 0) "$dayCount contributions recorded on $d." else "No activity recorded on this day.",
                    style = Theme.t.body,
                    color = c.ink2,
                )
            } else {
                val grouped = dayEvents.groupingBy { it.optString("action_name", "event") }.eachCount()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grouped.forEach { (actName, n) ->
                            BreakdownRow(actName.replace('_', ' '), "$n", widget.tint.color())
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) { inspectDate = null }
        }
    }
}

@Composable
fun WorkspaceRowCard(
    item: WorkspaceItem,
    widget: DashboardWidget,
    swipeRight: AdapterRowAction?,
    swipeLeft: AdapterRowAction?,
    onSwipe: (AdapterRowAction) -> Unit,
    onClick: () -> Unit,
) {
    val c = Theme.c
    val view = LocalView.current
    val reduced = Theme.reduced
    val scope = rememberCoroutineScope()
    val flick = with(LocalDensity.current) { 700.dp.toPx() }
    var width by remember { mutableStateOf(1f) }
    var raw by remember { mutableStateOf(0f) }
    var armedDir by remember { mutableIntStateOf(0) }
    var release by remember { mutableStateOf<Job?>(null) }
    val threshold = width * 0.38f

    val drag = rememberDraggableState { d ->
        val next = raw + d
        raw = if ((next > 0 && swipeRight != null) || (next < 0 && swipeLeft != null)) next else next * 0.15f
        val dir = when {
            raw > threshold && swipeRight != null -> 1
            raw < -threshold && swipeLeft != null -> -1
            else -> 0
        }
        if (dir != armedDir) {
            armedDir = dir
            if (dir != 0) view.tick()
        }
    }

    val shape = RoundedCornerShape(Dimens.cardRadius)
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                val acts = buildList {
                    swipeRight?.let { a -> add(CustomAccessibilityAction(a.label) { onSwipe(a); true }) }
                    swipeLeft?.let { a -> add(CustomAccessibilityAction(a.label) { onSwipe(a); true }) }
                }
                if (acts.isNotEmpty()) customActions = acts
            },
    ) {
        if (raw > 0f && swipeRight != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(if (armedDir == 1) swipeRight.tint.color() else swipeRight.tint.color().copy(alpha = 0.75f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(Modifier.padding(start = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(swipeRight.glyph, Color.White, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(swipeRight.label, style = Theme.t.label, color = Color.White)
                }
            }
        } else if (raw < 0f && swipeLeft != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(if (armedDir == -1) swipeLeft.tint.color() else swipeLeft.tint.color().copy(alpha = 0.75f)),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(Modifier.padding(end = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(swipeLeft.label, style = Theme.t.label, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Glyph(swipeLeft.glyph, Color.White, size = 20.dp)
                }
            }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = raw }
                .draggable(
                    drag,
                    Orientation.Horizontal,
                    enabled = swipeRight != null || swipeLeft != null,
                    onDragStarted = { release?.cancel() },
                    onDragStopped = { v ->
                        val hitRight = (raw > threshold || v > flick) && swipeRight != null
                        val hitLeft = (raw < -threshold || v < -flick) && swipeLeft != null
                        release = scope.launch {
                            if (hitRight && swipeRight != null) onSwipe(swipeRight)
                            else if (hitLeft && swipeLeft != null) onSwipe(swipeLeft)
                            if (reduced) raw = 0f
                            else androidx.compose.animation.core.animate(raw, 0f, animationSpec = Motion.fling()) { x, _ -> raw = x }
                            armedDir = 0
                        }
                    },
                )
                .pressable(label = item.title, onClick = onClick),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                val badgeTint = widget.badgeTints.entries.firstOrNull { it.key.equals(item.badge.trim(), ignoreCase = true) }?.value
                if (widget.glyph != "none") {
                    IconTile(widget.glyph.ifBlank { "layers" }, (badgeTint ?: widget.tint).color(), size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (item.badge.isNotBlank()) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeTint?.color()?.copy(alpha = 0.16f) ?: c.accentWash)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                AdapterEngine.prettyBadge(item.badge),
                                style = Theme.t.small.copy(fontSize = 12.sp),
                                color = badgeTint?.color() ?: c.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    val subFormatted = remember(item.subtitle) { AdapterEngine.formatIfTimestamp(item.subtitle) }
                    val metaFormatted = remember(item.meta) { AdapterEngine.formatIfTimestamp(item.meta) }
                    Text(item.title, style = Theme.t.label, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subFormatted.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(subFormatted, style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (metaFormatted.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(metaFormatted, style = Theme.t.small, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item.value?.let { v ->
                    Spacer(Modifier.width(10.dp))
                    Text(
                        formatRowValue(v, widget.unit),
                        style = Theme.t.label.copy(fontFeatureSettings = "tnum"),
                        color = c.ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Glyph("chevronRight", c.ink3, size = 16.dp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

internal fun formatRowValue(v: Double, unit: String): String =
    (if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.US, "%.1f", v)) + unit

private val DATE_KEY = Regex("""\d{4}-\d{2}-\d{2}""")

/** Each event's date read once, grouped by local day. */
private fun eventsByDate(events: List<org.json.JSONObject>, dateField: String): Map<java.time.LocalDate, List<org.json.JSONObject>> =
    events.groupBy { AdapterEngine.toLocalDate(AdapterEngine.extractString(it, dateField)) }
        .filterKeys { it != null }
        .mapKeys { it.key!! }
