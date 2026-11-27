package app.openshortcuts.engine

import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.ShipCommit
import app.openshortcuts.model.WorkspaceAdapter
import org.json.JSONObject

/** What a share_card reads from its workspace: who you are and your recent commits. */
/**
 * [complete] is false when the source had more commits than it hands out (we read the newest [MAX_PAGES]
 * pages): counts reaching back past the oldest one are then shown as "at least".
 */
data class ShipData(val handle: String, val commits: List<ShipCommit>, val complete: Boolean = true)

private const val MAX_PAGES = 5

object ShipSource {
    /**
     * Reads the profile at the widget's endpoint, then the commits at its itemsEndpoint with the
     * profile's fields filled in (so "{{login}}" becomes your username). Read-only GETs.
     */
    suspend fun load(adapter: WorkspaceAdapter, widget: DashboardWidget): Result<ShipData> = runCatching {
        val profileEp = widget.endpoint.orEmpty()
        val profile = if (profileEp.isBlank()) null
            else AdapterEngine.fetchEndpoints(adapter, setOf(profileEp))[profileEp]?.getOrThrow()
        val handle = AdapterEngine.extractString(profile, widget.titlePath)
        val itemsEp = AdapterEngine.interpolateUrl(widget.itemsEndpoint, profile as? JSONObject, emptyMap())
        if (widget.itemsEndpoint.isBlank()) return@runCatching ShipData(handle, emptyList())
        // Newest first, up to five pages of 100: enough for sixteen weeks of most people's commits.
        val items = ArrayList<JSONObject>()
        var complete = true
        for (page in 1..MAX_PAGES) {
            val ep = if (page == 1) itemsEp else itemsEp + (if ('?' in itemsEp) "&" else "?") + "page=$page"
            val res = AdapterEngine.fetchEndpoints(adapter, setOf(ep))[ep] ?: break
            if (res.isFailure) { if (page == 1) res.getOrThrow() else break }
            val got = AdapterEngine.extractList(res.getOrNull(), widget.itemsPath)
            items += got
            if (got.size < 100) break
            if (page == MAX_PAGES) complete = false
        }
        val commits = items.mapNotNull { o ->
            val at = AdapterEngine.parseInstantOrNull(AdapterEngine.extractString(o, widget.metaPath)) ?: return@mapNotNull null
            ShipCommit(
                at = at,
                message = if (widget.messagePath.isBlank()) "" else AdapterEngine.extractString(o, widget.messagePath),
                repo = if (widget.groupPath.isBlank()) "" else AdapterEngine.extractString(o, widget.groupPath),
                count = if (widget.valuePath.isBlank()) 1 else (AdapterEngine.extractNumber(o, widget.valuePath)?.toInt() ?: 1).coerceAtLeast(0),
                // A commit's SHA (GitHub) or an event's id (GitLab), to count each one once.
                id = AdapterEngine.extractString(o, "sha").ifBlank { AdapterEngine.extractString(o, "id") },
            )
        }
        ShipData(handle, commits, complete)
    }
}
