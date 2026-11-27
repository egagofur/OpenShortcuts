package app.openshortcuts.model

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One step of a run: what ran, whether it worked, what it gave back (or the error), and how long it took. */
data class RunStep(val title: String, val ok: Boolean, val detail: String, val ms: Long)

enum class RunStatus { Success, Error, Stopped }

/** One run of a shortcut, by hand or from an automation, like an execution in n8n. */
data class RunRecord(
    val id: String,
    val shortcutId: String,
    val shortcut: String,
    /** "In the app", "Home screen", or the automation's trigger, e.g. "At 17:00". */
    val source: String,
    val automationId: String?,
    val at: Long,
    val ms: Long,
    val status: RunStatus,
    val error: String?,
    val steps: List<RunStep>,
)

/**
 * The last runs, kept on the phone only: at most [MAX] and none older than a week. Step details are
 * trimmed, and a workspace token is never part of them.
 */
object RunLog {
    private const val MAX = 200
    private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    private val state = MutableStateFlow<List<RunRecord>>(emptyList())
    val records: StateFlow<List<RunRecord>> = state
    private var file: File? = null

    fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.applicationContext.filesDir, "runs.json")
        state.value = runCatching { decode(file!!.readText()) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(r: RunRecord) {
        val cutoff = System.currentTimeMillis() - WEEK_MS
        val next = (listOf(r) + state.value).filter { it.at >= cutoff }.take(MAX)
        state.value = next
        file?.let { f ->
            runCatching {
                val tmp = File(f.parentFile, "runs.json.tmp")
                tmp.writeText(encode(next))
                tmp.renameTo(f)
            }
        }
    }

    @Synchronized
    fun clear() {
        state.value = emptyList()
        file?.delete()
    }

    fun encode(list: List<RunRecord>): String = JSONArray(list.map { r ->
        JSONObject().put("id", r.id).put("sid", r.shortcutId).put("s", r.shortcut).put("src", r.source).put("aid", r.automationId ?: JSONObject.NULL)
            .put("at", r.at).put("ms", r.ms).put("st", r.status.name).put("err", r.error ?: JSONObject.NULL)
            .put("steps", JSONArray(r.steps.map { JSONObject().put("t", it.title).put("ok", it.ok).put("d", it.detail).put("ms", it.ms) }))
    }).toString()

    fun decode(text: String): List<RunRecord> {
        val a = JSONArray(text)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val steps = o.optJSONArray("steps") ?: JSONArray()
            RunRecord(
                id = o.getString("id"), shortcutId = o.optString("sid"), shortcut = o.optString("s"), source = o.optString("src"),
                automationId = o.optString("aid").takeIf { it.isNotBlank() && it != "null" }, at = o.getLong("at"), ms = o.optLong("ms"),
                status = runCatching { RunStatus.valueOf(o.getString("st")) }.getOrDefault(RunStatus.Success),
                error = o.optString("err").takeIf { it.isNotBlank() && it != "null" },
                steps = (0 until steps.length()).map { j ->
                    val s = steps.getJSONObject(j)
                    RunStep(s.optString("t"), s.optBoolean("ok", true), s.optString("d"), s.optLong("ms"))
                },
            )
        }
    }
}
