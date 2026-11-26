package app.openshortcuts.engine

import app.openshortcuts.model.ActionType
import app.openshortcuts.model.Library
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.TriggerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescribeTest {
    private val lib = Library(shortcuts = listOf(Shortcut(id = "s1", name = "Sleep Mode")))

    private val reply = """
    {"name":"Balas Mama","color":"Green","icon":"quote",
     "actions":[
       {"type":"IF","params":[{"key":"subject","value":"Shortcut Input"},{"key":"op","value":"contains"},{"key":"value","value":"urgent"}]},
       {"type":"CALL","params":[{"key":"number","value":""}]},
       {"type":"OTHERWISE","params":[]},
       {"type":"REPLY_WHATSAPP","params":[{"key":"chat","value":"{Message Chat}"},{"key":"body","value":"Lagi tidur ya, Ma"},{"key":"bogus","value":"x"}]},
       {"type":"END_IF","params":[]},
       {"type":"RUN_SHORTCUT","params":[{"key":"shortcut","value":"Sleep Mode"}]},
       {"type":"NOT_A_REAL_ACTION","params":[]}
     ],
     "automation":{"kind":"WhatsAppMessage","time":"00:00","days":[],"callMinutes":0,"windowStart":"23:00","windowEnd":"05:00","on":true,"level":0,"text":"Mama","warnFirst":false},
     "note":"Calls back if the message says urgent; add Mama's number."}
    """

    @Test fun parsesIntoValidatedShortcutAndDisabledAutomation() {
        val d = Describe.parse(reply, lib)
        val s = d.imported.shortcut
        assertEquals("Balas Mama", s.name)
        assertEquals(6, s.actions.size)
        assertEquals(1, d.imported.unknownActions)
        val reply = s.actions.first { it.type == ActionType.REPLY_WHATSAPP }
        assertFalse("bogus" in reply.params)
        assertEquals("Lagi tidur ya, Ma", reply.p("body"))
        assertEquals("s1", s.actions.first { it.type == ActionType.RUN_SHORTCUT }.p("shortcut"))
        val a = d.automation!!
        assertEquals(TriggerKind.WhatsAppMessage, a.kind)
        assertEquals("Mama", a.sender)
        assertEquals(java.time.LocalTime.of(23, 0), a.windowStart)
        assertEquals(s.id, a.shortcutId)
        assertFalse(a.enabled)
        assertTrue(d.note.contains("urgent"))
    }

    @Test fun noAutomationWhenKindNone() {
        val d = Describe.parse(reply.replace("\"kind\":\"WhatsAppMessage\"", "\"kind\":\"none\""), lib)
        assertNull(d.automation)
    }

    @Test(expected = IllegalStateException::class) fun rejectsEmpty() {
        Describe.parse("""{"name":"x","color":"Blue","icon":"layers","actions":[],"automation":{"kind":"none"},"note":""}""", lib)
    }

    @Test fun promptListsEveryAction() {
        val p = Describe.systemPrompt()
        ActionType.entries.forEach { assertTrue(it.name, p.contains("- ${it.name}:")) }
    }

    @Test fun requestBodyMatchesTheMessagesApi() {
        val body = Describe.requestBody("Turn on Do Not Disturb at 23:00", app.openshortcuts.model.Library())
        org.junit.Assert.assertEquals(Describe.MODEL, body.getString("model"))
        org.junit.Assert.assertEquals("default", body.getString("fallbacks"))
        val system = body.getJSONArray("system").getJSONObject(0)
        org.junit.Assert.assertEquals("ephemeral", system.getJSONObject("cache_control").getString("type"))
        val format = body.getJSONObject("output_config").getJSONObject("format")
        org.junit.Assert.assertEquals("json_schema", format.getString("type"))
        val schema = format.getJSONObject("schema")
        org.junit.Assert.assertEquals(false, schema.getBoolean("additionalProperties"))
        org.junit.Assert.assertTrue(schema.getJSONObject("properties").getJSONObject("actions").getJSONObject("items").has("properties"))
        val msg = body.getJSONArray("messages").getJSONObject(0)
        org.junit.Assert.assertEquals("user", msg.getString("role"))
        org.junit.Assert.assertTrue(msg.getString("content").endsWith("Turn on Do Not Disturb at 23:00"))
        org.json.JSONObject(body.toString())
    }

    @Test fun replyTextSkipsThinkingAndReportsStops() {
        val ok = org.json.JSONObject("""{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\"a\""},{"type":"text","text":":1}"}]}""")
        org.junit.Assert.assertEquals("{\"a\":1}", Describe.replyText(ok))
        val refused = runCatching { Describe.replyText(org.json.JSONObject("""{"stop_reason":"refusal","content":[]}""")) }.exceptionOrNull()?.message
        org.junit.Assert.assertEquals("Claude declined to build that shortcut.", refused)
    }
}
