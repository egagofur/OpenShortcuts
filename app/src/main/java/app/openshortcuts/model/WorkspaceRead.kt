package app.openshortcuts.model

import app.openshortcuts.engine.AdapterEngine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/**
 * One value a shortcut reads from a workspace, picked from a widget you already have, so nobody writes a
 * path: "This week" from Dotify, "Open pull requests" from GitHub. [output] says which value of the
 * widget (a metric, the count of a list, whether a timer runs); [label] names it in the shortcut.
 */
data class WorkspaceRead(val adapterId: String, val widgetId: String, val output: String, val label: String) {
    fun encode(): String = JSONObject().put("adapter", adapterId).put("widget", widgetId).put("output", output).put("label", label).toString()

    companion object {
        fun decode(raw: String): WorkspaceRead? = runCatching {
            val o = JSONObject(raw)
            WorkspaceRead(o.getString("adapter"), o.getString("widget"), o.getString("output"), o.optString("label"))
        }.getOrNull()?.takeIf { it.adapterId.isNotBlank() && it.widgetId.isNotBlank() }
    }
}

/** A value a widget can hand to a shortcut. */
data class ReadOutput(val key: String, val label: String)

object WorkspaceReads {
    /** The values [w] can give, named the way the widget shows them. */
    fun outputs(w: DashboardWidget): List<ReadOutput> {
        val name = w.title.ifBlank { w.type.label }
        return when (w.type) {
            WidgetType.STAT_GRID -> w.metrics.mapIndexed { i, m -> ReadOutput("metric:$i", m.label.ifBlank { name }) }
            WidgetType.TARGET_BAR -> w.metrics.flatMapIndexed { i, m ->
                val l = m.label.ifBlank { name }
                listOfNotNull(ReadOutput("metric:$i", l), m.targetPath.takeIf { it.isNotBlank() }?.let { ReadOutput("percent:$i", "$l (% of goal)") })
            }
            WidgetType.PROGRESS_RING -> listOfNotNull(
                ReadOutput("current", name),
                w.targetPath.takeIf { it.isNotBlank() }?.let { ReadOutput("percent", "$name (% of goal)") },
            )
            WidgetType.LIST, WidgetType.GROUPED_LIST -> listOf(
                ReadOutput("count", "$name (how many)"), ReadOutput("first", "$name (first one)"), ReadOutput("all", "$name (all, one per line)"),
            )
            WidgetType.LIVE_STATUS -> listOf(
                ReadOutput("active", "$name (running?)"), ReadOutput("title", "$name (what's running)"), ReadOutput("minutes", "$name (minutes so far)"),
            )
            WidgetType.PROFILE_CARD -> listOf(ReadOutput("name", "Name"), ReadOutput("handle", "Username"))
            WidgetType.BREAKDOWN -> listOf(ReadOutput("top", "$name (largest)"), ReadOutput("total", "$name (total)"))
            WidgetType.BAR_CHART -> listOf(ReadOutput("today", "$name (today)"), ReadOutput("total", "$name (total)"))
            WidgetType.HEATMAP_CHART -> listOf(ReadOutput("today", "$name (today)"), ReadOutput("total", "$name (total)"))
            else -> emptyList()
        }
    }

    /** Plain numbers ("34.8", not "34.8h") so an If can compare them. */
    private fun num(d: Double?): String = when {
        d == null -> ""
        d % 1.0 == 0.0 -> d.toLong().toString()
        else -> "%.1f".format(java.util.Locale.ROOT, d)
    }

    /** The value [output] of [w], read from the widget's response [payload]. */
    fun value(w: DashboardWidget, payload: Any?, output: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val idx = output.substringAfter(':', "").toIntOrNull() ?: 0
        val items by lazy { AdapterEngine.extractList(payload, w.itemsPath) }
        fun sum(list: List<JSONObject>) = list.sumOf { if (w.valuePath.isBlank()) 1.0 else AdapterEngine.extractNumber(it, w.valuePath) ?: 0.0 }
        val today = LocalDate.ofInstant(now, zone)
        fun onToday(o: JSONObject) = AdapterEngine.toLocalDate(AdapterEngine.extractString(o, w.metaPath.ifBlank { "created_at" })) == today
        return when (output.substringBefore(':')) {
            "metric" -> w.metrics.getOrNull(idx)?.let { AdapterEngine.extractString(payload, it.valuePath) }.orEmpty()
            "percent" -> {
                val (cur, tgt) = if (w.type == WidgetType.PROGRESS_RING) w.currentPath to w.targetPath
                    else (w.metrics.getOrNull(idx)?.valuePath.orEmpty() to w.metrics.getOrNull(idx)?.targetPath.orEmpty())
                val c = AdapterEngine.extractNumber(payload, cur); val t = AdapterEngine.extractNumber(payload, tgt)
                if (c == null || t == null || t == 0.0) "" else num(c / t * 100)
            }
            "current" -> AdapterEngine.extractString(payload, w.currentPath)
            "count" -> items.size.toString()
            "first" -> items.firstOrNull()?.let { AdapterEngine.extractString(it, w.titlePath) }.orEmpty()
            "all" -> items.joinToString("\n") { AdapterEngine.extractString(it, w.titlePath) }
            "active" -> if (AdapterEngine.extractBool(payload, w.activePath)) "yes" else "no"
            "title" -> AdapterEngine.extractString(payload, w.activeTitlePath)
            "minutes" -> AdapterEngine.parseInstantOrNull(AdapterEngine.extractString(payload, w.startedAtPath))
                ?.let { ((now.toEpochMilli() - it.toEpochMilli()) / 60_000).coerceAtLeast(0).toString() }.orEmpty()
            "name" -> AdapterEngine.extractString(payload, w.titlePath.ifBlank { "name" })
            "handle" -> AdapterEngine.extractString(payload, w.subtitlePath.ifBlank { "username" })
            "top" -> AdapterEngine.groupTotals(items, w.groupPath, w.valuePath, 1).firstOrNull()?.first.orEmpty()
            "total" -> num(sum(items))
            "today" -> num(sum(items.filter(::onToday)))
            else -> ""
        }
    }

    /** Where [read] points: the workspace and widget, or why it can't be read anymore. */
    fun resolve(read: WorkspaceRead): Pair<WorkspaceAdapter, DashboardWidget>? {
        val a = AdapterStore.get(read.adapterId) ?: return null
        val w = a.tabs.flatMap { it.widgets }.firstOrNull { it.id == read.widgetId } ?: return null
        return a to w
    }

    /** Fetches [read] fresh from its workspace: the value, or why it couldn't be read. */
    suspend fun fetch(read: WorkspaceRead): Result<String> = runCatching {
        val (adapter, widget) = resolve(read) ?: error("That workspace or widget isn't on this phone anymore.")
        if (!AdapterStore.hasToken(adapter)) error("Finish setting up ${adapter.name} in its settings.")
        val ep = endpointOf(adapter, widget).ifBlank { error("${widget.title} has no data to read.") }
        val res = AdapterEngine.fetchEndpoints(adapter, setOf(ep), mapOf(ep to widget.pages))[ep] ?: error("${adapter.name} didn't answer.")
        val payload = res.getOrElse { error("${adapter.name}: ${it.message}") }
        value(widget, payload, read.output)
    }

    /** The endpoint a widget reads (its own, or its tab's). */
    fun endpointOf(a: WorkspaceAdapter, w: DashboardWidget): String =
        w.endpoint?.takeIf { it.isNotBlank() } ?: a.tabs.firstOrNull { t -> t.widgets.any { it.id == w.id } }?.endpoint.orEmpty()
}
