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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
        if (a.kind == TriggerKind.App) add(Perm.UsageAccess)
        if (a.kind == TriggerKind.Bluetooth) add(Perm.Bluetooth)
        if (a.kind == TriggerKind.Location) { add(Perm.Location); add(Perm.BackgroundLocation) }
        if (a.kind == TriggerKind.Time && (a.timeMode == "sunrise" || a.timeMode == "sunset")) add(Perm.Location)
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
                // The trigger itself is a button: change what starts this automation without starting over.
                Row(
                    Modifier.weight(1f).pressable(scale = 0.97f, label = "Change trigger") { dialog = "kind" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.animation.AnimatedContent(a.kind, label = "kindIcon",
                        transitionSpec = { (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(220, easing = Motion.easeOut), initialScale = 0.85f)) togetherWith androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(100)) },
                    ) { k -> IconTile(k.glyph(), k.tint().color(), size = 32.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(a.kind.title, style = Theme.t.title, color = c.ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(24.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
                        Glyph("chevronDown", c.ink2, size = 16.dp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                DuoSwitch(a.enabled) { onChange(a.copy(enabled = it)) }
            }

            val gap = Modifier.height(12.dp)
            when (a.kind) {
                TriggerKind.Time -> TimeTriggerCard(a, onChange) { cur, set -> pickTime(cur, set) }
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
                TriggerKind.App -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When") + Seg.Pill(if (a.app.isBlank()) "App" else appLabel(ctx, a.app)) { dialog = "app" } +
                        words("is") + Seg.Pill(if (a.on) "opened" else "closed") { onChange(a.copy(on = !a.on)) })
                TriggerKind.AppNotification -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When a notification arrives from") + Seg.Pill(appLabel(ctx, a.app)) { dialog = "app" } +
                        words("containing") + Seg.Pill(a.text.ifBlank { "anything" }) { dialog = "text" })
                TriggerKind.Alarm -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When an alarm") + Seg.Pill(if (a.on) "goes off" else "is stopped") { onChange(a.copy(on = !a.on)) })
                TriggerKind.Bluetooth -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When") + Seg.Pill(a.text.ifBlank { "any device" }) { dialog = "text" } + Seg.Pill(if (a.on) "connects" else "disconnects") { onChange(a.copy(on = !a.on)) })
                TriggerKind.Headphones -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When headphones are") + Seg.Pill(if (a.on) "connected" else "disconnected") { onChange(a.copy(on = !a.on)) })
                TriggerKind.AirplaneMode -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When Airplane Mode turns") + Seg.Pill(if (a.on) "on" else "off") { onChange(a.copy(on = !a.on)) })
                TriggerKind.Location -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When I") + Seg.Pill(if (a.on) "arrive at" else "leave") { onChange(a.copy(on = !a.on)) } +
                        Seg.Pill(if (a.lat == 0.0 && a.lon == 0.0) "Choose place" else a.place.ifBlank { "Pinned place" }) { dialog = "place" })
                TriggerKind.BatterySaver -> SentenceCard(a.kind.glyph(), a.kind.tint().color(),
                    words("When Battery Saver turns") + Seg.Pill(if (a.on) "on" else "off") { onChange(a.copy(on = !a.on)) })
            }
            Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(2.dp).height(14.dp).background(c.ink3.copy(alpha = 0.45f)))
            }
            LaunchedEffect(a.shortcutId) { if (a.shortcutId.isNotBlank()) Tour.done(Tour.Step.PickShortcut) }
            Box(Modifier.tipAnchor("runCard")) {
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
            }
            val inputNote = when (a.kind) {
                TriggerKind.WhatsAppMessage -> "The message text goes to the shortcut as its input, for Show Result or If Previous Result."
                TriggerKind.BatteryLevel -> "The battery percentage goes to the shortcut as its input."
                TriggerKind.AppNotification -> "The notification's text is the shortcut's input; {Notification Title} and {Notification App} are set too."
                TriggerKind.App -> "The app's name goes to the shortcut as its input."
                TriggerKind.Bluetooth -> "The device's name goes to the shortcut as its input."
                TriggerKind.Location -> "Android watches the place for you, even when the app is closed. Location must be on."
                TriggerKind.Alarm -> "Works with your phone's Clock app while its alarm notification is showing."
                else -> null
            }
            if (inputNote != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    inputNote,
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
            DialogTitle("From", "A chat that messaged you, or a WhatsApp contact.")
            HistoryPicker(
                current = a.text, anyLabel = "Anyone", anyGlyph = "quote",
                load = {
                    val recent = app.openshortcuts.engine.Events.recentChats(ctx).map { HistoryItem(it, "Recent chat", "quote") }
                    val contacts = app.openshortcuts.engine.Contacts.load(ctx).filter { it.whatsapp.isNotEmpty() }
                        .filter { p -> recent.none { it.name.equals(p.name, true) } }.map { HistoryItem(it.name, "WhatsApp contact", "person") }
                    recent + contacts
                },
                needs = if (app.openshortcuts.engine.Contacts.allowed(ctx)) null else android.Manifest.permission.READ_CONTACTS,
                needsText = "Allow Contacts to also list your WhatsApp contacts. Chats that messaged you show up either way.",
            ) { onChange(a.copy(text = it)); dialog = null }
        }
        "text" -> if (a.kind == TriggerKind.Bluetooth) DuoDialog({ dialog = null }) {
            DialogTitle("Device", "Devices that have paired with this phone.")
            HistoryPicker(
                current = a.text, anyLabel = "Any device", anyGlyph = "bluetooth",
                load = {
                    runCatching {
                        ctx.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter?.bondedDevices.orEmpty()
                            .mapNotNull { it.name }.distinct().sorted().map { HistoryItem(it, "Paired", "bluetooth") }
                    }.getOrDefault(emptyList())
                },
                needs = if (app.openshortcuts.engine.Perm.Bluetooth.granted(ctx)) null else "android.permission.BLUETOOTH_CONNECT",
                needsText = "Allow Nearby devices to list the Bluetooth devices you've paired.",
            ) { onChange(a.copy(text = it)); dialog = null }
        } else DuoDialog({ dialog = null }) {
            DialogTitle("Containing", "Words the notification must contain. Leave empty for any notification.")
            var v by remember { mutableStateOf(a.text) }
            Field(v, { v = it }, "Anything")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Done", Modifier.fillMaxWidth()) { onChange(a.copy(text = v.trim())); dialog = null }
        }
        "app" -> DuoDialog({ dialog = null }) {
            DialogTitle("App")
            val apps = remember { launchableApps(ctx) }
            var q by remember { mutableStateOf("") }
            Field(q, { q = it }, "Search apps", autofocus = false)
            Spacer(Modifier.height(10.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = if (isShort) 200.dp else 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Notifications can come from any app; the App trigger needs one.
                if (a.kind == TriggerKind.AppNotification && q.isBlank()) item(key = "any") {
                    ChoiceRow("Any app", a.app.isBlank(), "grid", Tint.Gray.color()) { onChange(a.copy(app = "")); dialog = null }
                }
                items(apps.filter { q.isBlank() || it.label.contains(q, true) }.size) { i ->
                    val app = apps.filter { q.isBlank() || it.label.contains(q, true) }[i]
                    ChoiceRow(app.label, app.pkg == a.app) { onChange(a.copy(app = app.pkg)); dialog = null }
                }
            }
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
        "place" -> MapPicker(
            start = Picked(a.lat, a.lon, a.radius, a.place),
            onDismiss = { dialog = null },
        ) { p -> onChange(a.copy(lat = p.lat, lon = p.lon, radius = p.radius, place = p.name)); dialog = null }
        "kind" -> NewAutomationDialog(onDismiss = { dialog = null }, title = "Change Trigger", current = a.kind) { k ->
            dialog = null
            if (k != a.kind) onChange(a.switchTo(k))
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

/** Same automation, new trigger: keeps its shortcut and run options, resets what belonged to the old trigger. */
fun Automation.switchTo(k: TriggerKind): Automation {
    val base = copy(kind = k, on = true, text = "", app = "", level = 20, offset = 0, monthDay = 0, date = "", timeMode = "time")
    return when (k) {
        TriggerKind.WhatsAppCallLong -> base.copy(callMinutes = 45, windowStart = 23 * 60, windowEnd = 5 * 60)
        TriggerKind.BatteryLevel -> base.copy(on = false)
        else -> base
    }
}

data class HistoryItem(val name: String, val sub: String, val glyph: String)

/**
 * Choose from what the phone has seen (paired devices, chats, contacts) instead of typing a name.
 * The search box only filters the list; "Any …" is always the first row.
 */
@Composable
fun HistoryPicker(
    current: String,
    anyLabel: String,
    anyGlyph: String,
    load: suspend () -> List<HistoryItem>,
    needs: String?,
    needsText: String,
    onPick: (String) -> Unit,
) {
    val c = Theme.c
    var tick by remember { mutableStateOf(0) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { tick++ }
    val items by androidx.compose.runtime.produceState<List<HistoryItem>?>(null, tick) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { load() }
    }
    var q by remember { mutableStateOf("") }
    if ((items?.size ?: 0) > 6) {
        Field(q, { q = it }, "Search", autofocus = false)
        Spacer(Modifier.height(10.dp))
    }
    if (needs != null) {
        Row(
            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(c.bg).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(needsText, style = Theme.t.small, color = c.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.heightIn(min = 34.dp).pressable(label = "Allow") { ask.launch(needs) }
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(17.dp)).background(c.accentWash).padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Allow", style = Theme.t.label, color = c.accent) }
        }
        Spacer(Modifier.height(10.dp))
    }
    val shown = items.orEmpty().filter { q.isBlank() || it.name.contains(q.trim(), true) }
    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = if (isShort) 200.dp else 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (q.isBlank()) item(key = "any") { ChoiceRow(anyLabel, current.isBlank(), anyGlyph, Tint.Gray.color()) { onPick("") } }
        items(shown.size, key = { "h" + shown[it].sub + shown[it].name }) { i ->
            val it = shown[i]
            ChoiceRow(it.name, it.name.equals(current, true), it.glyph, if (it.glyph == "bluetooth") Tint.Blue.color() else Tint.Green.color()) { onPick(it.name) }
        }
        if (items != null && shown.isEmpty() && needs == null) item(key = "none") {
            Text(if (q.isBlank()) "Nothing here yet." else "No matches for “$q”.", style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(8.dp))
        }
    }
}
