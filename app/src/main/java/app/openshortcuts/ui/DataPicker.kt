package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.StatMetric
import app.openshortcuts.model.Tint
import app.openshortcuts.model.WidgetType
import org.json.JSONArray
import org.json.JSONObject

/**
 * One piece of data a widget needs, filled by tapping the response. [perItem] slots are read from each
 * item of the list ([DataSlots.ITEMS]); the others from the whole response. [hints] are the field names
 * guessed first when the response arrives.
 */
data class DataSlot(
    val key: String,
    val label: String,
    val perItem: Boolean = false,
    val wantsList: Boolean = false,
    val optional: Boolean = false,
    val hints: List<String> = emptyList(),
)

object DataSlots {
    const val ITEMS = "items"

    private val items = DataSlot(ITEMS, "List", wantsList = true)
    private val title = DataSlot("title", "Title", perItem = true, hints = listOf("title", "name", "subject", "summary", "full_name", "label", "message"))
    private val subtitle = DataSlot("subtitle", "Subtitle", perItem = true, optional = true,
        hints = listOf("description", "body", "author.name", "user.login", "author", "email", "project.name"))
    private val badge = DataSlot("badge", "Badge", perItem = true, optional = true, hints = listOf("status", "state", "type", "language", "priority"))
    private val date = DataSlot("date", "Date", perItem = true, hints = listOf("created_at", "updated_at", "date", "started_at", "timestamp", "pushed_at", "time"))
    private val dateOpt = date.copy(optional = true)
    private val group = DataSlot("group", "Group by", perItem = true, hints = listOf("status", "state", "type", "category", "language", "project.name", "repo.name"))
    private val value = DataSlot("value", "Amount", perItem = true, optional = true, hints = listOf("hours", "amount", "value", "count", "total", "duration"))

    fun forType(t: WidgetType): List<DataSlot> = when (t) {
        WidgetType.LIST -> listOf(items, title, subtitle, badge, dateOpt)
        WidgetType.GROUPED_LIST -> listOf(items, title, group, dateOpt)
        WidgetType.BREAKDOWN -> listOf(items, group, value)
        WidgetType.BAR_CHART -> listOf(items, date, value)
        WidgetType.HEATMAP_CHART -> listOf(items, date)
        WidgetType.STAT_GRID -> listOf(DataSlot("value", "Number", hints = listOf("count", "total", "total_count", "value", "data.count")))
        WidgetType.PROGRESS_RING, WidgetType.TARGET_BAR -> listOf(
            DataSlot("current", "Current", hints = listOf("current", "value", "done", "hours", "total")),
            DataSlot("target", "Goal", optional = true, hints = listOf("target", "goal", "limit", "max")),
        )
        WidgetType.PROFILE_CARD -> listOf(
            DataSlot("title", "Name", hints = listOf("name", "full_name", "display_name", "login", "username")),
            DataSlot("subtitle", "Handle", optional = true, hints = listOf("login", "username", "handle")),
            DataSlot("meta", "Detail", optional = true, hints = listOf("bio", "email", "job_title", "html_url")),
            DataSlot("badge", "Badge", optional = true, hints = listOf("location", "state", "type", "role")),
        )
        WidgetType.LIVE_STATUS -> listOf(
            DataSlot("active", "Running?", hints = listOf("running", "active", "is_running", "data.running")),
            DataSlot("activeTitle", "What's running", optional = true, hints = listOf("title", "description", "data.title")),
        )
        else -> emptyList()
    }

    /** The response's first list of objects (breadth first), as a path; "" when the response is one. */
    fun guessList(payload: Any?): String? {
        if (payload is JSONArray) return ""
        val queue = ArrayDeque<Pair<String, Any?>>().apply { add("" to payload) }
        while (queue.isNotEmpty()) {
            val (path, node) = queue.removeFirst()
            if (node !is JSONObject) continue
            for (k in node.keys()) {
                val v = node.opt(k)
                val p = if (path.isEmpty()) k else "$path.$k"
                if (v is JSONArray && v.length() > 0 && v.opt(0) is JSONObject) return p
                if (v is JSONObject && p.count { it == '.' } < 3) queue.add(p to v)
            }
        }
        return null
    }

    /** The first hint that has a value in [obj]. */
    fun guessField(obj: Any?, slot: DataSlot): String? =
        slot.hints.firstOrNull { AdapterEngine.extractString(obj, it).isNotBlank() }

    /** The widget [type] reads its data from [paths] (slot key to path or template). */
    fun build(id: String, type: WidgetType, title: String, tint: Tint, endpoint: String, paths: Map<String, String>): DashboardWidget {
        fun p(k: String) = paths[k].orEmpty().trim()
        val base = DashboardWidget(id = id, type = type, title = title, tint = tint, endpoint = endpoint)
        return when (type) {
            WidgetType.LIST -> base.copy(itemsPath = p(ITEMS), titlePath = p("title").ifEmpty { "title" }, subtitlePath = p("subtitle"), badgePath = p("badge"), metaPath = p("date"))
            WidgetType.GROUPED_LIST -> base.copy(itemsPath = p(ITEMS), titlePath = p("title").ifEmpty { "title" }, groupPath = p("group"), metaPath = p("date"))
            WidgetType.BREAKDOWN -> base.copy(itemsPath = p(ITEMS), groupPath = p("group"), valuePath = p("value"))
            WidgetType.BAR_CHART -> base.copy(itemsPath = p(ITEMS), metaPath = p("date"), valuePath = p("value"))
            WidgetType.HEATMAP_CHART -> base.copy(itemsPath = p(ITEMS), metaPath = p("date"))
            WidgetType.STAT_GRID -> base.copy(metrics = listOf(StatMetric(title.ifBlank { "Metric" }, p("value"), tint = tint)))
            WidgetType.PROGRESS_RING -> base.copy(currentPath = p("current"), targetPath = p("target"))
            WidgetType.TARGET_BAR -> base.copy(metrics = listOf(StatMetric(title.ifBlank { "Progress" }, p("current"), targetPath = p("target"), tint = tint)))
            WidgetType.PROFILE_CARD -> base.copy(titlePath = p("title"), subtitlePath = p("subtitle"), metaPath = p("meta"), badgePath = p("badge"))
            WidgetType.LIVE_STATUS -> base.copy(activePath = p("active"), activeTitlePath = p("activeTitle"))
            else -> base
        }
    }
}

/** One line of the response tree. */
private class Node(val path: String, val key: String, val value: Any?, val depth: Int)

/** "Fix login", 42, true, [30 items], {7 fields}: what a value looks like at a glance. */
private fun preview(v: Any?): String = when (v) {
    null, JSONObject.NULL -> "null"
    is JSONArray -> "[${v.length()} item${if (v.length() == 1) "" else "s"}]"
    is JSONObject -> "{${v.length()} field${if (v.length() == 1) "" else "s"}}"
    is String -> "\"" + v.replace('\n', ' ').take(60) + "\""
    else -> v.toString()
}

/**
 * Rows to show for [root], walking into [open] containers. A list shows its first item as the example
 * for all of them (paths use "key[*]" below the top so they read every item).
 */
private fun rows(root: Any?, open: Set<String>, limit: Int): List<Node> {
    val out = ArrayList<Node>()
    fun walk(node: Any?, path: String, depth: Int, underList: Boolean) {
        if (out.size >= limit) return
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                if (out.size >= limit) return
                val v = node.opt(k)
                val p = if (path.isEmpty()) k else "$path.$k"
                out += Node(p, k, v, depth)
                if (p in open) when (v) {
                    is JSONObject -> walk(v, p, depth + 1, underList)
                    // A path through a list reads that field of every item ("data.items.title").
                    is JSONArray -> v.optJSONObject(0)?.let { walk(it, p, depth + 1, true) }
                }
            }
            is JSONArray -> node.optJSONObject(0)?.let { walk(it, path, depth, true) }
        }
    }
    walk(root, "", 0, false)
    return out
}

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}.*)?$""")

/** What kind of value this is, in a word: text, number, date, yes/no, list, group. */
private fun kind(v: Any?): String = when (v) {
    is JSONArray -> "list"
    is JSONObject -> "group"
    is Boolean -> "yes/no"
    is Number -> "number"
    is String -> if (ISO_DATE.matches(v)) "date" else "text"
    else -> "empty"
}

/** Every field under [root] (lists read through their first item), with its full path, for searching. */
private fun flatten(root: Any?, limit: Int): List<Node> {
    val out = ArrayList<Node>()
    fun walk(node: Any?, path: String, depth: Int) {
        if (out.size >= limit || depth > 4) return
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                val v = node.opt(k)
                val p = if (path.isEmpty()) k else "$path.$k"
                out += Node(p, p, v, 0)
                if (v is JSONObject || v is JSONArray) walk(v, p, depth + 1)
            }
            is JSONArray -> node.optJSONObject(0)?.let { walk(it, path, depth) }
        }
    }
    walk(root, "", 0)
    return out
}

/**
 * The response as a tap-to-pick list. Each field shows its value and what kind it is; fields already used
 * are ticked. Search finds any field by name, path or value, however deep. Lists show their first item.
 */
@Composable
fun JsonPicker(root: Any?, active: DataSlot?, used: Set<String> = emptySet(), onPick: (String) -> Unit) {
    val c = Theme.c
    val open = remember(root) { mutableStateMapOf<String, Boolean>() }
    var query by remember(root) { androidx.compose.runtime.mutableStateOf("") }
    val q = query.trim()
    val shown = if (q.isEmpty()) rows(root, open.filterValues { it }.keys, 80)
        else flatten(root, 400).filter { n -> n.path.contains(q, true) || (n.value !is JSONObject && n.value !is JSONArray && preview(n.value).contains(q, true)) }.take(60)
    SearchField(query, { query = it }, "Search fields", surface = c.fill)
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.fill).padding(vertical = 6.dp),
    ) {
        if (shown.isEmpty()) Text(if (q.isEmpty()) preview(root) else "No field matches “$q”.", style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(12.dp))
        shown.forEach { n ->
            val container = n.value is JSONObject || n.value is JSONArray
            val pickable = when {
                active == null -> false
                active.wantsList -> n.value is JSONArray
                else -> !container || n.value is JSONObject
            }
            val isUsed = n.path in used
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .pressable(scale = 0.98f, label = n.path) {
                        if (pickable && (!container || active?.wantsList == true || open[n.path] == true || q.isNotEmpty())) onPick(n.path)
                        else if (container) open[n.path] = open[n.path] != true
                        else if (pickable) onPick(n.path)
                    }
                    .padding(start = (12 + n.depth * 16).dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(18.dp)) {
                    when {
                        isUsed -> Glyph("check", c.positive, 15.dp)
                        container && q.isEmpty() -> Glyph(if (open[n.path] == true) "chevronDown" else "chevronRight", c.ink3, 14.dp)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(n.key, style = Theme.t.small.copy(fontFamily = FontFamily.Monospace), color = if (pickable) c.ink else c.ink2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(kind(n.value), style = Theme.t.small.copy(fontSize = 11.sp), color = c.ink3, maxLines = 1)
                    }
                    if (!container) Text(preview(n.value), style = Theme.t.small.copy(fontSize = 12.sp), color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Text(preview(n.value), style = Theme.t.small.copy(fontSize = 12.sp), color = c.ink3, maxLines = 1)
                }
                if (pickable) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text("Use", style = Theme.t.small, color = c.accent)
                    }
                }
            }
        }
    }
}

/** The slots as chips: tap one to pick it; a filled one shows the field it reads. */
@Composable
fun SlotChips(slots: List<DataSlot>, paths: Map<String, String>, active: String?, onSelect: (String) -> Unit) {
    val c = Theme.c
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEach { s ->
            val on = s.key == active
            val value = paths[s.key].orEmpty()
            Column(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (on) c.ink else c.fill)
                    .border(if (on) 0.dp else 1.dp, if (value.isBlank() && !s.optional) c.accent.copy(alpha = 0.5f) else c.fill, RoundedCornerShape(14.dp))
                    .pressable(scale = 0.96f, label = s.label) { onSelect(s.key) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(s.label + if (s.optional) "" else " *", style = Theme.t.small, color = if (on) c.bg else c.ink2)
                Text(
                    value.ifBlank { if (s.key == DataSlots.ITEMS && paths.containsKey(DataSlots.ITEMS)) "(whole response)" else "Tap a field" },
                    style = Theme.t.small.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    color = if (on) c.bg else if (value.isBlank()) c.ink3 else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
            }
        }
    }
}
