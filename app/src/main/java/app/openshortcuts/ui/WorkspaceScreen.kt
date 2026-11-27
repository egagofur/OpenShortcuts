package app.openshortcuts.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.engine.AdapterResult
import app.openshortcuts.engine.WorkspaceItem
import app.openshortcuts.model.AdapterAction
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.AdapterTab
import app.openshortcuts.model.AuthKind
import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.Tint
import app.openshortcuts.model.WidgetType
import app.openshortcuts.model.WorkspaceAdapter
import kotlinx.coroutines.launch
import org.json.JSONObject


private const val FRESH_MS = 60_000L

@Composable
fun WorkspaceHub(
    adapters: List<WorkspaceAdapter>,
    bottomPad: Dp,
    onOpen: (WorkspaceAdapter) -> Unit,
    onImport: () -> Unit,
) {
    val c = Theme.c
    val short = isShort
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
        contentPadding = PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(
                Modifier.padding(
                    top = if (short) 10.dp else 18.dp,
                    bottom = if (short) 12.dp else 18.dp,
                    end = if (short) 250.dp else 0.dp,
                ),
            ) {
                Text(
                    "Workspaces",
                    style = if (short) Theme.t.large.copy(fontSize = 28.sp, lineHeight = 32.sp) else Theme.t.large,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    when (adapters.size) {
                        0 -> "No workspaces yet"
                        1 -> "1 workspace"
                        else -> "${adapters.size} workspaces"
                    },
                    style = Theme.t.small,
                    color = c.ink2,
                )
            }
        }
        item(key = "market") {
            val openMarket = LocalOpenMarketplace.current
            Card(Modifier.fillMaxWidth().pressable(label = "Open the marketplace", onClick = openMarket)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile("store", Tint.Green.color(), size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Marketplace", style = Theme.t.label, color = c.ink)
                        Text("Add a ready-made workspace in one tap", style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Glyph("chevronRight", c.ink3, size = 18.dp)
                }
            }
        }
        items(adapters, key = { it.id }) { adapter ->
            val connected = AdapterStore.hasToken(adapter)
            Card(
                Modifier
                    .fillMaxWidth()
                    .pressable(label = "Open ${adapter.name}", onClick = { onOpen(adapter) }),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(adapter.glyph, adapter.tint.color(), size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(adapter.name, style = Theme.t.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            adapter.subtitle.ifBlank { AdapterStore.getEffectiveBaseUrl(adapter).removePrefix("https://") },
                            style = Theme.t.small,
                            color = c.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (connected) c.positive.copy(alpha = 0.14f) else c.danger.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            if (connected) "Ready" else "Set token",
                            style = Theme.t.small,
                            color = if (connected) c.positive else c.danger,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Glyph("chevronRight", c.ink3, size = 18.dp)
                }
            }
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .pressable(label = "New workspace", onClick = onImport),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile("plus", c.accent, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("New Workspace", style = Theme.t.label, color = c.ink)
                        Text("Build with AI, start blank, or paste one. Ready-made ones are in the Marketplace.", style = Theme.t.small, color = c.ink2)
                    }
                    Glyph("chevronRight", c.ink3, size = 18.dp)
                }
            }
        }
    }
}

@Composable
fun WorkspaceScreen(
    adapterId: String,
    runs: RunController,
    onOpenDetail: (widgetId: String, itemJson: String) -> Unit,
    onBack: () -> Unit,
) {
    val c = Theme.c
    val reduced = Theme.reduced
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var adapter by remember(adapterId) { mutableStateOf(AdapterStore.get(adapterId)) }
    val curAdapter = adapter
    if (curAdapter == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    // A workspace with nothing on it yet opens ready to build.
    var editMode by remember(adapterId) { mutableStateOf(curAdapter.tabs.all { it.widgets.isEmpty() }) }
    androidx.activity.compose.BackHandler(enabled = editMode) { editMode = false }
    var showSettings by remember(adapterId) { mutableStateOf(!AdapterStore.hasToken(curAdapter)) }
    var showAddTab by remember { mutableStateOf(false) }
    var showAddWidget by remember { mutableStateOf(false) }
    var showQuickJump by remember { mutableStateOf(false) }
    var activeAction by remember { mutableStateOf<Pair<AdapterAction, JSONObject?>?>(null) }
    var pendingRowAction by remember { mutableStateOf<Pair<app.openshortcuts.model.AdapterRowAction, JSONObject>?>(null) }
    var pendingRemoveTab by remember { mutableStateOf<AdapterTab?>(null) }
    var toast by remember { mutableStateOf<AdapterResult?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var sentenceFilters by remember(adapterId) { mutableStateOf<Map<String, String>>(emptyMap()) }

    val tabs = curAdapter.tabs
    val pagerState = rememberPagerState(pageCount = { tabs.size.coerceAtLeast(1) })
    val tabsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val currentTab = tabs.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState.currentPage) {
        if (tabs.isNotEmpty()) tabsListState.animateScrollToItem(pagerState.currentPage)
    }

    // Data from earlier this session is there on the first frame, before the screen slides in.
    var endpointCache by remember(adapterId) { mutableStateOf(AdapterStore.cachedInMemory(adapterId)) }
    val navIdle = LocalNavIdle.current
    var cacheReady by remember(adapterId) { mutableStateOf(false) }
    var loading by remember(adapterId) { mutableStateOf(false) }
    // When each endpoint was last fetched (kept for the app's lifetime, so leaving and reopening a
    // workspace doesn't refetch). Without a token, public APIs allow few requests an hour: wait longer.
    val fetchedAt = remember(adapterId) { AdapterStore.fetchedAt(adapterId) }
    val freshMs = if (curAdapter.tokenOptional && AdapterStore.getToken(curAdapter.id).isBlank()) 5 * FRESH_MS else FRESH_MS
    var lastRefresh by remember(adapterId) { mutableIntStateOf(0) }
    var staleNote by remember(adapterId) { mutableStateOf<String?>(null) }

    // The cache can be a megabyte of JSON: read it off the main thread so opening the workspace stays smooth.
    LaunchedEffect(adapterId) {
        val cached = withContext(Dispatchers.IO) { AdapterStore.loadCache(adapterId) }
        if (cached !== endpointCache) {
            awaitNavIdle(navIdle)
            endpointCache = cached + endpointCache
        }
        cacheReady = true
    }

    LaunchedEffect(adapterId, pagerState.currentPage, refreshTick, tabs, cacheReady) {
        if (!cacheReady || currentTab == null || !AdapterStore.hasToken(curAdapter)) return@LaunchedEffect
        val forced = refreshTick != lastRefresh
        lastRefresh = refreshTick
        val now = System.currentTimeMillis()
        val eps = buildSet {
            if (currentTab.endpoint.isNotBlank()) add(currentTab.endpoint)
            currentTab.widgets.mapNotNull { it.endpoint?.takeIf { e -> e.isNotBlank() } }.forEach { add(it) }
        }.filterTo(LinkedHashSet()) { forced || endpointCache[it]?.isSuccess != true || now - (fetchedAt[it] ?: 0L) > freshMs }
        if (eps.isEmpty()) return@LaunchedEffect
        val pageCounts = currentTab.widgets
            .mapNotNull { w -> w.endpoint?.takeIf { it.isNotBlank() }?.let { it to w.pages } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.maxOrNull() ?: 1 }
        loading = true
        try {
            val fetched = AdapterEngine.fetchEndpoints(curAdapter, eps, pageCounts)
            awaitNavIdle(navIdle)
            // Keep the last good data for endpoints that failed this time; show the error only if there's nothing.
            val merged = endpointCache.toMutableMap()
            val keptOld = fetched.filter { (ep, res) -> res.isFailure && merged[ep]?.isSuccess == true }
            staleNote = if (keptOld.isEmpty()) null
                else if (!AdapterEngine.isOnline(ctx)) "Offline. Showing saved data."
                else "Couldn't refresh. Showing saved data."
            fetched.forEach { (ep, res) ->
                if (res.isSuccess || merged[ep]?.isSuccess != true) merged[ep] = res
                if (res.isSuccess) fetchedAt[ep] = now
            }
            endpointCache = merged
            withContext(Dispatchers.IO) { AdapterStore.saveCache(adapterId, merged) }
        } finally {
            loading = false
        }
    }

    fun saveAdapter(updated: WorkspaceAdapter) {
        adapter = updated
        AdapterStore.upsert(updated)
    }

    val short = isShort
    val bottomPad = (if (short) 24.dp else 40.dp) + if (useBottomBar) bottomBarSpace() else 0.dp

    val railActions = buildList<@Composable () -> Unit> {
        add { GlassCircle("chevronLeft", "Back", onClick = onBack) }
        if (!editMode) {
            add {
                GlassCircle("grid", "Quick jump & actions", tint = c.accent) {
                    view.tick()
                    showQuickJump = true
                }
            }
        }
        add {
            GlassCircle(
                if (editMode) "check" else "layers",
                if (editMode) "Done editing" else "Edit dashboard",
                tint = if (editMode) c.positive else c.ink,
            ) {
                view.tick()
                editMode = !editMode
            }
        }
        if (editMode) {
            add {
                GlassCircle("share", "Export .osadapter", tint = c.accent) {
                    ctx.startActivity(AdapterStore.exportIntent(ctx, curAdapter))
                }
            }
        } else {
            add { GlassCircle("sliders", "Workspace settings") { showSettings = true } }
        }
    }
    val bottomBar = useBottomBar
    GlassStage(Modifier.fillMaxSize().background(c.bg), chrome = {
        if (bottomBar) BottomBar(railActions, Modifier.align(Alignment.BottomCenter))
    }) {
    Row(Modifier.fillMaxSize().padding(end = if (bottomBar) Dimens.gutter - 4.dp else 0.dp)) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
        ) {
            Column(Modifier.padding(start = Dimens.gutter, end = 8.dp, top = if (short) 8.dp else 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        curAdapter.name,
                        style = if (short) Theme.t.title else Theme.t.large,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (editMode) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(c.accentWash)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) { Text("Editing", style = Theme.t.small, color = c.accent) }
                    } else if (loading) {
                        Text("Syncing…", style = Theme.t.small, color = c.ink3)
                    }
                }
                if (!short) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        curAdapter.subtitle.ifBlank { AdapterStore.getEffectiveBaseUrl(curAdapter).removePrefix("https://") },
                        style = Theme.t.small,
                        color = c.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                staleNote?.takeIf { !loading && !editMode }?.let { note ->
                    Spacer(Modifier.height(2.dp))
                    Text(note, style = Theme.t.small, color = c.danger, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Top Scrollable Pill Tabs
            if (tabs.isNotEmpty() || editMode) {
                Spacer(Modifier.height(if (short) 6.dp else 12.dp))
                LazyRow(
                    state = tabsListState,
                    contentPadding = PaddingValues(start = Dimens.gutter, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                        val active = pagerState.currentPage == i
                        val bg by animateColorAsState(if (active) c.ink else c.raised, tween(160, easing = Motion.easeOut), label = "pillBg")
                        val fg by animateColorAsState(if (active) c.card else c.ink2, tween(160, easing = Motion.easeOut), label = "pillFg")
                        var showTabInfo by remember { mutableStateOf(false) }
                        LaunchedEffect(showTabInfo) {
                            if (showTabInfo) {
                                kotlinx.coroutines.delay(1800)
                                showTabInfo = false
                            }
                        }
                        Box {
                            Row(
                                Modifier
                                    .height(if (short) 32.dp else 36.dp)
                                    .pressable(
                                        scale = 0.96f,
                                        label = t.label,
                                        onLongClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            showTabInfo = true
                                        },
                                        onClick = {
                                            showTabInfo = false
                                            view.tick()
                                            scope.launch {
                                                if (reduced) pagerState.scrollToPage(i)
                                                else pagerState.animateScrollToPage(i, animationSpec = Motion.settle())
                                            }
                                        },
                                    )
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(bg)
                                    .padding(horizontal = if (short) 12.dp else 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(t.label, style = Theme.t.label.copy(fontSize = 14.sp), color = fg, maxLines = 1)
                                if (editMode && tabs.size > 1) {
                                    Spacer(Modifier.width(6.dp))
                                    Glyph(
                                        "x",
                                        fg.copy(alpha = 0.7f),
                                        size = 14.dp,
                                        modifier = Modifier.pressable(scale = 0.9f, label = "Remove ${t.label}") {
                                            if (t.widgets.isNotEmpty()) pendingRemoveTab = t
                                            else saveAdapter(curAdapter.copy(tabs = tabs.filterNot { it.id == t.id }))
                                        },
                                    )
                                }
                            }
                            if (showTabInfo) {
                                val ep = t.endpoint.ifBlank { t.widgets.firstOrNull()?.endpoint.orEmpty() }
                                val res = endpointCache[ep]?.getOrNull()
                                val countInfo = when {
                                    t.widgets.any { it.type == WidgetType.LIST || it.type == WidgetType.GROUPED_LIST } -> {
                                        val lw = t.widgets.first { it.type == WidgetType.LIST || it.type == WidgetType.GROUPED_LIST }
                                        val n = AdapterEngine.extractList(res, lw.itemsPath).size
                                        if (res != null) "$n items" else "Not loaded"
                                    }
                                    else -> "${t.widgets.size} widgets"
                                }
                                androidx.compose.ui.window.Popup(
                                    alignment = Alignment.BottomStart,
                                    offset = androidx.compose.ui.unit.IntOffset(0, 12),
                                    onDismissRequest = { showTabInfo = false },
                                ) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(c.ink)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Text("${t.label} · $countInfo", style = Theme.t.small, color = c.card, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }
                    }
                    if (editMode) {
                        item {
                            Box(
                                Modifier
                                    .height(36.dp)
                                    .pressable(scale = 0.96f, label = "Add tab") { showAddTab = true }
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(c.accentWash)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("+ Tab", style = Theme.t.label.copy(fontSize = 14.sp), color = c.accent) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (short) 6.dp else 10.dp))

            if (!AdapterStore.hasToken(curAdapter)) {
                Box(Modifier.padding(horizontal = Dimens.gutter)) {
                    Card(Modifier.fillMaxWidth().pressable(label = "Set token") { showSettings = true }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile("lock", Tint.Orange.color(), size = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                val needsInput = curAdapter.inputs.isNotEmpty() && (curAdapter.authKind == AuthKind.NONE || curAdapter.tokenOptional)
                                Text(if (needsInput) "One more step" else "Token required", style = Theme.t.label, color = c.ink)
                                Text(
                                    if (needsInput) "Tap to add your ${curAdapter.inputs.joinToString(" and ") { it.label.lowercase() }}." else "Tap to add your token locally.",
                                    style = Theme.t.small, color = c.ink2,
                                )
                            }
                            Glyph("chevronRight", c.ink3, size = 18.dp)
                        }
                    }
                }
            } else if (tabs.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { pageIdx ->
                    val tab = tabs.getOrNull(pageIdx) ?: return@HorizontalPager
                    TabDashboardPage(
                        adapter = curAdapter,
                        tab = tab,
                        cache = endpointCache,
                        loading = loading,
                        editMode = editMode,
                        bottomPad = bottomPad,
                        runs = runs,
                        sentenceFilters = sentenceFilters,
                        onFilterChange = { k, v -> sentenceFilters = sentenceFilters + (k to v) },
                        onRetry = { view.tick(); refreshTick++ },
                        onOpenSettings = { showSettings = true },
                        onRunAction = { act -> activeAction = act to null },
                        onLiveResult = { act, res ->
                            if (res.ok) {
                                LiveOverrides.note(curAdapter, act) { w -> endpointCache[w.endpoint.orEmpty()]?.getOrNull() }
                                refreshTick++
                            } else {
                                view.performHapticFeedback(
                                    if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS,
                                )
                                toast = res
                            }
                        },
                        onSwipeRow = { act, raw ->
                            view.tick()
                            pendingRowAction = act to raw
                        },
                        onOpenDetail = { widgetId, item -> onOpenDetail(widgetId, item.raw.toString()) },
                        tabs = tabs,
                        onMoveWidget = { from, to ->
                            if (from in tab.widgets.indices && to in tab.widgets.indices) {
                                val mutable = tab.widgets.toMutableList()
                                mutable.add(to, mutable.removeAt(from))
                                saveAdapter(curAdapter.copy(tabs = tabs.mapIndexed { idx, t -> if (idx == pageIdx) t.copy(widgets = mutable) else t }))
                            }
                        },
                        onMoveTab = { from, to ->
                            if (from in tabs.indices && to in tabs.indices) {
                                // Stay on the tab you were looking at, wherever it moved to.
                                val shownId = tabs.getOrNull(pagerState.currentPage)?.id
                                val mutable = tabs.toMutableList()
                                mutable.add(to, mutable.removeAt(from))
                                saveAdapter(curAdapter.copy(tabs = mutable))
                                val newIdx = mutable.indexOfFirst { it.id == shownId }.coerceAtLeast(0)
                                scope.launch { pagerState.scrollToPage(newIdx) }
                            }
                        },
                        onDeleteWidget = { widgetId ->
                            saveAdapter(curAdapter.copy(tabs = tabs.mapIndexed { idx, t ->
                                if (idx == pageIdx) t.copy(widgets = t.widgets.filterNot { it.id == widgetId }) else t
                            }))
                        },
                        onAddWidget = { showAddWidget = true },
                    )
                }
            }
        }

        if (!bottomBar) Rail(*railActions.toTypedArray())
    }
    }

    if (showQuickJump) {
        DuoDialog(onDismiss = { showQuickJump = false }) {
            DialogTitle(curAdapter.name, "Jump to a tab or run a quick action.")
            if (tabs.isNotEmpty()) {
                Text("Tabs", style = Theme.t.small, color = c.ink3)
                Spacer(Modifier.height(6.dp))
                tabs.forEachIndexed { idx, t ->
                    val active = pagerState.currentPage == idx
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .pressable(label = t.label) {
                                view.tick()
                                showQuickJump = false
                                scope.launch {
                                    if (reduced) pagerState.scrollToPage(idx)
                                    else pagerState.animateScrollToPage(idx, animationSpec = Motion.settle())
                                }
                            },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.label, style = Theme.t.label, color = if (active) c.accent else c.ink, modifier = Modifier.weight(1f))
                            if (active) Glyph("check", c.accent, size = 18.dp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (curAdapter.globalActions.isNotEmpty()) {
                Text("Quick Actions", style = Theme.t.small, color = c.ink3)
                Spacer(Modifier.height(6.dp))
                curAdapter.globalActions.forEach { act ->
                    PrimaryButton(act.title, Modifier.fillMaxWidth().padding(bottom = 8.dp), color = act.tint.color()) {
                        showQuickJump = false
                        activeAction = act to null
                    }
                }
            }
            QuietButton("Refresh Now", Modifier.fillMaxWidth()) {
                showQuickJump = false
                view.tick()
                refreshTick++
            }
        }
    }

    if (showAddTab) {
        AddTabDialog(
            onDismiss = { showAddTab = false },
            onAdd = { label ->
                showAddTab = false
                val id = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "tab_${tabs.size + 1}" }
                saveAdapter(curAdapter.copy(tabs = tabs + AdapterTab(id = id, label = label)))
            },
        )
    }

    if (showAddWidget && currentTab != null) {
        AddWidgetDialog(
            adapter = curAdapter,
            defaultEndpoint = currentTab.endpoint,
            onDismiss = { showAddWidget = false },
            onAdd = { w ->
                showAddWidget = false
                val idx = pagerState.currentPage
                saveAdapter(curAdapter.copy(tabs = tabs.mapIndexed { i, t ->
                    if (i == idx) t.copy(widgets = t.widgets + w) else t
                }))
                refreshTick++
            },
        )
    }

    activeAction?.let { (act, item) ->
        AdapterActionDialog(
            adapter = curAdapter,
            action = act,
            item = item,
            onDismiss = { activeAction = null },
            onResult = { res ->
                activeAction = null
                // A start/stop card shows the result itself; other actions get the "Done" dialog.
                val shownOnCard = res.ok && LiveOverrides.note(curAdapter, act) { w -> endpointCache[w.endpoint.orEmpty()]?.getOrNull() }
                if (res.ok) { view.confirm(); refreshTick++ }
                if (!shownOnCard) toast = res
            },
        )
    }

    if (showSettings) {
        WorkspaceSettingsDialog(
            adapter = curAdapter,
            onDismiss = { showSettings = false },
            onSaved = { showSettings = false; refreshTick++ },
            onExport = {
                showSettings = false
                ctx.startActivity(AdapterStore.exportIntent(ctx, curAdapter))
            },
            onDelete = {
                showSettings = false
                AdapterStore.delete(curAdapter.id)
                onBack()
            },
        )
    }

    toast?.let { res ->
        DuoDialog(onDismiss = { toast = null }) {
            DialogTitle(if (res.ok) "Done" else "Error (${res.statusCode})", res.message)
            PrimaryButton("OK", Modifier.fillMaxWidth()) { toast = null }
        }
    }

    pendingRowAction?.let { (act, raw) ->
        val itemTitle = AdapterEngine.extractString(raw, "title")
            .ifBlank { AdapterEngine.extractString(raw, "name") }
            .ifBlank { AdapterEngine.extractString(raw, "description") }
        val isDanger = act.method.equals("DELETE", ignoreCase = true)
        ConfirmActionDialog(
            title = "${act.label}?",
            body = buildString {
                if (itemTitle.isNotBlank()) append("“${itemTitle.take(60)}”\n")
                append("${act.method.uppercase()} ${act.endpoint.substringBefore("?")}")
            },
            confirmLabel = act.label,
            confirmColor = if (isDanger) c.danger else act.tint.color(),
            onDismiss = { pendingRowAction = null },
            onConfirm = {
                pendingRowAction = null
                scope.launch {
                    val res = AdapterEngine.runRowAction(curAdapter, act, raw)
                    if (res.ok) { view.confirm(); refreshTick++ }
                    else view.performHapticFeedback(
                        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
                        else HapticFeedbackConstants.LONG_PRESS,
                    )
                    toast = res
                }
            },
        )
    }

    pendingRemoveTab?.let { t ->
        ConfirmActionDialog(
            title = "Remove “${t.label}”?",
            body = "This removes ${t.widgets.size} widget(s) from this tab.",
            confirmLabel = "Remove Tab",
            confirmColor = c.danger,
            onDismiss = { pendingRemoveTab = null },
            onConfirm = {
                pendingRemoveTab = null
                saveAdapter(curAdapter.copy(tabs = tabs.filterNot { it.id == t.id }))
            },
        )
    }
}

@Composable
internal fun ConfirmActionDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    DuoDialog(onDismiss = onDismiss) {
        DialogTitle(title, body)
        PrimaryButton(confirmLabel, Modifier.fillMaxWidth(), color = confirmColor, onClick = onConfirm)
        Spacer(Modifier.height(8.dp))
        QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
private fun TabDashboardPage(
    adapter: WorkspaceAdapter,
    tab: AdapterTab,
    cache: Map<String, Result<Any?>>,
    loading: Boolean,
    editMode: Boolean,
    bottomPad: Dp,
    runs: RunController,
    sentenceFilters: Map<String, String>,
    onFilterChange: (String, String) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onRunAction: (AdapterAction) -> Unit,
    onLiveResult: (AdapterAction, AdapterResult) -> Unit,
    onSwipeRow: (app.openshortcuts.model.AdapterRowAction, JSONObject) -> Unit,
    onOpenDetail: (widgetId: String, item: WorkspaceItem) -> Unit,
    tabs: List<AdapterTab>,
    onMoveWidget: (Int, Int) -> Unit,
    onMoveTab: (Int, Int) -> Unit,
    onDeleteWidget: (String) -> Unit,
    onAddWidget: () -> Unit,
) {
    val c = Theme.c
    var searchQueries by remember(tab.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Parse each list widget once per payload, not on every recomposition (typing, loading, swipes).
    val parsedLists = remember(tab.widgets, cache) {
        tab.widgets.filter { it.type == WidgetType.LIST || it.type == WidgetType.GROUPED_LIST }.associate { w ->
            val ep = w.endpoint?.takeIf { it.isNotBlank() } ?: tab.endpoint
            w.id to parseListItems(w, cache[ep]?.getOrNull())
        }
    }
    val filteredLists = remember(parsedLists, searchQueries) {
        parsedLists.mapValues { (id, items) ->
            val q = searchQueries[id].orEmpty()
            if (q.isBlank()) items
            else items.filter { (_, it) ->
                it.title.contains(q, true) || it.subtitle.contains(q, true) || it.meta.contains(q, true) || it.badge.contains(q, true)
            }
        }
    }
    // Pull down to refresh, the way every feed works; edit mode keeps the drag for reordering.
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = loading && !editMode,
        onRefresh = { if (!editMode) onRetry() },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = bottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (editMode) {
            if (tabs.size > 1 || tab.widgets.size > 1) item(key = "edit_hint") {
                Text("Hold and drag a row to reorder.", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            if (tabs.size > 1) {
                item(key = "edit_tabs") {
                    Column {
                        Text("Tabs", style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        ReorderableColumn(tabs, keyOf = { it.id }, onMove = onMoveTab) { t, _ ->
                            EditRow(
                                glyph = "grid",
                                tint = if (t.id == tab.id) c.accent else c.ink3,
                                title = t.label,
                                subtitle = if (t.widgets.size == 1) "1 widget" else "${t.widgets.size} widgets",
                            )
                        }
                    }
                }
            }
            item(key = "edit_widgets_${tab.id}") {
                Column {
                    Text("Widgets on ${tab.label}", style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                    if (tab.widgets.isEmpty()) {
                        Text("No widgets yet. Add one below.", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(start = 4.dp))
                    }
                    ReorderableColumn(tab.widgets, keyOf = { it.id }, onMove = onMoveWidget) { w, _ ->
                        EditRow(
                            glyph = w.glyph.takeIf { it != "none" } ?: "layers",
                            tint = w.tint.color(),
                            title = w.title.ifBlank { w.type.label },
                            subtitle = w.type.label,
                            onRemove = { onDeleteWidget(w.id) },
                        )
                    }
                }
            }
        }
        if (!editMode) tab.widgets.forEachIndexed { wIdx, w ->
            val ep = w.endpoint?.takeIf { it.isNotBlank() } ?: tab.endpoint
            val result = cache[ep]
            val payload = result?.getOrNull()
            val err = result?.exceptionOrNull()?.message

            val needsFetch = w.type != WidgetType.ACTION_GRID && w.type != WidgetType.NOTE_CARD && w.type != WidgetType.SENTENCE_BAR
            if (result == null && loading && needsFetch) {
                item(key = "skel_${w.id}") {
                    val h = when (w.type) {
                        WidgetType.HEATMAP_CHART -> 168
                        WidgetType.BAR_CHART, WidgetType.BREAKDOWN -> 228
                        WidgetType.TARGET_BAR -> 150
                        WidgetType.LIST, WidgetType.GROUPED_LIST -> 88
                        else -> 82
                    }
                    SkeletonCard(h)
                }
            } else if (result == null && needsFetch) {
                // Not fetched (yet): say so and offer a refresh, instead of an empty-looking widget.
                item(key = "none_${w.id}") {
                    EmptyWidgetCard("${w.title.ifBlank { tab.label }} hasn't loaded yet.", "Refresh", onRetry)
                }
            } else if (err != null && needsFetch) {
                item(key = "err_${w.id}") {
                    ErrorWidgetCard(
                        title = w.title.ifBlank { tab.label },
                        error = err,
                        onRetry = onRetry,
                        onSettings = onOpenSettings,
                    )
                }
            } else {
                when (w.type) {
                    WidgetType.STAT_GRID -> item(key = w.id) { StaggeredBlock(wIdx) { StatGridWidget(w, payload) } }
                    WidgetType.PROGRESS_RING -> item(key = w.id) { StaggeredBlock(wIdx) { ProgressRingWidget(w, payload) } }
                    WidgetType.TARGET_BAR -> item(key = w.id) { StaggeredBlock(wIdx) { TargetBarWidget(w, payload) } }
                    WidgetType.LIVE_STATUS -> item(key = w.id) { StaggeredBlock(wIdx) { LiveStatusWidget(w, adapter, payload, onRunAction, onLiveResult) } }
                    WidgetType.SHARE_CARD -> item(key = w.id) { StaggeredBlock(wIdx) { ShipCardTile(w, adapter.id) } }
                    WidgetType.ACTION_GRID -> item(key = w.id) { StaggeredBlock(wIdx) { ActionGridWidget(w, adapter, runs, onRunAction) } }
                    WidgetType.SENTENCE_BAR -> item(key = w.id) {
                        StaggeredBlock(wIdx) {
                            SentenceBarWidget(w, sentenceFilters[w.paramKey].orEmpty()) { onFilterChange(w.paramKey, it) }
                        }
                    }
                    WidgetType.NOTE_CARD -> item(key = w.id) { StaggeredBlock(wIdx) { NoteCardWidget(w) } }
                    WidgetType.PROFILE_CARD -> item(key = w.id) { StaggeredBlock(wIdx) { ProfileCardWidget(w, payload) } }
                    WidgetType.BAR_CHART -> item(key = w.id) { StaggeredBlock(wIdx) { BarChartWidget(w, payload) } }
                    WidgetType.HEATMAP_CHART -> item(key = w.id) { StaggeredBlock(wIdx) { HeatmapChartWidget(w, payload) } }
                    WidgetType.BREAKDOWN -> item(key = w.id) { StaggeredBlock(wIdx) { BreakdownWidget(w, payload) } }
                    WidgetType.LIST, WidgetType.GROUPED_LIST -> {
                        val query = searchQueries[w.id].orEmpty()
                        val allItems = parsedLists[w.id].orEmpty()
                        val rawList = filteredLists[w.id].orEmpty()
                        if (w.title.isNotBlank()) {
                            item(key = "hdr_${w.id}") {
                                Text(w.title, style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        // A search box earns its place on a long list, or when the adapter asks for one.
                        if (w.searchable || allItems.size > 8 || query.isNotEmpty()) {
                            item(key = "search_${w.id}") {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c.raised)
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                ) {
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { v -> searchQueries = searchQueries + (w.id to v) },
                                        textStyle = Theme.t.body.copy(color = c.ink),
                                        cursorBrush = SolidColor(c.accent),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { inner ->
                                            if (query.isEmpty()) {
                                                Text("Search ${w.title.ifBlank { "list" }}…", style = Theme.t.body, color = c.ink3)
                                            }
                                            inner()
                                        },
                                    )
                                }
                            }
                        }
                        val swipeR = w.rowActions.firstOrNull { it.swipe == "right" }
                        val swipeL = w.rowActions.firstOrNull { it.swipe == "left" }
                        if (w.type == WidgetType.GROUPED_LIST) {
                            val grouped = rawList.groupBy { it.second.group.ifBlank { "Other" } }
                            grouped.forEach { (grp, groupItems) ->
                                item(key = "grp_${w.id}_$grp") {
                                    val total = groupItems.mapNotNull { it.second.value }.takeIf { it.isNotEmpty() }?.sum()
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(AdapterEngine.prettyBadge(grp), style = Theme.t.label, color = c.ink2, modifier = Modifier.weight(1f))
                                        if (total != null) {
                                            Text(
                                                formatRowValue(total, w.unit),
                                                style = Theme.t.label.copy(fontFeatureSettings = "tnum"),
                                                color = c.ink2,
                                            )
                                        }
                                    }
                                }
                                items(groupItems, key = { (k, _) -> "${w.id}_$k" }) { (_, item) ->
                                    WorkspaceRowCard(item, w, swipeR, swipeL, onSwipe = { act -> onSwipeRow(act, item.raw) }) {
                                        onOpenDetail(w.id, item)
                                    }
                                }
                            }
                        } else {
                            items(rawList, key = { (k, _) -> "${w.id}_$k" }) { (_, item) ->
                                WorkspaceRowCard(item, w, swipeR, swipeL, onSwipe = { act -> onSwipeRow(act, item.raw) }) {
                                    onOpenDetail(w.id, item)
                                }
                            }
                        }
                        if (rawList.isEmpty() && payload != null) {
                            item(key = "empty_${w.id}") {
                                if (query.isNotBlank()) {
                                    EmptyWidgetCard("No results for “$query”.", "Clear") {
                                        searchQueries = searchQueries - w.id
                                    }
                                } else {
                                    EmptyWidgetCard("Nothing here right now.", "Refresh", onRetry)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (editMode) {
            item(key = "add_widget") {
                Card(Modifier.fillMaxWidth().pressable(label = "Add widget", onClick = onAddWidget)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile("plus", c.accent, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Add Widget to “${tab.label}”", style = Theme.t.label, color = c.accent)
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun AdapterActionDialog(
    adapter: WorkspaceAdapter,
    action: AdapterAction,
    item: JSONObject?,
    onDismiss: () -> Unit,
    onResult: (AdapterResult) -> Unit,
) {
    val c = Theme.c
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var values by remember(action.id) {
        mutableStateOf(action.params.associate { p ->
            val prefill = if (item != null && p.default.isEmpty()) {
                val snake = p.key.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
                AdapterEngine.extractString(item, p.key)
                    .ifBlank { AdapterEngine.extractString(item, snake) }
                    .ifBlank {
                        when {
                            p.key == "projectId" -> AdapterEngine.extractString(item, "project.id")
                            p.key == "endDate" -> AdapterEngine.extractString(item, "start_date")
                            else -> ""
                        }
                    }
            } else p.default
            p.key to prefill
        })
    }
    var optionLabels by remember(action.id) {
        mutableStateOf<Map<String, String>>(
            if (item != null) mapOf("projectId" to AdapterEngine.extractString(item, "project.name")).filterValues { it.isNotBlank() }
            else emptyMap(),
        )
    }
    var busy by remember { mutableStateOf(false) }
    var pickingTimeFor by remember { mutableStateOf<app.openshortcuts.model.AdapterParam?>(null) }
    var pickingOptionFor by remember { mutableStateOf<app.openshortcuts.model.AdapterParam?>(null) }

    DuoDialog(onDismiss = onDismiss) {
        DialogTitle(action.title, action.confirmMessage)
        action.params.forEach { p ->
            val isTime = p.kind == "time" || p.kind == "utc_iso" || p.key.equals("endDate", ignoreCase = true)
            val isOption = !p.optionsEndpoint.isNullOrBlank()
            Text(p.label, style = Theme.t.small, color = c.ink2)
            Spacer(Modifier.height(4.dp))
            if (isOption) {
                val curVal = values[p.key].orEmpty()
                val label = optionLabels[p.key]?.takeIf { it.isNotBlank() } ?: curVal.ifBlank { p.placeholder.ifBlank { "Select" } }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.fill)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(if (curVal.isNotBlank()) "#$curVal" else "Choose", style = Theme.t.small, color = c.ink2)
                    TokenPill(label) { pickingOptionFor = p }
                }
            } else if (isTime) {
                val cur = values[p.key].orEmpty()
                val display = remember(cur) {
                    if (p.kind == "utc_iso" || p.key.equals("endDate", ignoreCase = true)) {
                        runCatching {
                            val z = java.time.Instant.parse(cur).atZone(java.time.ZoneId.systemDefault())
                            String.format("%02d:%02d", z.hour, z.minute)
                        }.getOrElse { cur.ifBlank { "Pick time" } }
                    } else cur.ifBlank { "Pick time" }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.fill)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Local time", style = Theme.t.body, color = c.ink2)
                    TokenPill(display) { pickingTimeFor = p }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.fill)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = values[p.key].orEmpty(),
                        onValueChange = { v -> values = values + (p.key to v) },
                        textStyle = Theme.t.body.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (values[p.key].isNullOrEmpty() && p.placeholder.isNotBlank()) {
                                Text(p.placeholder, style = Theme.t.body, color = c.ink3)
                            }
                            inner()
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        PrimaryButton(if (busy) "Running…" else action.title, Modifier.fillMaxWidth(), color = action.tint.color()) {
            if (busy) return@PrimaryButton
            busy = true
            scope.launch {
                val res = AdapterEngine.runAction(adapter, action, values, item)
                busy = false
                onResult(res)
            }
        }
        Spacer(Modifier.height(8.dp))
        QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
    }

    pickingTimeFor?.let { p ->
        val cur = values[p.key].orEmpty()
        val isUtc = p.kind == "utc_iso" || p.key.equals("endDate", ignoreCase = true)
        val initialMinutes = remember(cur) {
            if (isUtc) {
                runCatching {
                    val z = java.time.Instant.parse(cur).atZone(java.time.ZoneId.systemDefault())
                    z.hour * 60 + z.minute
                }.getOrDefault(17 * 60)
            } else {
                val parts = cur.split(":")
                if (parts.size == 2) (parts[0].toIntOrNull() ?: 9) * 60 + (parts[1].toIntOrNull() ?: 0) else 9 * 60
            }
        }
        TimeWheelDialog(p.label, initialMinutes, onDismiss = { pickingTimeFor = null }) { mins ->
            pickingTimeFor = null
            val h = mins / 60
            val m = mins % 60
            val formatted = if (isUtc) {
                val baseDate = runCatching {
                    java.time.Instant.parse(cur.ifBlank { item?.optString("start_date").orEmpty() })
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                }.getOrDefault(java.time.LocalDate.now())
                java.time.ZonedDateTime.of(baseDate, java.time.LocalTime.of(h, m), java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toString()
            } else {
                String.format("%02d:%02d", h, m)
            }
            values = values + (p.key to formatted)
        }
    }

    pickingOptionFor?.let { p ->
        var options by remember(p.optionsEndpoint) { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var loadingOpts by remember(p.optionsEndpoint) { mutableStateOf(true) }
        var query by remember { mutableStateOf("") }
        LaunchedEffect(p.optionsEndpoint) {
            val ep = p.optionsEndpoint ?: return@LaunchedEffect
            loadingOpts = true
            val res = AdapterEngine.fetchEndpoints(adapter, setOf(ep))[ep]
            val list = AdapterEngine.extractList(res?.getOrNull(), p.optionsPath).mapNotNull { obj ->
                val v = AdapterEngine.extractString(obj, p.optionValuePath)
                val l = AdapterEngine.extractString(obj, p.optionLabelPath)
                if (v.isBlank() || l.isBlank()) null else v to l
            }
            options = list
            loadingOpts = false
        }
        DuoDialog(onDismiss = { pickingOptionFor = null }) {
            DialogTitle("Select ${p.label}", if (loadingOpts) "From ${adapter.name}" else "${options.size} available")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.fill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = Theme.t.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search…", style = Theme.t.body, color = c.ink3)
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            val filtered = remember(options, query) {
                if (query.isBlank()) options else options.filter { it.second.contains(query, ignoreCase = true) || it.first.contains(query, ignoreCase = true) }
            }
            if (loadingOpts) SkeletonRows(4) else LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = if (isShort) 150.dp else 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.first }) { (valStr, lblStr) ->
                    val selected = values[p.key] == valStr
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .pressable(label = lblStr) {
                                view.tick()
                                values = values + (p.key to valStr)
                                optionLabels = optionLabels + (p.key to lblStr)
                                pickingOptionFor = null
                            },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lblStr, style = Theme.t.label, color = if (selected) c.accent else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("#$valStr", style = Theme.t.small, color = c.ink3)
                            }
                            if (selected) Glyph("check", c.accent, size = 18.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            QuietButton("Cancel", Modifier.fillMaxWidth()) { pickingOptionFor = null }
        }
    }
}

@Composable
private fun WorkspaceSettingsDialog(
    adapter: WorkspaceAdapter,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Theme.c
    val view = LocalView.current
    var token by remember(adapter.id) { mutableStateOf(AdapterStore.getToken(adapter.id)) }
    var revealToken by remember(adapter.id) { mutableStateOf(false) }
    var baseUrl by remember(adapter.id) { mutableStateOf(AdapterStore.getEffectiveBaseUrl(adapter)) }
    val inputs = remember(adapter.id) { androidx.compose.runtime.mutableStateMapOf<String, String>().apply { adapter.inputs.forEach { put(it.key, AdapterStore.getInput(adapter.id, it.key)) } } }
    var confirmRemove by remember { mutableStateOf(false) }

    val maskTransformation = remember {
        androidx.compose.ui.text.input.VisualTransformation { text ->
            val masked = buildString {
                text.text.forEachIndexed { idx, ch ->
                    append(if (text.text.length > 8 && idx < 4) ch else '•')
                }
            }
            androidx.compose.ui.text.input.TransformedText(
                androidx.compose.ui.text.AnnotatedString(masked),
                androidx.compose.ui.text.input.OffsetMapping.Identity,
            )
        }
    }

    DuoDialog(onDismiss = onDismiss) {
        DialogTitle("${adapter.name} Settings", "Tokens stay on this device and are never exported.")
        adapter.inputs.forEach { input ->
            Text(input.label, style = Theme.t.small, color = c.ink2)
            Spacer(Modifier.height(4.dp))
            Field(inputs[input.key].orEmpty(), { inputs[input.key] = it.take(100) }, input.hint.ifBlank { input.label }, autofocus = false)
            Spacer(Modifier.height(12.dp))
        }
        if (adapter.authKind != AuthKind.NONE) {
            Text(if (adapter.tokenOptional) "Access Token (optional)" else "Access Token", style = Theme.t.small, color = c.ink2)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.fill)
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    visualTransformation = if (revealToken) androidx.compose.ui.text.input.VisualTransformation.None else maskTransformation,
                    textStyle = Theme.t.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    decorationBox = { inner ->
                        if (token.isEmpty()) Text(adapter.tokenHint, style = Theme.t.body, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    },
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .pressable(scale = 0.9f, label = if (revealToken) "Hide token" else "Show token") {
                            view.tick()
                            revealToken = !revealToken
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(if (revealToken) "eyeOff" else "eye", c.ink2, size = 19.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Text("Base URL", style = Theme.t.small, color = c.ink2)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.fill)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                textStyle = Theme.t.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Save", Modifier.fillMaxWidth()) {
            AdapterStore.setToken(adapter.id, token)
            AdapterStore.setBaseUrlOverride(adapter.id, baseUrl)
            inputs.forEach { (k, v) -> AdapterStore.setInput(adapter.id, k, v.removePrefix("@")) }
            onSaved()
        }
        Spacer(Modifier.height(8.dp))
        QuietButton("Export .osadapter Template", Modifier.fillMaxWidth(), onClick = onExport)
        Spacer(Modifier.height(8.dp))
        QuietButton("Remove Workspace", Modifier.fillMaxWidth(), color = c.danger) { confirmRemove = true }
    }

    if (confirmRemove) {
        ConfirmActionDialog(
            title = "Remove ${adapter.name}?",
            body = "Your local dashboard layout and saved token for ${adapter.name} will be deleted from this device.",
            confirmLabel = "Remove Workspace",
            confirmColor = c.danger,
            onDismiss = { confirmRemove = false },
            onConfirm = { confirmRemove = false; onDelete() },
        )
    }
}

@Composable
fun ImportAdapterDialog(
    adapter: WorkspaceAdapter,
    onCancel: () -> Unit,
    onAdd: () -> Unit,
) {
    val c = Theme.c
    DuoDialog(onDismiss = onCancel) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(adapter.glyph, adapter.tint.color(), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(adapter.name, style = Theme.t.title, color = c.ink)
                Text(adapter.subtitle.ifBlank { "${adapter.tabs.size} tabs" }, style = Theme.t.small, color = c.ink2)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Allowed domains", style = Theme.t.small, color = c.ink3)
        Spacer(Modifier.height(4.dp))
        Text(
            adapter.allowedDomains.joinToString(", ").ifBlank { adapter.baseUrl },
            style = Theme.t.label,
            color = c.ink,
        )
        Spacer(Modifier.height(10.dp))
        Text("Dashboard tabs", style = Theme.t.small, color = c.ink3)
        Spacer(Modifier.height(4.dp))
        Text(
            adapter.tabs.joinToString(" · ") { it.label }.ifBlank { "Custom actions" },
            style = Theme.t.body,
            color = c.ink2,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Add Workspace", Modifier.fillMaxWidth(), onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onCancel)
    }
}

/**
 * Turns a list widget's payload into rows, each with a key from the item's own id when it has one,
 * so swipe and animation state stay with the right row after searching or refreshing.
 */
private fun parseListItems(w: DashboardWidget, payload: Any?): List<Pair<String, WorkspaceItem>> {
    if (payload == null) return emptyList()
    val seen = HashSet<String>()
    val today = java.time.LocalDate.now()
    return AdapterEngine.extractList(payload, w.itemsPath)
        .asSequence()
        .filterNot { obj ->
            w.excludePath.isNotBlank() && w.excludeValues.any { ex ->
                AdapterEngine.extractString(obj, w.excludePath).equals(ex, ignoreCase = true)
            }
        }
        .mapIndexed { idx, obj ->
            val id = listOf("id", "iid", "uuid", "key").firstNotNullOfOrNull { k -> obj.optString(k).takeIf { it.isNotBlank() && it != "null" } }
            val key = (id ?: "i$idx").let { if (seen.add(it)) it else "$it#$idx" }
            key to WorkspaceItem(
                title = AdapterEngine.extractString(obj, w.titlePath).ifBlank { "Untitled" },
                subtitle = AdapterEngine.extractString(obj, w.subtitlePath),
                badge = AdapterEngine.extractString(obj, w.badgePath),
                meta = AdapterEngine.extractString(obj, w.metaPath),
                group = when {
                    w.type != WidgetType.GROUPED_LIST -> ""
                    w.groupBy == "week" -> AdapterEngine.weekLabel(AdapterEngine.toLocalDate(AdapterEngine.extractString(obj, w.metaPath)), today)
                    else -> AdapterEngine.extractString(obj, w.groupPath)
                },
                raw = obj,
                value = w.valuePath.takeIf { it.isNotBlank() }?.let { AdapterEngine.extractNumber(obj, it) },
            )
        }
        .toList()
}

@Composable
private fun EditRow(glyph: String, tint: Color, title: String, subtitle: String, onRemove: (() -> Unit)? = null) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(60.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(glyph, tint, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Theme.t.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = Theme.t.small, color = c.ink3, maxLines = 1)
            }
            if (onRemove != null) RemoveDot("Remove $title", onClick = onRemove)
            Spacer(Modifier.width(10.dp))
            Glyph("menu", c.ink3, size = 18.dp)
        }
    }
}
