package app.openshortcuts.model

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AdapterStore {
    private const val MAX_BYTES = 512 * 1024
    private lateinit var file: File
    // API responses can hold company data; keep them where Android never backs up.
    private lateinit var cacheDir: File
    private lateinit var secrets: SharedPreferences

    private val state = MutableStateFlow<List<WorkspaceAdapter>>(emptyList())
    val adapters: StateFlow<List<WorkspaceAdapter>> = state.asStateFlow()

    val incoming = MutableStateFlow<WorkspaceAdapter?>(null)
    val importError = MutableStateFlow<String?>(null)

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "adapters.json")
        cacheDir = context.noBackupFilesDir
        // Caches written by earlier builds sat in filesDir, which is backed up; drop them.
        context.filesDir.listFiles { f -> f.name.startsWith("adapter_cache_") }?.forEach { it.delete() }
        secrets = context.getSharedPreferences("adapter_secrets", Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(file.readText()) }.getOrNull() ?: JSONArray()
        state.value = (0 until arr.length()).mapNotNull { i ->
            runCatching { WorkspaceAdapter.fromJson(arr.getJSONObject(i)) }.getOrNull()?.let(::renamed)
        }
        // Read saved responses off the main thread now, so even the first open of a workspace
        // has its data on the first frame instead of loading it mid-slide.
        val ids = state.value.map { it.id }
        Thread({ ids.forEach { runCatching { loadCache(it) } } }, "workspace-cache").apply { isDaemon = true; start() }
    }

    /** "Ship Card" became Stracode: workspaces saved before the rename read the new name. */
    private fun renamed(a: WorkspaceAdapter): WorkspaceAdapter {
        val old = setOf("Ship Card", "Make a Ship Card")
        fun fix(t: String) = if (t in old) "Stracode" else t
        if (a.name !in old && a.tabs.none { t -> t.label == "Ship" || t.widgets.any { it.title in old } }) return a
        return a.copy(
            name = fix(a.name),
            subtitle = if (a.name in old) "Your commits as a card to share, like a run" else a.subtitle,
            glyph = if (a.name in old) "laptopRun" else a.glyph,
            tabs = a.tabs.map { t ->
                t.copy(label = if (t.label == "Ship") "Stracode" else t.label, widgets = t.widgets.map { w ->
                    if (w.title in old) w.copy(title = "Stracode", glyph = "laptopRun") else w
                })
            },
        )
    }

    private val fetched = java.util.concurrent.ConcurrentHashMap<String, MutableMap<String, Long>>()

    /** When each of a workspace's endpoints last loaded, for this run of the app. */
    fun fetchedAt(id: String): MutableMap<String, Long> = fetched.getOrPut(id) { java.util.concurrent.ConcurrentHashMap() }

    fun get(id: String): WorkspaceAdapter? = state.value.firstOrNull { it.id == id }

    fun upsert(adapter: WorkspaceAdapter) {
        val next = state.value.filterNot { it.id == adapter.id } + adapter
        state.value = next
        save(next)
    }

    fun delete(id: String) {
        val next = state.value.filterNot { it.id == id }
        state.value = next
        save(next)
        secrets.edit().remove("token_$id").remove("url_$id").apply {
            secrets.all.keys.filter { it.startsWith("input_${id}_") }.forEach { remove(it) }
        }.apply()
        memCache.remove(id)
        fetched.remove(id)
        if (::file.isInitialized) File(cacheDir, "adapter_cache_$id.json").delete()
    }

    /** Responses already read this session, so reopening a workspace shows its data on the first frame. */
    private val memCache = java.util.concurrent.ConcurrentHashMap<String, Map<String, Result<Any?>>>()

    fun cachedInMemory(id: String): Map<String, Result<Any?>> = memCache[id].orEmpty()

    fun loadCache(id: String): Map<String, Result<Any?>> {
        memCache[id]?.let { return it }
        if (!::file.isInitialized) return emptyMap()
        val f = File(cacheDir, "adapter_cache_$id.json")
        val obj = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, Result<Any?>>()
        obj.keys().forEach { ep ->
            val v = obj.opt(ep)
            if (v != null && v != JSONObject.NULL) out[ep] = Result.success(v)
        }
        memCache[id] = out
        return out
    }

    fun saveCache(id: String, cache: Map<String, Result<Any?>>) {
        memCache[id] = cache.filterValues { it.isSuccess }
        if (!::file.isInitialized) return
        val obj = JSONObject()
        cache.forEach { (ep, res) ->
            res.getOrNull()?.let { obj.put(ep, it) }
        }
        val tmp = File(cacheDir, "adapter_cache_$id.json.tmp")
        tmp.writeText(obj.toString())
        tmp.renameTo(File(cacheDir, "adapter_cache_$id.json"))
    }

    // Reads fall back to "nothing saved" before init(), so the engine also runs in plain JVM tests.
    fun getToken(id: String): String = if (!::secrets.isInitialized) "" else secrets.getString("token_$id", "").orEmpty()

    fun setToken(id: String, token: String) {
        secrets.edit().putString("token_$id", token.trim()).apply()
    }

    fun getEffectiveBaseUrl(adapter: WorkspaceAdapter): String =
        (if (::secrets.isInitialized) secrets.getString("url_${adapter.id}", null) else null)?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: adapter.baseUrl

    fun setBaseUrlOverride(id: String, url: String) {
        val clean = url.trim().trimEnd('/')
        if (clean.isEmpty()) secrets.edit().remove("url_$id").apply()
        else secrets.edit().putString("url_$id", clean).apply()
    }

    fun getInput(id: String, key: String): String = if (!::secrets.isInitialized) "" else secrets.getString("input_${id}_$key", "").orEmpty()

    fun setInput(id: String, key: String, value: String) {
        secrets.edit().putString("input_${id}_$key", value.trim()).apply()
    }

    /** Ready to load: signed in (or no sign-in needed) and everything it asks for is filled in. */
    fun hasToken(adapter: WorkspaceAdapter): Boolean =
        (adapter.authKind == AuthKind.NONE || adapter.tokenOptional || getToken(adapter.id).isNotBlank()) &&
            adapter.inputs.all { getInput(adapter.id, it.key).isNotBlank() }

    fun receive(ctx: Context, uri: Uri) {
        runCatching { read(ctx, uri) }
            .onSuccess { incoming.value = it; importError.value = null }
            .onFailure { importError.value = it.message ?: "Couldn't read adapter file." }
    }

    /**
     * A workspace pasted as text, usually an AI's reply: the JSON is taken from between the first "{" and
     * the last "}", so code fences and chatter around it don't matter. A pasted workspace never replaces
     * one you have; it gets its own id.
     */
    fun receiveText(text: String) {
        runCatching { parseText(text, state.value.map { it.id }.toSet()) }
            .onSuccess { incoming.value = it; importError.value = null }
            .onFailure { importError.value = it.message ?: "That doesn't look like a workspace." }
    }

    fun parseText(text: String, takenIds: Set<String>): WorkspaceAdapter {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) error("Copy the whole JSON from the AI's reply, then try again.")
        val body = text.substring(start, end + 1)
        if (body.length > MAX_BYTES) error("That workspace is too large.")
        val json = runCatching { JSONObject(body) }.getOrElse { error("The JSON isn't complete. Ask the AI to send the whole file again.") }
        if (!json.has("format")) json.put("format", WorkspaceAdapter.FORMAT)
        val adapter = runCatching { WorkspaceAdapter.fromJson(json) }
            .getOrElse { error("That isn't a workspace the app can read. Ask the AI to send only the final workspace JSON.") }
        if (adapter.baseUrl.isBlank()) error("The workspace has no baseUrl. Ask the AI to add the API address.")
        return adapter.copy(id = WorkspaceTemplates.uniqueId(adapter.id.ifBlank { adapter.name }, takenIds))
    }

    fun read(ctx: Context, uri: Uri): WorkspaceAdapter {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() <= MAX_BYTES) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: error("Couldn't open that file.")
        if (bytes.size > MAX_BYTES) error("Adapter file is too large.")
        val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse { error("Invalid JSON file.") }
        return WorkspaceAdapter.fromJson(json)
    }

    private fun save(list: List<WorkspaceAdapter>) {
        val tmp = File(file.parentFile, "adapters.json.tmp")
        tmp.writeText(JSONArray(list.map { it.toJson() }).toString())
        tmp.renameTo(file)
    }

    fun exportIntent(ctx: Context, adapter: WorkspaceAdapter): Intent {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val safe = adapter.id.replace(Regex("[^a-zA-Z0-9_-]"), "").ifEmpty { "workspace" }
        val out = File(dir, "$safe.${WorkspaceAdapter.EXTENSION}").apply {
            writeText(adapter.toJson().toString(2))
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "${adapter.name} Workspace Adapter")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Export ${adapter.name}",
        )
    }
}
