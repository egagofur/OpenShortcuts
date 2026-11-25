package app.openshortcuts.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Portable {
    const val EXTENSION = "openshortcut"
    private const val FORMAT = "openshortcuts"
    private const val MAX_BYTES = 512 * 1024

    val incoming = MutableStateFlow<Imported?>(null)
    val importError = MutableStateFlow<String?>(null)

    fun receive(ctx: Context, uri: Uri) {
        runCatching { read(ctx, uri) }
            .onSuccess { incoming.value = it; importError.value = null }
            .onFailure { importError.value = it.message ?: "Couldn't read that file." }
    }

    data class Imported(val shortcut: Shortcut, val unknownActions: Int)

    fun encode(s: Shortcut, lib: Library): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", 1)
        put("shortcut", JSONObject().apply {
            put("name", s.name); put("tint", s.tint.name); put("glyph", s.glyph); put("share", s.inShareSheet)
            put("actions", JSONArray(s.actions.map { a ->
                val params = a.params.toMutableMap()
                if (a.type == ActionType.RUN_SHORTCUT) {
                    params["shortcut"] = lib.shortcuts.firstOrNull { it.id == a.p("shortcut") }?.name.orEmpty()
                }
                JSONObject().apply { put("type", a.type.name); put("params", JSONObject(params as Map<*, *>)) }
            }))
        })
    }.toString(2)

    fun decode(text: String, lib: Library): Imported {
        val root = runCatching { JSONObject(text) }.getOrElse { error("This file isn't an OpenShortcuts shortcut.") }
        if (root.optString("format") != FORMAT) error("This file isn't an OpenShortcuts shortcut.")
        if (root.optInt("version", 1) > 1) error("This shortcut was made by a newer version of OpenShortcuts.")
        val o = root.getJSONObject("shortcut")
        var unknown = 0
        val actions = o.getJSONArray("actions").let { arr -> (0 until arr.length()).map(arr::getJSONObject) }.mapNotNull { a ->
            val type = runCatching { ActionType.valueOf(a.getString("type")) }.getOrNull()
                ?: run { unknown++; return@mapNotNull null }
            val p = a.optJSONObject("params") ?: JSONObject()
            val known = type.params.map { it.key }.toSet()
            var params = p.keys().asSequence().filter { it in known || it.removeSuffix(".name") in known }.associateWith { p.getString(it).take(4_000) }
            if (type == ActionType.RUN_SHORTCUT) {
                params = params + ("shortcut" to (lib.shortcuts.firstOrNull { it.name == params["shortcut"] }?.id ?: ""))
            }
            Action(type = type, params = type.params.associate { it.key to it.default } + params)
        }
        val name = o.optString("name").trim().take(60).ifEmpty { "Imported Shortcut" }
        return Imported(
            Shortcut(
                name = uniqueName(name, lib),
                tint = runCatching { Tint.valueOf(o.optString("tint")) }.getOrDefault(Tint.Blue),
                glyph = o.optString("glyph", "layers").take(20),
                inShareSheet = o.optBoolean("share"),
                actions = actions.take(500),
            ),
            unknown,
        )
    }

    fun uniqueName(name: String, lib: Library): String {
        if (lib.shortcuts.none { it.name == name }) return name
        var n = 2
        while (lib.shortcuts.any { it.name == "$name $n" }) n++
        return "$name $n"
    }

    fun read(ctx: Context, uri: Uri): Imported {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (out.size() <= MAX_BYTES) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
            out.toByteArray()
        } ?: error("Couldn't open that file.")
        if (bytes.size > MAX_BYTES) error("That file is too big to be a shortcut.")
        return decode(bytes.toString(Charsets.UTF_8), Store.library.value)
    }

    fun shareIntent(ctx: Context, s: Shortcut, recipe: String): Intent {
        val dir = File(ctx.cacheDir, "shared").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val safe = s.name.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifEmpty { "Shortcut" }
        val file = File(dir, "$safe.$EXTENSION").apply { writeText(encode(s, Store.library.value)) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        return Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, s.name)
                .putExtra(Intent.EXTRA_TEXT, recipe)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Share ${s.name}",
        )
    }
}
