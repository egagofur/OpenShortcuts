package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Library
import app.openshortcuts.model.Tint
import app.openshortcuts.model.TriggerKind
import app.openshortcuts.model.daysLabel
import app.openshortcuts.model.hhmm

sealed interface Seg {
    data class Word(val text: String) : Seg
    data class Pill(val text: String, val glyph: String? = null, val tint: Color? = null, val onClick: () -> Unit) : Seg
}

fun words(s: String): List<Seg> = s.split(' ').filter { it.isNotEmpty() }.map { Seg.Word(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceCard(glyph: String, tint: Color, segs: List<Seg>, trailing: (@Composable () -> Unit)? = null) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(glyph, tint)
            Spacer(Modifier.width(12.dp))
            FlowRow(
                Modifier.weight(1f).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                segs.forEach { s ->
                    when (s) {
                        is Seg.Word -> Text(s.text, style = Theme.t.action, color = c.ink)
                        is Seg.Pill -> TokenPill(s.text, glyph = s.glyph, glyphColor = s.tint, onClick = s.onClick)
                    }
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun AutomationScreen(
    a: Automation, lib: Library,
    onChange: (Automation) -> Unit, onBack: () -> Unit, onDelete: () -> Unit,
    onOpenShortcut: (String) -> Unit, onSetup: () -> Unit,
) {
    val c = Theme.c
    val ctx = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val target = lib.shortcuts.firstOrNull { it.id == a.shortcutId }

    var timePick by remember { mutableStateOf<Pair<Int, (Int) -> Unit>?>(null) }
    fun pickTime(current: Int, set: (Int) -> Unit) { timePick = current to set }
    timePick?.let { (current, set) ->
        TimeWheelDialog("Time", current, onDismiss = { timePick = null }) { set(it); timePick = null }
    }

    val needs = buildList {
        // Call and event triggers all run inside the notification-listener service.
        if (a.kind != TriggerKind.Time) add(Perm.CallAccess)
        add(Perm.Notifications); add(Perm.Alarms); add(Perm.Battery)
        target?.let { addAll(Perm.missing(ctx, it)) }
    }.distinct().filterNot { it.granted(ctx) }

    Row(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 4.dp, bottom = 32.dp),
        ) {
            Row(Modifier.padding(top = 22.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(a.kind.glyph(), a.kind.tint().color(), size = 32.dp)
                Spacer(Modifier.width(10.dp))
                Text("Automation", style = Theme.t.title, color = c.ink, modifier = Modifier.weight(1f))
                DuoSwitch(a.enabled) { onChange(a.copy(enabled = it)) }
            }

            val gap = Modifier.height(12.dp)
            when (a.kind) {
                TriggerKind.Time -> SentenceCard("clock", Tint.Blue.color(),
                    words("When it's") + Seg.Pill(a.timeOfDay.hhmm()) { pickTime(a.timeOfDay) { onChange(a.copy(timeOfDay = it)) } } +
                        Seg.Pill(a.days.daysLabel()) { dialog = "days" })
                TriggerKind.WhatsAppCallLong -> {
                    SentenceCard("phone", Tint.Green.color(),
                        words("When a WhatsApp call lasts") + Seg.Pill("${a.callMinutes} min") { dialog = "minutes" })
                    Spacer(gap)
                    SentenceCard("moon", Tint.Indigo.color(),
                        words("Only between") +
                            Seg.Pill(a.windowStart.hhmm()) { pickTime(a.windowStart) { onChange(a.copy(windowStart = it)) } } +
                            words("and") +
                            Seg.Pill(a.windowEnd.hhmm()) { pickTime(a.windowEnd) { onChange(a.copy(windowEnd = it)) } })
                }
                TriggerKind.Charger -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When charger is") + Seg.Pill(if (a.on) "connected" else "disconnected") { onChange(a.copy(on = !a.on)) })
                TriggerKind.BatteryLevel -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When battery") + Seg.Pill(if (a.on) "rises above" else "falls below") { onChange(a.copy(on = !a.on)) } +
                        Seg.Pill("${a.level}%") { dialog = "level" })
                TriggerKind.Dnd -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When Do Not Disturb turns") + Seg.Pill(if (a.on) "on" else "off") { onChange(a.copy(on = !a.on)) })
                TriggerKind.WiFi -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When Wi-Fi") + Seg.Pill(if (a.on) "connects" else "disconnects") { onChange(a.copy(on = !a.on)) })
                TriggerKind.WhatsAppMessage -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When a WhatsApp message arrives from") + Seg.Pill(a.text.ifBlank { "anyone" }) { dialog = "sender" })
            }
            Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(2.dp).height(14.dp).background(c.ink3.copy(alpha = 0.45f)))
            }
            SentenceCard(
                "layers", Tint.Purple.color(),
                words("Run") + Seg.Pill(target?.name ?: "Shortcut", target?.glyph, target?.tint?.color()) { dialog = "shortcut" },
                trailing = if (target != null) ({
                    Box(Modifier.size(44.dp).pressable(scale = 0.9f, label = "Open ${target.name}") { onOpenShortcut(target.id) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(c.accentWash), contentAlignment = Alignment.Center) {
                            Glyph("chevronRight", c.accent, size = 16.dp)
                        }
                    }
                }) else null,
            )
            if (a.kind == TriggerKind.WhatsAppMessage || a.kind == TriggerKind.BatteryLevel) {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (a.kind == TriggerKind.WhatsAppMessage) "The message text goes to the shortcut as its input, for Show Result or If Previous Result."
                    else "The battery percentage goes to the shortcut as its input.",
                    style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            Spacer(gap)
            SentenceCard("bell", Tint.Red.color(),
                words("Ask before running")) { DuoSwitch(a.warnFirst) { onChange(a.copy(warnFirst = it)) } }
            Spacer(Modifier.height(10.dp))
            Text(
                if (a.kind == TriggerKind.WhatsAppCallLong)
                    if (a.warnFirst) "A notification gives you a minute to snooze or skip. If you fell asleep, the call ends by itself." else "Runs by itself, with no notification to approve."
                else if (a.warnFirst) "A notification gives you a minute to skip this run or snooze it 30 minutes." else "Runs by itself, with no notification to approve.",
                style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp),
            )

            if (needs.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth().pressable(label = "Open setup", onClick = onSetup)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph("info", c.danger, size = 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Won't run reliably yet. Allow ${needs.joinToString { it.title.lowercase() }}.",
                            style = Theme.t.small.copy(fontSize = 15.sp), color = c.ink, modifier = Modifier.weight(1f))
                        Glyph("chevronRight", c.ink3, size = 18.dp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            if (!confirmDelete) QuietButton("Delete Automation", Modifier.fillMaxWidth(), color = c.danger) { confirmDelete = true }
            else PrimaryButton("Delete this automation", Modifier.fillMaxWidth(), color = c.danger, onClick = onDelete)
        }
        Rail({ GlassCircle("chevronLeft", "Back", onClick = onBack) })
    }

    when (dialog) {
        "level" -> DuoDialog({ dialog = null }) {
            DialogTitle("Battery level")
            var v by remember { mutableStateOf(a.level.toFloat()) }
            Text("${v.toInt()}%", style = Theme.t.large.copy(fontFeatureSettings = "tnum"), color = c.ink)
            androidx.compose.material3.Slider(
                value = v, onValueChange = { v = it }, valueRange = 5f..95f, steps = 17,
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.fill),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) { onChange(a.copy(level = v.toInt())); dialog = null }
        }
        "sender" -> DuoDialog({ dialog = null }) {
            DialogTitle("From", "Part of a contact or group name. Leave empty for anyone.")
            var v by remember { mutableStateOf(a.text) }
            Field(v, { v = it }, "Anyone")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) { onChange(a.copy(text = v.trim())); dialog = null }
        }
        "days" -> DaysDialog(a.days, onDismiss = { dialog = null }) { onChange(a.copy(days = it)) }
        "minutes" -> DuoDialog({ dialog = null }) {
            DialogTitle("Call length", "End the call once it has lasted this long.")
            var custom by remember { mutableStateOf(a.callMinutes.toString()) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 30, 45, 60, 90, 120).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { m ->
                            val on = m == a.callMinutes
                            Box(
                                Modifier.weight(1f).height(48.dp).pressable(scale = 0.95f, label = "$m minutes") { onChange(a.copy(callMinutes = m)); dialog = null }
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(if (on) c.accent else c.bg),
                                contentAlignment = Alignment.Center,
                            ) { Text("$m min", style = Theme.t.label, color = if (on) Color.White else c.ink) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Field(custom, { custom = it.filter(Char::isDigit).take(3) }, "Minutes", number = true, autofocus = false)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) {
                custom.toIntOrNull()?.takeIf { it in 1..600 }?.let { onChange(a.copy(callMinutes = it)) }
                dialog = null
            }
        }
        "shortcut" -> DuoDialog({ dialog = null }) {
            DialogTitle("Run Shortcut")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lib.shortcuts.forEach { s ->
                    val on = s.id == a.shortcutId
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).pressable(scale = 0.98f, label = s.name) { onChange(a.copy(shortcutId = s.id)); dialog = null }
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(if (on) c.accentWash else c.bg).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(s.glyph, s.tint.color())
                        Spacer(Modifier.width(12.dp))
                        Text(s.name, style = Theme.t.body.copy(fontSize = 17.sp), color = if (on) c.accent else c.ink, modifier = Modifier.weight(1f))
                        if (on) Glyph("check", c.accent, size = 20.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DaysDialog(days: Set<Int>, onDismiss: () -> Unit, onChange: (Set<Int>) -> Unit) {
    val c = Theme.c
    var sel by remember { mutableStateOf(if (days.isEmpty()) (1..7).toSet() else days) }
    DuoDialog(onDismiss) {
        DialogTitle("Repeat")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            // Monday first.
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
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Done", Modifier.fillMaxWidth()) { onChange(if (sel.size == 7) emptySet() else sel); onDismiss() }
    }
}
