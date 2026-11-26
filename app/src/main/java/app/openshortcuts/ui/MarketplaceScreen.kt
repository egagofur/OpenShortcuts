package app.openshortcuts.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.Listing
import app.openshortcuts.model.MarketIndex
import app.openshortcuts.model.Marketplace
import app.openshortcuts.model.WorkspaceAdapter
import app.openshortcuts.model.WorkspaceTemplates
import kotlinx.coroutines.launch

/** Has a workspace from [l] already been added (its id, or "id-2", "id-3"…)? */
private fun added(l: Listing, adapters: List<WorkspaceAdapter>) = adapters.any { it.id == l.id || it.id.matches(Regex(Regex.escape(l.id) + "-\\d+")) }

/**
 * Workspaces ready to add: featured cards up top, then every category as a list, like an app store.
 * Opened from the Workspaces tab, and once on a fresh install so a new user starts with something.
 */
@Composable
fun MarketplaceScreen(firstRun: Boolean, onBack: () -> Unit, onAdded: (WorkspaceAdapter) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val adapters by AdapterStore.adapters.collectAsStateWithLifecycle()
    var index by remember { mutableStateOf<MarketIndex?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<Listing?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { index = Marketplace.load(ctx) }

    fun add(l: Listing) {
        if (busy != null) return
        busy = l.id
        scope.launch {
            Marketplace.workspace(ctx, l)
                .onSuccess { t ->
                    val a = WorkspaceTemplates.instantiate(t, AdapterStore.adapters.value)
                    AdapterStore.upsert(a)
                    view.confirm()
                    open = null
                    onAdded(a)
                }
                .onFailure { error = it.message }
            busy = null
        }
    }

    val all = index?.listings.orEmpty()
    val q = query.trim()
    val shown = all.filter { (category == null || it.category == category) && (q.isEmpty() || "${it.name} ${it.author} ${it.description}".contains(q, ignoreCase = true)) }

    // Full width, with the way back in a glass bar at the bottom like a workspace.
    GlassStage(Modifier.fillMaxSize().background(c.bg), chrome = {
        BottomBar(listOf({ GlassCircle(if (firstRun) "x" else "chevronLeft", if (firstRun) "Skip" else "Back", onClick = onBack) }), Modifier.align(Alignment.BottomCenter))
    }) {
        LazyColumn(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
            contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 32.dp + bottomBarSpace()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(top = 18.dp, bottom = 6.dp)) {
                    Text("Marketplace", style = Theme.t.large, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (firstRun) "Pick a workspace to start with. You can add more any time from Workspaces."
                        else "Workspaces made for the apps you use. Add one in a tap; your tokens stay on this phone.",
                        style = Theme.t.small, color = c.ink2,
                    )
                }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarketChip("All", category == null) { category = null }
                    index?.categories.orEmpty().forEach { cat -> MarketChip(cat, category == cat) { category = cat } }
                }
            }
            item { Field(query, { query = it.take(40) }, "Search workspaces", autofocus = false) }

            if (index == null) item { Text("Loading…", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(6.dp)) }

            val featured = shown.filter { it.featured }
            if (q.isEmpty() && category == null && featured.isNotEmpty()) {
                item { SectionLabel("Featured") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 12.dp)) {
                        items(featured, key = { "f" + it.id }) { l -> FeaturedCard(l, added(l, adapters)) { open = l } }
                    }
                }
            }
            val cats = if (category != null) listOf(category!!) else index?.categories.orEmpty()
            cats.forEach { cat ->
                val inCat = shown.filter { it.category == cat }
                if (inCat.isEmpty()) return@forEach
                item(key = "h$cat") { SectionLabel(cat) }
                items(inCat, key = { cat + it.id }) { l ->
                    ListingRow(l, added(l, adapters), busy == l.id, onOpen = { open = l }, onAdd = { add(l) })
                }
            }
            if (index != null && shown.isEmpty()) item {
                Text("Nothing matches “$q”.", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(6.dp))
            }
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    (if (index?.live == false) "Showing the copy built into the app. " else "") +
                        "Made a workspace others could use? Add it to the marketplace folder on GitHub.",
                    style = Theme.t.small, color = c.ink3,
                    modifier = Modifier.padding(horizontal = 6.dp).pressable(label = "Open the marketplace on GitHub") {
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://github.com/egagofur/OpenShortcuts/tree/main/marketplace")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
            }
        }
    }

    open?.let { l -> ListingDialog(l, added(l, adapters), busy == l.id, onDismiss = { open = null }, onAdd = { add(l) }) }
    error?.let { msg ->
        DuoDialog({ error = null }) {
            DialogTitle("Couldn't add it", msg)
            PrimaryButton("OK", Modifier.fillMaxWidth()) { error = null }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Theme.t.label, color = Theme.c.ink2, modifier = Modifier.padding(start = 6.dp, top = 12.dp))
}

@Composable
private fun MarketChip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    Box(
        Modifier.heightIn(min = 40.dp).pressable(scale = 0.96f, label = text, onClick = onClick)
            .clip(RoundedCornerShape(20.dp)).background(if (on) c.ink else c.fill)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Theme.t.label, color = if (on) c.bg else c.ink, maxLines = 1) }
}

/** A big tile: the workspace's color as a soft wash with its icon, then name and author. */
@Composable
private fun FeaturedCard(l: Listing, isAdded: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    val tint = l.tint.color()
    Card(Modifier.width(220.dp).pressable(scale = 0.97f, label = "Open ${l.name}", onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(132.dp).background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.08f)))),
            contentAlignment = Alignment.Center,
        ) { IconTile(l.glyph, tint, size = 60.dp) }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(l.name, style = Theme.t.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (isAdded) "Added" else "by ${l.author}", style = Theme.t.small, color = if (isAdded) c.positive else c.ink3, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(l.description, style = Theme.t.small, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListingRow(l: Listing, isAdded: Boolean, busy: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    val c = Theme.c
    Card(Modifier.fillMaxWidth().pressable(label = "Open ${l.name}", onClick = onOpen)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(l.glyph, l.tint.color(), size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(l.name, style = Theme.t.label, color = c.ink, maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text("by ${l.author}", style = Theme.t.small, color = c.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(l.description, style = Theme.t.small, color = c.ink2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            AddPill(isAdded, busy, onAdd)
        }
    }
}

@Composable
private fun AddPill(isAdded: Boolean, busy: Boolean, onAdd: () -> Unit) {
    val c = Theme.c
    Box(
        Modifier.heightIn(min = 36.dp).clip(CircleShape)
            .background(if (isAdded) c.fill else c.ink)
            .pressable(scale = 0.96f, label = if (isAdded) "Add another" else "Add", onClick = onAdd)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (busy) "Adding…" else if (isAdded) "Added" else "Add", style = Theme.t.label, color = if (isAdded) c.ink2 else c.bg, maxLines = 1)
    }
}

/** What you're adding before you add it: what it needs, where it connects, what's inside. */
@Composable
private fun ListingDialog(l: Listing, isAdded: Boolean, busy: Boolean, onDismiss: () -> Unit, onAdd: () -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    var preview by remember(l.id) { mutableStateOf<WorkspaceAdapter?>(null) }
    LaunchedEffect(l.id) { preview = Marketplace.workspace(ctx, l).getOrNull() }
    DuoDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(l.glyph, l.tint.color(), size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(l.name, style = Theme.t.title, color = c.ink)
                Text("by ${l.author}", style = Theme.t.small, color = c.ink3)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(l.description, style = Theme.t.body, color = c.ink2)
        Spacer(Modifier.height(14.dp))
        SettingsGroup(surface = c.fill) {
            SettingsRow("You'll need", trailing = { Text(l.needs.ifBlank { "Nothing" }, style = Theme.t.small, color = c.ink2) })
            preview?.let { p ->
                SettingsRow("Talks to", trailing = { Text(Marketplace.host(p), style = Theme.t.small, color = c.ink2, maxLines = 1) })
                SettingsRow("Inside", trailing = {
                    val w = p.tabs.sumOf { it.widgets.size }
                    Text("${p.tabs.size} tab${if (p.tabs.size == 1) "" else "s"} · $w widget${if (w == 1) "" else "s"}", style = Theme.t.small, color = c.ink2)
                })
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(if (busy) "Adding…" else if (isAdded) "Add another" else "Add workspace", Modifier.fillMaxWidth(), onClick = onAdd)
        Spacer(Modifier.height(8.dp))
        QuietButton("Not now", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

/** Offered once, on a fresh install, after the tour. */
object MarketOffer {
    private const val KEY = "market_offered"
    fun shouldOffer(ctx: android.content.Context): Boolean = !ctx.getSharedPreferences("tour", android.content.Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun markOffered(ctx: android.content.Context) = ctx.getSharedPreferences("tour", android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
}
