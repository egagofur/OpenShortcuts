package app.openshortcuts

import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.model.WidgetType
import app.openshortcuts.ui.DataSlots
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DataPickerTest {
    private val res = JSONObject("""{"meta":{"page":1},"data":{"items":[{"title":"Fix login","state":"open","created_at":"2026-11-20T10:00:00Z","user":{"login":"ega"},"n":1234}]}}""")

    @Test
    fun guessesTheListAndItsFields() {
        assertEquals("data.items", DataSlots.guessList(res))
        val item = AdapterEngine.extractList(res, "data.items").first()
        val slots = DataSlots.forType(WidgetType.LIST).associateBy { it.key }
        assertEquals("title", DataSlots.guessField(item, slots.getValue("title")))
        assertEquals("user.login", DataSlots.guessField(item, slots.getValue("subtitle")))
        assertEquals("state", DataSlots.guessField(item, slots.getValue("badge")))
        assertEquals("created_at", DataSlots.guessField(item, slots.getValue("date")))
    }

    @Test
    fun templatesMixFieldsAndFormat() {
        val item = AdapterEngine.extractList(res, "data.items").first()
        assertEquals("OPEN · Fix login", AdapterEngine.extractString(item, "{{state|upper}} · {{title}}"))
        assertEquals("1,234", AdapterEngine.extractString(item, "{{n|number}}"))
        assertEquals("none", AdapterEngine.extractString(item, "{{missing|default:none}}"))
        assertEquals("Fix", AdapterEngine.extractString(item, "{{title|first:3}}"))
    }

    @Test
    fun buildsAListFromPickedPaths() {
        val w = DataSlots.build("x", WidgetType.LIST, "PRs", app.openshortcuts.model.Tint.Blue, "/prs",
            mapOf("items" to "data.items", "title" to "title", "badge" to "state"))
        assertEquals("data.items", w.itemsPath)
        assertEquals("title", w.titlePath)
        assertEquals("state", w.badgePath)
    }
}
