package app.openshortcuts

import app.openshortcuts.model.OpenApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenApiTest {
    @Test
    fun readsGetEndpointsWithSummaryAndTag() {
        val spec = JSONObject("""{"openapi":"3.0.0","paths":{
            "/tasks":{"get":{"summary":"List tasks","tags":["Work"]},"post":{"summary":"Create"}},
            "/me":{"get":{"operationId":"getMe","tags":["Account"]}},
            "/upload":{"post":{"summary":"Upload"}}}}""")
        val list = OpenApi.parse(spec)
        assertEquals(listOf("/me", "/tasks"), list.map { it.path })
        assertEquals("getMe", list[0].summary)
        assertEquals("Work", list[1].tag)
    }

    @Test
    fun swagger2BasePathIsKept() {
        val spec = JSONObject("""{"swagger":"2.0","basePath":"/api/v1","paths":{"/users":{"get":{"summary":"Users"}}}}""")
        assertEquals("/api/v1/users", OpenApi.parse(spec).single().path)
    }
}
