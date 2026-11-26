package app.openshortcuts

import app.openshortcuts.model.Marketplace
import app.openshortcuts.model.WorkspaceAdapter
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketplaceTest {
    private val root = File("../marketplace")

    @Test
    fun everyListingPointsAtAWorkspaceThatParses() {
        val index = Marketplace.parseIndex(File(root, "index.json").readText(), live = false)
        assertTrue(index.listings.isNotEmpty())
        index.listings.forEach { l ->
            val f = File(root, l.file)
            assertTrue("${l.file} exists", f.exists())
            val a = WorkspaceAdapter.fromJson(JSONObject(f.readText()))
            assertEquals("${l.id} matches its file", l.id, a.id)
            assertFalse("${l.id} carries a token", "\"token\":" in f.readText().lowercase())
            assertTrue("${l.id} has a category", l.category in index.categories)
        }
    }

    @Test
    fun listingsCanOnlyPointInsideTheMarketplace() {
        val json = """{"workspaces":[
            {"id":"a","name":"A","file":"workspaces/a.osadapter"},
            {"id":"b","name":"B","file":"../secrets.json"},
            {"id":"c","name":"C","file":"https://evil.example/x.osadapter"}]}"""
        assertEquals(listOf("a"), Marketplace.parseIndex(json, live = true).listings.map { it.id })
    }

    @Test
    fun stracodeNeedsOnlyAUsername() {
        val a = WorkspaceAdapter.fromJson(JSONObject(File(root, "workspaces/stracode.osadapter").readText()))
        assertTrue(a.tokenOptional)
        assertEquals(listOf("username"), a.inputs.map { it.key })
        assertTrue(a.tabs.first().widgets.first().endpoint.orEmpty().contains("{{input.username}}"))
        // The input survives a save and load.
        assertEquals(a.inputs, WorkspaceAdapter.fromJson(a.toJson()).inputs)
    }
}
