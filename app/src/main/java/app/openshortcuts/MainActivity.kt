package app.openshortcuts

import java.time.LocalTime
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.openshortcuts.ui.NewAutomationDialog
import app.openshortcuts.ui.ResultDialog
import app.openshortcuts.ui.RunController
import app.openshortcuts.ui.SetupScreen
import app.openshortcuts.ui.ShortcutsTheme

sealed interface Route {
    data object Home : Route
    data class Editor(val id: String) : Route
    data class Auto(val id: String) : Route
    data object Setup : Route
    data object Gallery : Route
    data class Workspace(val id: String) : Route
    data class WorkspaceItem(val adapterId: String, val widgetId: String, val itemJson: String) : Route
    data class ShipCard(val adapterId: String, val widgetId: String) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { ShortcutsTheme { App() } }
        if (savedInstanceState == null) receive(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: android.content.Intent?) {
        val uri = when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND -> androidx.core.content.IntentCompat.getParcelableExtra(
                intent, android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java,
            )
            else -> null
        } ?: return
        app.openshortcuts.model.Portable.receive(this, uri)
    }
}

@Composable
private fun App() {
    val ctx = LocalContext.current
    val lib by Store.library.collectAsStateWithLifecycle()
    val navScope = androidx.compose.runtime.rememberCoroutineScope()
    val nav = remember { app.openshortcuts.ui.NavState<Route>(Route.Home, navScope) }
    var tab by remember { mutableIntStateOf(0) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var newAuto by remember { mutableStateOf(false) }
    var newWorkspace by remember { mutableStateOf(false) }
    var newShortcut by remember { mutableStateOf(false) }
    var describing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<app.openshortcuts.engine.Describe.Draft?>(null) }
    val incoming by app.openshortcuts.model.Portable.incoming.collectAsStateWithLifecycle()
    val importError by app.openshortcuts.model.Portable.importError.collectAsStateWithLifecycle()
    val adapters by app.openshortcuts.model.AdapterStore.adapters.collectAsStateWithLifecycle()
    val incomingAdapter by app.openshortcuts.model.AdapterStore.incoming.collectAsStateWithLifecycle()
    val adapterError by app.openshortcuts.model.AdapterStore.importError.collectAsStateWithLifecycle()
    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { app.openshortcuts.model.Portable.receive(ctx, it) } }
    val runs = remember { RunController(ctx.applicationContext) }

    LifecycleResumeEffect(Unit) {
        resumeTick++
        if (app.openshortcuts.engine.CallWatcher.isEnabled(ctx) && app.openshortcuts.engine.CallWatcher.instance == null)
            app.openshortcuts.engine.CallWatcher.rebind(ctx)
        onPauseOrDispose { }
    }

    fun push(r: Route) = nav.push(r)
    fun pop() = nav.pop()
    androidx.activity.compose.PredictiveBackHandler(enabled = nav.stack.size > 1) { events -> nav.predictive(events) }

    val missingSetup = remember(lib, resumeTick) {
        buildList {
            if (lib.automations.any { it.enabled && it.kind != TriggerKind.Time } ||
                lib.shortcuts.any { s -> s.actions.any { it.type == app.openshortcuts.model.ActionType.END_WHATSAPP_CALL } }
            ) add(Perm.NotificationListener)
            lib.shortcuts.forEach { addAll(Perm.missing(ctx, it)) }
        }.distinct().filterNot { it.granted(ctx) }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        app.openshortcuts.ui.LocalGoHome provides if (nav.stack.size > 1) ({ nav.popToRoot() }) else null,
        app.openshortcuts.ui.LocalOpenShipCard provides { a, w -> push(Route.ShipCard(a, w)) },
    ) {
    app.openshortcuts.ui.NavHost(nav) { route ->
        when (val r = route) {
            Route.Home -> HomeScreen(
                lib = lib, tab = tab, runs = runs, missingSetup = missingSetup,
                onTab = { tab = it },
                onNew = {
                    when (tab) {
                        0 -> newShortcut = true
                        1 -> newAuto = true
                        else -> newWorkspace = true
                    }
                },
                onEdit = { push(Route.Editor(it.id)) },
                onAutomation = { push(Route.Auto(it.id)) },
                onToggle = { Store.upsert(it.copy(enabled = !it.enabled)) },
                onSetup = { push(Route.Setup) },
                adapters = adapters,
                onOpenWorkspace = { push(Route.Workspace(it.id)) },
                onImportAdapter = { newWorkspace = true },
            )
            is Route.Editor -> {
                val s = remember(r.id) { Store.shortcut(r.id) }
                LaunchedEffect(r) { app.openshortcuts.ui.Tour.done(app.openshortcuts.ui.Tour.Step.NewShortcut) }
                if (s == null) LaunchedEffect(r) { nav.popIfTop(r) } else EditorScreen(
                    s, runs, onBack = ::pop,
                    onDelete = { Store.deleteShortcut(s.id); pop() },
                    onSetup = { push(Route.Setup) },
                )
            }
            is Route.Auto -> {
                val a = lib.automations.firstOrNull { it.id == r.id }
                LaunchedEffect(r) { app.openshortcuts.ui.Tour.done(app.openshortcuts.ui.Tour.Step.NewAutomation) }
                if (a == null) LaunchedEffect(r) { nav.popIfTop(r) } else AutomationScreen(
                    a, lib,
                    onChange = { Store.upsert(it) }, onBack = ::pop,
                    onDelete = { Store.deleteAutomation(a.id); pop() },
                    onOpenShortcut = { push(Route.Editor(it)) },
                    onSetup = { push(Route.Setup) },
                )
            }
            Route.Setup -> SetupScreen(resumeTick, onBack = ::pop)
            Route.Gallery -> app.openshortcuts.ui.GalleryScreen(onBack = ::pop, onOpen = { push(Route.Editor(it)) })
            is Route.Workspace -> app.openshortcuts.ui.WorkspaceScreen(
                adapterId = r.id,
                runs = runs,
                onOpenDetail = { widgetId, itemJson -> push(Route.WorkspaceItem(r.id, widgetId, itemJson)) },
                onBack = ::pop,
            )
            is Route.ShipCard -> app.openshortcuts.ui.ShipStudioScreen(r.adapterId, r.widgetId, onBack = ::pop)
            is Route.WorkspaceItem -> app.openshortcuts.ui.WorkspaceDetailScreen(
                adapterId = r.adapterId,
                widgetId = r.widgetId,
                itemJson = r.itemJson,
                runs = runs,
                onBack = ::pop,
            )
        }
    }
    }

    if (newAuto) NewAutomationDialog(onDismiss = { newAuto = false }) { kind ->
        newAuto = false
        val a = Automation(kind = kind, shortcutId = "", callMinutes = 45,
            windowStart = LocalTime.of(23, 0), windowEnd = LocalTime.of(5, 0), warnFirst = false)
        Store.upsert(a); push(Route.Auto(a.id))
    }
    if (newWorkspace) app.openshortcuts.ui.NewWorkspaceDialog(
        onDismiss = { newWorkspace = false },
        onImport = { newWorkspace = false; pickFile.launch(arrayOf("*/*")) },
        onPaste = {
            val clip = ctx.getSystemService(android.content.ClipboardManager::class.java).primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            newWorkspace = false
            if (text.isBlank()) app.openshortcuts.model.AdapterStore.importError.value = "Copy the workspace JSON first, then tap Paste."
            else app.openshortcuts.model.AdapterStore.receiveText(text)
        },
        onCreated = { newWorkspace = false; push(Route.Workspace(it.id)) },
    )
    if (newShortcut) app.openshortcuts.ui.NewShortcutDialog(
        onDismiss = { newShortcut = false },
        onNew = {
            newShortcut = false
            val s = Shortcut(name = "New Shortcut", tint = Tint.entries.random(), glyph = "layers")
            Store.upsert(s); push(Route.Editor(s.id))
        },
        onImport = { newShortcut = false; pickFile.launch(arrayOf("*/*")) },
        onDescribe = { newShortcut = false; describing = true },
        onGallery = { newShortcut = false; push(Route.Gallery) },
    )
    if (describing) app.openshortcuts.ui.DescribeDialog(
        onDismiss = { describing = false },
        onDraft = { describing = false; draft = it },
    )
    draft?.let { d ->
        app.openshortcuts.ui.ImportDialog(
            d.imported, note = d.note, automation = d.automation,
            onCancel = { draft = null },
            onAdd = {
                draft = null
                Store.upsert(d.imported.shortcut)
                d.automation?.let { Store.upsert(it) }
                nav.reset(listOf(Route.Home)); tab = 0
                push(Route.Editor(d.imported.shortcut.id))
            },
        )
    }
    incoming?.let { imp ->
        app.openshortcuts.ui.ImportDialog(
            imp,
            onCancel = { app.openshortcuts.model.Portable.incoming.value = null },
            onAdd = {
                app.openshortcuts.model.Portable.incoming.value = null
                Store.upsert(imp.shortcut)
                nav.reset(listOf(Route.Home)); tab = 0
                push(Route.Editor(imp.shortcut.id))
            },
        )
    }
    importError?.let { msg ->
        app.openshortcuts.ui.DuoDialog({ app.openshortcuts.model.Portable.importError.value = null }) {
            app.openshortcuts.ui.DialogTitle("Couldn't import", msg)
            app.openshortcuts.ui.PrimaryButton("OK", androidx.compose.ui.Modifier.fillMaxWidth()) {
                app.openshortcuts.model.Portable.importError.value = null
            }
        }
    }
    incomingAdapter?.let { imp ->
        app.openshortcuts.ui.ImportAdapterDialog(
            imp,
            onCancel = { app.openshortcuts.model.AdapterStore.incoming.value = null },
            onAdd = {
                app.openshortcuts.model.AdapterStore.incoming.value = null
                app.openshortcuts.model.AdapterStore.upsert(imp)
                nav.reset(listOf(Route.Home)); tab = 2
                push(Route.Workspace(imp.id))
            },
        )
    }
    adapterError?.let { msg ->
        app.openshortcuts.ui.DuoDialog({ app.openshortcuts.model.AdapterStore.importError.value = null }) {
            app.openshortcuts.ui.DialogTitle("Couldn't import adapter", msg)
            app.openshortcuts.ui.PrimaryButton("OK", androidx.compose.ui.Modifier.fillMaxWidth()) {
                app.openshortcuts.model.AdapterStore.importError.value = null
            }
        }
    }
    runs.prompt?.let { (p, _) -> app.openshortcuts.ui.PromptDialog(p) { runs.answer(it) } }
    app.openshortcuts.ui.PrimerSheet()
    app.openshortcuts.ui.TourOverlay()
    if (app.openshortcuts.ui.Tour.step == app.openshortcuts.ui.Tour.Step.AllSet) app.openshortcuts.ui.AllSetDialog { app.openshortcuts.ui.Tour.finish() }
    if (app.openshortcuts.ui.Tour.step == app.openshortcuts.ui.Tour.Step.Welcome) app.openshortcuts.ui.WelcomeScreen(
        onContinue = { nav.reset(listOf(Route.Home)); tab = 0; app.openshortcuts.ui.Tour.start() },
        onNotNow = { app.openshortcuts.ui.Tour.skip() },
    )
    runs.result?.let { r ->
        ResultDialog(r, onDismiss = { runs.result = null }, onSetup = {
            val s = lib.shortcuts.firstOrNull { it.name == r.shortcut }
            val missing = s?.let { Perm.missing(ctx, it) }.orEmpty()
            if (missing.isNotEmpty()) app.openshortcuts.ui.Primer.ask(missing, s!!.name) else push(Route.Setup)
        })
    }
}
