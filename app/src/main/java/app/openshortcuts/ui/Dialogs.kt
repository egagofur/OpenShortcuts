package app.openshortcuts.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runs shortcuts from the UI and remembers what to show afterwards. */
@Stable
class RunController(private val ctx: Context) {
    var running by mutableStateOf<String?>(null); private set
    var done by mutableStateOf<String?>(null); private set
    var result by mutableStateOf<Runner.Result?>(null)
    private var job: Job? = null

    fun run(s: Shortcut) {
        if (running != null) return
        running = s.id
        job = (ctx.applicationContext as ShortcutsApp).scope.launch {
            val r = try { Runner.run(ctx, s, foreground = true) } finally { running = null }
            if (r.error != null || r.shown.isNotEmpty()) result = r
            else {
                done = s.id
                delay(1400)
                if (done == s.id) done = null
            }
        }
    }

    fun stop() { job?.cancel(); running = null }
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
fun Field(value: String, onChange: (String) -> Unit, placeholder: String, number: Boolean = false, autofocus: Boolean = true) {
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
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
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
    var confirmDelete by remember { mutableStateOf(false) }
    DuoDialog(onDismiss) {
        DialogTitle("Details")
        var name by remember { mutableStateOf(s.name) }
        Field(name, { name = it; if (it.isNotBlank()) onChange(s.copy(name = it.trim())) }, "Name", autofocus = false)
        Spacer(Modifier.height(10.dp))
        val ctx = LocalContext.current
        var tileOn by remember { mutableStateOf(app.openshortcuts.engine.Launchers.tileShortcutId(ctx) == s.id) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (app.openshortcuts.engine.Launchers.canPin(ctx)) {
                QuietButton("Home Screen", Modifier.weight(1f)) { app.openshortcuts.engine.Launchers.pin(ctx, s) }
            }
            QuietButton(if (tileOn) "In Quick Settings" else "Quick Settings", Modifier.weight(1f), color = if (tileOn) c.positive else c.accent) {
                app.openshortcuts.engine.Launchers.offerTile(ctx, s); tileOn = true
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Colour", style = Theme.t.small, color = c.ink2)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Tint.entries.forEach { t ->
                Box(
                    Modifier.size(36.dp).pressable(scale = 0.9f, label = t.name) { onChange(s.copy(tint = t, name = name.ifBlank { s.name })) }
                        .clip(CircleShape).background(t.color())
                        .then(if (t == s.tint) Modifier.border(3.dp, c.card, CircleShape) else Modifier),
                    contentAlignment = Alignment.Center,
                ) { if (t == s.tint) Glyph("check", androidx.compose.ui.graphics.Color.White, size = 18.dp) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Icon", style = Theme.t.small, color = c.ink2)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Glyphs.names.forEach { g ->
                Box(
                    Modifier.size(40.dp).pressable(scale = 0.9f, label = g) { onChange(s.copy(glyph = g, name = name.ifBlank { s.name })) }
                        .clip(RoundedCornerShape(11.dp)).background(if (g == s.glyph) s.tint.color() else c.bg),
                    contentAlignment = Alignment.Center,
                ) { Glyph(g, if (g == s.glyph) androidx.compose.ui.graphics.Color.White else c.ink2, size = 22.dp) }
            }
        }
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            QuietButton("Allow ${missing.size} permission${if (missing.size > 1) "s" else ""}", Modifier.fillMaxWidth(), onClick = onSetup)
        }
        Spacer(Modifier.height(10.dp))
        if (!confirmDelete) QuietButton("Delete Shortcut", Modifier.fillMaxWidth(), color = c.danger) { confirmDelete = true }
        else PrimaryButton("Delete “${s.name}” and its automations", Modifier.fillMaxWidth(), color = c.danger, onClick = onDelete)
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
            ChoiceRow("Time of day", false, "clock", Tint.Blue.color()) { onPick(app.openshortcuts.model.TriggerKind.Time) }
            ChoiceRow("WhatsApp call runs long", false, "phone", Tint.Green.color()) { onPick(app.openshortcuts.model.TriggerKind.WhatsAppCallLong) }
        }
    }
}
