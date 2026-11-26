package app.openshortcuts.model

import org.json.JSONArray
import org.json.JSONObject

enum class AuthKind(val key: String) {
    BEARER("bearer"),
    PRIVATE_TOKEN("private_token"),
    NONE("none");

    companion object {
        fun from(key: String): AuthKind = entries.firstOrNull { it.key == key } ?: BEARER
    }
}

enum class WidgetType(val key: String, val label: String, val summary: String) {
    STAT_GRID("stat_grid", "Metric Grid", "2-column KPI numbers with optional targets"),
    PROGRESS_RING("progress_ring", "Progress Ring", "Animated target arc with current/goal"),
    TARGET_BAR("target_bar", "Target Bar", "Big number with a bar toward its goal"),
    LIVE_STATUS("live_status", "Live Status", "Active indicator banner with quick action"),
    ACTION_GRID("action_grid", "Action Tiles", "2-column colored tiles for API or Shortcuts"),
    SENTENCE_BAR("sentence_bar", "Sentence Filter", "Tappable blue token bar"),
    LIST("list", "Card List", "Swipeable rows with detail page"),
    GROUPED_LIST("grouped_list", "Grouped List", "Sectioned rows grouped by a field"),
    NOTE_CARD("note_card", "Note Card", "Info or checklist reminder card"),
    PROFILE_CARD("profile_card", "Profile Card", "User identity card from API"),
    BAR_CHART("bar_chart", "Bar Chart", "Daily or weekly totals, counted or summed"),
    HEATMAP_CHART("heatmap_chart", "Contribution Heatmap", "GitLab-style weekly contribution grid"),
    BREAKDOWN("breakdown", "Breakdown", "Totals per group as ranked bars"),
    SHARE_CARD("share_card", "Stracode", "Your commits as a shareable photo card");

    companion object {
        fun from(key: String): WidgetType = entries.firstOrNull { it.key == key } ?: LIST
    }
}

data class AdapterParam(
    val key: String,
    val label: String,
    val default: String = "",
    val placeholder: String = "",
    val kind: String = "text", // "text", "time", "utc_iso"
    val choices: List<String> = emptyList(),
    val optionsEndpoint: String? = null,
    val optionsPath: String = "data",
    val optionLabelPath: String = "name",
    val optionValuePath: String = "id",
)

data class AdapterAction(
    val id: String,
    val title: String,
    val method: String = "POST",
    val endpoint: String,
    val bodyTemplate: String? = null,
    val params: List<AdapterParam> = emptyList(),
    val tint: Tint = Tint.Blue,
    val glyph: String = "bolt",
    val confirmMessage: String? = null,
)

/**
 * A workspace action saved inside a shortcut: which workspace, which action, and the values for its
 * fields (text may hold shortcut variables). [labels] keeps the names picked from lists, like a project.
 */
data class WorkspaceCall(
    val adapterId: String,
    val actionId: String,
    val params: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
) {
    fun encode(): String = JSONObject().apply {
        put("adapter", adapterId)
        put("action", actionId)
        put("params", JSONObject(params))
        put("labels", JSONObject(labels))
    }.toString()

    companion object {
        fun decode(raw: String): WorkspaceCall? = runCatching {
            val o = JSONObject(raw)
            fun map(k: String) = o.optJSONObject(k)?.let { m -> m.keys().asSequence().associateWith { m.optString(it) } }.orEmpty()
            WorkspaceCall(o.getString("adapter"), o.getString("action"), map("params"), map("labels"))
        }.getOrNull()?.takeIf { it.adapterId.isNotBlank() && it.actionId.isNotBlank() }
    }
}

data class AdapterRowAction(
    val label: String,
    val glyph: String = "check",
    val tint: Tint = Tint.Green,
    val swipe: String = "none", // "right", "left", "none"
    val method: String = "POST",
    val endpoint: String,
    val bodyTemplate: String? = null,
)

data class StatMetric(
    val label: String,
    val valuePath: String,
    val suffix: String = "",
    val targetPath: String = "",
    val tint: Tint = Tint.Blue,
)

data class DashboardActionRef(
    val title: String,
    val glyph: String = "bolt",
    val tint: Tint = Tint.Blue,
    val actionId: String? = null,
    val shortcutName: String? = null,
)

data class DetailField(
    val label: String,
    val path: String,
)

data class DetailSpec(
    val endpoint: String? = null,
    val bodyPath: String = "",
    val urlPath: String = "",
    val commentsEndpoint: String? = null,
    val commentAuthorPath: String = "author.name",
    val commentBodyPath: String = "body",
    val commentTimePath: String = "created_at",
    val commitsEndpoint: String? = null,
    val changesEndpoint: String? = null,
    val fields: List<DetailField> = emptyList(),
    val actions: List<AdapterAction> = emptyList(),
)

data class DashboardWidget(
    val id: String,
    val type: WidgetType,
    val title: String,
    val tint: Tint = Tint.Blue,
    val glyph: String = "layers",
    val endpoint: String? = null,
    val pages: Int = 1,
    // stat_grid
    val metrics: List<StatMetric> = emptyList(),
    // progress_ring
    val currentPath: String = "",
    val targetPath: String = "",
    val unit: String = "",
    val subtitlePath: String = "",
    // live_status
    val activePath: String = "",
    val activeTitlePath: String = "",
    val inactiveText: String = "Idle",
    val activeActionId: String? = null,
    val inactiveActionId: String? = null,
    /** When the running thing started (ISO date); live_status then counts up from it. */
    val startedAtPath: String = "",
    /** live_status: the stop button needs a press-and-hold instead of a tap. */
    val holdToStop: Boolean = false,
    /** share_card: commits to read, filled from the profile at [endpoint] (e.g. "/users/{{login}}/events"). */
    val itemsEndpoint: String = "",
    /** share_card: the commit message, where AI co-author trailers are found. */
    val messagePath: String = "",
    // action_grid
    val actionRefs: List<DashboardActionRef> = emptyList(),
    // sentence_bar
    val prefix: String = "Show",
    val paramKey: String = "filter",
    val choices: List<String> = emptyList(),
    // list / grouped_list
    val itemsPath: String = "",
    val titlePath: String = "title",
    val badgePath: String = "",
    val metaPath: String = "",
    val groupPath: String = "",
    val excludePath: String = "",
    val excludeValues: List<String> = emptyList(),
    val searchable: Boolean = false,
    val rowActions: List<AdapterRowAction> = emptyList(),
    val primaryAction: AdapterAction? = null,
    val detail: DetailSpec? = null,
    // note_card
    val body: String = "",
    // heatmap_chart optional baseline map
    val seedData: Map<String, Int> = emptyMap(),
    // bar_chart: sum this field per bar (blank counts items), "day" or "week" bars,
    // "rolling" (ending today) or "thisWeek" (Monday to Sunday), how many bars, and a goal line per bar
    val valuePath: String = "",
    val bucket: String = "day",
    val range: String = "rolling",
    val count: Int = 0,
    val target: Double? = null,
    // list / grouped_list: "week" groups rows by the week of metaPath; badge values mapped to a tint
    val groupBy: String = "",
    val badgeTints: Map<String, Tint> = emptyMap(),
)

data class AdapterTab(
    val id: String,
    val label: String,
    val endpoint: String = "",
    val widgets: List<DashboardWidget> = emptyList(),
)

/**
 * Something the workspace asks you for besides a token, like your GitHub username. Endpoints use it
 * as {{input.key}}. Kept on this device with the token.
 */
data class AdapterInput(val key: String, val label: String, val hint: String = "")

data class WorkspaceAdapter(
    val id: String,
    val name: String,
    val subtitle: String,
    val glyph: String = "layers",
    val tint: Tint = Tint.Blue,
    val baseUrl: String,
    val allowedDomains: List<String>,
    val authKind: AuthKind = AuthKind.BEARER,
    val authHeader: String = "Authorization",
    val authPrefix: String = "Bearer ",
    val tokenHint: String = "Paste your access token",
    val tabs: List<AdapterTab> = emptyList(),
    val globalActions: List<AdapterAction> = emptyList(),
    /** Works without a token too; a token only adds more (private repos, higher limits). */
    val tokenOptional: Boolean = false,
    val inputs: List<AdapterInput> = emptyList(),
) {
    fun actionById(actionId: String?): AdapterAction? {
        if (actionId.isNullOrBlank()) return null
        return globalActions.firstOrNull { it.id == actionId }
            ?: tabs.flatMap { it.widgets }.firstNotNullOfOrNull { w ->
                w.primaryAction?.takeIf { it.id == actionId }
                    ?: w.detail?.actions?.firstOrNull { it.id == actionId }
            }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("format", FORMAT)
        put("version", 2)
        put("id", id)
        put("name", name)
        put("subtitle", subtitle)
        put("glyph", glyph)
        put("tint", tint.name)
        put("baseUrl", baseUrl)
        put("allowedDomains", JSONArray(allowedDomains))
        put("authKind", authKind.key)
        put("authHeader", authHeader)
        put("authPrefix", authPrefix)
        put("tokenHint", tokenHint)
        if (tokenOptional) put("tokenOptional", true)
        if (inputs.isNotEmpty()) put("inputs", JSONArray(inputs.map { JSONObject().put("key", it.key).put("label", it.label).put("hint", it.hint) }))
        put("tabs", JSONArray(tabs.map { t ->
            JSONObject().apply {
                put("id", t.id)
                put("label", t.label)
                put("endpoint", t.endpoint)
                put("widgets", JSONArray(t.widgets.map(::encodeWidget)))
            }
        }))
        put("globalActions", JSONArray(globalActions.map(::encodeAction)))
    }

    companion object {
        const val FORMAT = "openshortcuts-adapter"
        const val EXTENSION = "osadapter"

        private fun encodeAction(a: AdapterAction) = JSONObject().apply {
            put("id", a.id)
            put("title", a.title)
            put("method", a.method)
            put("endpoint", a.endpoint)
            a.bodyTemplate?.let { put("bodyTemplate", it) }
            put("tint", a.tint.name)
            put("glyph", a.glyph)
            a.confirmMessage?.let { put("confirmMessage", it) }
            put("params", JSONArray(a.params.map { p ->
                JSONObject().apply {
                    put("key", p.key)
                    put("label", p.label)
                    put("default", p.default)
                    put("placeholder", p.placeholder)
                    if (p.kind != "text") put("kind", p.kind)
                    p.optionsEndpoint?.let { put("optionsEndpoint", it) }
                    if (p.optionsPath != "data") put("optionsPath", p.optionsPath)
                    if (p.optionLabelPath != "name") put("optionLabelPath", p.optionLabelPath)
                    if (p.optionValuePath != "id") put("optionValuePath", p.optionValuePath)
                    put("choices", JSONArray(p.choices))
                }
            }))
        }

        private fun encodeRowAction(r: AdapterRowAction) = JSONObject().apply {
            put("label", r.label)
            put("glyph", r.glyph)
            put("tint", r.tint.name)
            put("swipe", r.swipe)
            put("method", r.method)
            put("endpoint", r.endpoint)
            r.bodyTemplate?.let { put("bodyTemplate", it) }
        }

        private fun encodeWidget(w: DashboardWidget) = JSONObject().apply {
            put("id", w.id)
            put("type", w.type.key)
            put("title", w.title)
            put("tint", w.tint.name)
            put("glyph", w.glyph)
            w.endpoint?.let { put("endpoint", it) }
            if (w.pages > 1) put("pages", w.pages)
            if (w.metrics.isNotEmpty()) put("metrics", JSONArray(w.metrics.map { m ->
                JSONObject().apply {
                    put("label", m.label); put("valuePath", m.valuePath)
                    put("suffix", m.suffix); put("targetPath", m.targetPath); put("tint", m.tint.name)
                }
            }))
            if (w.currentPath.isNotEmpty()) put("currentPath", w.currentPath)
            if (w.targetPath.isNotEmpty()) put("targetPath", w.targetPath)
            if (w.unit.isNotEmpty()) put("unit", w.unit)
            if (w.subtitlePath.isNotEmpty()) put("subtitlePath", w.subtitlePath)
            if (w.activePath.isNotEmpty()) put("activePath", w.activePath)
            if (w.activeTitlePath.isNotEmpty()) put("activeTitlePath", w.activeTitlePath)
            if (w.inactiveText != "Idle") put("inactiveText", w.inactiveText)
            w.activeActionId?.let { put("activeActionId", it) }
            w.inactiveActionId?.let { put("inactiveActionId", it) }
            if (w.startedAtPath.isNotEmpty()) put("startedAtPath", w.startedAtPath)
            if (w.holdToStop) put("holdToStop", true)
            if (w.itemsEndpoint.isNotEmpty()) put("itemsEndpoint", w.itemsEndpoint)
            if (w.messagePath.isNotEmpty()) put("messagePath", w.messagePath)
            if (w.actionRefs.isNotEmpty()) put("actionRefs", JSONArray(w.actionRefs.map { r ->
                JSONObject().apply {
                    put("title", r.title); put("glyph", r.glyph); put("tint", r.tint.name)
                    r.actionId?.let { put("actionId", it) }
                    r.shortcutName?.let { put("shortcutName", it) }
                }
            }))
            if (w.prefix != "Show") put("prefix", w.prefix)
            if (w.paramKey != "filter") put("paramKey", w.paramKey)
            if (w.choices.isNotEmpty()) put("choices", JSONArray(w.choices))
            if (w.itemsPath.isNotEmpty()) put("itemsPath", w.itemsPath)
            if (w.titlePath != "title") put("titlePath", w.titlePath)
            if (w.badgePath.isNotEmpty()) put("badgePath", w.badgePath)
            if (w.metaPath.isNotEmpty()) put("metaPath", w.metaPath)
            if (w.valuePath.isNotEmpty()) put("valuePath", w.valuePath)
            if (w.bucket != "day") put("bucket", w.bucket)
            if (w.range != "rolling") put("range", w.range)
            if (w.count > 0) put("count", w.count)
            w.target?.let { put("target", it) }
            if (w.groupBy.isNotEmpty()) put("groupBy", w.groupBy)
            if (w.badgeTints.isNotEmpty()) put("badgeTints", JSONObject().apply { w.badgeTints.forEach { (k, v) -> put(k, v.name) } })
            if (w.groupPath.isNotEmpty()) put("groupPath", w.groupPath)
            if (w.excludePath.isNotEmpty()) put("excludePath", w.excludePath)
            if (w.excludeValues.isNotEmpty()) put("excludeValues", JSONArray(w.excludeValues))
            if (w.searchable) put("searchable", true)
            if (w.rowActions.isNotEmpty()) put("rowActions", JSONArray(w.rowActions.map(::encodeRowAction)))
            w.primaryAction?.let { put("primaryAction", encodeAction(it)) }
            w.detail?.let { d ->
                put("detail", JSONObject().apply {
                    d.endpoint?.let { put("endpoint", it) }
                    if (d.bodyPath.isNotEmpty()) put("bodyPath", d.bodyPath)
                    if (d.urlPath.isNotEmpty()) put("urlPath", d.urlPath)
                    d.commentsEndpoint?.let { put("commentsEndpoint", it) }
                    if (d.commentAuthorPath != "author.name") put("commentAuthorPath", d.commentAuthorPath)
                    if (d.commentBodyPath != "body") put("commentBodyPath", d.commentBodyPath)
                    if (d.commentTimePath != "created_at") put("commentTimePath", d.commentTimePath)
                    d.commitsEndpoint?.let { put("commitsEndpoint", it) }
                    d.changesEndpoint?.let { put("changesEndpoint", it) }
                    if (d.fields.isNotEmpty()) put("fields", JSONArray(d.fields.map { f ->
                        JSONObject().apply { put("label", f.label); put("path", f.path) }
                    }))
                    if (d.actions.isNotEmpty()) put("actions", JSONArray(d.actions.map(::encodeAction)))
                })
            }
            if (w.body.isNotEmpty()) put("body", w.body)
            if (w.seedData.isNotEmpty()) put("seedData", JSONObject(w.seedData as Map<*, *>))
        }

        private fun decodeAction(o: JSONObject): AdapterAction {
            val paramsArr = o.optJSONArray("params") ?: JSONArray()
            val params = (0 until paramsArr.length()).map { i ->
                val p = paramsArr.getJSONObject(i)
                val ch = p.optJSONArray("choices") ?: JSONArray()
                AdapterParam(
                    key = p.getString("key"),
                    label = p.optString("label", p.getString("key")),
                    default = p.optString("default", ""),
                    placeholder = p.optString("placeholder", ""),
                    kind = p.optString("kind", "text"),
                    choices = (0 until ch.length()).map { ch.getString(it) },
                    optionsEndpoint = if (p.has("optionsEndpoint") && !p.isNull("optionsEndpoint")) p.getString("optionsEndpoint") else null,
                    optionsPath = p.optString("optionsPath", "data"),
                    optionLabelPath = p.optString("optionLabelPath", "name"),
                    optionValuePath = p.optString("optionValuePath", "id"),
                )
            }
            return AdapterAction(
                id = o.getString("id"),
                title = o.getString("title"),
                method = o.optString("method", "POST"),
                endpoint = o.getString("endpoint"),
                bodyTemplate = if (o.has("bodyTemplate") && !o.isNull("bodyTemplate")) o.getString("bodyTemplate") else null,
                params = params,
                tint = runCatching { Tint.valueOf(o.optString("tint", "Blue")) }.getOrDefault(Tint.Blue),
                glyph = o.optString("glyph", "bolt"),
                confirmMessage = if (o.has("confirmMessage") && !o.isNull("confirmMessage")) o.getString("confirmMessage") else null,
            )
        }

        private fun decodeRowAction(r: JSONObject) = AdapterRowAction(
            label = r.getString("label"),
            glyph = r.optString("glyph", "check"),
            tint = runCatching { Tint.valueOf(r.optString("tint", "Green")) }.getOrDefault(Tint.Green),
            swipe = r.optString("swipe", "none"),
            method = r.optString("method", "POST"),
            endpoint = r.getString("endpoint"),
            bodyTemplate = if (r.has("bodyTemplate") && !r.isNull("bodyTemplate")) r.getString("bodyTemplate") else null,
        )

        private fun decodeWidget(o: JSONObject): DashboardWidget {
            val mArr = o.optJSONArray("metrics") ?: JSONArray()
            val metrics = (0 until mArr.length()).map { i ->
                val m = mArr.getJSONObject(i)
                StatMetric(
                    label = m.getString("label"),
                    valuePath = m.getString("valuePath"),
                    suffix = m.optString("suffix", ""),
                    targetPath = m.optString("targetPath", ""),
                    tint = runCatching { Tint.valueOf(m.optString("tint", "Blue")) }.getOrDefault(Tint.Blue),
                )
            }
            val refsArr = o.optJSONArray("actionRefs") ?: JSONArray()
            val refs = (0 until refsArr.length()).map { i ->
                val r = refsArr.getJSONObject(i)
                DashboardActionRef(
                    title = r.getString("title"),
                    glyph = r.optString("glyph", "bolt"),
                    tint = runCatching { Tint.valueOf(r.optString("tint", "Blue")) }.getOrDefault(Tint.Blue),
                    actionId = if (r.has("actionId") && !r.isNull("actionId")) r.getString("actionId") else null,
                    shortcutName = if (r.has("shortcutName") && !r.isNull("shortcutName")) r.getString("shortcutName") else null,
                )
            }
            val chArr = o.optJSONArray("choices") ?: JSONArray()
            val exArr = o.optJSONArray("excludeValues") ?: JSONArray()
            val rowsArr = o.optJSONArray("rowActions") ?: JSONArray()
            val dObj = o.optJSONObject("detail")
            val detail = dObj?.let { d ->
                val fArr = d.optJSONArray("fields") ?: JSONArray()
                val aArr = d.optJSONArray("actions") ?: JSONArray()
                DetailSpec(
                    endpoint = if (d.has("endpoint") && !d.isNull("endpoint")) d.getString("endpoint") else null,
                    bodyPath = d.optString("bodyPath", ""),
                    urlPath = d.optString("urlPath", ""),
                    commentsEndpoint = if (d.has("commentsEndpoint") && !d.isNull("commentsEndpoint")) d.getString("commentsEndpoint") else null,
                    commentAuthorPath = d.optString("commentAuthorPath", "author.name"),
                    commentBodyPath = d.optString("commentBodyPath", "body"),
                    commentTimePath = d.optString("commentTimePath", "created_at"),
                    commitsEndpoint = if (d.has("commitsEndpoint") && !d.isNull("commitsEndpoint")) d.getString("commitsEndpoint") else null,
                    changesEndpoint = if (d.has("changesEndpoint") && !d.isNull("changesEndpoint")) d.getString("changesEndpoint") else null,
                    fields = (0 until fArr.length()).map { i ->
                        val f = fArr.getJSONObject(i)
                        DetailField(f.getString("label"), f.getString("path"))
                    },
                    actions = (0 until aArr.length()).map { decodeAction(aArr.getJSONObject(it)) },
                )
            }
            val sObj = o.optJSONObject("seedData")
            val seedMap = if (sObj != null) {
                sObj.keys().asSequence().associateWith { k -> sObj.optInt(k, 0) }.filterValues { it > 0 }
            } else emptyMap()
            return DashboardWidget(
                id = o.optString("id", java.util.UUID.randomUUID().toString().take(8)),
                type = WidgetType.from(o.optString("type", "list")),
                title = o.optString("title", ""),
                tint = runCatching { Tint.valueOf(o.optString("tint", "Blue")) }.getOrDefault(Tint.Blue),
                glyph = o.optString("glyph", "layers"),
                endpoint = if (o.has("endpoint") && !o.isNull("endpoint")) o.getString("endpoint") else null,
                pages = o.optInt("pages", 1).coerceIn(1, 10),
                metrics = metrics,
                currentPath = o.optString("currentPath", ""),
                targetPath = o.optString("targetPath", ""),
                unit = o.optString("unit", ""),
                subtitlePath = o.optString("subtitlePath", ""),
                activePath = o.optString("activePath", ""),
                activeTitlePath = o.optString("activeTitlePath", ""),
                inactiveText = o.optString("inactiveText", "Idle"),
                activeActionId = if (o.has("activeActionId") && !o.isNull("activeActionId")) o.getString("activeActionId") else null,
                inactiveActionId = if (o.has("inactiveActionId") && !o.isNull("inactiveActionId")) o.getString("inactiveActionId") else null,
                startedAtPath = o.optString("startedAtPath", ""),
                holdToStop = o.optBoolean("holdToStop", false),
                itemsEndpoint = o.optString("itemsEndpoint", ""),
                messagePath = o.optString("messagePath", ""),
                actionRefs = refs,
                prefix = o.optString("prefix", "Show"),
                paramKey = o.optString("paramKey", "filter"),
                choices = (0 until chArr.length()).map { chArr.getString(it) },
                itemsPath = o.optString("itemsPath", ""),
                titlePath = o.optString("titlePath", "title"),
                badgePath = o.optString("badgePath", ""),
                metaPath = o.optString("metaPath", ""),
                groupPath = o.optString("groupPath", ""),
                excludePath = o.optString("excludePath", ""),
                excludeValues = (0 until exArr.length()).map { exArr.getString(it) },
                searchable = o.optBoolean("searchable", false),
                rowActions = (0 until rowsArr.length()).map { decodeRowAction(rowsArr.getJSONObject(it)) },
                primaryAction = if (o.has("primaryAction") && !o.isNull("primaryAction")) decodeAction(o.getJSONObject("primaryAction")) else null,
                detail = detail,
                body = o.optString("body", ""),
                seedData = seedMap,
                valuePath = o.optString("valuePath", ""),
                bucket = o.optString("bucket", "day").takeIf { it == "week" } ?: "day",
                range = o.optString("range", "rolling").takeIf { it == "thisWeek" } ?: "rolling",
                count = o.optInt("count", 0).coerceIn(0, 31),
                groupBy = o.optString("groupBy", "").takeIf { it == "week" } ?: "",
                badgeTints = o.optJSONObject("badgeTints")?.let { bt ->
                    bt.keys().asSequence().mapNotNull { k ->
                        runCatching { Tint.valueOf(bt.getString(k)) }.getOrNull()?.let { k to it }
                    }.toMap()
                }.orEmpty(),
                target = if (o.has("target") && !o.isNull("target")) o.optDouble("target").takeIf { !it.isNaN() && it > 0 } else null,
            )
        }

        fun fromJson(o: JSONObject): WorkspaceAdapter {
            if (o.optString("format") != FORMAT) error("Not a valid .osadapter file.")
            if (o.optInt("version", 1) > 2) error("This adapter requires a newer version of OpenShortcuts.")
            val domainsArr = o.optJSONArray("allowedDomains") ?: JSONArray()
            val domains = (0 until domainsArr.length()).map { domainsArr.getString(it).lowercase().trim() }.filter { it.isNotEmpty() }
            val tabsArr = o.optJSONArray("tabs") ?: JSONArray()
            val tabs = (0 until tabsArr.length()).map { i ->
                val t = tabsArr.getJSONObject(i)
                val tabEndpoint = t.optString("endpoint", "")
                val wArr = t.optJSONArray("widgets")
                val widgets = if (wArr != null && wArr.length() > 0) {
                    (0 until wArr.length()).map { decodeWidget(wArr.getJSONObject(it)) }
                } else {
                    // v1 compatibility: wrap legacy tab fields into a LIST widget
                    val rowsArr = t.optJSONArray("rowActions") ?: JSONArray()
                    listOf(
                        DashboardWidget(
                            id = "${t.getString("id")}_list",
                            type = WidgetType.LIST,
                            title = t.getString("label"),
                            endpoint = tabEndpoint,
                            itemsPath = t.optString("itemsPath", ""),
                            titlePath = t.optString("titlePath", "title"),
                            subtitlePath = t.optString("subtitlePath", ""),
                            badgePath = t.optString("badgePath", ""),
                            metaPath = t.optString("metaPath", ""),
                            rowActions = (0 until rowsArr.length()).map { decodeRowAction(rowsArr.getJSONObject(it)) },
                            primaryAction = if (t.has("primaryAction") && !t.isNull("primaryAction")) decodeAction(t.getJSONObject("primaryAction")) else null,
                        ),
                    )
                }
                AdapterTab(
                    id = t.getString("id"),
                    label = t.getString("label"),
                    endpoint = tabEndpoint,
                    widgets = widgets,
                )
            }
            val globalsArr = o.optJSONArray("globalActions") ?: JSONArray()
            val globals = (0 until globalsArr.length()).map { decodeAction(globalsArr.getJSONObject(it)) }
            return WorkspaceAdapter(
                id = o.getString("id").trim(),
                name = o.getString("name").trim(),
                subtitle = o.optString("subtitle", ""),
                glyph = o.optString("glyph", "layers"),
                tint = runCatching { Tint.valueOf(o.optString("tint", "Blue")) }.getOrDefault(Tint.Blue),
                baseUrl = o.getString("baseUrl").trimEnd('/'),
                allowedDomains = domains,
                authKind = AuthKind.from(o.optString("authKind", "bearer")),
                authHeader = o.optString("authHeader", "Authorization"),
                authPrefix = o.optString("authPrefix", "Bearer "),
                tokenHint = o.optString("tokenHint", "Paste your access token"),
                tabs = tabs,
                globalActions = globals,
                tokenOptional = o.optBoolean("tokenOptional", false),
                inputs = o.optJSONArray("inputs")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        val io = a.optJSONObject(i) ?: return@mapNotNull null
                        val key = io.optString("key").trim().takeIf { it.matches(Regex("[A-Za-z0-9_]{1,40}")) } ?: return@mapNotNull null
                        AdapterInput(key, io.optString("label", key), io.optString("hint", ""))
                    }
                }.orEmpty(),
            )
        }
    }
}

/** Starting points for a new workspace: built-in templates and a blank one for your own API. */
object WorkspaceTemplates {
    val keys = listOf("demo", "stracode", "github", "gitlab")

    fun load(ctx: android.content.Context, key: String): WorkspaceAdapter? = runCatching {
        WorkspaceAdapter.fromJson(JSONObject(ctx.assets.open("workspaces/$key.osadapter").bufferedReader().readText()))
    }.getOrNull()

    /** "github", then "github-2", "github-3"… so adding a template twice never overwrites the first. */
    fun uniqueId(base: String, taken: Set<String>): String {
        val clean = base.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "workspace" }
        if (clean !in taken) return clean
        var n = 2
        while ("$clean-$n" in taken) n++
        return "$clean-$n"
    }

    fun instantiate(template: WorkspaceAdapter, existing: List<WorkspaceAdapter>, baseUrl: String = template.baseUrl): WorkspaceAdapter {
        val id = uniqueId(template.id, existing.map { it.id }.toSet())
        // "GitHub", "GitHub 2"… counting only earlier copies, not other workspaces that merely start with the name.
        val copy = Regex("^" + Regex.escape(template.name) + "( \\d+)?$")
        val sameName = existing.count { copy.matches(it.name) }
        return template.copy(
            id = id,
            name = if (sameName == 0) template.name else "${template.name} ${sameName + 1}",
            baseUrl = baseUrl.trim().trimEnd('/'),
        )
    }

    fun blank(name: String, baseUrl: String, auth: AuthKind, existing: List<WorkspaceAdapter>): WorkspaceAdapter = WorkspaceAdapter(
        id = uniqueId(name, existing.map { it.id }.toSet()),
        name = name.trim(),
        subtitle = "",
        glyph = "layers",
        tint = Tint.Blue,
        baseUrl = baseUrl.trim().trimEnd('/'),
        allowedDomains = emptyList(),
        authKind = auth,
        authHeader = if (auth == AuthKind.PRIVATE_TOKEN) "PRIVATE-TOKEN" else "Authorization",
        authPrefix = if (auth == AuthKind.BEARER) "Bearer " else "",
        tabs = listOf(AdapterTab(id = "overview", label = "Overview")),
    )

    /** A usable API address: http(s), a host, no spaces. */
    fun isValidBaseUrl(url: String): Boolean {
        val u = url.trim()
        if (!(u.startsWith("https://") || u.startsWith("http://")) || u.any { it.isWhitespace() }) return false
        return runCatching { !java.net.URI(u).host.isNullOrBlank() }.getOrDefault(false)
    }
}
