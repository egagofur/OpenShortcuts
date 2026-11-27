package app.openshortcuts.ui

import androidx.compose.ui.graphics.toArgb

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.openshortcuts.model.Tint

@Immutable
data class Palette(
    val bg: Color,
    val card: Color,
    val raised: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val fill: Color,
    val hairline: Color,
    val accent: Color,
    val accentWash: Color,
    val positive: Color,
    val danger: Color,
    val scrim: Color,
    val shadow: Color,
    val dark: Boolean,
)

private val Light = Palette(
    bg = Color(0xFFF1F1F5),
    card = Color(0xFFFDFDFE),
    raised = Color(0xFFFDFDFE),
    ink = Color(0xFF0C0C10),
    ink2 = Color(0xFF66666D),
    ink3 = Color(0xFF9C9CA3),
    fill = Color(0xFFE4E4EA),
    hairline = Color(0x14000000),
    accent = Color(0xFF0062E0),
    accentWash = Color(0xFFE6EFFD),
    positive = Color(0xFF1F9E3F),
    danger = Color(0xFFD9322A),
    scrim = Color(0x52000000),
    shadow = Color(0x26000000),
    dark = false,
)

private val Dark = Palette(
    bg = Color(0xFF0A0A0C),
    card = Color(0xFF1C1C1F),
    raised = Color(0xFF26262A),
    ink = Color(0xFFF3F3F6),
    ink2 = Color(0xFF9E9EA6),
    ink3 = Color(0xFF6E6E76),
    fill = Color(0xFF2E2E33),
    hairline = Color(0x1AFFFFFF),
    accent = Color(0xFF5AA2FF),
    accentWash = Color(0xFF14284A),
    positive = Color(0xFF3ACB5C),
    danger = Color(0xFFFF5A50),
    scrim = Color(0x8C000000),
    shadow = Color(0x00000000),
    dark = true,
)

fun Tint.color(): Color = when (this) {
    Tint.Blue -> Color(0xFF2F7CF6)
    Tint.Green -> Color(0xFF28B14A)
    Tint.Indigo -> Color(0xFF5654D4)
    Tint.Orange -> Color(0xFFF08A0A)
    Tint.Red -> Color(0xFFEC4338)
    Tint.Gray -> Color(0xFF8A8A90)
    Tint.Teal -> Color(0xFF239FC2)
    Tint.Yellow -> Color(0xFFF2AE00)
    Tint.Pink -> Color(0xFFEE3768)
    Tint.Purple -> Color(0xFF9F4FD6)
}

@Immutable
data class Type(
    val title: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em),
    val large: TextStyle = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
    val action: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.01).em),
    val token: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.01).em),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    val label: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    val small: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    val clock: TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum", letterSpacing = (-0.01).em),
)

object Motion {
    val easeOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val drawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    fun <T> settle() = spring<T>(dampingRatio = 1f, stiffness = 247f)
    fun <T> fling() = spring<T>(dampingRatio = 0.8f, stiffness = 438f)
    fun <T> press() = spring<T>(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)
}

object Dimens {
    val rail = 76.dp
    val gutter = 20.dp
    val cardRadius = 26.dp
    val tile = 28.dp
    val glass = 52.dp
}

val LocalPalette = staticCompositionLocalOf { Light }
val LocalType = staticCompositionLocalOf { Type() }
val LocalReducedMotion = staticCompositionLocalOf { false }

object Theme {
    val c: Palette @Composable get() = LocalPalette.current
    val t: Type @Composable get() = LocalType.current
    val reduced: Boolean @Composable get() = LocalReducedMotion.current
}

@Composable
fun ShortcutsTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val reduced = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalPalette provides if (isSystemInDarkTheme()) Dark else Light,
        LocalReducedMotion provides reduced,
        content = content,
    )
}

/** A tint as a plain ARGB int, for places outside Compose like home-screen widgets. */
fun tintArgb(t: app.openshortcuts.model.Tint): Int = t.color().toArgb()
