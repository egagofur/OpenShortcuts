package app.openshortcuts.model

import app.openshortcuts.engine.AdapterEngine
import org.json.JSONObject

/** One GET endpoint from an API's OpenAPI (Swagger) description. */
data class ApiEndpoint(val path: String, val summary: String, val tag: String)

/**
 * Reads an API's OpenAPI description so endpoints can be picked from a searchable list instead of typed.
 * JSON descriptions only (OpenAPI 3 and Swagger 2). Found at the workspace's openApiUrl, or at the usual
 * places on its server.
 */
object OpenApi {
    /** Where API descriptions usually live, tried in order. */
    val commonPaths = listOf("/openapi.json", "/swagger.json", "/v3/api-docs", "/api-docs", "/docs/openapi.json", "/swagger/v1/swagger.json")

    private val cache = java.util.concurrent.ConcurrentHashMap<String, List<ApiEndpoint>>()

    fun cached(adapterId: String): List<ApiEndpoint>? = cache[adapterId]

    /** The GET endpoints in [json], sorted by tag then path. */
    fun parse(json: JSONObject): List<ApiEndpoint> {
        val paths = json.optJSONObject("paths") ?: return emptyList()
        // Swagger 2 can put a prefix in basePath; OpenAPI 3 puts it in the server URL, which baseUrl already has.
        val base = json.optString("basePath").trimEnd('/').takeIf { it.isNotBlank() && it != "/" }.orEmpty()
        return paths.keys().asSequence().mapNotNull { p ->
            val get = paths.optJSONObject(p)?.optJSONObject("get") ?: return@mapNotNull null
            ApiEndpoint(
                path = base + p,
                summary = get.optString("summary").ifBlank { get.optString("operationId") }.ifBlank { get.optString("description").take(80) },
                tag = get.optJSONArray("tags")?.optString(0).orEmpty(),
            )
        }.sortedWith(compareBy({ it.tag.lowercase() }, { it.path })).toList()
    }

    /**
     * Loads endpoints from [url] (or the workspace's own, or the usual paths). The request goes through
     * the workspace, so a token is only sent when the docs are on its own server.
     */
    suspend fun load(adapter: WorkspaceAdapter, url: String? = null): Result<List<ApiEndpoint>> {
        val tries = listOfNotNull(url?.trim()?.takeIf { it.isNotEmpty() }, adapter.openApiUrl.takeIf { it.isNotBlank() }) +
            if (url.isNullOrBlank()) commonPaths else emptyList()
        var lastError = "No API description found. Paste the URL of its openapi.json."
        for (t in tries.distinct()) {
            val res = AdapterEngine.fetchEndpoints(adapter, setOf(t))[t] ?: continue
            val body = res.getOrNull()
            if (body is JSONObject && body.has("paths")) {
                val list = parse(body)
                if (list.isNotEmpty()) { cache[adapter.id] = list; return Result.success(list) }
                lastError = "That API description has no GET endpoints."
            } else if (res.isFailure && !url.isNullOrBlank()) {
                lastError = res.exceptionOrNull()?.message ?: lastError
            }
        }
        return Result.failure(IllegalStateException(lastError))
    }
}
