package app.openshortcuts

import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.model.WorkspaceAdapter
import app.openshortcuts.model.WorkspaceCall
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.Collections

class WorkspaceCallTest {
    private lateinit var server: HttpServer
    private var running = false
    private val posts = Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    private fun adapter(port: Int) = WorkspaceAdapter.fromJson(
        JSONObject(
            """{"format":"openshortcuts-adapter","version":2,"id":"timer","name":"Timer","baseUrl":"http://127.0.0.1:$port",
            "allowedDomains":[],"authKind":"none",
            "tabs":[{"id":"o","label":"Overview","widgets":[{"id":"live","type":"live_status","title":"Stopwatch",
              "endpoint":"/status","activePath":"data.running","activeActionId":"stop","inactiveActionId":"start"}]}],
            "globalActions":[
              {"id":"start","title":"Clock In","method":"POST","endpoint":"/start",
               "bodyTemplate":"{\"projectId\": {{projectId}}, \"description\": \"{{description}}\"}",
               "params":[{"key":"projectId","label":"Project"},{"key":"description","label":"Task"}]},
              {"id":"stop","title":"Clock Out","method":"POST","endpoint":"/stop"}]}""",
        ),
    )

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val body = ex.requestBody.readBytes().decodeToString()
            val out = if (ex.requestURI.path == "/status") """{"data":{"running":$running}}"""
            else { posts += ex.requestURI.path to body; """{"ok":true}""" }
            val bytes = out.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun encodesAndDecodes() {
        val call = WorkspaceCall("timer", "start", mapOf("projectId" to "200", "description" to "Fix \"login\""), mapOf("projectId" to "Dashboard"))
        assertEquals(call, WorkspaceCall.decode(call.encode()))
        assertNull(WorkspaceCall.decode(""))
        assertNull(WorkspaceCall.decode("""{"adapter":"","action":"x"}"""))
    }

    @Test
    fun skipsStartWhileRunning() = runBlocking {
        running = true
        val a = adapter(server.address.port)
        val res = AdapterEngine.runCall(a, a.actionById("start")!!, mapOf("projectId" to "200", "description" to "x"))
        assertTrue(res.ok)
        assertEquals("Skipped Clock In: it's already running.", res.message)
        assertTrue(posts.isEmpty())
    }

    @Test
    fun startsWithFieldsWhenIdle() = runBlocking {
        running = false
        val a = adapter(server.address.port)
        val res = AdapterEngine.runCall(a, a.actionById("start")!!, mapOf("projectId" to "200", "description" to "Fix \"login\""))
        assertTrue(res.message, res.ok)
        assertEquals("Clock In done", res.message)
        assertEquals("/start", posts.single().first)
        val sent = JSONObject(posts.single().second)
        assertEquals(200, sent.getInt("projectId"))
        assertEquals("Fix \"login\"", sent.getString("description"))
    }

    @Test
    fun skipsStopWhenIdle() = runBlocking {
        running = false
        val a = adapter(server.address.port)
        val res = AdapterEngine.runCall(a, a.actionById("stop")!!, emptyMap())
        assertEquals("Skipped Clock Out: nothing is running.", res.message)
        assertTrue(posts.isEmpty())
    }
}
