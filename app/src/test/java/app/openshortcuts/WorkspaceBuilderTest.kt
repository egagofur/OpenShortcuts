package app.openshortcuts

import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.engine.DemoApi
import app.openshortcuts.model.AuthKind
import app.openshortcuts.model.WidgetType
import app.openshortcuts.model.WorkspaceAdapter
import app.openshortcuts.model.WorkspaceTemplates
import app.openshortcuts.ui.reorderTarget
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

class WorkspaceBuilderTest {
    private fun template(key: String) = WorkspaceAdapter.fromJson(JSONObject(File("../marketplace/workspaces/$key.osadapter").readText()))

    @Test
    fun templatesParseAndHaveNoPersonalHosts() {
        WorkspaceTemplates.keys.forEach { key ->
            val a = template(key)
            assertEquals(key, a.id)
            assertTrue("$key has tabs", a.tabs.isNotEmpty() && a.tabs.all { it.widgets.isNotEmpty() })
            val text = File("../marketplace/workspaces/$key.osadapter").readText().lowercase()
            assertFalse("$key carries a token", "\"token\":" in text)
            assertTrue("$key points at a public host", a.baseUrl in setOf("demo://local", "https://api.github.com", "https://gitlab.com/api/v4"))
        }
    }

    @Test
    fun uniqueIdsNeverOverwrite() {
        assertEquals("github", WorkspaceTemplates.uniqueId("github", emptySet()))
        assertEquals("github-2", WorkspaceTemplates.uniqueId("github", setOf("github")))
        assertEquals("github-3", WorkspaceTemplates.uniqueId("github", setOf("github", "github-2")))
        assertEquals("team-tracker", WorkspaceTemplates.uniqueId("  Team Tracker! ", emptySet()))
        assertEquals("workspace", WorkspaceTemplates.uniqueId("???", emptySet()))
    }

    @Test
    fun instantiateRenamesDuplicates() {
        val t = template("github")
        val first = WorkspaceTemplates.instantiate(t, emptyList())
        val second = WorkspaceTemplates.instantiate(t, listOf(first))
        assertEquals("GitHub", first.name)
        assertEquals("github-2", second.id)
        assertEquals("GitHub 2", second.name)
        val other = WorkspaceTemplates.instantiate(t, listOf(first.copy(id = "x", name = "GitHub Enterprise")))
        assertEquals("GitHub", other.name)
    }

    @Test
    fun blankWorkspaceStartsWithOneEmptyTab() {
        val a = WorkspaceTemplates.blank("Team tracker", "https://api.example.com/v1/", AuthKind.PRIVATE_TOKEN, emptyList())
        assertEquals("https://api.example.com/v1", a.baseUrl)
        assertEquals("PRIVATE-TOKEN", a.authHeader)
        assertEquals("", a.authPrefix)
        assertEquals(1, a.tabs.size)
        assertTrue(a.tabs.single().widgets.isEmpty())
        val roundTrip = WorkspaceAdapter.fromJson(a.toJson())
        assertEquals(a.tabs.single().label, roundTrip.tabs.single().label)
    }

    @Test
    fun baseUrlValidation() {
        assertTrue(WorkspaceTemplates.isValidBaseUrl("https://api.example.com"))
        assertTrue(WorkspaceTemplates.isValidBaseUrl("http://192.168.1.5:8080/api"))
        assertFalse(WorkspaceTemplates.isValidBaseUrl("https://"))
        assertFalse(WorkspaceTemplates.isValidBaseUrl("api.example.com"))
        assertFalse(WorkspaceTemplates.isValidBaseUrl("https://api example.com"))
    }

    @Test
    fun demoDataFillsThisWeekAndAddsUp() {
        val thu = LocalDate.of(2026, 11, 26)
        val entries = DemoApi.entries(thu)
        assertEquals(entries.map { it.toString() }, DemoApi.entries(thu).map { it.toString() })
        assertTrue(entries.first().getString("started_at").matches(Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.*""")))
        val days = entries.mapNotNull { AdapterEngine.toLocalDate(it.getString("started_at")) }
        assertTrue(days.all { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY && !it.isAfter(thu) })
        assertTrue(days.any { it == thu })
        val summary = DemoApi.respond("GET", "/summary", null, thu).body as JSONObject
        val week = entries.filter { !AdapterEngine.toLocalDate(it.getString("started_at"))!!.isBefore(thu.with(DayOfWeek.MONDAY)) }
            .sumOf { it.getDouble("hours") }
        assertEquals(Math.round(week * 10) / 10.0, summary.getJSONObject("data").getDouble("week_hours"), 0.001)
    }

    @Test
    fun demoTimerSkipsDoubleStart() = runBlocking {
        val demo = template("demo")
        val start = demo.actionById("start_timer")!!
        DemoApi.respond("POST", "/timer/stop", null)
        assertEquals("Start Timer done", AdapterEngine.runCall(demo, start, mapOf("description" to "Write docs")).message)
        assertEquals("Skipped Start Timer: it's already running.", AdapterEngine.runCall(demo, start, emptyMap()).message)
        DemoApi.respond("POST", "/timer/stop", null)
        Unit
    }

    @Test
    fun demoWidgetsReadTheirData() {
        val demo = template("demo")
        val days = demo.tabs.first().widgets.first { it.id == "days" }
        assertEquals(WidgetType.BAR_CHART, days.type)
        val body = DemoApi.respond("GET", days.endpoint!!, null).body
        assertTrue(AdapterEngine.extractList(body, days.itemsPath).isNotEmpty())
    }

    @Test
    fun reorderLandsOnTheNearestSlot() {
        assertEquals(0, reorderTarget(0, 10f, 100f, 4))
        assertEquals(1, reorderTarget(0, 60f, 100f, 4))
        assertEquals(3, reorderTarget(0, 900f, 100f, 4))
        assertEquals(0, reorderTarget(2, -260f, 100f, 4))
        assertEquals(2, reorderTarget(2, 0f, 0f, 4))
    }
}
