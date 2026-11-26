/*
 * Adapted from Codync (https://github.com/leepokai/Codync), apps/android/design/.../Glass.kt
 * Copyright 2026 Po Kai Lee and the Codync contributors.
 * Licensed under the Apache License, Version 2.0; see third_party/codync/LICENSE and NOTICE.
 *
 * Changes: uses OpenShortcuts' palette and motion, drops the scroll-edge and frost helpers,
 * and lets the swell follow a held tab bar.
 */
package app.openshortcuts.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.min

/** What Liquid Glass above refracts: the nearest [GlassStage]'s content. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

/**
 * Content with glass chrome floating over it. [content] is recorded so [chrome]'s glass can blur
 * and bend it; the chrome itself is never recorded.
 */
@Composable
fun GlassStage(
    modifier: Modifier = Modifier,
    chrome: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val ground = Theme.c.bg
    val onDraw: ContentDrawScope.() -> Unit = remember(ground) { { drawRect(ground); drawContent() } }
    val backdrop = rememberLayerBackdrop(onDraw = onDraw)
    Box(modifier, propagateMinConstraints = true) {
        Box(Modifier.layerBackdrop(backdrop), content = content)
        CompositionLocalProvider(LocalBackdrop provides backdrop) { Box(Modifier.matchParentSize(), content = chrome) }
    }
}

/**
 * Liquid Glass: what's behind is blurred, bent at the rim, lit along the edge and lifted by a soft
 * shadow. [pressed] swells it, like glass under a finger.
 */
@Composable
fun Modifier.glass(shape: RoundedRectangularShape = Capsule(), pressed: Boolean = false): Modifier {
    val backdrop = LocalBackdrop.current
    val c = Theme.c
    val surface = c.raised.copy(alpha = if (c.dark) 0.62f else 0.72f)
    val swell by animateFloatAsState(if (pressed && !Theme.reduced) 1.04f else 1f, Motion.settle(), label = "glassPress")
    return graphicsLayer { scaleX = swell; scaleY = swell }.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(4.dp.toPx())
            val rim = min(size.minDimension * 0.3f, 18.dp.toPx())
            lens(rim, rim * 2f)
        },
        highlight = { Highlight(width = 0.75.dp, alpha = if (c.dark) 0.45f else 0.8f) },
        shadow = { Shadow(radius = 18.dp, offset = DpOffset(0.dp, 4.dp), color = Color.Black.copy(alpha = if (c.dark) 0.32f else 0.09f)) },
        onDrawSurface = { drawRect(surface) },
    )
}
