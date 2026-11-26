package app.openshortcuts

import app.openshortcuts.model.WidgetType
import app.openshortcuts.model.WorkspaceAdapter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdapterModelTest {
    @Test
    fun v1AdapterAutoMigratesToListWidget() {
        val v1 = JSONObject(
            """
            {
              "format": "openshortcuts-adapter",
              "version": 1,
              "id": "legacy",
              "name": "Legacy",
              "baseUrl": "https://example.com/api",
              "allowedDomains": ["example.com"],
              "tabs": [
                { "id": "items", "label": "Items", "endpoint": "/items", "itemsPath": "data", "titlePath": "name" }
              ]
            }
            """.trimIndent(),
        )
        val adapter = WorkspaceAdapter.fromJson(v1)
        assertEquals(1, adapter.tabs.size)
        assertEquals(1, adapter.tabs[0].widgets.size)
        assertEquals(WidgetType.LIST, adapter.tabs[0].widgets[0].type)
        assertEquals("data", adapter.tabs[0].widgets[0].itemsPath)
    }

    @Test
    fun v2DashboardRoundTripAndNoSecretsExported() {
        val v2 = JSONObject(
            """
            {
              "format": "openshortcuts-adapter",
              "version": 2,
              "id": "dash",
              "name": "Dashboard",
              "baseUrl": "https://api.example.com",
              "allowedDomains": ["api.example.com"],
              "tabs": [
                {
                  "id": "overview",
                  "label": "Overview",
                  "endpoint": "/summary",
                  "widgets": [
                    {
                      "id": "w1",
                      "type": "stat_grid",
                      "title": "KPIs",
                      "metrics": [{ "label": "Hours", "valuePath": "data.hours", "suffix": "h", "tint": "Blue" }]
                    },
                    {
                      "id": "w2",
                      "type": "list",
                      "title": "Tasks",
                      "endpoint": "/tasks",
                      "itemsPath": "data",
                      "detail": {
                        "endpoint": "/tasks/{{id}}",
                        "bodyPath": "description",
                        "fields": [{ "label": "Status", "path": "status" }]
                      }
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val parsed = WorkspaceAdapter.fromJson(v2)
        val exported = parsed.toJson().toString()
        val reparsed = WorkspaceAdapter.fromJson(JSONObject(exported))
        assertEquals(2, reparsed.tabs[0].widgets.size)
        assertEquals(WidgetType.STAT_GRID, reparsed.tabs[0].widgets[0].type)
        assertNotNull(reparsed.tabs[0].widgets[1].detail)
        assertEquals("/tasks/{{id}}", reparsed.tabs[0].widgets[1].detail?.endpoint)
        assertFalse(exported.contains("secret", ignoreCase = true))
    }
}

class AdapterInterpolateTest {
    private val item = org.json.JSONObject()
        .put("id", 42)
        .put("title", "Fix \"login\" & redirect\nnow")
        .put("project", org.json.JSONObject().put("path", "dot/web app"))

    @org.junit.Test fun urlValuesAreEncoded() {
        val ep = app.openshortcuts.engine.AdapterEngine.interpolateUrl("/projects/{{project.path}}/items/{{id}}?q={{title}}", item, emptyMap())
        org.junit.Assert.assertEquals("/projects/dot%2Fweb%20app/items/42?q=Fix%20%22login%22%20%26%20redirect%0Anow", ep)
    }

    @org.junit.Test fun jsonValuesStayValid() {
        val body = app.openshortcuts.engine.AdapterEngine.interpolateJson("""{"id": {{id}}, "note": "{{title}}"}""", item, emptyMap())
        val parsed = org.json.JSONObject(body)
        org.junit.Assert.assertEquals(42, parsed.getInt("id"))
        org.junit.Assert.assertEquals("Fix \"login\" & redirect\nnow", parsed.getString("note"))
    }
}

class AdapterBucketTest {
    private val wed = java.time.LocalDate.of(2026, 11, 25)

    @Test
    fun thisWeekRunsMondayToSunday() {
        val starts = app.openshortcuts.engine.AdapterEngine.bucketStarts(wed, "day", "thisWeek", 0)
        assertEquals(7, starts.size)
        assertEquals(java.time.LocalDate.of(2026, 11, 23), starts.first())
        assertEquals(java.time.LocalDate.of(2026, 11, 29), starts.last())
    }

    @Test
    fun weeksEndWithThisWeek() {
        val starts = app.openshortcuts.engine.AdapterEngine.bucketStarts(wed, "week", "rolling", 3)
        assertEquals(
            listOf("2026-11-09", "2026-11-16", "2026-11-23"),
            starts.map { it.toString() },
        )
    }

    @Test
    fun sumsValuesPerWeekAndSkipsOutOfRange() {
        val items = listOf(
            JSONObject("""{"start_date":"2026-11-24","duration":4.5}"""),
            JSONObject("""{"start_date":"2026-11-29","duration":"1.5"}"""),
            JSONObject("""{"start_date":"2026-11-16","duration":8}"""),
            JSONObject("""{"start_date":"2026-11-01","duration":99}"""),
            JSONObject("""{"start_date":"","duration":5}"""),
        )
        val starts = app.openshortcuts.engine.AdapterEngine.bucketStarts(wed, "week", "rolling", 2)
        val totals = app.openshortcuts.engine.AdapterEngine.bucketTotals(items, "start_date", "duration", starts, 7)
        assertEquals(listOf(8.0, 6.0), totals)
    }

    @Test
    fun countsItemsWhenNoValuePath() {
        val items = listOf(
            JSONObject("""{"created_at":"2026-11-25T03:00:00Z"}"""),
            JSONObject("""{"created_at":"2026-11-25T04:00:00Z"}"""),
            JSONObject("""{"created_at":"2026-11-24T04:00:00Z"}"""),
        )
        val starts = app.openshortcuts.engine.AdapterEngine.bucketStarts(wed, "day", "rolling", 2)
        val totals = app.openshortcuts.engine.AdapterEngine.bucketTotals(items, "created_at", "", starts, 1)
        assertEquals(listOf(1.0, 2.0), totals)
    }

    @Test
    fun barChartFieldsRoundTrip() {
        val json = """{"format":"openshortcuts-adapter","version":2,"id":"t","name":"T","baseUrl":"https://x.test","allowedDomains":[],
            "tabs":[{"id":"a","label":"A","widgets":[{"id":"w","type":"bar_chart","title":"Hours","valuePath":"duration",
            "bucket":"week","count":5,"target":40,"unit":"h"}]}]}"""
        val w = WorkspaceAdapter.fromJson(JSONObject(json)).tabs.first().widgets.first()
        assertEquals(WidgetType.BAR_CHART, w.type)
        assertEquals("duration", w.valuePath)
        assertEquals("week", w.bucket)
        assertEquals(5, w.count)
        assertEquals(40.0, w.target!!, 0.0)
    }
}

class AdapterListLabelTest {
    private val thu = java.time.LocalDate.of(2026, 11, 26)
    private val engine = app.openshortcuts.engine.AdapterEngine

    @Test
    fun weekLabels() {
        assertEquals("This week", engine.weekLabel(java.time.LocalDate.of(2026, 11, 23), thu))
        assertEquals("This week", engine.weekLabel(java.time.LocalDate.of(2026, 11, 29), thu))
        assertEquals("Last week", engine.weekLabel(java.time.LocalDate.of(2026, 11, 22), thu))
        assertEquals("No date", engine.weekLabel(null, thu))
        assert(engine.weekLabel(java.time.LocalDate.of(2026, 11, 12), thu).startsWith("Week of 9"))
    }

    @Test
    fun badgesReadNaturally() {
        assertEquals("Need approval", engine.prettyBadge("NEED APPROVAL"))
        assertEquals("Approved", engine.prettyBadge("APPROVED"))
        assertEquals("In review", engine.prettyBadge("IN_REVIEW"))
        assertEquals("mergeable", engine.prettyBadge("mergeable"))
        assertEquals("WIP", engine.prettyBadge("WIP"))
        assertEquals("v2.1", engine.prettyBadge("v2.1"))
    }

    @Test
    fun listFieldsRoundTrip() {
        val json = """{"format":"openshortcuts-adapter","version":2,"id":"t","name":"T","baseUrl":"https://x.test","allowedDomains":[],
            "tabs":[{"id":"a","label":"A","widgets":[{"id":"w","type":"grouped_list","title":"Logs","groupBy":"week",
            "valuePath":"duration","unit":"h","badgeTints":{"APPROVED":"Green","NEED APPROVAL":"Orange","bogus":"Nope"}}]}]}"""
        val a = WorkspaceAdapter.fromJson(JSONObject(json))
        val w = a.tabs.first().widgets.first()
        assertEquals("week", w.groupBy)
        assertEquals(mapOf("APPROVED" to app.openshortcuts.model.Tint.Green, "NEED APPROVAL" to app.openshortcuts.model.Tint.Orange), w.badgeTints)
        val again = WorkspaceAdapter.fromJson(JSONObject(a.toJson().toString())).tabs.first().widgets.first()
        assertEquals(w.badgeTints, again.badgeTints)
        assertEquals("week", again.groupBy)
    }
}

class AdapterGroupTotalsTest {
    @Test
    fun ranksGroupsAndFoldsTheRest() {
        val items = listOf(
            """{"p":{"name":"A"},"h":2}""", """{"p":{"name":"B"},"h":5}""", """{"p":{"name":"A"},"h":4}""",
            """{"p":{"name":"C"},"h":1}""", """{"p":{"name":"D"},"h":0.5}""", """{"p":{"name":"E"},"h":0}""",
        ).map { JSONObject(it) }
        val totals = app.openshortcuts.engine.AdapterEngine.groupTotals(items, "p.name", "h", 2)
        assertEquals(listOf("A" to 6.0, "B" to 5.0, "Other" to 1.5), totals)
    }

    @Test
    fun countsWhenNoValuePath() {
        val items = listOf("""{"s":"x"}""", """{"s":"y"}""", """{"s":"x"}""").map { JSONObject(it) }
        assertEquals(listOf("x" to 2.0, "y" to 1.0), app.openshortcuts.engine.AdapterEngine.groupTotals(items, "s", "", 5))
    }
}
