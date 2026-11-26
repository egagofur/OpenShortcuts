package app.openshortcuts.model

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One workspace in the marketplace, as its index card describes it. */
data class Listing(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val glyph: String,
    val tint: Tint,
    val category: String,
    /** What you'll be asked for, e.g. "GitHub username"; blank when nothing. */
    val needs: String,
    val file: String,
    val featured: Boolean,
)

data class MarketIndex(val categories: List<String>, val listings: List<Listing>, val live: Boolean)

/**
 * Workspaces anyone can add in one tap. The catalog lives in the app's public GitHub repo
 * (marketplace/index.json and marketplace/workspaces/), so a new workspace ships by pushing a file,
 * not by updating the app. The same folder is bundled into the app, so the store works offline.
 * Only these two places are read; a workspace never carries a token.
 */
object Marketplace {
    private const val REMOTE = "https://raw.githubusercontent.com/egagofur/OpenShortcuts/main/marketplace/"
    private const val MAX_BYTES = 512 * 1024

    fun parseIndex(text: String, live: Boolean): MarketIndex {
        val o = JSONObject(text)
        val cats = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val arr = o.getJSONArray("workspaces")
        val listings = (0 until arr.length()).mapNotNull { i ->
            val w = arr.optJSONObject(i) ?: return@mapNotNull null
            val file = w.optString("file")
            // A listing may only point at a workspace file inside the marketplace folder.
            if (!file.matches(Regex("workspaces/[a-z0-9_-]+\\.osadapter"))) return@mapNotNull null
            Listing(
                id = w.getString("id"), name = w.getString("name"), author = w.optString("author", "Community"),
                description = w.optString("description"), glyph = w.optString("glyph", "layers"),
                tint = runCatching { Tint.valueOf(w.optString("tint", "Blue")) }.getOrDefault(Tint.Blue),
                category = w.optString("category", "Other"), needs = w.optString("needs"), file = file,
                featured = w.optBoolean("featured", false),
            )
        }
        val allCats = (cats + listings.map { it.category }).distinct()
        return MarketIndex(allCats, listings, live)
    }

    private fun bundled(ctx: Context, path: String): String = ctx.assets.open(path).bufferedReader().use { it.readText() }

    private fun fetch(path: String): String? = runCatching {
        val conn = (URL(REMOTE + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000; readTimeout = 8_000
            setRequestProperty("User-Agent", "OpenShortcuts-Marketplace/1.0")
        }
        try {
            if (conn.responseCode != 200) return@runCatching null
            val bytes = conn.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
            if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
        } finally { conn.disconnect() }
    }.getOrNull()

    /** The latest catalog from GitHub, else the last one fetched, else the one bundled with the app. */
    suspend fun load(ctx: Context): MarketIndex = withContext(Dispatchers.IO) {
        val saved = File(ctx.filesDir, "market_index.json")
        fetch("index.json")?.let { text ->
            runCatching { parseIndex(text, live = true) }.getOrNull()?.also { saved.writeText(text) }
        } ?: saved.takeIf { it.exists() }?.let { f -> runCatching { parseIndex(f.readText(), live = false) }.getOrNull() }
        ?: parseIndex(bundled(ctx, "index.json"), live = false)
    }

    /** Downloads (or reads the bundled copy of) a listing's workspace, ready to add. */
    suspend fun workspace(ctx: Context, listing: Listing): Result<WorkspaceAdapter> = withContext(Dispatchers.IO) {
        runCatching {
            val text = fetch(listing.file) ?: runCatching { bundled(ctx, listing.file) }.getOrNull()
                ?: error("Couldn't download ${listing.name}. Check your connection and try again.")
            val adapter = runCatching { WorkspaceAdapter.fromJson(JSONObject(text)) }.getOrElse { error("${listing.name} isn't a workspace this version can read. Update the app.") }
            if (!WorkspaceTemplates.isValidBaseUrl(adapter.baseUrl) && !adapter.baseUrl.startsWith("demo://")) error("${listing.name} has an invalid address.")
            adapter
        }
    }

    /** The host a workspace talks to, for the "Talks to" line before you add it. */
    fun host(adapter: WorkspaceAdapter): String =
        if (adapter.baseUrl.startsWith("demo://")) "Nothing, it runs on the phone" else runCatching { java.net.URI(adapter.baseUrl).host }.getOrNull() ?: adapter.baseUrl
}
