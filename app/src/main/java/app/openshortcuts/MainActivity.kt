package app.openshortcuts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.Store
import app.openshortcuts.model.Tint
import app.openshortcuts.model.TriggerKind
import app.openshortcuts.ui.AutomationScreen
import app.openshortcuts.ui.EditorScreen
import app.openshortcuts.ui.HomeScreen
import app.openshortcuts.ui.Motion
import app.openshortcuts.ui.NewAutomationDialog
import app.openshortcuts.ui.ResultDialog
import app.openshortcuts.ui.RunController
import app.openshortcuts.ui.SetupScreen
import app.openshortcuts.ui.ShortcutsTheme
import app.openshortcuts.ui.Theme

sealed interface Route {
    data object Home : Route
    data class Editor(val id: String) : Route
    data class Auto(val id: String) : Route
    data object Setup : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The rail shows time and battery, like the folded Duo, so the status bar steps aside.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { ShortcutsTheme { App() } }
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val lib by Store.library.collectAsStateWithLifecycle()
    var stack by remember { mutableStateOf(listOf<Route>(Route.Home)) }
    var tab by remember { mutableIntStateOf(0) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var newAuto by remember { mutableStateOf(false) }
    val runs = remember { RunController(ctx.applicationContext) }

    LifecycleResumeEffect(Unit) { resumeTick++; onPauseOrDispose { } }

    fun push(r: Route) { stack = stack + r }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { pop() }

    val missingSetup = remember(lib, resumeTick) {
        buildList {
            if (lib.automations.any { it.enabled && it.kind != TriggerKind.Time } ||
                lib.shortcuts.any { s -> s.actions.any { it.type == app.openshortcuts.model.ActionType.END_WHATSAPP_CALL } }
            ) add(Perm.CallAccess)
            lib.shortcuts.forEach { addAll(Perm.missing(ctx, it)) }
        }.distinct().filterNot { it.granted(ctx) }
    }

    val reduced = Theme.reduced
    var lastDepth by remember { mutableIntStateOf(1) }
    AnimatedContent(
        targetState = stack,
        contentKey = { it.last() },
        transitionSpec = {
            val forward = targetState.size >= lastDepth
            lastDepth = targetState.size
            if (reduced) fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            else {
                // Enter and leave along the same path: push from the right, pop back to the right.
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320, easing = Motion.easeOut)) { w -> dir * w / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(320, easing = Motion.easeOut)) { w -> -dir * w / 6 } + fadeOut(tween(160)))
            }
        },
        label = "nav",
    ) { st ->
        when (val r = st.last()) {
            Route.Home -> HomeScreen(
                lib = lib, tab = tab, runs = runs, missingSetup = missingSetup,
                onTab = { tab = it },
                onNew = {
                    if (tab == 0) {
                        val s = Shortcut(name = "New Shortcut", tint = Tint.entries.random(), glyph = "layers")
                        Store.upsert(s); push(Route.Editor(s.id))
                    } else newAuto = true
                },
                onEdit = { push(Route.Editor(it.id)) },
                onAutomation = { push(Route.Auto(it.id)) },
                onToggle = { Store.upsert(it.copy(enabled = !it.enabled)) },
                onSetup = { push(Route.Setup) },
            )
            is Route.Editor -> {
                val s = remember(r.id) { Store.shortcut(r.id) }
                if (s == null) pop() else EditorScreen(
                    s, runs, onBack = ::pop,
                    onDelete = { Store.deleteShortcut(s.id); pop() },
                    onSetup = { push(Route.Setup) },
                )
            }
            is Route.Auto -> {
                val a = lib.automations.firstOrNull { it.id == r.id }
                if (a == null) pop() else AutomationScreen(
                    a, lib,
                    onChange = { Store.upsert(it) }, onBack = ::pop,
                    onDelete = { Store.deleteAutomation(a.id); pop() },
                    onOpenShortcut = { push(Route.Editor(it)) },
                    onSetup = { push(Route.Setup) },
                )
            }
            Route.Setup -> SetupScreen(resumeTick, onBack = ::pop)
        }
    }

    if (newAuto) NewAutomationDialog(onDismiss = { newAuto = false }) { kind ->
        newAuto = false
        // Event triggers run straight away by default; time and call triggers warn first.
        // No shortcut picked yet, so a new trigger never runs something you didn't choose.
        val a = Automation(kind = kind, shortcutId = "", callMinutes = 45,
            windowStart = 23 * 60, windowEnd = 5 * 60, warnFirst = !kind.event)
        Store.upsert(a); push(Route.Auto(a.id))
    }
    runs.prompt?.let { (p, _) -> app.openshortcuts.ui.PromptDialog(p) { runs.answer(it) } }
    runs.result?.let { r -> ResultDialog(r, onDismiss = { runs.result = null }, onSetup = { push(Route.Setup) }) }
}
