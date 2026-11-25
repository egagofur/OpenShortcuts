package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.openshortcuts.engine.Scheduler
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Tint
import app.openshortcuts.model.daysLabel
import app.openshortcuts.model.hhmm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MODES = listOf("time" to "Time", "sunrise" to "Sunrise", "sunset" to "Sunset", "every" to "Interval", "once" to "Once")

fun ordinal(n: Int) = "$n" + if (n in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }

fun everyLabel(m: Int) = if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60} min"

fun offsetLabel(o: Int) = when {
    o == 0 -> "at"
    o < 0 -> "${everyLabel(-o)} before"
    else -> "${everyLabel(o)} after"
}

private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

fun dateLabel(iso: String): String = runCatching {
    val d = LocalDate.parse(iso)
    when (d) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> DAY.format(d)
    }
}.getOrDefault("Pick a date")

fun repeatLabel(a: Automation) = if (a.monthDay > 0) "monthly on the ${ordinal(a.monthDay)}" else a.days.daysLabel().lowercase()

/** One line for the automation list. */
fun timeHeadline(a: Automation): String = when (a.timeMode) {
    "sunrise", "sunset" -> {
        val o = offsetLabel(a.offset)
        (if (a.offset == 0) "At ${a.timeMode}" else "$o ${a.timeMode}").replaceFirstChar { it.uppercase() } + ", " + a.days.daysLabel().lowercase()
    }
    "every" -> "Every ${everyLabel(a.every)}, " + (if (a.windowStart == a.windowEnd) "all day" else "${a.windowStart.hhmm()}–${a.windowEnd.hhmm()}")
    "once" -> "Once, ${dateLabel(a.date)} at ${a.timeOfDay.hhmm()}"
    else -> "At ${a.timeOfDay.hhmm()}, ${repeatLabel(a)}"
}

/**
 * The Time trigger: a mode switch, then one sentence for that mode, then when it runs next.
 * [pickTime] opens the wheel picker owned by the automation screen.
 */
@Composable
fun TimeTriggerCard(a: Automation, onChange: (Automation) -> Unit, pickTime: (Int, (Int) -> Unit) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }

    Segmented(MODES.map { it.second }, MODES.indexOfFirst { it.first == a.timeMode }.coerceAtLeast(0)) { i ->
        val mode = MODES[i].first
        onChange(
            when (mode) {
                "every" -> a.copy(timeMode = mode, every = a.every.coerceAtLeast(15), windowStart = 8 * 60, windowEnd = 22 * 60)
                "once" -> a.copy(timeMode = mode, date = a.date.ifBlank { LocalDate.now().plusDays(1).toString() })
                else -> a.copy(timeMode = mode)
            },
        )
    }
    Spacer(Modifier.height(10.dp))

    val segs = when (a.timeMode) {
        "sunrise", "sunset" ->
            (if (a.offset == 0) words("At") else emptyList()) +
                Seg.Pill(if (a.offset == 0) a.timeMode else offsetLabel(a.offset)) { dialog = "offset" } +
                (if (a.offset == 0) emptyList() else words(a.timeMode)) +
                Seg.Pill(a.days.daysLabel()) { dialog = "days" }
        "every" -> words("Every") + Seg.Pill(everyLabel(a.every)) { dialog = "every" } +
            Seg.Pill(if (a.windowStart == a.windowEnd) "all day" else "${a.windowStart.hhmm()}–${a.windowEnd.hhmm()}") { dialog = "window" } +
            Seg.Pill(a.days.daysLabel()) { dialog = "days" }
        "once" -> words("Once on") + Seg.Pill(dateLabel(a.date)) { dialog = "date" } + words("at") +
            Seg.Pill(a.timeOfDay.hhmm()) { pickTime(a.timeOfDay) { onChange(a.copy(timeOfDay = it)) } }
        else -> words("At") + Seg.Pill(a.timeOfDay.hhmm()) { pickTime(a.timeOfDay) { onChange(a.copy(timeOfDay = it)) } } +
            Seg.Pill(repeatLabel(a)) { dialog = "repeat" }
    }
    SentenceCard(if (a.timeMode == "sunrise" || a.timeMode == "sunset") "sun" else "clock", Tint.Blue.color(), segs)

    // When it actually runs next: the quickest way to trust an interval or a sunset offset.
    val sun = a.timeMode == "sunrise" || a.timeMode == "sunset"
    var located by remember { mutableIntStateOf(0) }
    val next = remember(a, located) { Scheduler.nextTime(ctx, a) }
    val sunNoPlace = sun && remember(a, located) { Scheduler.place(ctx) } == null
    if (sunNoPlace) androidx.compose.runtime.LaunchedEffect(a.timeMode) {
        Scheduler.refreshPlace(ctx) { ok -> if (ok) { located++; Scheduler.rescheduleAll(ctx) } }
    }
    val locOn = remember(a, located) { ctx.getSystemService(android.location.LocationManager::class.java).isLocationEnabled }
    val allowed = remember(a, located) { app.openshortcuts.engine.Perm.Location.granted(ctx) }
    val fallback = if (a.timeMode == "sunrise") "06:00" else "18:00"
    Spacer(Modifier.height(8.dp))
    Text(
        when {
            next == null -> "This date has passed, so it won't run again. Pick a new one."
            !sunNoPlace -> "Next run: " + nextLabel(next)
            !allowed -> "Next run: ${nextLabel(next)}. Allow Location so this follows your real ${a.timeMode}; until then it uses $fallback."
            !locOn -> "Next run: ${nextLabel(next)}. Location is off: turn it on once so this follows your real ${a.timeMode}. Until then it uses $fallback."
            else -> "Next run: ${nextLabel(next)}. Finding where you are… until then it uses $fallback."
        },
        style = Theme.t.small, color = if (next == null) c.danger else c.ink2,
        modifier = Modifier.padding(horizontal = 6.dp).then(
            // Location off: the hint opens the switch, so it's one tap to fix.
            if (sunNoPlace && allowed && !locOn) Modifier.pressable(label = "Open Location settings") {
                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            } else Modifier,
        ),
    )
    // Back from Location settings: try again.
    if (sunNoPlace) androidx.lifecycle.compose.LifecycleResumeEffect(a.timeMode) {
        Scheduler.refreshPlace(ctx) { ok -> if (ok) { located++; Scheduler.rescheduleAll(ctx) } }
        onPauseOrDispose { }
    }

    when (dialog) {
        "repeat" -> RepeatDialog(a, onDismiss = { dialog = null }) { onChange(it); dialog = null }
        "days" -> RepeatDialog(a.copy(monthDay = 0), weeklyOnly = true, onDismiss = { dialog = null }) { onChange(it); dialog = null }
        "offset" -> OffsetDialog(a, onDismiss = { dialog = null }) { onChange(a.copy(offset = it)); dialog = null }
        "every" -> DuoDialog({ dialog = null }) {
            DialogTitle("Every")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 60, 120, 180, 240, 360, 720).chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            val on = m == a.every
                            Box(
                                Modifier.weight(1f).height(48.dp).pressable(scale = 0.95f, label = everyLabel(m)) { onChange(a.copy(every = m)); dialog = null }
                                    .clip(RoundedCornerShape(14.dp)).background(if (on) c.accent else c.bg),
                                contentAlignment = Alignment.Center,
                            ) { Text(everyLabel(m), style = Theme.t.label, color = if (on) Color.White else c.ink) }
                        }
                    }
                }
            }
        }
        "window" -> WindowDialog(a, pickTime, onDismiss = { dialog = null }, onChange = onChange)
        "date" -> DuoDialog({ dialog = null }) {
            DialogTitle("Date")
            // The picker speaks "today"/"tomorrow"; a one-off stores the real date.
            DayTimePicker("${a.date} ${a.timeOfDay.hhmm()}", daysAhead = 30) { v ->
                val (day, time) = v.split(" ")
                val d = when (day) { "today" -> LocalDate.now(); "tomorrow" -> LocalDate.now().plusDays(1); else -> LocalDate.parse(day) }
                val (h, m) = time.split(":").map { it.toInt() }
                onChange(a.copy(date = d.toString(), timeOfDay = h * 60 + m)); dialog = null
            }
        }
    }
}

private fun nextLabel(ms: Long): String {
    val t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
    val d = t.toLocalDate()
    val day = when (d) {
        LocalDate.now() -> "today"
        LocalDate.now().plusDays(1) -> "tomorrow"
        else -> DAY.format(d)
    }
    return "$day at ${"%02d:%02d".format(t.hour, t.minute)}"
}

/** Daily / Weekly / Monthly, like Apple's Repeat row. */
@Composable
private fun RepeatDialog(a: Automation, weeklyOnly: Boolean = false, onDismiss: () -> Unit, onDone: (Automation) -> Unit) {
    val c = Theme.c
    var mode by remember { mutableIntStateOf(if (a.monthDay > 0) 2 else if (a.days.isEmpty()) 0 else 1) }
    var sel by remember { mutableStateOf(if (a.days.isEmpty()) (1..7).toSet() else a.days) }
    var monthDay by remember { mutableIntStateOf(a.monthDay.takeIf { it > 0 } ?: LocalDate.now().dayOfMonth) }
    DuoDialog(onDismiss) {
        DialogTitle("Repeat")
        if (!weeklyOnly) {
            Segmented(listOf("Daily", "Weekly", "Monthly"), mode) { mode = it }
            Spacer(Modifier.height(16.dp))
        }
        if (weeklyOnly || mode == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(2, 3, 4, 5, 6, 7, 1).forEach { d ->
                    val on = d in sel
                    Box(
                        Modifier.weight(1f).height(44.dp)
                            .pressable(scale = 0.9f, label = listOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[d]) {
                                val next = if (on) sel - d else sel + d
                                if (next.isNotEmpty()) sel = next
                            }
                            .clip(CircleShape).background(if (on) c.accent else c.bg),
                        contentAlignment = Alignment.Center,
                    ) { Text(listOf("", "S", "M", "T", "W", "T", "F", "S")[d], style = Theme.t.label, color = if (on) Color.White else c.ink) }
                }
            }
        } else if (mode == 2) {
            Text("On the", style = Theme.t.small, color = c.ink2)
            NumberStepper(monthDay, 1, 31, 1, "") { monthDay = it }
            Text("Months without that day run on their last day.", style = Theme.t.small, color = c.ink2)
        } else {
            Text("Every day.", style = Theme.t.body, color = c.ink2)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth()) {
            onDone(
                when {
                    weeklyOnly || mode == 1 -> a.copy(monthDay = 0, days = if (sel.size == 7) emptySet() else sel)
                    mode == 2 -> a.copy(monthDay = monthDay, days = emptySet())
                    else -> a.copy(monthDay = 0, days = emptySet())
                },
            )
        }
    }
}

@Composable
private fun OffsetDialog(a: Automation, onDismiss: () -> Unit, onDone: (Int) -> Unit) {
    var side by remember { mutableIntStateOf(if (a.offset < 0) 0 else if (a.offset == 0) 1 else 2) }
    var amount by remember { mutableIntStateOf(kotlin.math.abs(a.offset).takeIf { it > 0 } ?: 15) }
    DuoDialog(onDismiss) {
        DialogTitle(a.timeMode.replaceFirstChar { it.uppercase() })
        Segmented(listOf("Before", "At ${a.timeMode}", "After"), side) { side = it }
        if (side != 1) {
            Spacer(Modifier.height(16.dp))
            NumberStepper(amount, 5, 240, 5, " min") { amount = it }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth()) { onDone(when (side) { 0 -> -amount; 1 -> 0; else -> amount }) }
    }
}

@Composable
private fun WindowDialog(a: Automation, pickTime: (Int, (Int) -> Unit) -> Unit, onDismiss: () -> Unit, onChange: (Automation) -> Unit) {
    val c = Theme.c
    val allDay = a.windowStart == a.windowEnd
    DuoDialog(onDismiss) {
        DialogTitle("When")
        Segmented(listOf("All Day", "Between"), if (allDay) 0 else 1) { i ->
            onChange(if (i == 0) a.copy(windowStart = 0, windowEnd = 0) else a.copy(windowStart = 8 * 60, windowEnd = 22 * 60))
        }
        if (!allDay) {
            Spacer(Modifier.height(14.dp))
            SettingsGroup(surface = c.bg) {
                SettingsRow("From", onClick = { pickTime(a.windowStart) { onChange(a.copy(windowStart = it)) } }) {
                    Text(a.windowStart.hhmm(), style = Theme.t.body, color = c.accent)
                }
                SettingsDivider(14.dp)
                SettingsRow("To", onClick = { pickTime(a.windowEnd) { onChange(a.copy(windowEnd = it)) } }) {
                    Text(a.windowEnd.hhmm(), style = Theme.t.body, color = c.accent)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}
