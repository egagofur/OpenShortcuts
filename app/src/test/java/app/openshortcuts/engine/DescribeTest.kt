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
        assertEquals(6, s.actions.size)                       // unknown action dropped
        assertEquals(1, d.imported.unknownActions)
        val reply = s.actions.first { it.type == ActionType.REPLY_WHATSAPP }
        assertFalse("bogus" in reply.params)                  // unknown params dropped
        assertEquals("Lagi tidur ya, Ma", reply.p("body"))
        assertEquals("s1", s.actions.first { it.type == ActionType.RUN_SHORTCUT }.p("shortcut"))  // name -> id
        val a = d.automation!!
        assertEquals(TriggerKind.WhatsAppMessage, a.kind)
        assertEquals("Mama", a.text)
        assertEquals(23 * 60, a.windowStart)
        assertEquals(s.id, a.shortcutId)
        assertFalse(a.enabled)                                 // never switched on without review
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
}
