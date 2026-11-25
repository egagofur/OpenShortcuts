package app.openshortcuts.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Action
import app.openshortcuts.model.ActionType
import app.openshortcuts.model.Category
import app.openshortcuts.model.ParamKind
import app.openshortcuts.model.ParamSpec
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.Store
import kotlinx.coroutines.launch
import kotlin.math.abs

private val SHEET_COLLAPSED = 128.dp

/** Editing state with undo/redo. Every change is saved straight away, like Shortcuts. */
private class Draft(initial: Shortcut) {
    var value by mutableStateOf(initial)
    val undo = mutableStateListOf<Shortcut>()
    val redo = mutableStateListOf<Shortcut>()

    fun set(next: Shortcut) {
        if (next == value) return
        undo += value; redo.clear(); value = next; Store.upsert(next)
    }
    fun undo() { undo.removeLastOrNull()?.let { redo += value; value = it; Store.upsert(it) } }
    fun redo() { redo.removeLastOrNull()?.let { undo += value; value = it; Store.upsert(it) } }
    fun actions(f: (MutableList<Action>) -> Unit) = set(value.copy(actions = value.actions.toMutableList().also(f)))
}

/** Indices of an opener and its markers (If/Otherwise/End If, Repeat/End Repeat, Menu/Options/End Menu). */
private fun blockOf(actions: List<Action>, index: Int): List<Int> {
    val t = actions[index].type
    val openerType = if (t.opensBlock) t else t.opener ?: return listOf(index)
    var start = index
    if (!t.opensBlock) {
        var depth = 0
        start = -1
        for (i in index - 1 downTo 0) {
            val u = actions[i].type
            if (u == openerType.closer) depth++
            else if (u == openerType) { if (depth == 0) { start = i; break } else depth-- }
        }
        if (start < 0) return listOf(index)
    }
    val out = mutableListOf(start)
    var depth = 0
    for (i in start + 1 until actions.size) {
        val u = actions[i].type
        when {
            u == openerType -> depth++
            u.opener == openerType && u.isMidBlock -> if (depth == 0) out += i
            u == openerType.closer -> if (depth == 0) { out += i; break } else depth--
        }
    }
    return out
}

private fun levels(actions: List<Action>): List<Int> {
    var level = 0
    return actions.map { a ->
        when {
            a.type.closesBlock -> { level = (level - 1).coerceAtLeast(0); level }
            a.type.isMidBlock -> (level - 1).coerceAtLeast(0)
            a.type.opensBlock -> level.also { level++ }
            else -> level
        }
    }
}

@Composable
fun EditorScreen(shortcut: Shortcut, runs: RunController, onBack: () -> Unit, onDelete: () -> Unit, onSetup: () -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val view = rememberHaptics()
    val draft = remember(shortcut.id) { Draft(shortcut) }
    val s = draft.value
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Pair<Int, ParamSpec>?>(null) }
    var details by remember { mutableStateOf(false) }
    val missing = remember(s) { Perm.missing(ctx, s) }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        // With the keyboard up, the sheet fills what's left above it.
        val imeH = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val expandedH = if (imeH > 0.dp) (maxHeight - imeH) * 0.86f else maxHeight * 0.74f
        // Sideways (short and wide): actions on the left, the catalogue docked beside them, like a
        // tablet. No sheet covering half of a 360dp-tall screen.
        val docked = maxHeight < 480.dp && maxWidth > 600.dp
        val sheet: @Composable (Modifier, Boolean) -> Unit = { m, dock ->
            ActionSheet(
                docked = dock,
                expandedH = expandedH,
                canUndo = draft.undo.isNotEmpty(), canRedo = draft.redo.isNotEmpty(),
                running = runs.running == s.id,
                onUndo = draft::undo, onRedo = draft::redo,
                onInfo = { details = true },
                // A .openshortcut file anyone with the app can add, plus a readable recipe.
                onShare = { ctx.startActivity(app.openshortcuts.model.Portable.shareIntent(ctx, s, s.describe(ctx))) },
                onRun = { if (runs.running == s.id) runs.stop() else runs.run(s) },
                onAdd = { type ->
                    view.confirm()
                    draft.actions { l ->
                        l += Action(type = type)
                        when (type) {
                            ActionType.IF -> { l += Action(type = ActionType.OTHERWISE); l += Action(type = ActionType.END_IF) }
                            ActionType.REPEAT -> l += Action(type = ActionType.END_REPEAT)
                            ActionType.REPEAT_EACH -> l += Action(type = ActionType.END_EACH)
                            ActionType.MENU -> {
                                l += Action(type = ActionType.CASE, params = mapOf("label" to "Option 1"))
                                l += Action(type = ActionType.CASE, params = mapOf("label" to "Option 2"))
                                l += Action(type = ActionType.END_MENU)
                            }
                            else -> Unit
                        }
                    }
                    scope.launch { list.animateScrollToItem(list.layoutInfo.totalItemsCount) }
                },
            
                modifier = m,
            )
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                        .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom)),
                    contentPadding = PaddingValues(start = 18.dp, end = 4.dp, bottom = if (docked) 24.dp else SHEET_COLLAPSED + 40.dp),
                ) {
                    item(key = "header") {
                        EditorHeader(s, onDetails = { details = true })
                    }
                    if (missing.isNotEmpty()) item(key = "perm") {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .pressable(label = "Open setup", onClick = onSetup)
                                .clip(RoundedCornerShape(18.dp)).background(c.card).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph("info", c.danger, size = 20.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Needs ${missing.joinToString { it.title.lowercase() }}. Tap to allow.",
                                style = Theme.t.small, color = c.ink, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    val lv = levels(s.actions)
                    itemsIndexed(s.actions, key = { _, a -> a.id }) { i, a ->
                        Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                            if (i > 0) Connector(lv[i]) else LaunchedEffect(Unit) { Tour.done(Tour.Step.AddAction) }
                            ActionCard(
                                anchor = i == 0,
                                action = a, index = i, all = s.actions, level = lv[i],
                                onParam = { spec -> editing = i to spec },
                                onBody = { text -> draft.actions { it[i] = a.copy(params = a.params + ("body" to text)) } },
                                onRemove = {
                                    view.tick()
                                    draft.actions { l ->
                                        // Removing an option or Otherwise keeps the block; removing its start or end removes all markers.
                                        val idx = if (a.type.isMidBlock) listOf(i) else
                                            if (a.type.opensBlock || a.type.closesBlock) blockOf(l, i) else listOf(i)
                                        idx.sortedDescending().forEach { l.removeAt(it) }
                                    }
                                },
                                onMove = { dir ->
                                    draft.actions { l ->
                                        val j = i + dir
                                        if (j in l.indices) { val t = l[i]; l[i] = l[j]; l[j] = t }
                                    }
                                },
                                onDuplicate = { draft.actions { it.add(i + 1, a.copy(id = java.util.UUID.randomUUID().toString())) } },
                            )
                        }
                    }
                    if (s.actions.isEmpty()) item(key = "empty") {
                        Card(Modifier.fillMaxWidth()) {
                            Text(
                                "Search below to add your first action, like End WhatsApp Call.",
                                style = Theme.t.body, color = c.ink2, modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }

                if (!docked) sheet(Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start))
                    .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                    .padding(start = 14.dp, bottom = 14.dp), false)
            }
            if (docked) sheet(Modifier
                .width(310.dp)
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom))
                .windowInsetsPadding(WindowInsets.ime.only(WindowInsetsSides.Bottom))
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp), true)
            Rail({ GlassCircle("chevronLeft", "Back", modifier = Modifier.tipAnchor("railBack"), onClick = onBack) })
        }
    }

    LaunchedEffect(editing) { if (editing != null) Tour.done(Tour.Step.EditToken) }
    editing?.let { (i, spec) ->
        val a = s.actions.getOrNull(i)
        if (a == null) { editing = null; return@let }
        ParamDialog(a, spec, s, i, onDismiss = { editing = null }) { values ->
            draft.actions { it[i] = a.copy(params = a.params + values) }
            editing = null
        }
    }
    if (details) DetailsDialog(
        s, missing,
        onDismiss = { details = false },
        onChange = { draft.set(it) },
        onDelete = { details = false; onDelete() },
        onSetup = { details = false; onSetup() },
    )
}

@Composable
private fun EditorHeader(s: Shortcut, onDetails: () -> Unit) {
    val c = Theme.c
    Row(
        Modifier.padding(top = 22.dp, bottom = 20.dp).pressable(label = "Rename and change icon", onClick = onDetails),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(s.glyph, s.tint.color(), size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(s.name, style = Theme.t.title, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(c.fill), contentAlignment = Alignment.Center) {
            Glyph("chevronDown", c.ink2, size = 16.dp)
        }
    }
}

/** Thin line between cards that hand their result to the next one. */
@Composable
private fun Connector(level: Int) {
    Box(Modifier.fillMaxWidth().height(14.dp).padding(start = 18.dp * level), contentAlignment = Alignment.Center) {
        Box(Modifier.width(2.dp).height(14.dp).background(Theme.c.ink3.copy(alpha = 0.45f)))
    }
}

data class TokenView(val text: String, val glyph: String? = null, val tint: androidx.compose.ui.graphics.Color? = null, val empty: Boolean = false)

fun tokenFor(ctx: android.content.Context, a: Action, spec: ParamSpec, all: List<Action>, index: Int): TokenView {
    val v = a.p(spec.key)
    return when (spec.kind) {
        ParamKind.NUMBER -> TokenView(v + spec.suffix)
        // A picked contact shows as the person, like Apple's contact tokens; typed numbers show as typed.
        ParamKind.PHONE, ParamKind.EMAIL -> a.params[spec.key + ".name"]?.takeIf { it.isNotBlank() && v.isNotBlank() }
            ?.let { TokenView(it, "person", monogramTint(it)) }
            ?: TokenView(v.ifBlank { spec.placeholder }, empty = v.isBlank())
        ParamKind.DATETIME -> TokenView(dayTimeLabel(v).ifBlank { spec.placeholder }, empty = v.isBlank())
        // The send mode's full wording lives in its picker; in the sentence it stays short.
        ParamKind.CHOICE -> TokenView(if (v == "and let me tap Send") "for review" else v)
        ParamKind.SHORTCUT -> Store.shortcut(v)?.let { TokenView(it.name, it.glyph, it.tint.color()) } ?: TokenView(spec.placeholder, empty = true)
        ParamKind.APP -> if (v.isBlank()) TokenView(spec.placeholder, empty = true)
            else TokenView(runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(v, 0)).toString() }.getOrDefault(v))
        ParamKind.INPUT -> if (v == "@prev") {
            val src = all.take(index).lastOrNull { it.type.output != null }
            if (src != null) TokenView(src.type.output!!, src.type.glyph, src.type.tint.color())
            else TokenView("Previous Result")
        } else TokenView(v.ifBlank { spec.placeholder }, empty = v.isBlank())
        else -> TokenView(v.ifBlank { spec.placeholder }, empty = v.isBlank())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionCard(
    anchor: Boolean = false,
    action: Action, index: Int, all: List<Action>, level: Int,
    onParam: (ParamSpec) -> Unit, onBody: (String) -> Unit, onRemove: () -> Unit,
    onMove: (Int) -> Unit, onDuplicate: () -> Unit,
) {
    val c = Theme.c
    val ctx = LocalContext.current
    val t = action.type
    var menu by remember { mutableStateOf(false) }
    val body = t.params.firstOrNull { it.key == "body" }

    Box(Modifier.padding(start = 18.dp * level).then(if (anchor) Modifier.tipAnchor("firstAction") else Modifier)) {
        SwipeToDelete(t.title, onDelete = onRemove) {
        Card(Modifier.fillMaxWidth().pressable(scale = 0.985f, label = "More for ${t.title}", onLongClick = { menu = true }, onClick = {})) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(t.glyph, t.tint.color())
                Spacer(Modifier.width(12.dp))
                FlowRow(
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Regex("\\{(\\w+)\\}|[^{\\s]+").findAll(t.template).forEach { m ->
                        val key = m.groupValues[1]
                        if (key.isEmpty()) {
                            Text(m.value, style = Theme.t.action, color = c.ink)
                        } else if (key != "body") {
                            val spec = t.params.first { it.key == key }
                            val tok = tokenFor(ctx, action, spec, all, index)
                            TokenPill(tok.text, glyph = tok.glyph, glyphColor = tok.tint, empty = tok.empty) { onParam(spec) }
                        }
                    }
                }
                RemoveDot("Remove ${t.title}", onRemove)
            }
            if (body != null) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
                var text by remember(action.id) { mutableStateOf(action.p("body")) }
                var focused by remember { mutableStateOf(false) }
                Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp).fillMaxWidth()) {
                    if (text.isEmpty()) Text(body.placeholder, style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink3)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it; onBody(it) },
                        textStyle = Theme.t.body.copy(fontSize = 17.sp, color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                    )
                }
                if (focused) Box(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp)) {
                    VariableChips(app.openshortcuts.model.variableNames(all)) { text += it; onBody(text) }
                }
            }
        }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.raised, shape = RoundedCornerShape(18.dp)) {
            listOf(
                Triple("Move Up", "up") { onMove(-1) },
                Triple("Move Down", "down") { onMove(1) },
                Triple("Duplicate", "copy", onDuplicate),
                Triple("Delete", "trash", onRemove),
            ).forEach { (label, g, f) ->
                val tint = if (g == "trash") c.danger else c.ink
                DropdownMenuItem(
                    text = { Text(label, style = Theme.t.body, color = tint) },
                    trailingIcon = { Glyph(g, tint, size = 20.dp) },
                    onClick = { menu = false; f() },
                )
            }
        }
    }
}

// --- Bottom sheet ---------------------------------------------------------------

private fun rubber(overshoot: Float, dim: Float = 1f, k: Float = 0.55f) = (overshoot * dim * k) / (dim + k * abs(overshoot))

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ActionSheet(
    docked: Boolean = false,
    expandedH: Dp, canUndo: Boolean, canRedo: Boolean, running: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit, onInfo: () -> Unit, onShare: () -> Unit, onRun: () -> Unit,
    onAdd: (ActionType) -> Unit, modifier: Modifier = Modifier,
) {
    val c = Theme.c
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val search = remember { FocusRequester() }
    val progress = remember { Animatable(0f) }
    var raw by remember { mutableStateOf(0f) }
    var query by remember { mutableStateOf("") }
    val rangePx = with(density) { (expandedH - SHEET_COLLAPSED).toPx() }.coerceAtLeast(1f)

    fun settle(target: Float, velocity: Float = 0f) {
        raw = target
        if (target == 0f) { focus.clearFocus(); query = "" }
        scope.launch {
            progress.animateTo(target, if (abs(velocity) > 1.5f) Motion.fling() else Motion.settle(), initialVelocity = velocity)
        }
    }
    BackHandler(enabled = !docked && progress.targetValue > 0f) { settle(0f) }

    val drag = rememberDraggableState { dy ->
        raw -= dy / rangePx
        val shown = when { raw > 1f -> 1f + rubber(raw - 1f, 0.3f); raw < 0f -> -rubber(-raw, 0.3f); else -> raw }
        scope.launch { progress.snapTo(shown) }
    }
    val h = SHEET_COLLAPSED + (expandedH - SHEET_COLLAPSED) * progress.value
    // Typing inside a card: the keyboard owns the bottom, the sheet steps away so the field stays visible.
    if (!docked && WindowInsets.isImeVisible && progress.targetValue == 0f) return
    // Docked beside the actions it is always open: no handle, no drag, the catalogue always there.
    val open = if (docked) 1f else progress.value

    Column(
        modifier
            .then(if (docked) Modifier.fillMaxHeight() else Modifier.fillMaxWidth().height(h))
            .shadow(if (c.dark) 0.dp else 24.dp, RoundedCornerShape(34.dp), ambientColor = c.shadow, spotColor = c.shadow)
            .clip(RoundedCornerShape(34.dp))
            .background(c.raised)
            .draggable(
                drag, Orientation.Vertical, enabled = !docked,
                onDragStarted = { raw = progress.value },
                onDragStopped = { v ->
                    // Project where the flick is heading (Apple's deceleration), snap to the nearer end.
                    val vp = -v / rangePx
                    val projected = progress.value + (vp * 0.998f / (1f - 0.998f)) / 1000f
                    settle(if (projected > 0.5f) 1f else 0f, vp)
                },
            ),
    ) {
        if (docked) Spacer(Modifier.height(14.dp))
        else Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(38.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(c.ink3.copy(alpha = 0.5f)))
        }
        // Search field
        Row(
            Modifier
                .padding(horizontal = 14.dp)
                .tipAnchor("search")
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(c.fill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("search", c.ink2, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search for actions", style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink2)
                BasicTextField(
                    value = query, onValueChange = { query = it; if (!docked && progress.targetValue < 1f) settle(1f) },
                    singleLine = true,
                    textStyle = Theme.t.body.copy(fontSize = 17.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(search)
                        .onFocusChanged { if (!docked && it.isFocused && progress.targetValue < 1f) settle(1f) },
                )
            }
            if (query.isNotEmpty()) Box(Modifier.size(36.dp).pressable(label = "Clear search") { query = "" }, contentAlignment = Alignment.Center) {
                Glyph("x", c.ink2, size = 16.dp)
            }
        }
        // Catalogue: grows with the sheet.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (open > 0.02f) Catalog(query, alpha = open.coerceIn(0f, 1f)) { type ->
                onAdd(type)
                if (docked) { query = ""; focus.clearFocus() } else settle(0f)
            }
        }
        // While typing, the keyboard owns the bottom; the toolbar steps aside for results.
        if (!WindowInsets.isImeVisible) Row(
            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton("undo", "Undo", canUndo, onUndo)
            ToolButton("redo", "Redo", canRedo, onRedo)
            ToolButton("info", "Details", true, onInfo)
            ToolButton("share", "Share", true, onShare)
            ToolButton(if (running) "stopFill" else "playFill", if (running) "Stop" else "Run", true, { Tour.done(Tour.Step.Run); onRun() }, strong = true, modifier = Modifier.tipAnchor("run"))
        }
    }
}

@Composable
private fun ToolButton(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit, strong: Boolean = false, modifier: Modifier = Modifier) {
    val c = Theme.c
    Box(modifier.size(48.dp).pressable(enabled = enabled, scale = 0.9f, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, if (enabled) c.ink else c.ink3, size = if (strong) 28.dp else 25.dp, label = label)
    }
}

@Composable
private fun Catalog(query: String, alpha: Float, onPick: (ActionType) -> Unit) {
    val c = Theme.c
    val q = query.trim().lowercase()
    val types = ActionType.entries.filter { !it.isMarker }
        .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.summary.lowercase().contains(q) || it.category.title.lowercase().contains(q) || keywords(it).any { k -> k.startsWith(q) } }
    LazyColumn(
        Modifier.fillMaxSize().padding(top = 8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    ) {
        val grouped = types.groupBy { it.category }
        Category.entries.forEach { cat ->
            val items = grouped[cat] ?: return@forEach
            item(key = "h" + cat.name) {
                Text(cat.title, style = Theme.t.label, color = c.ink2.copy(alpha = alpha), modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 6.dp))
            }
            items.forEach { t ->
                item(key = t.name) {
                    Row(
                        Modifier.fillMaxWidth().pressable(scale = 0.98f, label = "Add ${t.title}") { onPick(t) }
                            .clip(RoundedCornerShape(16.dp)).padding(horizontal = 6.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(t.glyph, t.tint.color(), size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink)
                            if (t.summary.isNotEmpty()) Text(t.summary, style = Theme.t.small, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Glyph("plus", c.accent, size = 20.dp)
                    }
                }
            }
        }
        if (types.isEmpty()) item {
            Text("No actions match “$query”.", style = Theme.t.body, color = c.ink2, modifier = Modifier.padding(16.dp))
        }
    }
}

/** Indonesian words too, so "tutup telepon" or "tidur" finds the right action. */
private fun keywords(t: ActionType): List<String> = when (t) {
    ActionType.END_WHATSAPP_CALL -> listOf("whatsapp", "wa", "hang", "akhiri", "tutup", "telepon", "panggilan", "call")
    ActionType.END_PHONE_CALL -> listOf("hang", "akhiri", "tutup", "telepon", "panggilan", "call")
    ActionType.DND -> listOf("focus", "silent", "tidur", "sleep", "jangan", "ganggu")
    ActionType.MEDIA -> listOf("youtube", "spotify", "music", "musik", "video", "jeda")
    ActionType.BRIGHTNESS -> listOf("kecerahan", "layar", "screen")
    ActionType.VOLUME -> listOf("suara", "sound")
    ActionType.WAIT -> listOf("tunggu", "delay", "timer")
    ActionType.FLASHLIGHT -> listOf("senter", "torch")
    ActionType.NOTIFY -> listOf("notifikasi", "alert")
    ActionType.BATTERY -> listOf("baterai")
    ActionType.CALL -> listOf("telepon", "panggil", "call", "nelpon")
    ActionType.SEND_SMS -> listOf("sms", "pesan", "kirim")
    ActionType.EMAIL -> listOf("email", "surel", "mail")
    ActionType.SHARE -> listOf("bagikan", "share")
    ActionType.TIMER -> listOf("timer", "hitung mundur", "pomodoro")
    ActionType.ALARM -> listOf("alarm", "bangun", "jam")
    ActionType.ADD_EVENT, ActionType.UPCOMING_EVENTS -> listOf("kalender", "acara", "jadwal", "event", "agenda")
    ActionType.LOCATION, ActionType.OPEN_MAPS -> listOf("lokasi", "peta", "maps", "gps", "alamat")
    ActionType.REPLACE_TEXT -> listOf("ganti", "replace", "teks")
    ActionType.SPLIT_TEXT -> listOf("pisah", "split", "pecah")
    ActionType.COMBINE_TEXT -> listOf("gabung", "join", "combine")
    ActionType.MATCH_TEXT -> listOf("regex", "cari", "match", "cocok")
    ActionType.CHANGE_CASE -> listOf("huruf", "kapital", "besar", "kecil", "case")
    ActionType.EXTRACT -> listOf("ambil", "angka", "nomor", "link", "email", "url")
    ActionType.LIST, ActionType.GET_ITEM, ActionType.COUNT, ActionType.CHOOSE_LIST -> listOf("daftar", "list", "item", "hitung", "pilih")
    ActionType.REPEAT_EACH -> listOf("ulang", "setiap", "each", "loop")
    ActionType.DICTIONARY, ActionType.GET_VALUE, ActionType.SET_VALUE -> listOf("json", "kamus", "dictionary", "key")
    ActionType.CALCULATE, ActionType.ROUND, ActionType.RANDOM -> listOf("hitung", "kalkulator", "math", "angka", "acak", "bulat")
    ActionType.DATE, ActionType.ADJUST_DATE, ActionType.FORMAT_DATE, ActionType.TIME_BETWEEN -> listOf("tanggal", "waktu", "jam", "hari", "date")
    ActionType.COMMENT -> listOf("catatan", "komentar", "note")
    ActionType.REPLY_WHATSAPP -> listOf("balas", "reply", "whatsapp", "wa", "pesan", "auto")
    ActionType.SEND_WHATSAPP -> listOf("kirim", "send", "whatsapp", "wa", "pesan", "chat")
    ActionType.LOCK -> listOf("kunci", "layar", "lock", "screen off", "matikan")
    ActionType.SPEAK -> listOf("bicara", "ucapkan", "baca", "suara", "tts", "say")
    ActionType.REPEAT -> listOf("ulang", "loop", "berulang")
    ActionType.MENU -> listOf("menu", "pilih", "pilihan", "choose")
    ActionType.ASK -> listOf("tanya", "input", "masukan", "prompt")
    ActionType.SET_VARIABLE, ActionType.GET_VARIABLE -> listOf("variabel", "variable", "simpan")
    ActionType.COPY -> listOf("salin", "clipboard", "copy")
    else -> emptyList()
}

/** Plain-text recipe for sharing. */
fun Shortcut.describe(ctx: android.content.Context): String = buildString {
    appendLine(name)
    val lv = levels(actions)
    actions.forEachIndexed { i, a ->
        val line = Regex("\\{(\\w+)\\}").replace(a.type.template) { m ->
            val spec = a.type.params.first { it.key == m.groupValues[1] }
            if (spec.key == "body") "“${a.p("body")}”" else tokenFor(ctx, a, spec, actions, i).text
        }
        append("  ".repeat(lv[i] + 1)).appendLine(if (a.type == ActionType.TEXT) "Text “${a.p("body")}”" else line)
    }
}
