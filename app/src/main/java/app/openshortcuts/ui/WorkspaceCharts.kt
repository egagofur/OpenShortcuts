package app.openshortcuts.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.model.DashboardWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private fun fmtNum(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

/**
 * One headline number with a bar toward its goal. The first metric is the headline; any others
 * follow as a slim row each, so "this week" and "this period" read as one card instead of four.
 */
@Composable
fun TargetBarWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val reduced = Theme.reduced
    val metrics = widget.metrics
    if (metrics.isEmpty()) return
    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    val values = metrics.map { m ->
        AdapterEngine.extractNumber(payload, m.valuePath) to
            m.targetPath.takeIf { it.isNotBlank() }?.let { AdapterEngine.extractNumber(payload, it) }
    }
    LaunchedEffect(values) {
        if (!reduced) {
            grow.snapTo(0f)
            grow.animateTo(1f, tween(650, easing = Motion.easeOut))
        }
    }

    val head = metrics.first()
    val (cur, goal) = values.first()
    val frac = if (cur != null && goal != null && goal > 0) (cur / goal).toFloat() else null
    val description = buildString {
        metrics.forEachIndexed { i, m ->
            val (v, g) = values[i]
            append(m.label).append(": ").append(v?.let { fmtNum(it) + m.suffix } ?: "unknown")
            if (g != null) append(" of ").append(fmtNum(g)).append(m.suffix)
            append(". ")
        }
    }

    Card(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    widget.title.ifBlank { head.label },
                    style = Theme.t.label,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (frac != null) {
                    Text("${(frac * 100).toInt()}%", style = Theme.t.small.copy(fontFeatureSettings = "tnum"), color = c.ink2)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    cur?.let { fmtNum(it) + head.suffix } ?: "—",
                    style = Theme.t.large.copy(fontSize = 34.sp, lineHeight = 38.sp, fontFeatureSettings = "tnum"),
                    color = c.ink,
                    maxLines = 1,
                    softWrap = false,
                )
                if (goal != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "of ${fmtNum(goal)}${head.suffix}",
                        style = Theme.t.body.copy(fontFeatureSettings = "tnum"),
                        color = c.ink2,
                        modifier = Modifier.padding(bottom = 4.dp),
                        maxLines = 1,
                    )
                }
            }
            if (frac != null && cur != null && goal != null) {
                Spacer(Modifier.height(12.dp))
                GoalBar(frac, grow.value, head.tint.color(), 10.dp)
                Spacer(Modifier.height(8.dp))
                val left = goal - cur
                Text(
                    if (left > 0) "${fmtNum(left)}${head.suffix} to go" else "Goal reached" + if (left < 0) ", ${fmtNum(-left)}${head.suffix} over" else "",
                    style = Theme.t.small.copy(fontFeatureSettings = "tnum"),
                    color = if (left > 0) c.ink2 else c.positive,
                )
            }
            metrics.drop(1).forEachIndexed { i, m ->
                val (v, g) = values[i + 1]
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.fill))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.label, style = Theme.t.small, color = c.ink2, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (v?.let { fmtNum(it) + m.suffix } ?: "—") + (g?.let { " of ${fmtNum(it)}${m.suffix}" } ?: ""),
                        style = Theme.t.small.copy(fontFeatureSettings = "tnum"),
                        color = c.ink,
                        maxLines = 1,
                    )
                }
                if (v != null && g != null && g > 0) {
                    Spacer(Modifier.height(6.dp))
                    GoalBar((v / g).toFloat(), grow.value, m.tint.color(), 6.dp)
                }
            }
        }
    }
}

@Composable
private fun GoalBar(frac: Float, grow: Float, color: androidx.compose.ui.graphics.Color, height: androidx.compose.ui.unit.Dp) {
    val c = Theme.c
    val shown = (frac.coerceIn(0f, 1f) * grow)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(c.fill),
    ) {
        if (shown > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(height / 2))
                    .background(if (frac >= 1f) c.positive else color),
            )
        }
    }
}

/**
 * Bars per day or per week. Counts items, or sums [DashboardWidget.valuePath] (hours, amounts).
 * An optional dashed line marks the goal for each bar; the bar still in progress is drawn lighter.
 */
@Composable
fun BarChartWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val view = LocalView.current
    val reduced = Theme.reduced
    val today = remember { LocalDate.now() }
    val weekly = widget.bucket == "week"
    val thisWeek = !weekly && widget.range == "thisWeek"
    val count = when {
        thisWeek -> 7
        widget.count > 0 -> widget.count
        weekly -> 5
        else -> 7
    }
    val bucketDays = if (weekly) 7 else 1
    val starts = remember(today, widget.bucket, widget.range, count) {
        AdapterEngine.bucketStarts(today, widget.bucket, widget.range, count)
    }
    val dateField = widget.metaPath.ifBlank { "created_at" }
    val summed = widget.valuePath.isNotBlank()
    val values by produceState(List(starts.size) { 0.0 }, payload, dateField, widget.valuePath, starts) {
        value = withContext(Dispatchers.Default) {
            AdapterEngine.bucketTotals(AdapterEngine.extractList(payload, widget.itemsPath), dateField, widget.valuePath, starts, bucketDays)
        }
    }
    val unit = if (summed) widget.unit else ""
    val fmt = { d: Double -> fmtNum(d) + unit }
    // The bar that contains today is still filling up; future bars (later this week) are empty, not zero.
    val current = starts.indexOfLast { !it.isAfter(today) }
    val target = widget.target
    val maxVal = maxOf(values.maxOrNull() ?: 0.0, target ?: 0.0).takeIf { it > 0 } ?: 1.0
    var selected by remember { mutableStateOf<Int?>(null) }
    val barMax = if (weekly) 44.dp else 28.dp

    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(values) {
        if (!reduced && values.any { it > 0 }) {
            grow.snapTo(0f)
            grow.animateTo(1f, tween(650, easing = Motion.easeOut))
        }
    }

    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val weekFmt = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()) }
    fun bucketName(i: Int): String {
        val d = starts[i]
        return when {
            weekly && i == current -> "This week"
            weekly -> "Week of ${weekFmt.format(d)}"
            d == today -> "Today"
            else -> dayFmt.format(d)
        }
    }
    fun axisLabel(i: Int): String {
        val d = starts[i]
        return if (weekly) weekFmt.format(d) else d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)
    }

    // Headline: the total for days, the average of finished weeks for weeks.
    val done = if (weekly) values.take(current.coerceAtLeast(0)) else values
    val headline = if (weekly) done.takeIf { it.isNotEmpty() }?.average() ?: 0.0 else values.sum()
    val headlineCaption = when {
        weekly -> "weekly average"
        summed -> "total"
        else -> if (headline == 1.0) "event" else "events"
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        widget.title.ifBlank { if (weekly) "By week" else "By day" },
                        style = Theme.t.label,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sel = selected
                    Text(
                        if (sel != null) "${bucketName(sel)}: ${fmt(values[sel])}" + if (!summed) " events" else ""
                        else "Tap a bar for details",
                        style = Theme.t.small.copy(fontFeatureSettings = "tnum"),
                        color = if (sel != null) c.ink else c.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        fmt(headline),
                        style = Theme.t.title.copy(fontFeatureSettings = "tnum"),
                        color = c.ink,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(headlineCaption, style = Theme.t.small, color = c.ink3, maxLines = 1)
                }
            }
            Spacer(Modifier.height(18.dp))

            val plotH = 112.dp
            val valueH = 18.dp
            // Bars and their day labels share one tap target, so tapping "Thu" selects Thursday.
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    if (target != null) {
                        val y = valueH + plotH * (1f - (target / maxVal).toFloat())
                        val lineColor = c.ink3
                        Canvas(Modifier.fillMaxWidth().height(plotH + valueH)) {
                            val yPx = y.toPx()
                            drawLine(
                                lineColor,
                                Offset(0f, yPx),
                                Offset(size.width, yPx),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                            )
                        }
                    } else {
                        Spacer(Modifier.height(plotH + valueH))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.fill))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    values.forEachIndexed { i, v ->
                        val future = i > current
                        val inProgress = i == current && (weekly || thisWeek)
                        val isSel = selected == i
                        val dim = selected != null && !isSel
                        val frac = (v / maxVal).toFloat().coerceIn(0f, 1f)
                        val barColor = widget.tint.color().copy(
                            alpha = when {
                                isSel -> 1f
                                dim -> 0.3f
                                inProgress -> 0.55f
                                else -> 1f
                            },
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .pressable(
                                    enabled = !future,
                                    scale = 0.95f,
                                    label = "${bucketName(i)}: ${fmt(v)}" + if (!summed) " events" else "",
                                    onLongClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        selected = i
                                    },
                                    onClick = {
                                        view.tick()
                                        selected = if (isSel) null else i
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().height(plotH + valueH),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                if (v > 0) {
                                    Text(
                                        fmtNum(v),
                                        style = Theme.t.small.copy(fontSize = 12.sp, lineHeight = 14.sp, fontFeatureSettings = "tnum"),
                                        color = if (dim) c.ink3 else c.ink2,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth(0.62f)
                                            .requiredWidthIn(max = barMax)
                                            .height((plotH * frac * grow.value).coerceAtLeast(4.dp))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(barColor),
                                    )
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                axisLabel(i),
                                style = Theme.t.small.copy(fontSize = 12.sp),
                                color = if (isSel || i == current) c.ink else c.ink3,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                            )
                        }
                    }
                }
            }
            if (target != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.width(16.dp).height(2.dp)) {
                        drawLine(
                            c.ink3,
                            Offset(0f, size.height / 2),
                            Offset(size.width, size.height / 2),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Goal ${fmt(target)}" + if (weekly) " a week" else " a day",
                        style = Theme.t.small,
                        color = c.ink3,
                    )
                }
            }
        }
    }
}

/**
 * Where the total goes: one row per group (a project, a label), largest first, each with a bar
 * scaled to the largest group and its share of the whole.
 */
@Composable
fun BreakdownWidget(widget: DashboardWidget, payload: Any?) {
    val c = Theme.c
    val reduced = Theme.reduced
    val top = if (widget.count > 0) widget.count else 5
    val rows by produceState(emptyList<Pair<String, Double>>(), payload, widget.groupPath, widget.valuePath, top) {
        value = withContext(Dispatchers.Default) {
            AdapterEngine.groupTotals(AdapterEngine.extractList(payload, widget.itemsPath), widget.groupPath, widget.valuePath, top)
        }
    }
    val summed = widget.valuePath.isNotBlank()
    val unit = if (summed) widget.unit else ""
    val total = rows.sumOf { it.second }
    val max = rows.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    val grow = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(total) {
        if (!reduced && total > 0) {
            grow.snapTo(0f)
            grow.animateTo(1f, tween(650, easing = Motion.easeOut))
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    widget.title.ifBlank { "Breakdown" },
                    style = Theme.t.label,
                    color = c.ink,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtNum(total) + unit, style = Theme.t.title.copy(fontFeatureSettings = "tnum"), color = c.ink, maxLines = 1)
                    Text(if (summed) "total" else "items", style = Theme.t.small, color = c.ink3)
                }
            }
            if (rows.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Nothing logged yet.", style = Theme.t.small, color = c.ink3)
            }
            rows.forEach { (name, v) ->
                val other = name == "Other"
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "$name: ${fmtNum(v)}$unit, ${(v / total * 100).toInt()} percent"
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name,
                            style = Theme.t.small,
                            color = if (other) c.ink2 else c.ink,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${fmtNum(v)}$unit",
                            style = Theme.t.small.copy(fontFeatureSettings = "tnum"),
                            color = c.ink,
                            maxLines = 1,
                        )
                        Text(
                            "  ${(v / total * 100).toInt()}%",
                            style = Theme.t.small.copy(fontFeatureSettings = "tnum"),
                            color = c.ink3,
                            maxLines = 1,
                            modifier = Modifier.width(44.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp)) {
                        Box(
                            Modifier
                                .fillMaxWidth(((v / max).toFloat() * grow.value).coerceIn(0.01f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (other) c.ink3 else widget.tint.color()),
                        )
                    }
                }
            }
        }
    }
}
