package app.openshortcuts.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One small, consistent outline icon set on a 24 grid. Filled variants end in "Fill". */
object Glyphs {
    private val strokes = mapOf(
        "chevronLeft" to listOf("M15 5l-7 7 7 7"),
        "chevronRight" to listOf("M9.5 5.5l6.5 6.5-6.5 6.5"),
        "chevronDown" to listOf("M6.5 9.5l5.5 5.5 5.5-5.5"),
        "x" to listOf("M7.5 7.5l9 9M16.5 7.5l-9 9"),
        "plus" to listOf("M12 5v14M5 12h14"),
        "search" to listOf("M10.5 4a6.5 6.5 0 1 0 0 13a6.5 6.5 0 1 0 0-13z", "M15.5 15.5L20 20"),
        "undo" to listOf("M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11"),
        "redo" to listOf("M15 14l5-5-5-5", "M20 9H9.5a5.5 5.5 0 0 0 0 11H13"),
        "info" to listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v5.5", "M12 7.6v.01"),
        "share" to listOf("M12 3.5v11", "M8 7.5l4-4 4 4", "M8.5 10.5H7a2 2 0 0 0-2 2v6.5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6.5a2 2 0 0 0-2-2h-1.5"),
        "phone" to listOf("M7.2 3.5H4.6a1.1 1.1 0 0 0-1.1 1.2C4.1 13.4 10.6 19.9 19.3 20.5a1.1 1.1 0 0 0 1.2-1.1v-2.6a1.1 1.1 0 0 0-.8-1l-3.2-.9a1.1 1.1 0 0 0-1.1.3l-1.6 1.6a13 13 0 0 1-6.6-6.6l1.6-1.6a1.1 1.1 0 0 0 .3-1.1l-.9-3.2a1.1 1.1 0 0 0-1-.8z"),
        "phoneOff" to listOf("M2.8 13.6c5-4.9 13.4-4.9 18.4 0a1 1 0 0 1 .1 1.3l-1.5 2a1 1 0 0 1-1.3.3l-2.7-1.3a1 1 0 0 1-.6-.9v-2.2a12.5 12.5 0 0 0-6.4 0V15a1 1 0 0 1-.6.9l-2.7 1.3a1 1 0 0 1-1.3-.3l-1.5-2a1 1 0 0 1 .1-1.3z"),
        "moon" to listOf("M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"),
        "sun" to listOf("M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2.5v2M12 19.5v2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M2.5 12h2M19.5 12h2M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"),
        "bell" to listOf("M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 1.5h-15z", "M10 20.5a2 2 0 0 0 4 0"),
        "pause" to listOf("M8.5 5.5v13M15.5 5.5v13"),
        "volume" to listOf("M4 9.5h3.5L12 5v14l-4.5-4.5H4z", "M15.5 9a4 4 0 0 1 0 6", "M18 6.5a7.5 7.5 0 0 1 0 11"),
        "flash" to listOf("M13 2.5L5.5 13.5h6L10.5 21.5l8-11h-6z"),
        "vibrate" to listOf("M9 4h6a1.5 1.5 0 0 1 1.5 1.5v13A1.5 1.5 0 0 1 15 20H9a1.5 1.5 0 0 1-1.5-1.5v-13A1.5 1.5 0 0 1 9 4z", "M4 8.5v7M20 8.5v7"),
        "battery" to listOf("M4.5 7.5h12a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-12a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z", "M21.5 10.5v3", "M6.5 10.5v3M9.5 10.5v3"),
        "timer" to listOf("M12 6.5a7.25 7.25 0 1 0 0 14.5a7.25 7.25 0 1 0 0-14.5z", "M12 10.5v3.5l2 1.5", "M10 2.5h4"),
        "clock" to listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 7.5V12l3 2"),
        "branch" to listOf("M12 12.5V20", "M5.5 4v2a6.5 6.5 0 0 0 13 0V4"),
        "stop" to listOf("M7 5.5h10A1.5 1.5 0 0 1 18.5 7v10a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 17V7A1.5 1.5 0 0 1 7 5.5z"),
        "layers" to listOf("M12 3.5l8.5 4.75-8.5 4.75-8.5-4.75z", "M3.5 12.75L12 17.5l8.5-4.75"),
        "grid" to listOf(
            "M5.5 4h3A1.5 1.5 0 0 1 10 5.5v3A1.5 1.5 0 0 1 8.5 10h-3A1.5 1.5 0 0 1 4 8.5v-3A1.5 1.5 0 0 1 5.5 4z",
            "M15.5 4h3A1.5 1.5 0 0 1 20 5.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 14 8.5v-3A1.5 1.5 0 0 1 15.5 4z",
            "M5.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3A1.5 1.5 0 0 1 8.5 20h-3A1.5 1.5 0 0 1 4 18.5v-3A1.5 1.5 0 0 1 5.5 14z",
            "M15.5 14h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z",
        ),
        "link" to listOf("M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1", "M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1"),
        "text" to listOf("M5.5 7h13M5.5 11h13M5.5 15h8"),
        "quote" to listOf("M5.5 4.5h13a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H11l-4 3.5V16.5H5.5A1.5 1.5 0 0 1 4 15V6a1.5 1.5 0 0 1 1.5-1.5z", "M8 9h8M8 12h5"),
        "sliders" to listOf("M4 7h8.5M17.5 7H20M4 17h2.5M11.5 17H20", "M15 4.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z", "M9 14.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z"),
        "check" to listOf("M5 12.5l4.5 4.5L19 7.5"),
        "trash" to listOf("M4.5 7h15", "M9.5 7V4.5h5V7", "M6.5 7l1 13h9l1-13"),
        "up" to listOf("M12 19V5.5", "M6.5 11L12 5.5l5.5 5.5"),
        "down" to listOf("M12 5v13.5", "M6.5 13L12 18.5l5.5-5.5"),
        "copy" to listOf("M9 8.5h9a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H9A1.5 1.5 0 0 1 7.5 19v-9A1.5 1.5 0 0 1 9 8.5z", "M4.5 15V5.5A1 1 0 0 1 5.5 4.5H15"),
        "more" to listOf("M6 12h.01M12 12h.01M18 12h.01"),
        "home" to listOf("M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z"),
        "repeat" to listOf("M17 2.5l3 3-3 3", "M4 11.5v-2a4 4 0 0 1 4-4h12", "M7 21.5l-3-3 3-3", "M20 12.5v2a4 4 0 0 1-4 4H4"),
        "menu" to listOf("M9 6.5h11M9 12h11M9 17.5h11", "M4.5 6.5h.01M4.5 12h.01M4.5 17.5h.01"),
        "braces" to listOf("M8 4.5a2.5 2.5 0 0 0-2.5 2.5v2.5L4 12l1.5 2.5V17A2.5 2.5 0 0 0 8 19.5", "M16 4.5a2.5 2.5 0 0 1 2.5 2.5v2.5L20 12l-1.5 2.5V17a2.5 2.5 0 0 1-2.5 2.5"),
        "wifi" to listOf("M2.5 9a14 14 0 0 1 19 0", "M5.5 12.3a9.5 9.5 0 0 1 13 0", "M8.6 15.5a5 5 0 0 1 6.8 0", "M12 19v.01"),
        "bolt" to listOf("M13 2.5L5.5 13.5h6L10.5 21.5l8-11h-6z"),
    )

    private val fills = mapOf(
        "playFill" to "M7.5 5.2v13.6a1.2 1.2 0 0 0 1.8 1l11-6.8a1.2 1.2 0 0 0 0-2L9.3 4.2a1.2 1.2 0 0 0-1.8 1z",
        "stopFill" to "M7.5 6.5h9a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1z",
        "boltFill" to "M13 2.5L5.5 13.5h6L10.5 21.5l8-11h-6z",
    )

    val names: List<String> = listOf("layers", "moon", "sun", "phoneOff", "phone", "battery", "bell", "timer",
        "volume", "flash", "home", "link", "grid", "text", "clock", "quote")

    private val cache = HashMap<String, ImageVector>()

    /** Raw path data for drawing outside Compose (launcher icons). Second value: filled. */
    fun pathData(name: String): Pair<List<String>, Boolean> =
        fills[name]?.let { listOf(it) to true } ?: ((strokes[name] ?: strokes.getValue("layers")) to false)

    fun get(name: String): ImageVector = cache.getOrPut(name) {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        val fill = fills[name]
        if (fill != null) {
            b.addPath(PathParser().parsePathString(fill).toNodes(), fill = SolidColor(Color.Black))
        } else {
            val bold = name == "more"
            (strokes[name] ?: strokes.getValue("layers")).forEach { d ->
                b.addPath(
                    PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = if (bold) 3f else 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        b.build()
    }
}

@Composable
fun Glyph(name: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier, label: String? = null) {
    Icon(Glyphs.get(name), contentDescription = label, tint = tint, modifier = modifier.size(size))
}
