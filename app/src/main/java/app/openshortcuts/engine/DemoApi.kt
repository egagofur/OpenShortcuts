package app.openshortcuts.engine

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

/**
 * A pretend API for the Demo workspace, answered on the phone with no network. Sample data is built
 * around today's date, so "this week" always has something in it. A demo workspace's base URL starts
 * with [SCHEME]; nothing it does leaves the phone.
 */
object DemoApi {
    const val SCHEME = "demo:"

    @Volatile private var running = false
    @Volatile private var runningTitle = ""
    @Volatile private var runningSince: String? = null

    private val clients = listOf("Northwind", "Bluebird Studio", "Acme Labs", "Internal")
    private val tasks = listOf(
        "Design review for the onboarding flow",
        "Fix the date picker on small screens",
        "Write release notes",
        "Client call and follow-up",
        "Refactor the settings screen",
        "Plan next sprint",
        "Update the pricing page copy",
        "Investigate slow search results",
    )

    fun isDemo(baseUrl: String) = baseUrl.startsWith(SCHEME)

    fun respond(method: String, endpoint: String, body: String?, today: LocalDate = LocalDate.now()): AdapterResult {
        val path = endpoint.substringBefore('?').trimEnd('/').ifEmpty { "/" }.let { if (it.startsWith("/")) it else "/$it" }
        if (method.uppercase() != "GET") return act(path, body)
        val json: Any = when {
            path == "/me" -> JSONObject()
                .put("name", "Alex Rivera").put("username", "alex").put("email", "alex@example.com").put("state", "active")
            path == "/timer" -> JSONObject().put(
                "data",
                JSONObject().put("running", running).put("title", runningTitle.ifBlank { JSONObject.NULL })
                    .put("client", if (running) "Bluebird Studio" else JSONObject.NULL)
                    .put("started_at", runningSince ?: JSONObject.NULL),
            )
            path == "/summary" -> summary(today)
            path == "/entries" -> JSONObject().put("data", JSONArray(entries(today)))
            path.startsWith("/entries/") -> entries(today).firstOrNull { it.optString("id") == path.substringAfterLast('/') }
                ?: return AdapterResult(false, 404, "Not found")
            path == "/tasks" -> JSONArray(taskList(today))
            path == "/activity" -> JSONArray(activity(today))
            path == "/commits" -> JSONArray(commits(today))
            else -> return AdapterResult(false, 404, "The demo has no $path.")
        }
        return AdapterResult(true, 200, "OK", json)
    }

    private fun act(path: String, body: String?): AdapterResult = when (path) {
        "/timer/start" -> {
            running = true
            runningSince = java.time.Instant.now().toString()
            runningTitle = runCatching { JSONObject(body.orEmpty()).optString("description") }.getOrNull().orEmpty().ifBlank { "Demo task" }
            AdapterResult(true, 200, "Timer started (demo)")
        }
        "/timer/stop" -> {
            running = false
            runningTitle = ""
            runningSince = null
            AdapterResult(true, 200, "Timer stopped (demo)")
        }
        else -> AdapterResult(true, 200, "Demo: nothing was sent.")
    }

    /** Weekday entries for the last five weeks up to today; the same date always gives the same entries. */
    fun entries(today: LocalDate): List<JSONObject> {
        val start = today.with(DayOfWeek.MONDAY).minusWeeks(4)
        val out = ArrayList<JSONObject>()
        var d = today
        while (!d.isBefore(start)) {
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) {
                val rnd = Random(d.toEpochDay())
                val morning = 3.0 + rnd.nextInt(0, 16) / 10.0
                val afternoon = 2.5 + rnd.nextInt(0, 20) / 10.0
                listOf(9 to morning, 13 to afternoon).forEachIndexed { i, (hour, hrs) ->
                    val id = "${d.toEpochDay()}$i"
                    val billed = d.isBefore(today.with(DayOfWeek.MONDAY))
                    out += JSONObject()
                        .put("id", id)
                        .put("title", tasks[(rnd.nextInt(tasks.size) + i) % tasks.size])
                        .put("client", JSONObject().put("name", clients[(d.dayOfMonth + i) % clients.size]))
                        .put("status", if (billed) "BILLED" else "UNBILLED")
                        .put("hours", hrs)
                        .put("started_at", d.atTime(hour, 0).atOffset(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .put("note", "Sample entry from the demo workspace.")
                }
            }
            d = d.minusDays(1)
        }
        return out
    }

    private fun summary(today: LocalDate): JSONObject {
        val all = entries(today)
        val monday = today.with(DayOfWeek.MONDAY)
        fun sumSince(from: LocalDate) = all.filter {
            AdapterEngine.toLocalDate(it.optString("started_at"))?.let { day -> !day.isBefore(from) } == true
        }.sumOf { it.optDouble("hours") }
        val week = sumSince(monday)
        val month = sumSince(today.withDayOfMonth(1))
        return JSONObject().put(
            "data",
            JSONObject().put("week_hours", round1(week)).put("week_goal", 40).put("month_hours", round1(month)).put("month_goal", 160),
        )
    }

    private fun taskList(today: LocalDate): List<JSONObject> = listOf(
        Triple("Fix the date picker on small screens", "IN_PROGRESS", "Bluebird Studio"),
        Triple("Design review for the onboarding flow", "IN_REVIEW", "Northwind"),
        Triple("Investigate slow search results", "TODO", "Acme Labs"),
        Triple("Write release notes", "TODO", "Internal"),
        Triple("Update the pricing page copy", "DONE", "Northwind"),
    ).mapIndexed { i, (title, status, client) ->
        JSONObject()
            .put("id", "t${i + 1}")
            .put("title", title)
            .put("status", status)
            .put("project", client)
            .put("updated_at", today.minusDays(i.toLong()).atTime(10, 30).atOffset(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .put("description", "## What\n$title.\n\n**Client:** $client\n\n- Sample task from the demo workspace\n- Edit the dashboard to try your own widgets")
    }

    private fun activity(today: LocalDate): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var d = today.minusDays(364)
        while (!d.isAfter(today)) {
            val rnd = Random(d.toEpochDay() * 31)
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            val n = if (weekend) rnd.nextInt(0, 2) else rnd.nextInt(0, 7)
            repeat(n) { out += JSONObject().put("created_at", d.atTime(9 + it, 0).atOffset(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)) }
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * A week of commits with real-looking times, some crediting an AI pair in a Co-Authored-By
     * trailer, the way Claude Code, Copilot and friends sign theirs.
     */
    fun commits(today: LocalDate, now: java.time.LocalTime = java.time.LocalTime.now()): List<JSONObject> {
        val repos = listOf("alex/openboard", "alex/portfolio", "northwind/checkout")
        val ais = listOf(
            "Claude <noreply@anthropic.com>", "Claude <noreply@anthropic.com>", "Copilot <copilot@github.com>",
            "Gemini <gemini@google.com>", null, null, null,
        )
        val out = ArrayList<JSONObject>()
        var d = today.minusDays(7)
        while (!d.isAfter(today)) {
            val rnd = Random(d.toEpochDay() * 17)
            val n = if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) rnd.nextInt(0, 3) else rnd.nextInt(3, 9)
            repeat(n) { i ->
                val hour = 9 + (i * 11 / n.coerceAtLeast(1)) + rnd.nextInt(0, 2)
                val minute = rnd.nextInt(0, 60)
                val at = d.atTime(hour.coerceAtMost(23), minute)
                if (d == today && at.toLocalTime().isAfter(now)) return@repeat
                val ai = ais[rnd.nextInt(ais.size)]
                out += JSONObject()
                    .put("sha", "%07x".format(rnd.nextInt(0x1000000, 0xFFFFFFF)))
                    .put("message", tasks[rnd.nextInt(tasks.size)] + (if (ai != null) "\n\nCo-Authored-By: $ai" else ""))
                    .put("repo", repos[rnd.nextInt(repos.size)])
                    .put("at", at.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            }
            d = d.plusDays(1)
        }
        return out
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0
}
