package app.openshortcuts.model

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Single source of truth. Writes are small, so every change is saved straight to disk. */
object Store {
    private lateinit var file: File
    private val state = MutableStateFlow(Library())
    val library: StateFlow<Library> = state.asStateFlow()

    /** Called after every change so alarms follow the data. */
    var onChange: ((Library) -> Unit)? = null

    fun init(context: Context) {
        if (::file.isInitialized) return
        file = File(context.filesDir, "library.json")
        state.value = runCatching { decode(JSONObject(file.readText())) }.getOrNull() ?: Seeds.library().also { save(it) }
    }

    fun shortcut(id: String) = state.value.shortcuts.firstOrNull { it.id == id }
    fun automation(id: String) = state.value.automations.firstOrNull { it.id == id }

    fun update(transform: (Library) -> Library) {
        val next = transform(state.value)
        if (next == state.value) return
        state.value = next
        save(next)
        onChange?.invoke(next)
    }

    fun upsert(s: Shortcut) = update { lib ->
        val exists = lib.shortcuts.any { it.id == s.id }
        lib.copy(shortcuts = if (exists) lib.shortcuts.map { if (it.id == s.id) s else it } else lib.shortcuts + s)
    }

    fun upsert(a: Automation) = update { lib ->
        val exists = lib.automations.any { it.id == a.id }
        lib.copy(automations = if (exists) lib.automations.map { if (it.id == a.id) a else it } else lib.automations + a)
    }

    fun deleteShortcut(id: String) = update { lib ->
        lib.copy(
            shortcuts = lib.shortcuts.filterNot { it.id == id },
            automations = lib.automations.filterNot { it.shortcutId == id },
        )
    }

    fun deleteAutomation(id: String) = update { lib -> lib.copy(automations = lib.automations.filterNot { it.id == id }) }

    private fun save(lib: Library) {
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(encode(lib).toString())
        tmp.renameTo(file)
    }

    // --- JSON ---------------------------------------------------------------

    private fun encode(lib: Library) = JSONObject().apply {
        put("v", 1)
        put("shortcuts", JSONArray(lib.shortcuts.map { s ->
            JSONObject().apply {
                put("id", s.id); put("name", s.name); put("tint", s.tint.name); put("glyph", s.glyph); put("share", s.inShareSheet)
                put("actions", JSONArray(s.actions.map { a ->
                    JSONObject().apply {
                        put("id", a.id); put("type", a.type.name)
                        put("params", JSONObject(a.params))
                    }
                }))
            }
        }))
        put("automations", JSONArray(lib.automations.map { a ->
            JSONObject().apply {
                put("id", a.id); put("kind", a.kind.name); put("time", a.timeOfDay)
                put("days", JSONArray(a.days.toList())); put("callMinutes", a.callMinutes)
                put("windowStart", a.windowStart); put("windowEnd", a.windowEnd)
                put("shortcutId", a.shortcutId); put("enabled", a.enabled); put("warn", a.warnFirst)
                put("on", a.on); put("level", a.level); put("text", a.text)
            }
        }))
    }

    private fun decode(o: JSONObject): Library {
        val shortcuts = o.getJSONArray("shortcuts").objects().map { s ->
            Shortcut(
                id = s.getString("id"), name = s.getString("name"),
                tint = enumOr(s.optString("tint"), Tint.Blue), glyph = s.optString("glyph", "layers"), inShareSheet = s.optBoolean("share"),
                actions = s.getJSONArray("actions").objects().mapNotNull { a ->
                    val type = runCatching { ActionType.valueOf(a.getString("type")) }.getOrNull() ?: return@mapNotNull null
                    val p = a.optJSONObject("params") ?: JSONObject()
                    Action(id = a.getString("id"), type = type, params = p.keys().asSequence().associateWith { p.getString(it) })
                },
            )
        }
        val automations = o.optJSONArray("automations")?.objects()?.mapNotNull { a ->
            val kind = runCatching { TriggerKind.valueOf(a.getString("kind")) }.getOrNull() ?: return@mapNotNull null
            val days = a.optJSONArray("days")
            Automation(
                id = a.getString("id"), kind = kind, timeOfDay = a.optInt("time", 23 * 60),
                days = (0 until (days?.length() ?: 0)).map { days!!.getInt(it) }.toSet(),
                callMinutes = a.optInt("callMinutes", 30), windowStart = a.optInt("windowStart", 22 * 60),
                windowEnd = a.optInt("windowEnd", 6 * 60), shortcutId = a.optString("shortcutId"),
                enabled = a.optBoolean("enabled", true), warnFirst = a.optBoolean("warn", true),
                on = a.optBoolean("on", true), level = a.optInt("level", 20), text = a.optString("text"),
            )
        }.orEmpty()
        return Library(shortcuts, automations)
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E) =
        runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)
}
