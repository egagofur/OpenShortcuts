package app.openshortcuts.engine

import app.openshortcuts.model.AdapterAction
import app.openshortcuts.model.AdapterRowAction
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.AuthKind
import app.openshortcuts.model.WorkspaceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WorkspaceItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val meta: String,
    val group: String,
    val raw: JSONObject,
    val value: Double? = null,
)

data class AdapterResult(
    val ok: Boolean,
    val statusCode: Int,
    val message: String,
    val body: Any? = null,
)

object AdapterEngine {
    // At most this many requests in flight per refresh, however many endpoints and pages a tab asks for.
    private const val MAX_PARALLEL = 6

    @Volatile private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun isOnline(context: android.content.Context? = appContext): Boolean {
        val cm = context?.getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) return true
        // A VPN can stay "connected" in airplane mode with nothing underneath it.
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@any false
            !c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) &&
                c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private enum class Escape { NONE, URL, JSON }

    suspend fun fetchEndpoints(
        adapter: WorkspaceAdapter,
        endpoints: Set<String>,
        pageCounts: Map<String, Int> = emptyMap(),
    ): Map<String, Result<Any?>> = withContext(Dispatchers.IO) {
        val gate = Semaphore(MAX_PARALLEL)
        coroutineScope {
            endpoints.filter { it.isNotBlank() }.map { ep ->
                async {
                    val pages = (pageCounts[ep] ?: 1).coerceIn(1, 10)
                    if (pages == 1) {
                        val res = gate.withPermit { request(adapter, "GET", ep, null) }
                        ep to if (res.ok) Result.success(res.body) else Result.failure(IllegalStateException(res.message))
                    } else {
                        val pageResults = (1..pages).map { p ->
                            async {
                                val sep = if ('?' in ep) "&" else "?"
                                gate.withPermit { request(adapter, "GET", "$ep${sep}page=$p", null) }
                            }
                        }.awaitAll()
                        val firstErr = pageResults.firstOrNull { !it.ok }
                        if (pageResults.all { !it.ok } && firstErr != null) {
                            ep to Result.failure(IllegalStateException(firstErr.message))
                        } else {
                            val merged = JSONArray()
                            pageResults.filter { it.ok }.forEach { r ->
                                val arr = r.body as? JSONArray
                                if (arr != null) {
                                    for (i in 0 until arr.length()) merged.put(arr.get(i))
                                }
                            }
                            ep to Result.success<Any?>(merged)
                        }
                    }
                }
            }.awaitAll().toMap()
        }
    }

    suspend fun fetchDetail(
        adapter: WorkspaceAdapter,
        endpointTemplate: String,
        item: JSONObject,
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val ep = interpolateUrl(endpointTemplate, item, emptyMap())
            val res = request(adapter, "GET", ep, null)
            if (!res.ok) error(res.message)
            when (val b = res.body) {
                is JSONObject -> b.optJSONObject("data") ?: b
                else -> item
            }
        }
    }

    suspend fun fetchComments(
        adapter: WorkspaceAdapter,
        endpointTemplate: String,
        item: JSONObject,
    ): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        runCatching {
            val ep = interpolateUrl(endpointTemplate, item, emptyMap())
            val res = request(adapter, "GET", ep, null)
            if (!res.ok) error(res.message)
            extractList(res.body, if ((res.body as? JSONObject)?.has("data") == true) "data" else "")
                .filterNot { it.optBoolean("system", false) }
        }
    }

    suspend fun fetchArray(
        adapter: WorkspaceAdapter,
        endpointTemplate: String,
        item: JSONObject,
        arrayPath: String = "",
    ): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        runCatching {
            val ep = interpolateUrl(endpointTemplate, item, emptyMap())
            val res = request(adapter, "GET", ep, null)
            if (!res.ok) error(res.message)
            val fallbackPath = if (arrayPath.isNotBlank()) arrayPath
            else when {
                (res.body as? JSONObject)?.has("changes") == true -> "changes"
                (res.body as? JSONObject)?.has("data") == true -> "data"
                else -> ""
            }
            extractList(res.body, fallbackPath)
        }
    }

    suspend fun runRowAction(
        adapter: WorkspaceAdapter,
        action: AdapterRowAction,
        item: JSONObject,
    ): AdapterResult = withContext(Dispatchers.IO) {
        val ep = interpolateUrl(action.endpoint, item, emptyMap())
        val body = action.bodyTemplate?.let { interpolateJson(it, item, emptyMap()) }
        request(adapter, action.method, ep, body)
    }

    suspend fun runAction(
        adapter: WorkspaceAdapter,
        action: AdapterAction,
        params: Map<String, String>,
        item: JSONObject? = null,
    ): AdapterResult = withContext(Dispatchers.IO) {
        val ep = interpolateUrl(action.endpoint, item, params)
        val body = action.bodyTemplate?.let { interpolateJson(it, item, params) }
        request(adapter, action.method, ep, body)
    }

    private fun request(
        adapter: WorkspaceAdapter,
        method: String,
        endpoint: String,
        jsonBody: String?,
    ): AdapterResult = withInputs(adapter, endpoint).let { endpoint -> if (DemoApi.isDemo(AdapterStore.getEffectiveBaseUrl(adapter))) {
        DemoApi.respond(method, endpoint, jsonBody)
    } else if (!isOnline()) {
        AdapterResult(false, 0, "You're offline. Connect to the internet and try again.")
    } else try {
        send(adapter, method, endpoint, jsonBody)
    } catch (e: java.net.UnknownHostException) {
        AdapterResult(false, 0, "You're offline, or ${e.message ?: "the server"} can't be reached.")
    } catch (e: java.net.SocketTimeoutException) {
        AdapterResult(false, 0, "The server took too long to answer.")
    } catch (e: java.io.IOException) {
        AdapterResult(false, 0, e.message ?: "Couldn't reach the server.")
    } catch (e: IllegalArgumentException) {
        AdapterResult(false, 0, "Invalid address: $endpoint")
    } }

    private val inputPlaceholder = Regex("""\{\{\s*input\.([A-Za-z0-9_]+)\s*\}\}""")

    /** Fills {{input.key}} with what you entered for this workspace (percent-encoded, so it stays one URL part). */
    private fun withInputs(adapter: WorkspaceAdapter, endpoint: String): String =
        if ("{{" !in endpoint) endpoint else inputPlaceholder.replace(endpoint) { m ->
            URLEncoder.encode(AdapterStore.getInput(adapter.id, m.groupValues[1]), "UTF-8").replace("+", "%20")
        }

    /**
     * The last body and ETag per URL. A GET sends If-None-Match; a 304 reuses the body here, so an
     * unchanged response costs no download (and, when signed in, no GitHub rate limit).
     */
    private val etags = object : LinkedHashMap<String, Pair<String, Any?>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, Any?>>?) = size > 32
    }

    private fun send(
        adapter: WorkspaceAdapter,
        method: String,
        endpoint: String,
        jsonBody: String?,
    ): AdapterResult {
        val base = AdapterStore.getEffectiveBaseUrl(adapter)
        val fullUrl = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) endpoint
        else "$base/${endpoint.trimStart('/')}"

        val host = runCatching { URI(fullUrl).host?.lowercase().orEmpty() }.getOrDefault("")
        if (adapter.allowedDomains.isNotEmpty() && adapter.allowedDomains.none { host == it || host.endsWith(".$it") }) {
            return AdapterResult(false, 0, "Blocked domain: $host is not in allowedDomains.")
        }

        // The token only ever goes to the adapter's own server or a domain it explicitly allows,
        // so an absolute URL in an edited adapter can't carry it somewhere else.
        val baseHost = runCatching { URI(base).host?.lowercase().orEmpty() }.getOrDefault("")
        val trusted = host.isNotEmpty() && (host == baseHost || adapter.allowedDomains.any { host == it || host.endsWith(".$it") })

        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method.uppercase()
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "OpenShortcuts-Workspace/2.0")
            if (method.uppercase() == "GET") synchronized(etags) { etags[fullUrl] }?.let { setRequestProperty("If-None-Match", it.first) }
            if (adapter.authKind != AuthKind.NONE && trusted) {
                val token = AdapterStore.getToken(adapter.id)
                if (token.isNotBlank()) {
                    val value = if (adapter.authPrefix.isNotEmpty() && !token.startsWith(adapter.authPrefix)) {
                        adapter.authPrefix + token
                    } else token
                    setRequestProperty(adapter.authHeader, value)
                }
            }
            if (jsonBody != null && method.uppercase() in setOf("POST", "PUT", "PATCH")) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
        }

        val code: Int
        val text: String
        val etag: String?
        val limitLeft: String?
        val limitReset: String?
        try {
            code = conn.responseCode
            etag = conn.getHeaderField("ETag")
            limitLeft = conn.getHeaderField("X-RateLimit-Remaining")
            limitReset = conn.getHeaderField("X-RateLimit-Reset")
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            text = if (code == 304) "" else stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
        if (code == 304) synchronized(etags) { etags[fullUrl] }?.let { return AdapterResult(true, 200, "Not modified", it.second) }
        val parsed = runCatching { if (text.isNotBlank()) JSONTokener(text).nextValue() else null }.getOrNull()
        if (code in 200..299 && method.uppercase() == "GET" && !etag.isNullOrBlank()) synchronized(etags) { etags[fullUrl] = etag to parsed }

        // Out of requests for now: say when they come back, and that a token raises the limit.
        if ((code == 403 || code == 429) && limitLeft == "0") {
            val at = limitReset?.toLongOrNull()?.let {
                java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()))
            }
            val noToken = adapter.authKind == AuthKind.NONE || AdapterStore.getToken(adapter.id).isBlank()
            return AdapterResult(false, code,
                "Hit the API's hourly limit" + (at?.let { ". It resets at $it" } ?: "") + "." + if (noToken) " Adding a token in settings raises it." else "", parsed)
        }

        if (code !in 200..299) {
            val errMsg = (parsed as? JSONObject)?.optString("message")?.takeIf { it.isNotBlank() }
                ?: (parsed as? JSONObject)?.optString("error")?.takeIf { it.isNotBlank() }
                ?: "HTTP $code"
            return AdapterResult(false, code, errMsg, parsed)
        }
        val okMsg = (parsed as? JSONObject)?.optString("message")?.takeIf { it.isNotBlank() } ?: "Done ($code)"
        return AdapterResult(true, code, okMsg, parsed)
    }

    private val placeholder = Regex("""\{\{([^}]+)\}\}""")

    fun interpolate(template: String, item: JSONObject?, params: Map<String, String>): String =
        interpolate(template, item, params, Escape.NONE)

    /** Values go into a URL: each is percent-encoded, so a title with `&` or `/` can't change the request. */
    fun interpolateUrl(template: String, item: JSONObject?, params: Map<String, String>): String =
        interpolate(template, item, params, Escape.URL)

    /** Values go into a JSON body: quotes, backslashes and newlines are escaped so the body stays valid. */
    fun interpolateJson(template: String, item: JSONObject?, params: Map<String, String>): String =
        interpolate(template, item, params, Escape.JSON)

    private fun interpolate(template: String, item: JSONObject?, params: Map<String, String>, escape: Escape): String {
        val now = LocalDate.now()
        val builtIns = mapOf(
            "today" to now.format(DateTimeFormatter.ISO_LOCAL_DATE),
            "month" to now.format(DateTimeFormatter.ofPattern("yyyy-MM")),
            "now_utc" to Instant.now().toString(),
            // A week back plus a day, so "this week" and a 72h hackathon are always covered.
            "since_7d" to now.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE),
            // Sixteen weeks and change: enough for a contribution heatmap.
            "since_120d" to now.minusDays(120).format(DateTimeFormatter.ISO_LOCAL_DATE),
        )
        return placeholder.replace(template) { m ->
            val key = m.groupValues[1].trim()
            // {{input.x}} is filled per workspace just before the request goes out.
            if (key.startsWith("input.") && key !in params) return@replace m.value
            val value = params[key] ?: builtIns[key] ?: item?.let { extractString(it, key) }.orEmpty()
            when (escape) {
                Escape.NONE -> value
                Escape.URL -> URLEncoder.encode(value, "UTF-8").replace("+", "%20")
                Escape.JSON -> JSONObject.quote(value).let { it.substring(1, it.length - 1) }
            }
        }
    }

    fun extractList(root: Any?, path: String): List<JSONObject> {
        if (root == null) return emptyList()
        if (path.isBlank()) {
            return when (root) {
                is JSONArray -> (0 until root.length()).mapNotNull { root.optJSONObject(it) }
                is JSONObject -> listOf(root)
                else -> emptyList()
            }
        }
        val nodes = resolvePath(root, path.split('.'))
        return nodes.flatMap { n ->
            when (n) {
                is JSONArray -> (0 until n.length()).mapNotNull {
                    n.optJSONObject(it) ?: JSONObject().put("title", n.optString(it))
                }
                is JSONObject -> listOf(n)
                else -> emptyList()
            }
        }
    }

    private val templatePart = Regex("""\{\{\s*([^}|]+?)\s*(?:\|\s*([a-z]+)(?::([^}]*))?\s*)?\}\}""")

    /**
     * A field written as a template: "{{first_name}} {{last_name}}", or with a pipe:
     * |upper |lower |trim |date |number |first:20 |default:none.
     */
    fun template(root: Any?, text: String): String = templatePart.replace(text) { m ->
        val v = extractString(root, m.groupValues[1])
        val arg = m.groupValues[3]
        when (m.groupValues[2]) {
            "upper" -> v.uppercase()
            "lower" -> v.lowercase()
            "trim" -> v.trim()
            "date" -> formatIfTimestamp(v)
            "number" -> v.toDoubleOrNull()?.let { d -> if (d % 1.0 == 0.0) "%,d".format(d.toLong()) else "%,.2f".format(d) } ?: v
            "first" -> v.take(arg.toIntOrNull() ?: 40)
            "default" -> v.ifBlank { arg }
            else -> v
        }
    }

    fun extractString(root: Any?, path: String): String {
        if (root == null || path.isBlank()) return ""
        if ("{{" in path) return template(root, path)
        val nodes = resolvePath(root, path.split('.'))
        val first = nodes.firstOrNull { it != null && it != JSONObject.NULL } ?: return ""
        return when (first) {
            is JSONObject -> first.optString("name", first.toString())
            is JSONArray -> "${first.length()} items"
            else -> first.toString()
        }
    }

    fun extractNumber(root: Any?, path: String): Double? =
        extractString(root, path).toDoubleOrNull()

    fun extractBool(root: Any?, path: String): Boolean =
        extractString(root, path).equals("true", ignoreCase = true)

    private val isoTsRegex = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.*$""")
    private val localDateTimeFmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.getDefault())
    private val localDateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())

    fun parseInstantOrNull(raw: String): Instant? {
        if (!isoTsRegex.matches(raw.trim())) return null
        return runCatching { Instant.parse(raw.trim()) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw.trim()).toInstant() }.getOrNull()
    }

    /**
     * First day of each bar: [count] days ending today, Monday to Sunday of this week, or the Mondays
     * of the last [count] weeks. Bars are always consecutive and the same length.
     */
    fun bucketStarts(today: LocalDate, bucket: String, range: String, count: Int): List<LocalDate> {
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        return when {
            bucket == "week" -> (count - 1 downTo 0).map { monday.minusWeeks(it.toLong()) }
            range == "thisWeek" -> (0 until 7).map { monday.plusDays(it.toLong()) }
            else -> (count - 1 downTo 0).map { today.minusDays(it.toLong()) }
        }
    }

    /**
     * A live status widget that starts or stops with this action (a stopwatch's Clock In and Clock Out).
     * Running the action again would do nothing useful, so an automation can skip it.
     */
    fun statusWidgetFor(adapter: WorkspaceAdapter, actionId: String): app.openshortcuts.model.DashboardWidget? =
        adapter.tabs.flatMap { it.widgets }.firstOrNull { w ->
            w.type == app.openshortcuts.model.WidgetType.LIVE_STATUS && !w.endpoint.isNullOrBlank() &&
                (w.inactiveActionId == actionId || w.activeActionId == actionId)
        }

    /** Why the action can be skipped given the widget's current state, or null to go ahead. */
    fun skipReason(widget: app.openshortcuts.model.DashboardWidget, actionId: String, title: String, active: Boolean): String? = when {
        widget.inactiveActionId == actionId && active -> "Skipped $title: it's already running."
        widget.activeActionId == actionId && !active -> "Skipped $title: nothing is running."
        else -> null
    }

    /** Runs a saved workspace action, checking its live status first so a start isn't sent twice. */
    suspend fun runCall(
        adapter: WorkspaceAdapter,
        action: AdapterAction,
        params: Map<String, String>,
    ): AdapterResult {
        statusWidgetFor(adapter, action.id)?.let { w ->
            val res = fetchEndpoints(adapter, setOf(w.endpoint!!))[w.endpoint]
            val body = res?.getOrNull()
            if (body != null) skipReason(w, action.id, action.title, extractBool(body, w.activePath))?.let {
                return AdapterResult(true, 0, it)
            }
        }
        val res = runAction(adapter, action, params)
        return if (res.ok) res.copy(message = "${action.title} done") else res
    }

    /**
     * Totals per group, largest first. Sums [valuePath] (or counts items when blank); beyond [top] groups
     * the rest fold into one "Other" entry so the chart stays readable.
     */
    fun groupTotals(items: List<JSONObject>, groupPath: String, valuePath: String, top: Int): List<Pair<String, Double>> {
        val sums = LinkedHashMap<String, Double>()
        for (item in items) {
            val key = extractString(item, groupPath).ifBlank { "Other" }
            val v = if (valuePath.isBlank()) 1.0 else extractNumber(item, valuePath) ?: 0.0
            sums[key] = (sums[key] ?: 0.0) + v
        }
        val sorted = sums.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value }
        if (top <= 0 || sorted.size <= top) return sorted
        val rest = sorted.drop(top).sumOf { it.second }
        return sorted.take(top) + ("Other" to rest)
    }

    /** "This week", "Last week", or "Week of 9 Nov" for the week (Monday start) that holds [day]. */
    fun weekLabel(day: LocalDate?, today: LocalDate): String {
        if (day == null) return "No date"
        val monday = day.with(java.time.DayOfWeek.MONDAY)
        val thisMonday = today.with(java.time.DayOfWeek.MONDAY)
        return when (monday) {
            thisMonday -> "This week"
            thisMonday.minusWeeks(1) -> "Last week"
            else -> "Week of " + monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        }
    }

    /** API statuses often arrive as "NEED APPROVAL"; show them as "Need approval". Short codes stay as they are. */
    fun prettyBadge(raw: String): String {
        val t = raw.trim().replace('_', ' ')
        if (t.length <= 3 || t.none { it.isLetter() } || t != t.uppercase()) return t
        return t.lowercase().replaceFirstChar { it.titlecase() }
    }

    /** Sums [valuePath] for the items in each bar, or counts them when [valuePath] is blank. */
    fun bucketTotals(
        items: List<JSONObject>,
        dateField: String,
        valuePath: String,
        starts: List<LocalDate>,
        bucketDays: Int,
    ): List<Double> {
        if (starts.isEmpty()) return emptyList()
        val totals = DoubleArray(starts.size)
        for (item in items) {
            val day = toLocalDate(extractString(item, dateField)) ?: continue
            val offset = java.time.temporal.ChronoUnit.DAYS.between(starts.first(), day)
            if (offset < 0) continue
            val idx = (offset / bucketDays).toInt()
            if (idx >= starts.size) continue
            totals[idx] += if (valuePath.isBlank()) 1.0 else extractNumber(item, valuePath) ?: 0.0
        }
        return totals.toList()
    }

    fun toLocalDate(raw: String): LocalDate? =
        parseInstantOrNull(raw)?.atZone(ZoneId.systemDefault())?.toLocalDate()
            ?: runCatching { LocalDate.parse(raw.trim().take(10)) }.getOrNull()

    fun formatIfTimestamp(raw: String, dateOnly: Boolean = false): String {
        val inst = parseInstantOrNull(raw) ?: return raw
        val zdt = inst.atZone(ZoneId.systemDefault())
        return if (dateOnly) localDateFmt.format(zdt) else localDateTimeFmt.format(zdt)
    }

    private fun resolvePath(current: Any?, parts: List<String>): List<Any?> {
        if (current == null || parts.isEmpty()) return listOf(current)
        val head = parts.first()
        val tail = parts.drop(1)
        val nextValues: List<Any?> = when {
            head == "*" || head == "[]" -> when (current) {
                is JSONArray -> (0 until current.length()).map { current.opt(it) }
                else -> listOf(current)
            }
            head.endsWith("[*]") -> {
                val key = head.removeSuffix("[*]")
                val arr = (current as? JSONObject)?.optJSONArray(key)
                if (arr != null) (0 until arr.length()).map { arr.opt(it) } else emptyList()
            }
            current is JSONObject -> listOf(current.opt(head))
            current is JSONArray -> (0 until current.length()).mapNotNull { current.optJSONObject(it)?.opt(head) }
            else -> emptyList()
        }
        return if (tail.isEmpty()) nextValues else nextValues.flatMap { resolvePath(it, tail) }
    }
}
