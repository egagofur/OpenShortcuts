package app.openshortcuts.engine

import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.ShipCommit
import app.openshortcuts.model.WorkspaceAdapter
import org.json.JSONObject

/** What a share_card reads from its workspace: who you are and your recent commits. */
data class ShipData(val handle: String, val commits: List<ShipCommit>)

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
        val raw = AdapterEngine.fetchEndpoints(adapter, setOf(itemsEp))[itemsEp]?.getOrThrow()
        val commits = AdapterEngine.extractList(raw, widget.itemsPath).mapNotNull { o ->
            val at = AdapterEngine.parseInstantOrNull(AdapterEngine.extractString(o, widget.metaPath)) ?: return@mapNotNull null
            ShipCommit(
                at = at,
                message = if (widget.messagePath.isBlank()) "" else AdapterEngine.extractString(o, widget.messagePath),
                repo = if (widget.groupPath.isBlank()) "" else AdapterEngine.extractString(o, widget.groupPath),
                count = if (widget.valuePath.isBlank()) 1 else (AdapterEngine.extractNumber(o, widget.valuePath)?.toInt() ?: 1).coerceAtLeast(0),
            )
        }
        ShipData(handle, commits)
    }
}
