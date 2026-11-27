package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openshortcuts.model.RunLog
import app.openshortcuts.model.RunRecord
import app.openshortcuts.model.RunStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Opens the run log, for every run or one automation's. Provided by the app's navigation. */
val LocalOpenActivity = androidx.compose.runtime.staticCompositionLocalOf<(automationId: String?) -> Unit> { {} }

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private fun dayLabel(at: Long): String {
    val d = LocalDate.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault())
    val today = LocalDate.now()
    return when (d) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> dayFmt.format(d) }
}

private fun timeOf(at: Long) = timeFmt.format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

private fun duration(ms: Long) = when {
    ms < 1000 -> "${ms} ms"
    ms < 60_000 -> "%.1f s".format(Locale.ROOT, ms / 1000.0)
    else -> "${ms / 60_000} min ${(ms / 1000) % 60} s"
}

@Composable
private fun StatusDot(status: RunStatus, size: Int = 30) {
    val c = Theme.c
    val (glyph, tint) = when (status) {
        RunStatus.Success -> "check" to c.positive
        RunStatus.Error -> "x" to c.danger
        RunStatus.Stopped -> "stop" to c.ink3
    }
    Box(Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Glyph(glyph, tint, (size * 0.55f).dp)
    }
}

/** A run as a row: its status, the shortcut, what started it, when and how long. */
@Composable
fun RunRow(r: RunRecord, onClick: () -> Unit) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth().pressable(label = "Run of ${r.shortcut}", onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(r.status)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.shortcut, style = Theme.t.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${r.source} · ${timeOf(r.at)} · ${duration(r.ms)}", style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                r.error?.let { Text(it, style = Theme.t.small, color = c.danger, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Glyph("chevronRight", c.ink3, 16.dp)
        }
    }
}

/**
 * Every run, newest first, like executions in n8n: tap one to see each step, what it gave back, and
 * where it failed. [automationId] narrows it to one automation.
 */
@Composable
fun ActivityScreen(automationId: String?, onBack: () -> Unit, onRunAgain: (shortcutId: String) -> Unit) {
    val c = Theme.c
    val all by RunLog.records.collectAsStateWithLifecycle()
    var errorsOnly by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<RunRecord?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val runs = all.filter { (automationId == null || it.automationId == automationId) && (!errorsOnly || it.status == RunStatus.Error) }

    GlassStage(Modifier.fillMaxSize().background(c.bg), chrome = {
        BottomBar(listOf({ GlassCircle("chevronLeft", "Back", onClick = onBack) }), Modifier.align(Alignment.BottomCenter))
    }) {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
            contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 32.dp + bottomBarSpace()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                    Text("Activity", style = Theme.t.large, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (automationId != null) "Runs of this automation over the last week." else "Every run over the last week, newest first. Kept on this phone only.",
                        style = Theme.t.small, color = c.ink2,
                    )
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All" to false, "Errors only" to true).forEach { (l, v) ->
                        val on = errorsOnly == v
                        Box(
                            Modifier.heightIn(min = 40.dp).pressable(scale = 0.96f, label = l) { errorsOnly = v }
                                .clip(RoundedCornerShape(20.dp)).background(if (on) c.ink else c.fill).padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(l, style = Theme.t.label, color = if (on) c.bg else c.ink) }
                    }
                    if (all.isNotEmpty()) Box(
                        Modifier.heightIn(min = 40.dp).pressable(scale = 0.96f, label = "Clear activity") { confirmClear = true }
                            .clip(RoundedCornerShape(20.dp)).background(c.fill).padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Clear", style = Theme.t.label, color = c.danger) }
                }
            }
            if (runs.isEmpty()) item {
                Text(
                    if (errorsOnly) "No errors this week." else "Nothing has run yet. Runs show up here as they happen.",
                    style = Theme.t.body, color = c.ink2, modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            }
            runs.groupBy { dayLabel(it.at) }.forEach { (day, list) ->
                item(key = "d$day") { Text(day, style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 4.dp, top = 10.dp)) }
                items(list, key = { it.id }) { r -> RunRow(r) { open = r } }
            }
        }
    }

    open?.let { r -> RunDetailDialog(r, onDismiss = { open = null }, onRunAgain = { open = null; onRunAgain(r.shortcutId) }) }
    if (confirmClear) ConfirmActionDialog(
        title = "Clear activity?",
        body = "Removes the run history from this phone. Shortcuts and automations aren't touched.",
        confirmLabel = "Clear", confirmColor = c.danger,
        onDismiss = { confirmClear = false },
        onConfirm = { confirmClear = false; RunLog.clear() },
    )
}

/** One run, step by step: what each step did or returned, how long it took, and the error if it stopped there. */
@Composable
fun RunDetailDialog(r: RunRecord, onDismiss: () -> Unit, onRunAgain: () -> Unit) {
    val c = Theme.c
    DuoDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(r.status, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.shortcut, style = Theme.t.title, color = c.ink, maxLines = 2)
                Text(
                    "${when (r.status) { RunStatus.Success -> "Success"; RunStatus.Error -> "Error"; RunStatus.Stopped -> "Stopped" }} · ${r.source}",
                    style = Theme.t.small, color = c.ink2,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("${dayLabel(r.at)} at ${timeOf(r.at)} · took ${duration(r.ms)}", style = Theme.t.small, color = c.ink3)
        r.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = Theme.t.small, color = c.danger,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.danger.copy(alpha = 0.1f)).padding(12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Steps", style = Theme.t.label, color = c.ink)
        Spacer(Modifier.height(8.dp))
        if (r.steps.isEmpty()) Text("No steps ran.", style = Theme.t.small, color = c.ink3)
        r.steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Text("${i + 1}", style = Theme.t.small.copy(fontFeatureSettings = "tnum"), color = c.ink3, modifier = Modifier.width(22.dp))
                Box(Modifier.padding(top = 2.dp)) { Glyph(if (s.ok) "check" else "x", if (s.ok) c.positive else c.danger, 15.dp) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(s.title, style = Theme.t.small, color = c.ink, modifier = Modifier.weight(1f))
                        if (s.ms > 0) Text(duration(s.ms), style = Theme.t.small.copy(fontSize = 12.sp), color = c.ink3)
                    }
                    if (s.detail.isNotBlank()) Text(
                        s.detail, style = Theme.t.small.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        color = if (s.ok) c.ink2 else c.danger, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Run again", Modifier.fillMaxWidth(), onClick = onRunAgain)
        Spacer(Modifier.height(8.dp))
        QuietButton("Close", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}
