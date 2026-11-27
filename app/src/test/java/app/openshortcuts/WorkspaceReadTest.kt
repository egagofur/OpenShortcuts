package app.openshortcuts

import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.RunLog
import app.openshortcuts.model.RunRecord
import app.openshortcuts.model.RunStatus
import app.openshortcuts.model.RunStep
import app.openshortcuts.model.StatMetric
import app.openshortcuts.model.WidgetType
import app.openshortcuts.model.WorkspaceRead
import app.openshortcuts.model.WorkspaceReads
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceReadTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun readsWhatEachWidgetShows() {
        val summary = JSONObject("""{"data":{"week":34.8,"target":40}}""")
        val bar = DashboardWidget("w", WidgetType.TARGET_BAR, "This week", metrics = listOf(StatMetric("This week", "data.week", targetPath = "data.target")))
        assertEquals(listOf("This week", "This week (% of goal)"), WorkspaceReads.outputs(bar).map { it.label })
        assertEquals("34.8", WorkspaceReads.value(bar, summary, "metric:0"))
        assertEquals("87", WorkspaceReads.value(bar, summary, "percent:0").substringBefore('.'))

        val prs = JSONObject("""{"items":[{"title":"Fix login"},{"title":"Add tests"}]}""")
        val list = DashboardWidget("l", WidgetType.LIST, "Open PRs", itemsPath = "items", titlePath = "title")
        assertEquals("2", WorkspaceReads.value(list, prs, "count"))
        assertEquals("Fix login", WorkspaceReads.value(list, prs, "first"))
        assertEquals("Fix login\nAdd tests", WorkspaceReads.value(list, prs, "all"))

        val timer = JSONObject("""{"data":{"running":true,"title":"Review","started_at":"2026-11-27T09:00:00Z"}}""")
        val live = DashboardWidget("t", WidgetType.LIVE_STATUS, "Timer", activePath = "data.running", activeTitlePath = "data.title", startedAtPath = "data.started_at")
        val now = Instant.parse("2026-11-27T10:30:00Z")
        assertEquals("yes", WorkspaceReads.value(live, timer, "active", now, utc))
        assertEquals("90", WorkspaceReads.value(live, timer, "minutes", now, utc))
    }

    @Test
    fun readAndRunRecordRoundTrip() {
        val r = WorkspaceRead("dotify", "week", "metric:0", "This week")
        assertEquals(r, WorkspaceRead.decode(r.encode()))
        val rec = RunRecord("1", "s", "Sleep Mode", "At 23:00", "a", 1000, 42, RunStatus.Error, "No token",
            listOf(RunStep("Get Workspace Data", true, "34.8", 12), RunStep("Send WhatsApp", false, "No token", 3)))
        assertEquals(listOf(rec), RunLog.decode(RunLog.encode(listOf(rec))))
    }
}
