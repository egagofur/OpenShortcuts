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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.openshortcuts.model.DashboardActionRef
import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.Tint
import app.openshortcuts.model.WidgetType
import app.openshortcuts.engine.AdapterEngine
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddTabDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String) -> Unit,
) {
    val c = Theme.c
    var label by remember { mutableStateOf("") }
    DuoDialog(onDismiss = onDismiss) {
        DialogTitle("New Dashboard Tab", "Group related widgets into a swipeable tab.")
        Text("Tab Name", style = Theme.t.small, color = c.ink2)
        Spacer(Modifier.height(4.dp))
        BuilderInput(label, { label = it }, "e.g. Overview, Sprint, Approvals")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Add Tab", Modifier.fillMaxWidth()) {
            val clean = label.trim().ifEmpty { "Tab" }
            onAdd(clean)
        }
        Spacer(Modifier.height(8.dp))
        QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

/**
 * Add a widget by looking at real data: pick the kind, the endpoint is called right away, and the
 * response shows as a tree. Tap a list, then tap fields; the widget previews with the real data as you
 * go. Any field can also be typed: a path ("data.items") or a template ("{{first}} {{last}}|upper").
 */
@Composable
fun AddWidgetDialog(
    adapter: app.openshortcuts.model.WorkspaceAdapter,
    defaultEndpoint: String,
    onDismiss: () -> Unit,
    onAdd: (DashboardWidget) -> Unit,
) {
    val c = Theme.c
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var selectedType by remember { mutableStateOf<WidgetType?>(null) }
    var title by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(defaultEndpoint) }
    var pathA by remember { mutableStateOf("") }
    var selectedTint by remember { mutableStateOf(Tint.Blue) }
    val paths = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    var active by remember { mutableStateOf<String?>(null) }
    var response by remember { mutableStateOf<Result<Any?>?>(null) }
    var loadingData by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val slots = selectedType?.let { DataSlots.forType(it) }.orEmpty()
    val payload = response?.getOrNull()
    // Fields of a list are picked inside one item; the rest from the whole response.
    fun rootFor(slot: DataSlot, data: Any? = payload): Any? =
        if (slot.perItem) AdapterEngine.extractList(data, paths[DataSlots.ITEMS].orEmpty()).firstOrNull() else data
    fun nextEmpty(after: String?): String? {
        val order = slots.map { it.key }
        val start = after?.let { order.indexOf(it) + 1 } ?: 0
        return (order.drop(start) + order.take(start)).firstOrNull { k -> paths[k].isNullOrBlank() && slots.first { it.key == k }.let { !it.optional } }
    }
    fun load() {
        val ep = endpoint.trim()
        if (ep.isEmpty() || slots.isEmpty()) return
        loadingData = true
        scope.launch {
            val res = AdapterEngine.fetchEndpoints(adapter, setOf(ep))[ep] ?: Result.failure(IllegalStateException("No response"))
            response = res
            loadingData = false
            val data = res.getOrNull() ?: return@launch
            // Guess the obvious: the first list of objects, then the usual field names.
            if (slots.any { it.key == DataSlots.ITEMS } && paths[DataSlots.ITEMS] == null) DataSlots.guessList(data)?.let { paths[DataSlots.ITEMS] = it }
            slots.filter { it.key != DataSlots.ITEMS && paths[it.key].isNullOrBlank() }.forEach { s ->
                DataSlots.guessField(rootFor(s, data), s)?.let { paths[s.key] = it }
            }
            active = nextEmpty(null) ?: slots.firstOrNull { it.key != DataSlots.ITEMS }?.key ?: slots.firstOrNull()?.key
        }
    }
    androidx.compose.runtime.LaunchedEffect(selectedType) { if (selectedType != null) load() }

    DuoDialog(onDismiss = onDismiss) {
        val type = selectedType
        if (type == null) {
            DialogTitle("Add Widget", "Choose a block. Its data comes from your API, and you'll see it before adding.")
            WidgetType.entries.filter { it != WidgetType.SHARE_CARD }.forEach { wt ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .pressable(label = wt.label) {
                            selectedType = wt
                            title = wt.label
                        },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile("layers", Tint.Blue.color(), size = 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(wt.label, style = Theme.t.label, color = c.ink)
                            Text(wt.summary, style = Theme.t.small, color = c.ink2)
                        }
                        Glyph("chevronRight", c.ink3, size = 16.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
        } else {
            DialogTitle(type.label, type.summary)
            Text("Title", style = Theme.t.small, color = c.ink2)
            Spacer(Modifier.height(4.dp))
            BuilderInput(title, { title = it }, type.label)
            Spacer(Modifier.height(12.dp))

            if (slots.isNotEmpty()) {
                Text("API endpoint (GET)", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { BuilderInput(endpoint, { endpoint = it }, "/endpoint") }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(c.ink)
                            .pressable(scale = 0.96f, label = "Load") { load() }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) { Text(if (loadingData) "…" else "Load", style = Theme.t.label, color = c.bg) }
                }
                Spacer(Modifier.height(8.dp))
                EndpointPicker(adapter) { picked -> endpoint = picked; load() }
                Spacer(Modifier.height(6.dp))
                val err = response?.exceptionOrNull()?.message
                Text(
                    when {
                        loadingData -> "Loading ${endpoint.trim()}"
                        err != null -> "Couldn't load: $err"
                        payload != null -> "Loaded. Tap a field below to use it."
                        else -> "Tap Load to see what this endpoint returns."
                    },
                    style = Theme.t.small, color = if (err != null) c.danger else c.ink3,
                )

                if (loadingData && payload == null) {
                    Spacer(Modifier.height(14.dp))
                    SkeletonRows(4)
                }
                if (payload != null) {
                    Spacer(Modifier.height(14.dp))
                    SlotChips(slots, paths, active) { active = it }
                    val slot = slots.firstOrNull { it.key == active }
                    if (slot != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            when {
                                slot.wantsList -> "Pick the list: tap one marked Use."
                                slot.perItem -> "${slot.label}: tap a field of one item (all items read the same field)."
                                else -> "${slot.label}: tap a field."
                            },
                            style = Theme.t.small, color = c.ink2,
                        )
                        Spacer(Modifier.height(8.dp))
                        JsonPicker(rootFor(slot), slot, paths.values.toSet()) { path ->
                            paths[slot.key] = path
                            if (slot.key == DataSlots.ITEMS) {
                                // A new list: re-guess its fields from its first item.
                                slots.filter { it.perItem }.forEach { s -> paths.remove(s.key); DataSlots.guessField(rootFor(s), s)?.let { paths[s.key] = it } }
                            }
                            active = nextEmpty(slot.key) ?: slot.key
                        }
                        Spacer(Modifier.height(8.dp))
                        // Or type it: a path, or a template that combines and formats fields.
                        BuilderInput(paths[slot.key].orEmpty(), { paths[slot.key] = it }, if (slot.wantsList) "e.g. data.items (empty: whole response)" else "path, or {{first}} {{last}}|upper")
                        if (!slot.wantsList) {
                            Spacer(Modifier.height(4.dp))
                            Text("Templates: {{field}} mixes fields and text. Pipes: |upper |lower |date |number |first:20 |default:none", style = Theme.t.small, color = c.ink3)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (showRaw) "Hide raw JSON" else "Show raw JSON", style = Theme.t.small, color = c.accent,
                        modifier = Modifier.pressable(label = "Raw JSON") { showRaw = !showRaw }.padding(vertical = 6.dp),
                    )
                    if (showRaw) {
                        val raw = remember(payload) {
                            (when (payload) { is org.json.JSONObject -> payload.toString(2); is org.json.JSONArray -> payload.toString(2); else -> payload.toString() }).take(6000)
                        }
                        Text(
                            raw, style = Theme.t.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp), color = c.ink2,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(c.fill)
                                .verticalScroll(rememberScrollState()).padding(10.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Preview", style = Theme.t.label, color = c.ink)
                    Spacer(Modifier.height(8.dp))
                    WidgetPreview(DataSlots.build("preview", type, title.trim(), selectedTint, endpoint.trim(), paths.toMap()), payload)
                }
            } else if (type == WidgetType.NOTE_CARD) {
                Text("Note text", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                BuilderInput(pathA, { pathA = it }, "Reminder or checklist...")
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Accent", style = Theme.t.small, color = c.ink2)
                TokenPill(selectedTint.name) {
                    val idx = Tint.entries.indexOf(selectedTint)
                    selectedTint = Tint.entries[(idx + 1) % Tint.entries.size]
                }
            }
            Spacer(Modifier.height(16.dp))
            // The list may be the whole response (an empty path); every other required field needs a path.
            val missing = slots.filter { !it.optional && it.key != DataSlots.ITEMS && paths[it.key].isNullOrBlank() }
            PrimaryButton(if (missing.isEmpty()) "Add to Dashboard" else "Pick ${missing.first().label} first", Modifier.fillMaxWidth(), color = selectedTint.color()) {
                if (missing.isNotEmpty()) { active = missing.first().key; return@PrimaryButton }
                val id = UUID.randomUUID().toString().take(8)
                val widget = when (type) {
                    WidgetType.ACTION_GRID -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint,
                        actionRefs = listOf(DashboardActionRef(title.trim().ifEmpty { "Run" }, "bolt", selectedTint)),
                    )
                    WidgetType.SENTENCE_BAR -> DashboardWidget(id = id, type = type, title = title.trim(), tint = selectedTint, choices = listOf("All", "Open", "Done"))
                    WidgetType.NOTE_CARD -> DashboardWidget(id = id, type = type, title = title.trim(), tint = selectedTint, body = pathA.trim().ifEmpty { "Tap Edit to customize this note." })
                    WidgetType.SHARE_CARD -> DashboardWidget(id = id, type = type, title = title.trim().ifEmpty { "Stracode" }, tint = selectedTint, glyph = "laptopRun")
                    else -> DataSlots.build(id, type, title.trim(), selectedTint, endpoint.trim(), paths.toMap())
                }
                onAdd(widget)
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Back", Modifier.fillMaxWidth()) { selectedType = null; paths.clear(); response = null; active = null }
        }
    }
}

/**
 * The API's GET endpoints, from its OpenAPI description, as a searchable list. Tap one to use it; the
 * endpoint field above still takes anything typed.
 */
@Composable
private fun EndpointPicker(adapter: app.openshortcuts.model.WorkspaceAdapter, onPick: (String) -> Unit) {
    val c = Theme.c
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var list by remember(adapter.id) { mutableStateOf(app.openshortcuts.model.OpenApi.cached(adapter.id)) }
    var open by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var docsUrl by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    fun fetch(url: String? = null) {
        loading = true; error = null
        scope.launch {
            app.openshortcuts.model.OpenApi.load(adapter, url).onSuccess { list = it; open = true }.onFailure { error = it.message }
            loading = false
        }
    }
    val l = list
    if (!open) {
        Text(
            if (l != null) "Browse ${l.size} endpoints" else if (loading) "Looking for API docs…" else "Browse endpoints from the API docs",
            style = Theme.t.small, color = c.accent,
            modifier = Modifier.pressable(label = "Browse endpoints") { if (l != null) open = true else fetch() }.padding(vertical = 6.dp),
        )
        error?.let { e ->
            Text(e, style = Theme.t.small, color = c.ink3)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BuilderInput(docsUrl, { docsUrl = it }, "https://…/openapi.json or /docs.json") }
                Spacer(Modifier.width(8.dp))
                QuietButton("Load", onClick = { fetch(docsUrl) })
            }
        }
        return
    }
    if (l == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${l.size} endpoints", style = Theme.t.small, color = c.ink2, modifier = Modifier.weight(1f))
        Text("Hide", style = Theme.t.small, color = c.accent, modifier = Modifier.pressable(label = "Hide endpoints") { open = false }.padding(6.dp))
    }
    Spacer(Modifier.height(6.dp))
    SearchField(query, { query = it }, "Search endpoints", surface = c.fill)
    Spacer(Modifier.height(6.dp))
    val q = query.trim()
    val shown = l.filter { q.isEmpty() || "${it.path} ${it.summary} ${it.tag}".contains(q, ignoreCase = true) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.fill)) {
        shown.take(8).forEach { e ->
            Column(
                Modifier.fillMaxWidth().pressable(scale = 0.98f, label = e.path) { onPick(e.path); open = false }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GET", style = Theme.t.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 11.sp), color = c.positive)
                    Spacer(Modifier.width(8.dp))
                    Text(e.path, style = Theme.t.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.ink, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (e.tag.isNotBlank()) Text(e.tag, style = Theme.t.small, color = c.ink3, maxLines = 1)
                }
                if (e.summary.isNotBlank()) Text(e.summary, style = Theme.t.small, color = c.ink2, maxLines = 2)
            }
        }
        if (shown.isEmpty()) Text("No endpoint matches “$q”. You can still type it above.", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(14.dp))
        if (shown.size > 8) Text("${shown.size - 8} more: keep typing to narrow it down", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

/** The widget as it will look, drawn with the real response. Lists show their first three rows. */
@Composable
private fun WidgetPreview(w: DashboardWidget, payload: Any?) {
    val c = Theme.c
    when (w.type) {
        WidgetType.STAT_GRID -> StatGridWidget(w, payload)
        WidgetType.PROGRESS_RING -> ProgressRingWidget(w, payload)
        WidgetType.TARGET_BAR -> TargetBarWidget(w, payload)
        WidgetType.BAR_CHART -> BarChartWidget(w, payload)
        WidgetType.HEATMAP_CHART -> HeatmapChartWidget(w, payload)
        WidgetType.BREAKDOWN -> BreakdownWidget(w, payload)
        WidgetType.PROFILE_CARD -> ProfileCardWidget(w, payload)
        WidgetType.LIST, WidgetType.GROUPED_LIST -> {
            val items = AdapterEngine.extractList(payload, w.itemsPath)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isEmpty()) Text("No items at that path.", style = Theme.t.small, color = c.ink3)
                items.take(3).forEach { o ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(AdapterEngine.extractString(o, w.titlePath).ifBlank { "Untitled" }, style = Theme.t.label, color = c.ink, maxLines = 2)
                                val sub = AdapterEngine.extractString(o, if (w.type == WidgetType.GROUPED_LIST) w.groupPath else w.subtitlePath)
                                if (sub.isNotBlank()) Text(sub, style = Theme.t.small, color = c.ink2, maxLines = 1)
                                val meta = AdapterEngine.extractString(o, w.metaPath)
                                if (meta.isNotBlank()) Text(AdapterEngine.formatIfTimestamp(meta), style = Theme.t.small, color = c.ink3, maxLines = 1)
                            }
                            val badge = AdapterEngine.extractString(o, w.badgePath)
                            if (badge.isNotBlank()) TokenPill(badge) {}
                        }
                    }
                }
                if (items.size > 3) Text("+ ${items.size - 3} more", style = Theme.t.small, color = c.ink3)
            }
        }
        WidgetType.LIVE_STATUS -> Text(
            "Running: ${AdapterEngine.extractBool(payload, w.activePath)}" +
                AdapterEngine.extractString(payload, w.activeTitlePath).takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
            style = Theme.t.small, color = c.ink2,
        )
        else -> Unit
    }
}

@Composable
private fun BuilderInput(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val c = Theme.c
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.fill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Theme.t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = Theme.t.body, color = c.ink3)
                }
                inner()
            },
        )
    }
}
