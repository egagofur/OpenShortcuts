package app.openshortcuts.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.ShortcutsApp
import app.openshortcuts.engine.Perm
import app.openshortcuts.engine.Runner
import app.openshortcuts.model.Action
import app.openshortcuts.model.ParamKind
import app.openshortcuts.model.ParamSpec
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.Store
import app.openshortcuts.model.Tint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs shortcuts from the UI and remembers what to show afterwards. */
@Stable
class RunController(private val ctx: Context) {
    var running by mutableStateOf<String?>(null); private set
    var done by mutableStateOf<String?>(null); private set
    /** Briefly set when a run ends in an error, so its tile can shake. */
    var failed by mutableStateOf<String?>(null); private set
    var result by mutableStateOf<Runner.Result?>(null)
    /** A question the running shortcut is waiting on (Choose from Menu, Ask for Input). */
    var prompt by mutableStateOf<Pair<Runner.Prompt, CompletableDeferred<String?>>?>(null); private set
    private var job: Job? = null

    private val ask: suspend (Runner.Prompt) -> String? = { p ->
        val answer = CompletableDeferred<String?>()
        prompt = p to answer
        try { answer.await() } finally { prompt = null }
    }

    fun answer(value: String?) { prompt?.second?.complete(value) }

    fun run(s: Shortcut, input: String = "", vars: Map<String, String> = emptyMap()) {
        if (running != null) return
        running = s.id
        job = (ctx.applicationContext as ShortcutsApp).scope.launch {
            val r = try { Runner.run(ctx, s, foreground = true, ask = ask, input = input, vars = vars) } finally { running = null }
            if (r.error != null) { failed = s.id; delay(400); if (failed == s.id) failed = null }
            if (r.error != null || r.shown.isNotEmpty()) result = r
            else {
                done = s.id
                delay(1400)
                if (done == s.id) done = null
            }
        }
    }

    fun stop() { job?.cancel(); answer(null); running = null }
}

@Composable
fun DialogTitle(text: String, sub: String? = null) {
    Text(text, style = Theme.t.title, color = Theme.c.ink)
    if (sub != null) {
        Spacer(Modifier.height(4.dp))
        Text(sub, style = Theme.t.small, color = Theme.c.ink2)
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, glyph: String? = null, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 50.dp)
            .pressable(scale = 0.98f, label = text, onClick = onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accentWash else c.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) { IconTile(glyph, tint ?: c.accent, size = 28.dp); Spacer(Modifier.width(12.dp)) }
        Text(text, style = Theme.t.body.copy(fontSize = 17.sp), color = if (selected) c.accent else c.ink,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Glyph("check", c.accent, size = 20.dp)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, number: Boolean = false, autofocus: Boolean = true, secret: Boolean = false) {
    val c = Theme.c
    val fr = remember { FocusRequester() }
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(c.bg).padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink3)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = Theme.t.body.copy(fontSize = 17.sp, color = c.ink),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = when {
                // Secrets: masked, and the keyboard neither suggests nor learns them.
                secret -> KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
                number -> KeyboardOptions(keyboardType = KeyboardType.Number)
                else -> KeyboardOptions.Default
            },
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().focusRequester(fr),
        )
    }
    if (autofocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}

private data class AppEntry(val pkg: String, val label: String)

private fun launchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
}

@Composable
fun ParamDialog(a: Action, spec: ParamSpec, owner: Shortcut, index: Int, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    DuoDialog(onDismiss) {
        DialogTitle(a.type.title)
        when (spec.kind) {
            ParamKind.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                spec.choices.forEach { ChoiceRow(it, it == a.p(spec.key)) { onDone(it) } }
            }
            ParamKind.NUMBER -> {
                var v by remember { mutableStateOf(a.p(spec.key)) }
                if (spec.max <= 100) {
                    val f = (v.toFloatOrNull() ?: spec.min.toFloat()).coerceIn(spec.min.toFloat(), spec.max.toFloat())
                    Text("${f.toInt()}${spec.suffix}", style = Theme.t.large.copy(fontFeatureSettings = "tnum"), color = c.ink)
                    Slider(
                        value = f, onValueChange = { v = it.toInt().toString() },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.fill),
                    )
                } else {
                    Field(v, { s -> v = s.filter(Char::isDigit).take(5) }, spec.suffix.trim().ifEmpty { "Number" }, number = true)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Done", Modifier.fillMaxWidth()) {
                    onDone(((v.toIntOrNull() ?: spec.min).coerceIn(spec.min, spec.max)).toString())
                }
            }
            ParamKind.TEXT, ParamKind.TIME -> {
                var v by remember { mutableStateOf(a.p(spec.key)) }
                Field(v, { v = it }, spec.placeholder)
                // Names aren't templates; everything else can mix in variables.
                if (spec.key != "name") {
                    Spacer(Modifier.height(10.dp))
                    VariableChips(app.openshortcuts.model.variableNames(owner.actions)) { v += it }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Done", Modifier.fillMaxWidth()) { onDone(v) }
            }
            ParamKind.SHORTCUT -> {
                val options = Store.library.value.shortcuts.filter { it.id != owner.id }
                if (options.isEmpty()) Text("Make another shortcut first.", style = Theme.t.body, color = c.ink2)
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options, key = { it.id }) { s ->
                        ChoiceRow(s.name, s.id == a.p(spec.key), s.glyph, s.tint.color()) { onDone(s.id) }
                    }
                }
            }
            ParamKind.APP -> {
                val apps = remember { launchableApps(ctx) }
                var q by remember { mutableStateOf("") }
                Field(q, { q = it }, "Search apps", autofocus = false)
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(apps.filter { q.isBlank() || it.label.contains(q, true) }, key = { it.pkg }) { app ->
                        ChoiceRow(app.label, app.pkg == a.p(spec.key)) { onDone(app.pkg) }
                    }
                }
            }
            ParamKind.INPUT -> {
                val tok = tokenFor(ctx, a.copy(params = a.params + (spec.key to "@prev")), spec, owner.actions, index)
                ChoiceRow("${tok.text} (from the action above)", a.p(spec.key) == "@prev", tok.glyph ?: "layers", tok.tint) { onDone("@prev") }
                Spacer(Modifier.height(14.dp))
                Text("Or type text", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(6.dp))
                var v by remember { mutableStateOf(a.p(spec.key).takeIf { it != "@prev" }.orEmpty()) }
                Field(v, { v = it }, "Text", autofocus = false)
                Spacer(Modifier.height(10.dp))
                VariableChips(app.openshortcuts.model.variableNames(owner.actions)) { v += it }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Use this text", Modifier.fillMaxWidth()) { onDone(v) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsDialog(
    s: Shortcut, missing: List<Perm>,
    onDismiss: () -> Unit, onChange: (Shortcut) -> Unit, onDelete: () -> Unit, onSetup: () -> Unit,
) {
    val c = Theme.c
    val ctx = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var confirmDelete by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(s.name) }
    fun edit(f: (Shortcut) -> Shortcut) = onChange(f(s.copy(name = name.trim().ifBlank { s.name })))
    val automations = remember(s.id) { app.openshortcuts.model.Store.library.value.automations.count { it.shortcutId == s.id } }
    var tileOn by remember { mutableStateOf(app.openshortcuts.engine.Launchers.tileShortcutId(ctx) == s.id) }

    DuoDialog(onDismiss) {
        // Identity: live preview of the icon beside the name it will carry.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val tint by androidx.compose.animation.animateColorAsState(s.tint.color(), tween(220, easing = Motion.easeOut), label = "tint")
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)).background(tint), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedContent(
                    s.glyph, label = "glyph",
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200, easing = Motion.easeOut), initialScale = 0.85f)) togetherWith
                            androidx.compose.animation.fadeOut(tween(100))
                    },
                ) { g -> Glyph(g, androidx.compose.ui.graphics.Color.White, size = 32.dp) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Field(name, { name = it; if (it.isNotBlank()) onChange(s.copy(name = it.trim())) }, "Name", autofocus = false)
            }
        }

        Spacer(Modifier.height(20.dp))
        SettingsGroup("Run from") {
            if (app.openshortcuts.engine.Launchers.canPin(ctx)) {
                SettingsRow("Home Screen", "home", app.openshortcuts.model.Tint.Blue.color(), "Add an icon that runs it in one tap",
                    onClick = { app.openshortcuts.engine.Launchers.pin(ctx, s) }) { Glyph("plus", c.accent, size = 18.dp) }
                SettingsDivider()
            }
            SettingsRow("Quick Settings", "grid", app.openshortcuts.model.Tint.Gray.color(),
                if (tileOn) "Swipe down twice, then tap its tile" else "Put it in the pull-down panel",
                onClick = { app.openshortcuts.engine.Launchers.offerTile(ctx, s); tileOn = true; view.confirm() }) {
                androidx.compose.animation.AnimatedContent(tileOn, label = "tile") { on ->
                    if (on) Glyph("check", c.positive, size = 18.dp) else Glyph("plus", c.accent, size = 18.dp)
                }
            }
            SettingsDivider()
            SettingsRow("Share Sheet", "share", app.openshortcuts.model.Tint.Teal.color(), "Run it on text you share or select",
                onClick = { edit { it.copy(inShareSheet = !it.inShareSheet) } }) {
                DuoSwitch(s.inShareSheet)
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsGroup("Color") {
            FlowRow(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tint.entries.forEach { t ->
                    val on = t == s.tint
                    val ring by androidx.compose.animation.core.animateDpAsState(if (on) 3.dp else 0.dp, Motion.settle(), label = "ring")
                    Box(
                        Modifier.size(36.dp).pressable(scale = 0.88f, label = t.name) { view.tick(); edit { it.copy(tint = t) } }
                            .clip(CircleShape).background(t.color())
                            .border(ring, c.bg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (on) Glyph("check", androidx.compose.ui.graphics.Color.White, size = 18.dp) }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        SettingsGroup("Icon") {
            FlowRow(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Glyphs.names.forEach { g ->
                    val on = g == s.glyph
                    val fill by androidx.compose.animation.animateColorAsState(if (on) s.tint.color() else c.card, tween(180, easing = Motion.easeOut), label = "iconFill")
                    Box(
                        Modifier.size(40.dp).pressable(scale = 0.88f, label = g) { view.tick(); edit { it.copy(glyph = g) } }
                            .clip(RoundedCornerShape(11.dp)).background(fill),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(g, if (on) androidx.compose.ui.graphics.Color.White else c.ink2, size = 22.dp) }
                }
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            SettingsGroup {
                SettingsRow(
                    if (missing.size == 1) "Allow 1 Permission" else "Allow ${missing.size} Permissions", "sliders", app.openshortcuts.model.Tint.Orange.color(),
                    missing.joinToString(", ") { it.title }, onClick = onSetup,
                ) { Glyph("chevronRight", c.ink3, size = 16.dp) }
            }
        }

        Spacer(Modifier.height(18.dp))
        // Delete asks once, in place. Short labels: the name is already at the top of this sheet.
        androidx.compose.animation.AnimatedContent(
            confirmDelete, label = "delete",
            transitionSpec = { androidx.compose.animation.fadeIn(tween(160)) togetherWith androidx.compose.animation.fadeOut(tween(100)) using androidx.compose.animation.SizeTransform(clip = false) },
        ) { asking ->
            if (!asking) SettingsGroup {
                SettingsRow("Delete Shortcut", "trash", c.danger, titleColor = c.danger, onClick = { confirmDelete = true; view.tick() })
            } else {
                val into = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(180); into.bringIntoView() }
                Column(Modifier.bringIntoViewRequester(into).padding(bottom = 16.dp)) {
                Text("Delete “${s.name}”?", style = Theme.t.label, color = c.ink, modifier = Modifier.padding(horizontal = 6.dp))
                Spacer(Modifier.height(2.dp))
                Text(
                    when (automations) {
                        0 -> "It can't be restored afterwards."
                        1 -> "Its automation is deleted too. Neither can be restored afterwards."
                        else -> "Its $automations automations are deleted too. None can be restored afterwards."
                    },
                    style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("Cancel", Modifier.weight(1f), color = c.ink) { confirmDelete = false }
                    PrimaryButton("Delete Shortcut", Modifier.weight(1f), color = c.danger) { view.confirm(); onDelete() }
                }
            }
            }
        }
    }
}

@Composable
fun ResultDialog(r: Runner.Result, onDismiss: () -> Unit, onSetup: () -> Unit) {
    val c = Theme.c
    DuoDialog(onDismiss) {
        DialogTitle(r.shortcut, if (r.error != null) "Couldn't finish" else null)
        if (r.error != null) {
            Row(verticalAlignment = Alignment.Top) {
                Glyph("info", c.danger, size = 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(r.error, style = Theme.t.body, color = c.ink)
            }
        }
        r.shown.forEach {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.bg).padding(16.dp)) {
                Text(it, style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (r.error?.startsWith("Allow") == true) {
            PrimaryButton("Open Setup", Modifier.fillMaxWidth()) { onDismiss(); onSetup() }
            Spacer(Modifier.height(8.dp))
            QuietButton("Close", Modifier.fillMaxWidth(), onClick = onDismiss)
        } else PrimaryButton("Done", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
fun NewAutomationDialog(onDismiss: () -> Unit, onPick: (app.openshortcuts.model.TriggerKind) -> Unit) {
    DuoDialog(onDismiss) {
        DialogTitle("New Automation", "Choose what starts it.")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                app.openshortcuts.model.TriggerKind.Time to "Time of day",
                app.openshortcuts.model.TriggerKind.WhatsAppCallLong to "WhatsApp call runs long",
                app.openshortcuts.model.TriggerKind.WhatsAppMessage to "WhatsApp message arrives",
                app.openshortcuts.model.TriggerKind.Charger to "Charger",
                app.openshortcuts.model.TriggerKind.BatteryLevel to "Battery level",
                app.openshortcuts.model.TriggerKind.Dnd to "Do Not Disturb",
                app.openshortcuts.model.TriggerKind.WiFi to "Wi-Fi",
            ).forEach { (k, label) -> ChoiceRow(label, false, k.glyph(), k.tint().color()) { onPick(k) } }
        }
    }
}

/** Shown while a shortcut waits for an answer. Dismissing stops the shortcut. */
@Composable
fun PromptDialog(p: Runner.Prompt, onAnswer: (String?) -> Unit) {
    DuoDialog(onDismiss = { onAnswer(null) }) {
        when (p) {
            is Runner.Prompt.Menu -> {
                DialogTitle(p.title)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.options.forEach { o -> ChoiceRow(o, false) { onAnswer(o) } }
                }
                Spacer(Modifier.height(12.dp))
                QuietButton("Cancel", Modifier.fillMaxWidth()) { onAnswer(null) }
            }
            is Runner.Prompt.Input -> {
                DialogTitle(p.title)
                var v by remember { mutableStateOf("") }
                Field(v, { v = if (p.number) it.filter { c -> c.isDigit() || c == '.' || c == '-' } else it }, if (p.number) "Number" else "Text", number = p.number)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("Cancel", Modifier.weight(1f)) { onAnswer(null) }
                    PrimaryButton("Done", Modifier.weight(1f)) { onAnswer(v) }
                }
            }
        }
    }
}

/** Tap to insert {Name} into the text being edited. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VariableChips(names: List<String>, onInsert: (String) -> Unit) {
    val c = Theme.c
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { n ->
            Row(
                Modifier.pressable(scale = 0.95f, label = "Insert $n") { onInsert("{$n}") }
                    .clip(RoundedCornerShape(10.dp)).background(c.accentWash).padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("braces", c.accent, size = 14.dp)
                Spacer(Modifier.width(4.dp))
                Text(n, style = Theme.t.small, color = c.accent)
            }
        }
    }
}

@Composable
fun NewShortcutDialog(onDismiss: () -> Unit, onNew: () -> Unit, onImport: () -> Unit, onDescribe: () -> Unit, onGallery: () -> Unit) {
    DuoDialog(onDismiss) {
        DialogTitle("New Shortcut")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceRow("Browse the Gallery", false, "grid", Tint.Orange.color(), onGallery)
            ChoiceRow("Describe what it should do", false, "quote", Tint.Purple.color(), onDescribe)
            ChoiceRow("Start from scratch", false, "plus", Tint.Blue.color(), onNew)
            ChoiceRow("Import a shortcut file", false, "down", Tint.Gray.color(), onImport)
        }
    }
}

/** Actions worth naming before someone adds a shortcut they were sent. */
private val SENSITIVE = mapOf(
    app.openshortcuts.model.ActionType.END_WHATSAPP_CALL to "end WhatsApp calls",
    app.openshortcuts.model.ActionType.END_PHONE_CALL to "end phone calls",
    app.openshortcuts.model.ActionType.DND to "change Do Not Disturb",
    app.openshortcuts.model.ActionType.RINGER to "change the ringer",
    app.openshortcuts.model.ActionType.BRIGHTNESS to "change brightness",
    app.openshortcuts.model.ActionType.OPEN_URL to "open web links",
    app.openshortcuts.model.ActionType.OPEN_APP to "open apps",
    app.openshortcuts.model.ActionType.COPY to "change the clipboard",
    app.openshortcuts.model.ActionType.RUN_SHORTCUT to "run your other shortcuts",
    app.openshortcuts.model.ActionType.REPLY_WHATSAPP to "reply to WhatsApp messages for you",
    app.openshortcuts.model.ActionType.SEND_WHATSAPP to "send WhatsApp messages",
    app.openshortcuts.model.ActionType.SEND_SMS to "send SMS",
    app.openshortcuts.model.ActionType.CALL to "make phone calls",
    app.openshortcuts.model.ActionType.EMAIL to "write emails",
    app.openshortcuts.model.ActionType.LOCK to "lock your screen",
    app.openshortcuts.model.ActionType.ADD_EVENT to "add calendar events",
    app.openshortcuts.model.ActionType.UPCOMING_EVENTS to "read your calendar",
    app.openshortcuts.model.ActionType.LOCATION to "read your location",
    app.openshortcuts.model.ActionType.SHARE to "share text",
)

/** Like Apple's "Add Shortcut" sheet: show what it does before it's added. */
@Composable
fun ImportDialog(
    imp: app.openshortcuts.model.Portable.Imported, onCancel: () -> Unit, onAdd: () -> Unit,
    note: String? = null, automation: app.openshortcuts.model.Automation? = null,
) {
    val c = Theme.c
    val s = imp.shortcut
    DuoDialog(onCancel) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(s.glyph, s.tint.color(), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = Theme.t.title, color = c.ink)
                val n = s.actions.count { !it.type.isMarker }
                Text("$n action${if (n == 1) "" else "s"}", style = Theme.t.small, color = c.ink2)
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            s.actions.filterNot { it.type.isMarker }.take(10).forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(a.type.glyph, a.type.tint.color(), size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(a.type.title, style = Theme.t.body, color = c.ink)
                }
            }
            val more = s.actions.count { !it.type.isMarker } - 10
            if (more > 0) Text("and $more more", style = Theme.t.small, color = c.ink2)
        }
        automation?.let { a ->
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(a.kind.glyph(), a.kind.tint().color(), size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.headline(), style = Theme.t.body, color = c.ink)
                    Text("Added switched off. Turn it on in Automation once it looks right.", style = Theme.t.small, color = c.ink2)
                }
            }
        }
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(note, style = Theme.t.small, color = c.ink2)
        }
        val can = s.actions.mapNotNull { SENSITIVE[it.type] }.distinct()
        if (can.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Glyph("info", c.danger, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("This shortcut can ${can.joinToString()}. Only add shortcuts from people you trust.",
                    style = Theme.t.small, color = c.ink)
            }
        }
        if (imp.unknownActions > 0) {
            Spacer(Modifier.height(8.dp))
            Text("${imp.unknownActions} action${if (imp.unknownActions == 1) " isn't" else "s aren't"} supported by this version and will be left out.",
                style = Theme.t.small, color = c.ink2)
        }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Add Shortcut", Modifier.fillMaxWidth(), onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        QuietButton("Don't Add", Modifier.fillMaxWidth(), onClick = onCancel)
    }
}
