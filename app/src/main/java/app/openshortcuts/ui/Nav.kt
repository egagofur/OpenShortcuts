package app.openshortcuts.ui

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Stable
class NavState<R : Any>(initial: R, private val scope: CoroutineScope) {
    var stack by mutableStateOf(listOf(initial)); private set
    var p by mutableFloatStateOf(0f); private set
    var reduced = false

    private var job: Job? = null
    private var finish: (() -> Unit)? = null

    private fun complete() { val f = finish; finish = null; f?.invoke() }

    private fun settle() { job?.cancel(); job = null; complete() }

    private suspend fun go(target: Float, velocity: Float = 0f) =
        animate(p, target, velocity, if (reduced) tween(200) else spring(dampingRatio = 1f, stiffness = 300f)) { v, _ -> p = v }

    val top: R get() = stack.last()

    fun push(r: R) {
        settle()
        stack = stack + r
        p = 1f
        finish = { p = 0f }
        job = scope.launch { go(0f); complete() }
    }

    fun pop() {
        if (stack.size < 2) return
        settle()
        finish = { stack = stack.dropLast(1); p = 0f }
        job = scope.launch { go(1f); complete() }
    }

    fun popToRoot() {
        if (stack.size < 2) return
        settle()
        if (stack.size > 2) stack = listOf(stack.first(), stack.last())
        pop()
    }

    fun popIfTop(r: R) { if (stack.size > 1 && stack.last() == r && finish == null) pop() }

    fun reset(to: List<R>) { settle(); stack = to; p = 0f }

    suspend fun predictive(events: Flow<BackEventCompat>) {
        if (stack.size < 2) return
        settle()
        try {
            events.collect { e -> p = e.progress }
            finish = { stack = stack.dropLast(1); p = 0f }
            go(1f)
            complete()
        } catch (e: CancellationException) {
            if (finish == null) job = scope.launch { go(0f) }
            throw e
        }
    }
}

@Composable
fun <R : Any> NavHost(nav: NavState<R>, content: @Composable (R) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val reduced = Theme.reduced
    nav.reduced = reduced
    val c = Theme.c
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val w = constraints.maxWidth.toFloat()
        val moving by remember { derivedStateOf { nav.p > 0.0005f } }
        val st = nav.stack
        val first = if (moving && st.size >= 2) st.size - 2 else st.size - 1
        for (i in first until st.size) {
            val r = st[i]
            val isTop = i == st.lastIndex
            key("$i:$r") {
                holder.SaveableStateProvider("$i:$r") {
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer {
                                val p = nav.p
                                if (reduced) {
                                    alpha = if (isTop) 1f - p else 1f
                                } else {
                                    translationX = if (isTop) p * w else -0.3f * w * (1f - p)
                                }
                            }
                            .then(
                                if (isTop && moving && !reduced) Modifier.drawBehind {
                                    val edge = 18.dp.toPx()
                                    drawRect(
                                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f * (1f - nav.p))), startX = -edge, endX = 0f),
                                        topLeft = Offset(-edge, 0f), size = Size(edge, size.height),
                                    )
                                } else Modifier,
                            ),
                    ) {
                        content(r)
                        if (!isTop && !reduced) Box(
                            Modifier.fillMaxSize().graphicsLayer { alpha = (1f - nav.p) }.background(Color.Black.copy(alpha = 0.14f)),
                        )
                    }
                }
            }
        }
    }
}
