package app.openshortcuts.ui

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Fonts for Ship Cards: a few open-licensed faces bundled with the app (see assets/fonts/licenses),
 * plus any .ttf/.otf you import. Imported fonts are copied into the app's private storage and never
 * leave the phone, so a font licensed to you alone stays yours.
 */
object ShipFonts {
    data class Face(val key: String, val label: String)

    const val SYSTEM = "system"

    val builtIn = listOf(
        Face(SYSTEM, "Sans"), Face("bebas", "Bebas Neue"), Face("poppins", "Poppins"),
        Face("spartan", "Spartan"), Face("playfair", "Playfair"), Face("mono", "Mono"),
    )

    private val assetFile = mapOf(
        "bebas" to "fonts/bebas_neue.ttf", "poppins" to "fonts/poppins_bold.ttf",
        "spartan" to "fonts/spartan.ttf", "playfair" to "fonts/playfair_display.ttf",
    )
    /** Variable fonts: their weight is set through the 'wght' axis. */
    private val variable = setOf("spartan", "playfair")

    private val cache = ConcurrentHashMap<String, Typeface>()
    private var assets: AssetManager? = null
    private var dir: File? = null

    fun init(ctx: Context) {
        if (assets != null) return
        assets = ctx.applicationContext.assets
        dir = File(ctx.applicationContext.filesDir, "fonts").apply { mkdirs() }
    }

    /** Fonts you imported, by file name. */
    fun custom(): List<Face> = dir?.listFiles()
        ?.filter { it.extension.lowercase() in setOf("ttf", "otf") }
        ?.sortedBy { it.name.lowercase() }
        ?.map { Face("file:" + it.name, it.nameWithoutExtension) }
        .orEmpty()

    fun all(): List<Face> = builtIn + custom()

    fun label(key: String): String = all().firstOrNull { it.key == key }?.label ?: "Sans"

    /** The face for [key] at [weight]; the system sans when the font can't be loaded. */
    fun typeface(key: String, weight: Int = 700): Typeface = cache.getOrPut("$key@$weight") { load(key, weight) ?: sans(weight) }

    private fun sans(weight: Int) = Typeface.create(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL), weight, false)

    private fun load(key: String, weight: Int): Typeface? = runCatching {
        when {
            key == SYSTEM -> sans(weight)
            key == "mono" -> Typeface.create(Typeface.MONOSPACE, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            key.startsWith("file:") -> dir?.let { d -> File(d, key.removePrefix("file:")).takeIf { it.exists() }?.let { Typeface.Builder(it).build() } }
            else -> {
                val a = assets ?: return@runCatching null
                val path = assetFile[key] ?: return@runCatching null
                Typeface.Builder(a, path).apply { if (key in variable) setFontVariationSettings("'wght' $weight") }.build()
            }
        }
    }.getOrNull()

    /** Copies a font file into the app and checks it loads. Returns the new font, or an error to show. */
    fun import(ctx: Context, uri: Uri): Result<Face> = runCatching {
        init(ctx)
        val d = dir ?: error("No storage for fonts.")
        val display = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "font.ttf"
        val ext = display.substringAfterLast('.', "").lowercase()
        if (ext !in setOf("ttf", "otf")) error("Pick a .ttf or .otf font file.")
        val base = display.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "Font" }.take(40)
        val file = File(d, "$base.$ext")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            if (input.available() > 15_000_000) error("That font file is too large.")
            file.outputStream().use { input.copyTo(it) }
        } ?: error("Couldn't read that file.")
        if (runCatching { Typeface.Builder(file).build() }.getOrNull() == null) {
            file.delete()
            error("That file isn't a font this phone can read.")
        }
        cache.keys.removeAll { it.startsWith("file:${file.name}@") }
        Face("file:" + file.name, base)
    }

    fun delete(key: String) {
        if (!key.startsWith("file:")) return
        dir?.let { File(it, key.removePrefix("file:")).delete() }
        cache.keys.removeAll { it.startsWith("$key@") }
    }
}
