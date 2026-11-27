package app.openshortcuts.ui

import app.openshortcuts.model.Tint

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Library
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.TriggerKind
import app.openshortcuts.model.daysLabel
import app.openshortcuts.model.hhmm

@Composable
fun HomeScreen(
    lib: Library,
    tab: Int,
    runs: RunController,
    missingSetup: List<Perm>,
    onTab: (Int) -> Unit,
    onNew: () -> Unit,
    onEdit: (Shortcut) -> Unit,
    onAutomation: (Automation) -> Unit,
    onToggle: (Automation) -> Unit,
    onSetup: () -> Unit,
    adapters: List<app.openshortcuts.model.WorkspaceAdapter> = emptyList(),
    onOpenWorkspace: (app.openshortcuts.model.WorkspaceAdapter) -> Unit = {},
    onImportAdapter: () -> Unit = {},
) {
    val c = Theme.c
    LaunchedEffect(Unit) { Tour.done(Tour.Step.GoBack) }
    LaunchedEffect(tab) { if (tab == 1) Tour.done(Tour.Step.OpenAutomation) }
    var removed by remember { mutableStateOf<Pair<Automation, Int>?>(null) }
    LaunchedEffect(removed) { if (removed != null) { kotlinx.coroutines.delay(5000); removed = null } }
    var removedShortcut by remember { mutableStateOf<Triple<Shortcut, Int, List<Automation>>?>(null) }
    LaunchedEffect(removedShortcut) { if (removedShortcut != null) { kotlinx.coroutines.delay(5000); removedShortcut = null } }
    Row(Modifier.fillMaxSize().background(c.bg)) {
        val short = isShort
        val bottomPad = if (short) 24.dp else 112.dp
        GlassStage(Modifier.weight(1f).fillMaxSize(), chrome = {
            TabBar(
                tab = tab, onTab = onTab, compact = short, showWorkspaces = true,
                modifier = if (short) Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top))
                    .padding(top = 10.dp, end = 4.dp)
                else Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start))
                    // The rail's circles already sit 12dp inside it; nudging right leaves an 8dp gap to them.
                    .padding(start = 8.dp, bottom = 16.dp)
                    .offset(x = 4.dp),
            )
            Box(Modifier.fillMaxSize().padding(bottom = if (short) 0.dp else 72.dp)) {
                UndoBar(removed?.let { "Automation deleted" }, "Undo") {
                    removed?.let { (a, i) -> app.openshortcuts.model.Store.restore(a, i) }
                    removed = null
                }
                UndoBar(removedShortcut?.let { "“${it.first.name}” deleted" }, "Undo") {
                    removedShortcut?.let { (s, i, autos) -> app.openshortcuts.model.Store.restoreShortcut(s, i, autos) }
                    removedShortcut = null
                }
            }
        }) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> ShortcutGrid(lib, runs, missingSetup, bottomPad, onEdit, onSetup) { s ->
                        removedShortcut = Triple(s, lib.shortcuts.indexOfFirst { it.id == s.id }, lib.automations.filter { it.shortcutId == s.id })
                        app.openshortcuts.model.Store.deleteShortcut(s.id)
                    }
                    1 -> AutomationList(lib, bottomPad, onAutomation, onToggle) { a ->
                        removed = a to lib.automations.indexOfFirst { it.id == a.id }
                        app.openshortcuts.model.Store.deleteAutomation(a.id)
                    }
                    else -> WorkspaceHub(adapters, bottomPad, onOpen = onOpenWorkspace, onImport = onImportAdapter)
                }
            }
        }
        Rail(
            { GlassCircle("plus", if (tab == 0) "New shortcut" else if (tab == 1) "New automation" else "New workspace", modifier = Modifier.tipAnchor("railPlus"), onClick = onNew) },
            { GlassCircle("sliders", "Setup", onClick = onSetup) },
        )
    }
}

@Composable
private fun Header(title: String, sub: String) {
    val c = Theme.c
    val short = isShort
    Column(Modifier.padding(top = if (short) 10.dp else 18.dp, bottom = if (short) 12.dp else 18.dp, end = if (short) 250.dp else 0.dp)) {
        Text(title, style = if (short) Theme.t.large.copy(fontSize = 28.sp, lineHeight = 32.sp) else Theme.t.large, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = Theme.t.small, color = c.ink2, maxLines = if (short) 1 else 3, overflow = TextOverflow.Ellipsis)
    }
}

private val TileCells = object : GridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val n = maxOf(2, (availableSize + spacing) / (170.dp.roundToPx() + spacing))
        val total = availableSize - spacing * (n - 1)
        return List(n) { total / n + if (it < total % n) 1 else 0 }
    }
}

@Composable
private fun ShortcutGrid(
    lib: Library, runs: RunController, missing: List<Perm>, bottomPad: androidx.compose.ui.unit.Dp,
    onEdit: (Shortcut) -> Unit, onSetup: () -> Unit, onDelete: (Shortcut) -> Unit,
) {
    LazyVerticalGrid(
        columns = TileCells,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
        contentPadding = PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("All Shortcuts", if (lib.shortcuts.size == 1) "1 shortcut" else "${lib.shortcuts.size} shortcuts")
        }
        if (missing.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { SetupBanner(missing) { Primer.ask(missing) } }
        items(lib.shortcuts, key = { it.id }) { s ->
            ShortcutTile(s, running = runs.running == s.id, done = runs.done == s.id, failed = runs.failed == s.id,
                onRun = { runs.run(s) }, onEdit = { onEdit(s) }, onDelete = { onDelete(s) })
        }
        if (lib.shortcuts.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Tap + to make your first shortcut.", style = Theme.t.body, color = Theme.c.ink2)
        }
    }
}

@Composable
private fun SetupBanner(missing: List<Perm>, onSetup: () -> Unit) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth().pressable(onClick = onSetup, label = "Open setup")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (Perm.NotificationListener in missing) IconTile("phoneOff", app.openshortcuts.model.Tint.Green.color(), size = 34.dp)
            else IconTile("sliders", app.openshortcuts.model.Tint.Gray.color(), size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Finish setup", style = Theme.t.label, color = c.ink)
                Text(
                    if (Perm.NotificationListener in missing) "Allow notification access so OpenShortcuts can hang up WhatsApp calls."
                    else if (missing.size == 1) "Your shortcuts still need 1 permission." else "Your shortcuts still need ${missing.size} permissions.",
                    style = Theme.t.small, color = c.ink2,
                )
            }
            Glyph("chevronRight", c.ink3, size = 18.dp)
        }
    }
}

@Composable
private fun ShortcutTile(s: Shortcut, running: Boolean, done: Boolean, failed: Boolean, onRun: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val hold = remember { androidx.compose.animation.core.Animatable(0f) }
    val base = s.tint.color()
    val view = androidx.compose.ui.platform.LocalView.current
    val reduced = Theme.reduced
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(done) {
        if (done) {
            view.confirm()
            if (!reduced) {
                pop.animateTo(1.04f, androidx.compose.animation.core.tween(120, easing = Motion.easeOut))
                pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 400f))
            }
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.REJECT else android.view.HapticFeedbackConstants.LONG_PRESS)
            if (!reduced) {
                shake.snapTo(0f)
                shake.animateTo(0f, androidx.compose.animation.core.keyframes {
                    durationMillis = 360
                    -10f at 50; 9f at 110; -6f at 170; 4f at 230; -2f at 290
                })
            }
        }
    }
    val breathe = if (running && !reduced) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "run").animateFloat(
            1f, 0.82f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "breathe",
        ).value
    } else 1f
    Box(
        Modifier
            .fillMaxWidth()
            .height(108.dp)
            .graphicsLayer { val k = pop.value - 0.05f * hold.value; scaleX = k; scaleY = k; translationX = shake.value * density; alpha = breathe }
            .pressable(label = "Run ${s.name}", onLongClick = onEdit, onClick = onRun)
            .clip(RoundedCornerShape(22.dp))
            .background(base),
    ) {
        AnimatedContent(
            targetState = when { running -> "timer"; done -> "check"; else -> s.glyph },
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.padding(14.dp),
            label = "tileGlyph",
        ) { g -> Glyph(g, Color.White, size = 26.dp) }
        HoldToDelete(s.name, hold, onTap = onEdit, onDelete = onDelete, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        Text(
            s.name,
            style = Theme.t.label.copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
        )
    }
}

@Composable
private fun AutomationList(
    lib: Library, bottomPad: androidx.compose.ui.unit.Dp,
    onOpen: (Automation) -> Unit, onToggle: (Automation) -> Unit, onDelete: (Automation) -> Unit,
) {
    val c = Theme.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
        contentPadding = PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header("Automation", "Runs shortcuts for you, even while you sleep") }
        item(key = "activity") {
            // Every run, by hand or on its own, with each step: the place to look when something didn't happen.
            val openActivity = LocalOpenActivity.current
            val runs by app.openshortcuts.model.RunLog.records.collectAsStateWithLifecycle()
            val errors = runs.count { it.status == app.openshortcuts.model.RunStatus.Error && System.currentTimeMillis() - it.at < 24 * 3600_000L }
            Card(Modifier.fillMaxWidth().pressable(label = "Open activity") { openActivity(null) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile("clock", Tint.Indigo.color(), size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Activity", style = Theme.t.label, color = c.ink)
                        Text(
                            when {
                                runs.isEmpty() -> "Every run shows up here, step by step"
                                errors > 0 -> "$errors failed in the last day"
                                else -> "Last run ${runs.first().shortcut}"
                            },
                            style = Theme.t.small, color = if (errors > 0) c.danger else c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Glyph("chevronRight", c.ink3, size = 18.dp)
                }
            }
        }
        items(lib.automations, key = { it.id }) { a ->
            val target = lib.shortcuts.firstOrNull { it.id == a.shortcutId }
            SwipeToDelete(a.headline(ctx), onDelete = { onDelete(a) }, modifier = Modifier.animateItem()) {
            Card(Modifier.fillMaxWidth().pressable(label = "Edit automation", onClick = { onOpen(a) })) {
                Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(a.kind.glyph(), a.kind.tint().color(), size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.headline(ctx), style = Theme.t.label, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            target?.let { "Run ${it.name}" } ?: "Choose a shortcut",
                            style = Theme.t.small, color = if (target == null) c.danger else c.ink2,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    DuoSwitch(a.enabled) { onToggle(a) }
                }
            }
            }
        }
        if (lib.automations.isEmpty()) item {
            Text("Tap + to run a shortcut at a time, when the charger or Wi-Fi changes, or when a WhatsApp call runs late.", style = Theme.t.body, color = c.ink2)
        }
    }
}

fun appLabel(ctx: android.content.Context?, pkg: String): String =
    if (pkg.isBlank()) "any app" else ctx?.let { c ->
        runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    } ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }

fun Automation.headline(ctx: android.content.Context? = null): String = when (kind) {
    TriggerKind.Time -> timeHeadline(this)
    TriggerKind.WhatsAppCallLong -> "WhatsApp call over $callMinutes min, ${windowStart.hhmm()}–${windowEnd.hhmm()}"
    TriggerKind.Charger -> if (on) "Charger connected" else "Charger disconnected"
    TriggerKind.BatteryLevel -> "Battery ${if (on) "rises above" else "falls below"} $level%"
    TriggerKind.Dnd -> "Do Not Disturb turns ${if (on) "on" else "off"}"
    TriggerKind.WiFi -> if (on) "Wi-Fi connects" else "Wi-Fi disconnects"
    TriggerKind.WhatsAppMessage -> "WhatsApp message from ${sender.ifBlank { "anyone" }}"
    TriggerKind.App -> if (app.isBlank()) "An app opens" else "${appLabel(ctx, app)} is ${if (on) "opened" else "closed"}"
    TriggerKind.AppNotification -> "Notification from ${appLabel(ctx, app)}" + if (keyword.isNotBlank()) " with “$keyword”" else ""
    TriggerKind.Alarm -> if (on) "An alarm goes off" else "An alarm is stopped"
    TriggerKind.Bluetooth -> "${device.ifBlank { "Any Bluetooth device" }} ${if (on) "connects" else "disconnects"}"
    TriggerKind.Headphones -> "Headphones ${if (on) "connected" else "disconnected"}"
    TriggerKind.AirplaneMode -> "Airplane Mode turns ${if (on) "on" else "off"}"
    TriggerKind.BatterySaver -> "Battery Saver turns ${if (on) "on" else "off"}"
    TriggerKind.Location -> (if (on) "Arrive at " else "Leave ") + place.ifBlank { "a place" }
}

fun TriggerKind.glyph(): String = when (this) {
    TriggerKind.Time -> "clock"
    TriggerKind.WhatsAppCallLong -> "phone"
    TriggerKind.Charger -> "bolt"
    TriggerKind.BatteryLevel -> "battery"
    TriggerKind.Dnd -> "moon"
    TriggerKind.WiFi -> "wifi"
    TriggerKind.WhatsAppMessage -> "quote"
    TriggerKind.App -> "grid"
    TriggerKind.AppNotification -> "bell"
    TriggerKind.Alarm -> "alarm"
    TriggerKind.Bluetooth -> "bluetooth"
    TriggerKind.Headphones -> "headphones"
    TriggerKind.AirplaneMode -> "airplane"
    TriggerKind.BatterySaver -> "battery"
    TriggerKind.Location -> "pin"
}

fun TriggerKind.tint(): app.openshortcuts.model.Tint = when (this) {
    TriggerKind.Time -> app.openshortcuts.model.Tint.Blue
    TriggerKind.WhatsAppCallLong, TriggerKind.WhatsAppMessage, TriggerKind.Charger -> app.openshortcuts.model.Tint.Green
    TriggerKind.BatteryLevel -> app.openshortcuts.model.Tint.Orange
    TriggerKind.Dnd -> app.openshortcuts.model.Tint.Indigo
    TriggerKind.WiFi -> app.openshortcuts.model.Tint.Teal
    TriggerKind.App -> app.openshortcuts.model.Tint.Indigo
    TriggerKind.AppNotification -> app.openshortcuts.model.Tint.Red
    TriggerKind.Alarm -> app.openshortcuts.model.Tint.Orange
    TriggerKind.Bluetooth -> app.openshortcuts.model.Tint.Blue
    TriggerKind.Headphones -> app.openshortcuts.model.Tint.Pink
    TriggerKind.AirplaneMode -> app.openshortcuts.model.Tint.Orange
    TriggerKind.BatterySaver -> app.openshortcuts.model.Tint.Yellow
    TriggerKind.Location -> app.openshortcuts.model.Tint.Blue
}

/**
 * The floating tab switcher, in Liquid Glass. Tap a tab, or press and hold (or just drag) to lift
 * the selection and slide it across the tabs; it ticks on each tab and lands where you let go.
 */
@Composable
private fun TabBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false, showWorkspaces: Boolean = false) {
    // With its 5dp inset the bar is exactly as tall as the rail's glass circles beside it.
    val segH = if (compact) 40.dp else Dimens.glass - 10.dp
    val c = Theme.c
    val reduced = Theme.reduced
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val labels = if (showWorkspaces) listOf("Shortcuts", "Automation", "Workspaces") else listOf("Shortcuts", "Automation")
    val maxSeg = if (compact) 108.dp else 124.dp
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val avail = (maxWidth - 10.dp).coerceAtMost(maxSeg * labels.size)
        val segDp = (avail / labels.size).coerceAtLeast(72.dp)
        val seg = with(LocalDensity.current) { segDp.toPx() }
        val last = labels.lastIndex
        val selected = tab.coerceIn(0, last)
        // The gesture below outlives recompositions; read the current tab and callback through these.
        val current by androidx.compose.runtime.rememberUpdatedState(selected)
        val select by androidx.compose.runtime.rememberUpdatedState(onTab)
        val pill = remember { androidx.compose.animation.core.Animatable(seg * selected) }
        var lifted by remember { mutableStateOf(false) }
        var pressed by remember { mutableStateOf(false) }
        var hover by remember { mutableStateOf(selected) }
        LaunchedEffect(selected, seg) { if (!lifted) pill.animateTo(seg * selected, Motion.settle()) }
        val lift by androidx.compose.animation.core.animateFloatAsState(
            if (lifted && !reduced) 1f else 0f, Motion.fling(), label = "pillLift",
        )
        val labelStyle = if (segDp < 104.dp) Theme.t.label.copy(fontSize = 13.sp) else Theme.t.label
        val shown = if (lifted) hover else selected
        Box(
            Modifier
                .glass(com.kyant.shapes.RoundedRectangle(30.dp), pressed = pressed)
                .padding(5.dp)
                .pointerInput(seg, last) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pressed = true
                        fun at(x: Float) = (x / seg).toInt().coerceIn(0, last)
                        fun follow(x: Float) {
                            val to = (x - seg / 2).coerceIn(0f, seg * last)
                            scope.launch { pill.snapTo(to) }
                            val h = ((to + seg / 2) / seg).toInt().coerceIn(0, last)
                            if (h != hover) { hover = h; view.tick() }
                        }
                        fun liftAt(x: Float) {
                            if (lifted) return
                            hover = at(x)
                            lifted = true
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            scope.launch { pill.animateTo((x - seg / 2).coerceIn(0f, seg * last), Motion.fling()) }
                        }
                        val hold = scope.launch { kotlinx.coroutines.delay(220); liftAt(down.position.x) }
                        var x = down.position.x
                        var cancelled = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) { x = ch.position.x; ch.consume(); break }
                            x = ch.position.x
                            if (!lifted && kotlin.math.abs(x - down.position.x) > viewConfiguration.touchSlop) { hold.cancel(); liftAt(x) }
                            if (lifted) { ch.consume(); follow(x) }
                            if (!lifted && kotlin.math.abs(ch.position.y - down.position.y) > viewConfiguration.touchSlop * 3) { cancelled = true; break }
                        }
                        hold.cancel()
                        pressed = false
                        val pick = if (lifted) hover else at(x)
                        lifted = false
                        if (!cancelled) {
                            if (pick != current) { view.tick(); select(pick) }
                            scope.launch { pill.animateTo(seg * pick, Motion.fling()) }
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = pill.value
                        val k = 1f + 0.12f * lift
                        scaleX = k; scaleY = k
                    }
                    .width(segDp).height(segH)
                    .shadow((10 * lift).dp, RoundedCornerShape(segH / 2), clip = false, ambientColor = c.shadow, spotColor = c.shadow)
                    .clip(RoundedCornerShape(segH / 2))
                    .background(if (lift > 0f) androidx.compose.ui.graphics.lerp(c.fill, c.raised, lift) else c.fill),
            )
            Row {
                labels.forEachIndexed { i, l ->
                    Box(
                        Modifier
                            .height(segH)
                            .width(segDp)
                            .then(if (i == 1) Modifier.tipAnchor("tabAutomation") else Modifier)
                            .semantics(mergeDescendants = true) {
                                role = androidx.compose.ui.semantics.Role.Tab
                                this.selected = i == selected
                                onClick(l) { onTab(i); true }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(l, style = labelStyle, color = if (shown == i) c.ink else c.ink2, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldToDelete(
    name: String,
    hold: androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    onTap: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Theme.c
    val view = androidx.compose.ui.platform.LocalView.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val reduced = Theme.reduced
    var pressed by remember { mutableStateOf(false) }
    val press by androidx.compose.animation.core.animateFloatAsState(if (pressed && !reduced) 0.9f else 1f, Motion.settle(), label = "dotPress")
    Box(
        modifier
            .size(40.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .semantics(mergeDescendants = true) {
                contentDescription = "More for $name"
                onClick("Edit $name") { onTap(); true }
                customActions = listOf(androidx.compose.ui.semantics.CustomAccessibilityAction("Delete $name") { onDelete(); true })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    pressed = true
                    var deleted = false
                    val run = scope.launch {
                        kotlinx.coroutines.delay(180)
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        hold.animateTo(1f, androidx.compose.animation.core.tween(((1f - hold.value) * 700).toInt(), easing = androidx.compose.animation.core.LinearEasing))
                        deleted = true
                        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM else android.view.HapticFeedbackConstants.LONG_PRESS)
                        onDelete()
                        hold.snapTo(0f)
                    }
                    val up = waitForUpOrCancellation()
                    pressed = false
                    if (!deleted) {
                        val wasTap = hold.value == 0f && run.isActive
                        run.cancel()
                        if (wasTap && up != null) { up.consume(); onTap() }
                        scope.launch { hold.animateTo(0f, androidx.compose.animation.core.tween(180, easing = Motion.easeOut)) }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.24f)).drawBehind {
                val h = size.height * hold.value
                drawRect(c.danger, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h))
            },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(if (hold.value > 0.01f) "trash" else "more", Color.White, size = if (hold.value > 0.01f) 15.dp else 18.dp)
        }
    }
}
