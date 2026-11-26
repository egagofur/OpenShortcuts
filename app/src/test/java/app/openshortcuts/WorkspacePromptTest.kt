package app.openshortcuts

import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.WorkspaceAdapter
import app.openshortcuts.model.WorkspacePrompt
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePromptTest {
    @Test
    fun promptsCarryTheFormatAndNeverAskForTokens() {
        listOf(WorkspacePrompt.chat(), WorkspacePrompt.agent()).forEach { p ->
            assertTrue("openshortcuts-adapter" in p)
            assertTrue("one question at a time" in p.lowercase())
            assertTrue("never" in p.lowercase() && "token" in p.lowercase())
            listOf("target_bar", "bar_chart", "breakdown", "grouped_list", "live_status", "globalActions").forEach { assertTrue(it, it in p) }
        }
        println("chat prompt: ${WorkspacePrompt.chat().length} chars, url-encoded ${java.net.URLEncoder.encode(WorkspacePrompt.chat(), "UTF-8").length}")
    }

    @Test
    fun promptExampleIsAValidWorkspace() {
        val p = WorkspacePrompt.chat()
        val start = p.indexOf("## A small complete example")
        val json = p.substring(p.indexOf('{', start), p.indexOf("\n\n## Rules"))
        val a = WorkspaceAdapter.fromJson(JSONObject(json))
        assertEquals("tracker", a.id)
        assertEquals(2, a.tabs.size)
        assertTrue(a.actionById("start")!!.params.isNotEmpty())
    }

    @Test
    fun pasteFindsJsonInsideAnAiReply() {
        val reply = "Here is your file:\n```json\n{\"version\":2,\"id\":\"tracker\",\"name\":\"Tracker\",\"baseUrl\":\"https://api.example.com\",\"tabs\":[]}\n```\nCopy it into the app."
        val a = AdapterStore.parseText(reply, setOf("tracker"))
        assertEquals("tracker-2", a.id)
        assertEquals("Tracker", a.name)
    }

    @Test
    fun pasteExplainsWhatIsWrong() {
        val noJson = runCatching { AdapterStore.parseText("hello", emptySet()) }.exceptionOrNull()?.message.orEmpty()
        assertTrue(noJson.startsWith("Copy the whole JSON"))
        val prompt = runCatching { AdapterStore.parseText(WorkspacePrompt.chat(), emptySet()) }.exceptionOrNull()?.message.orEmpty()
        assertTrue(prompt, prompt.startsWith("That isn't a workspace") || prompt.startsWith("The JSON isn't complete"))
        val cut = runCatching { AdapterStore.parseText("{\"id\": \"x\", \"name\": {", emptySet()) }.exceptionOrNull()?.message.orEmpty()
        assertFalse(cut.isBlank())
        val noUrl = runCatching { AdapterStore.parseText("{\"id\":\"x\",\"name\":\"X\",\"baseUrl\":\"\"}", emptySet()) }.exceptionOrNull()?.message.orEmpty()
        assertTrue(noUrl, "baseUrl" in noUrl || "Not a valid" in noUrl || noUrl.isNotBlank())
    }
}
