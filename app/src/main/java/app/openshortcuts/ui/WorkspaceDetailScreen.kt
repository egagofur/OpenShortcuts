package app.openshortcuts.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.engine.AdapterResult
import app.openshortcuts.model.AdapterAction
import app.openshortcuts.model.AdapterRowAction
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.DetailField
import app.openshortcuts.model.Store
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun WorkspaceDetailScreen(
    adapterId: String,
    widgetId: String,
    itemJson: String,
    runs: RunController,
    onBack: () -> Unit,
) {
    val c = Theme.c
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val adapter = AdapterStore.get(adapterId)
    val widget = adapter?.tabs?.flatMap { it.widgets }?.firstOrNull { it.id == widgetId }
    val initialRaw = remember(itemJson) { runCatching { JSONObject(itemJson) }.getOrDefault(JSONObject()) }

    if (adapter == null || widget == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var raw by remember(itemJson) { mutableStateOf(initialRaw) }
    var comments by remember(itemJson) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var commits by remember(itemJson) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var changes by remember(itemJson) { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadingComments by remember { mutableStateOf(false) }
    var loadingCommits by remember { mutableStateOf(false) }
    var loadingChanges by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var activeAction by remember { mutableStateOf<AdapterAction?>(null) }
    var pendingRowAction by remember { mutableStateOf<AdapterRowAction?>(null) }
    var showQuickActions by remember { mutableStateOf(false) }
    var commentsExpanded by remember { mutableStateOf(true) }
    var toast by remember { mutableStateOf<AdapterResult?>(null) }
    var rawTab by remember { mutableIntStateOf(0) } // 0 = Formatted, 1 = Raw
    var sectionTab by remember { mutableIntStateOf(0) } // 0 = Details, 1 = Commits, 2 = Code

    val detail = widget.detail
    val hasCommits = !detail?.commitsEndpoint.isNullOrBlank()
    val hasChanges = !detail?.changesEndpoint.isNullOrBlank()

    LaunchedEffect(refreshTick) {
        val ep = detail?.endpoint
        if (!ep.isNullOrBlank()) {
            loading = true
            detailError = null
            AdapterEngine.fetchDetail(adapter, ep, initialRaw)
                .onSuccess { raw = it }
                .onFailure { detailError = it.message ?: "Failed to load details" }
            loading = false
        }
        val cep = detail?.commentsEndpoint
        if (!cep.isNullOrBlank()) {
            loadingComments = true
            AdapterEngine.fetchComments(adapter, cep, initialRaw).onSuccess { comments = it }
            loadingComments = false
        }
        val comEp = detail?.commitsEndpoint
        if (!comEp.isNullOrBlank()) {
            loadingCommits = true
            AdapterEngine.fetchArray(adapter, comEp, initialRaw).onSuccess { commits = it }
            loadingCommits = false
        }
        val chgEp = detail?.changesEndpoint
        if (!chgEp.isNullOrBlank()) {
            loadingChanges = true
            AdapterEngine.fetchArray(adapter, chgEp, initialRaw, "changes").onSuccess { changes = it }
            loadingChanges = false
        }
    }

    val title = AdapterEngine.extractString(raw, widget.titlePath).ifBlank { "Details" }
    val badge = AdapterEngine.extractString(raw, widget.badgePath)
    val body = detail?.bodyPath?.takeIf { it.isNotBlank() }?.let { AdapterEngine.extractString(raw, it) }
        ?: AdapterEngine.extractString(raw, "description").ifBlank { AdapterEngine.extractString(raw, "body") }
    val webUrl = detail?.urlPath?.takeIf { it.isNotBlank() }?.let { AdapterEngine.extractString(raw, it) }
        ?: AdapterEngine.extractString(raw, "web_url")

    val fields: List<Pair<String, String>> = remember(raw, detail) {
        if (detail != null && detail.fields.isNotEmpty()) {
            detail.fields.mapNotNull { f: DetailField ->
                val v = AdapterEngine.formatIfTimestamp(AdapterEngine.extractString(raw, f.path))
                if (v.isNotBlank()) f.label to v else null
            }
        } else {
            raw.keys().asSequence().toList().take(10).mapNotNull { k ->
                val v = raw.opt(k)
                if (v == null || v == JSONObject.NULL) null
                else {
                    val str = when (v) {
                        is JSONObject -> v.optString("name", v.optString("title", "")).ifBlank { null }
                        else -> AdapterEngine.formatIfTimestamp(v.toString()).take(140)
                    }
                    if (str.isNullOrBlank()) null else k.replace('_', ' ') to str
                }
            }
        }
    }

    val sections = remember(hasCommits, hasChanges, commits.size, changes.size) {
        buildList {
            add(0 to "Details")
            if (hasCommits) add(1 to if (commits.isEmpty()) "Commits" else "Commits (${commits.size})")
            if (hasChanges) add(2 to if (changes.isEmpty()) "Code" else "Code (${changes.size})")
        }
    }

    val short = isShort
    val rail = buildList<@Composable () -> Unit> {
        add { GlassCircle("chevronLeft", "Back", onClick = onBack) }
        add {
            GlassCircle("grid", "Quick actions & navigation", tint = c.accent) {
                view.tick()
                showQuickActions = true
            }
        }
        if (!detail?.endpoint.isNullOrBlank() || !detail?.commentsEndpoint.isNullOrBlank() || hasCommits || hasChanges) {
            add { GlassCircle("refresh", "Refresh") { view.tick(); refreshTick++ } }
        }
    }
    val bottomBar = useBottomBar
    Box(Modifier.fillMaxSize().background(c.bg)) {
    Row(Modifier.fillMaxSize().padding(end = if (bottomBar) Dimens.gutter - 4.dp else 0.dp)) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start)),
            contentPadding = PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = (if (short) 24.dp else 40.dp) + if (useBottomBar) bottomBarSpace() else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(top = if (short) 12.dp else 24.dp, bottom = 4.dp)) {
                    if (badge.isNotBlank()) {
                        val badgeTint = widget.badgeTints.entries.firstOrNull { it.key.equals(badge.trim(), ignoreCase = true) }?.value?.color()
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeTint?.copy(alpha = 0.16f) ?: c.accentWash)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                AdapterEngine.prettyBadge(badge),
                                style = Theme.t.small.copy(fontSize = 12.sp),
                                color = badgeTint ?: c.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        title,
                        style = if (short) Theme.t.title else Theme.t.large.copy(fontSize = 24.sp, lineHeight = 29.sp),
                        color = c.ink,
                    )
                    if (loading) {
                        Spacer(Modifier.height(6.dp))
                        SkeletonCard(64)
                    } else if (detailError != null) {
                        Spacer(Modifier.height(6.dp))
                        ErrorWidgetCard("Couldn't refresh", detailError.orEmpty(), onRetry = { view.tick(); refreshTick++ })
                    }
                }
            }

            if (sections.size > 1) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.fill)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        sections.forEach { (id, lbl) ->
                            val active = sectionTab == id
                            Box(
                                Modifier
                                    .weight(1f)
                                    .pressable(scale = 0.96f, label = lbl) { view.tick(); sectionTab = id }
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) c.card else Color.Transparent)
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    lbl,
                                    style = Theme.t.small.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                                    color = if (active) c.ink else c.ink2,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            if (sectionTab == 0) {
                if (body.isNotBlank() && body != title) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Description", style = Theme.t.small, color = c.ink3)
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(c.fill)
                                            .padding(3.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        listOf("Formatted", "Raw").forEachIndexed { idx, lbl ->
                                            val active = rawTab == idx
                                            Box(
                                                Modifier
                                                    .pressable(scale = 0.95f, label = lbl) { view.tick(); rawTab = idx }
                                                    .clip(RoundedCornerShape(11.dp))
                                                    .background(if (active) c.card else c.fill)
                                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text(
                                                    lbl,
                                                    style = Theme.t.small.copy(fontSize = 12.sp, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                                                    color = if (active) c.ink else c.ink2,
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                if (rawTab == 0) {
                                    val formatted = remember(body, c) { formatMarkdown(body, c) }
                                    Text(formatted, style = Theme.t.body, color = c.ink)
                                } else {
                                    Text(
                                        body,
                                        style = Theme.t.small.copy(fontFamily = FontFamily.Monospace),
                                        color = c.ink2,
                                    )
                                }
                            }
                        }
                    }
                }

                if (fields.isNotEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                fields.forEach { (k, v) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(k, style = Theme.t.small, color = c.ink3, modifier = Modifier.weight(0.42f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(v, style = Theme.t.label, color = c.ink, modifier = Modifier.weight(0.58f))
                                    }
                                }
                            }
                        }
                    }
                }

                if (!detail?.commentsEndpoint.isNullOrBlank()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(label = "Toggle comments") { view.tick(); commentsExpanded = !commentsExpanded }
                                .padding(start = 4.dp, top = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                if (loadingComments) "Comments…" else "Comments (${comments.size})",
                                style = Theme.t.label,
                                color = c.ink2,
                            )
                            Glyph(if (commentsExpanded) "chevronDown" else "chevronRight", c.ink3, size = 16.dp)
                        }
                    }
                    if (commentsExpanded) {
                        items(comments) { cmt ->
                            val author = AdapterEngine.extractString(cmt, detail?.commentAuthorPath ?: "author.name").ifBlank { "User" }
                            val cmtBody = AdapterEngine.extractString(cmt, detail?.commentBodyPath ?: "body")
                            val time = AdapterEngine.formatIfTimestamp(AdapterEngine.extractString(cmt, detail?.commentTimePath ?: "created_at"))
                            ExpandableCommentCard(author, time, cmtBody, rawTab, c)
                        }
                        if (!loadingComments && comments.isEmpty()) {
                            item { EmptyWidgetCard("No comments yet.") }
                        }
                    }
                }

                item {
                    DetailActionsBlock(
                        primary = detail?.actions?.firstOrNull() ?: widget.primaryAction,
                        extraActions = detail?.actions?.drop(1).orEmpty(),
                        rowActions = widget.rowActions,
                        webUrl = webUrl,
                        raw = raw,
                        runs = runs,
                        onRunAction = { activeAction = it },
                        onRunRowAction = { pendingRowAction = it },
                        onOpenUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    )
                }
            } else if (sectionTab == 1) {
                if (loadingCommits && commits.isEmpty()) {
                    items(3) { SkeletonCard(76) }
                } else if (commits.isEmpty()) {
                    item { EmptyWidgetCard("No commits found.") }
                } else {
                    itemsIndexed(commits, key = { i, cm -> cm.optString("id", "$i") }) { _, cm ->
                        CommitCard(cm)
                    }
                }
            } else {
                if (loadingChanges && changes.isEmpty()) {
                    items(3) { SkeletonCard(82) }
                } else if (changes.isEmpty()) {
                    item { EmptyWidgetCard("No file changes found.") }
                } else {
                    itemsIndexed(changes, key = { i, ch -> ch.optString("new_path", "$i") }) { _, ch ->
                        DiffFileCard(ch)
                    }
                }
                item {
                    DetailActionsBlock(
                        primary = detail?.actions?.firstOrNull() ?: widget.primaryAction,
                        extraActions = detail?.actions?.drop(1).orEmpty(),
                        rowActions = widget.rowActions,
                        webUrl = webUrl,
                        raw = raw,
                        runs = runs,
                        onRunAction = { activeAction = it },
                        onRunRowAction = { pendingRowAction = it },
                        onOpenUrl = { url -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    )
                }
            }
        }

        if (!bottomBar) Rail(*rail.toTypedArray())
    }
    if (bottomBar) BottomBar(rail, Modifier.align(Alignment.BottomCenter))
    }

    if (showQuickActions) {
        DuoDialog(onDismiss = { showQuickActions = false }) {
            DialogTitle("Quick Actions", title.take(70))
            if (sections.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { (id, lbl) ->
                        val active = sectionTab == id
                        Box(
                            Modifier
                                .weight(1f)
                                .pressable(scale = 0.96f, label = lbl) {
                                    view.tick()
                                    sectionTab = id
                                    showQuickActions = false
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) c.accentWash else c.fill)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(lbl.substringBefore(" ("), style = Theme.t.small, color = if (active) c.accent else c.ink, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            val primary = detail?.actions?.firstOrNull() ?: widget.primaryAction
            val shownTitles = buildSet {
                primary?.let { add(it.title.lowercase()) }
                detail?.actions?.drop(1)?.forEach { add(it.title.lowercase()) }
            }
            primary?.let { act ->
                PrimaryButton(act.title, Modifier.fillMaxWidth().padding(bottom = 8.dp), color = act.tint.color()) {
                    showQuickActions = false
                    activeAction = act
                }
            }
            detail?.actions?.drop(1)?.forEach { act ->
                QuietButton(act.title, Modifier.fillMaxWidth().padding(bottom = 8.dp), color = act.tint.color()) {
                    showQuickActions = false
                    activeAction = act
                }
            }
            widget.rowActions
                .filterNot { it.label.lowercase() in shownTitles }
                .forEach { rAct ->
                    QuietButton(rAct.label, Modifier.fillMaxWidth().padding(bottom = 8.dp), color = rAct.tint.color()) {
                        showQuickActions = false
                        pendingRowAction = rAct
                    }
                }
            if (webUrl.startsWith("http://") || webUrl.startsWith("https://")) {
                QuietButton("Open in Browser", Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    showQuickActions = false
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
                }
            }
            QuietButton("Close", Modifier.fillMaxWidth()) { showQuickActions = false }
        }
    }

    activeAction?.let { act ->
        AdapterActionDialog(
            adapter = adapter,
            action = act,
            item = raw,
            onDismiss = { activeAction = null },
            onResult = { res ->
                activeAction = null
                if (res.ok) { view.confirm(); refreshTick++ }
                toast = res
            },
        )
    }

    toast?.let { res ->
        DuoDialog(onDismiss = { toast = null }) {
            DialogTitle(if (res.ok) "Done" else "Error (${res.statusCode})", res.message)
            PrimaryButton("OK", Modifier.fillMaxWidth()) { toast = null }
        }
    }

    pendingRowAction?.let { rAct ->
        val isDanger = rAct.method.equals("DELETE", ignoreCase = true)
        ConfirmActionDialog(
            title = "${rAct.label}?",
            body = "“${title.take(60)}”\n${rAct.method.uppercase()} ${rAct.endpoint.substringBefore("?")}",
            confirmLabel = rAct.label,
            confirmColor = if (isDanger) c.danger else rAct.tint.color(),
            onDismiss = { pendingRowAction = null },
            onConfirm = {
                pendingRowAction = null
                scope.launch {
                    val res = AdapterEngine.runRowAction(adapter, rAct, raw)
                    if (res.ok) { view.confirm(); refreshTick++ }
                    toast = res
                }
            },
        )
    }
}

@Composable
private fun DetailActionsBlock(
    primary: AdapterAction?,
    extraActions: List<AdapterAction>,
    rowActions: List<AdapterRowAction>,
    webUrl: String,
    raw: JSONObject,
    runs: RunController,
    onRunAction: (AdapterAction) -> Unit,
    onRunRowAction: (AdapterRowAction) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val shownTitles = buildSet {
        primary?.let { add(it.title.lowercase()) }
        extraActions.forEach { add(it.title.lowercase()) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        primary?.let { act ->
            PrimaryButton(act.title, Modifier.fillMaxWidth(), color = act.tint.color()) { onRunAction(act) }
        }
        extraActions.forEach { act ->
            QuietButton(act.title, Modifier.fillMaxWidth(), color = act.tint.color()) { onRunAction(act) }
        }
        rowActions
            .filterNot { it.label.lowercase() in shownTitles }
            .forEach { rAct ->
                QuietButton(rAct.label, Modifier.fillMaxWidth(), color = rAct.tint.color()) { onRunRowAction(rAct) }
            }
        if (webUrl.startsWith("http://") || webUrl.startsWith("https://")) {
            QuietButton("Open in Browser", Modifier.fillMaxWidth()) { onOpenUrl(webUrl) }
        }
        val shareShortcuts = Store.library.value.shortcuts.filter { it.inShareSheet }
        shareShortcuts.firstOrNull()?.let { s ->
            QuietButton("Run “${s.name}” with this item", Modifier.fillMaxWidth()) {
                runs.run(s, input = raw.toString())
            }
        }
    }
}

@Composable
private fun ExpandableCommentCard(author: String, time: String, body: String, rawTab: Int, c: Palette) {
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val isLong = body.length > 160 || body.count { it == '\n' } >= 3
    Card(
        Modifier
            .fillMaxWidth()
            .then(if (isLong) Modifier.pressable(label = "Expand comment by $author") { view.tick(); expanded = !expanded } else Modifier),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(author, style = Theme.t.label, color = c.ink, modifier = Modifier.weight(1f))
                if (time.isNotBlank()) Text(time, style = Theme.t.small, color = c.ink3)
                if (isLong) {
                    Spacer(Modifier.width(6.dp))
                    Glyph(if (expanded) "chevronDown" else "chevronRight", c.ink3, size = 14.dp)
                }
            }
            if (body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                val maxL = if (expanded || !isLong) Int.MAX_VALUE else 3
                if (rawTab == 0) {
                    val formatted = remember(body, c) { formatMarkdown(body, c) }
                    Text(formatted, style = Theme.t.body, color = c.ink2, maxLines = maxL, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(body, style = Theme.t.small.copy(fontFamily = FontFamily.Monospace), color = c.ink2, maxLines = maxL, overflow = TextOverflow.Ellipsis)
                }
                if (isLong && !expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to read more", style = Theme.t.small, color = c.accent)
                }
            }
        }
    }
}

@Composable
private fun CommitCard(cm: JSONObject) {
    val c = Theme.c
    val view = LocalView.current
    var expanded by remember { mutableStateOf(false) }
    val sha = cm.optString("short_id", cm.optString("id").take(8))
    val title = cm.optString("title", "Commit")
    val msg = cm.optString("message", "").trim()
    val author = cm.optString("author_name", "")
    val date = AdapterEngine.formatIfTimestamp(cm.optString("authored_date", cm.optString("created_at", "")))

    Card(Modifier.fillMaxWidth().pressable(label = title) { view.tick(); expanded = !expanded }) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(c.accentWash)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(sha, style = Theme.t.small.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = c.accent)
                }
                if (date.isNotBlank()) Text(date, style = Theme.t.small, color = c.ink3)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, style = Theme.t.label, color = c.ink, maxLines = if (expanded) 10 else 2, overflow = TextOverflow.Ellipsis)
            if (author.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(author, style = Theme.t.small, color = c.ink2)
            }
            if (expanded && msg.isNotBlank() && msg != title) {
                Spacer(Modifier.height(8.dp))
                Text(msg, style = Theme.t.small.copy(fontFamily = FontFamily.Monospace), color = c.ink2)
            }
        }
    }
}

@Composable
private fun DiffFileCard(ch: JSONObject) {
    val c = Theme.c
    val view = LocalView.current
    var expanded by remember { mutableStateOf(true) }
    val path = ch.optString("new_path", ch.optString("old_path", "file"))
    val diff = ch.optString("diff", "")
    val isNew = ch.optBoolean("new_file", false)
    val isDel = ch.optBoolean("deleted_file", false)
    val isRen = ch.optBoolean("renamed_file", false)

    val lines = remember(diff) { if (diff.isBlank()) emptyList() else diff.split("\n") }
    val added = remember(lines) { lines.count { it.startsWith("+") && !it.startsWith("+++") } }
    val removed = remember(lines) { lines.count { it.startsWith("-") && !it.startsWith("---") } }

    val statusLabel = when {
        isNew -> "NEW"
        isDel -> "DEL"
        isRen -> "REN"
        else -> "MOD"
    }
    val statusColor = when {
        isNew -> c.positive
        isDel -> c.danger
        else -> c.accent
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().pressable(label = path) { view.tick(); expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(statusColor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(statusLabel, style = Theme.t.small.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = statusColor)
                }
                Spacer(Modifier.width(8.dp))
                Text(path, style = Theme.t.label.copy(fontSize = 13.sp), color = c.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Text("+$added -$removed", style = Theme.t.small.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = c.ink2, softWrap = false)
                Spacer(Modifier.width(6.dp))
                Glyph(if (expanded) "chevronDown" else "chevronRight", c.ink3, size = 16.dp)
            }

            if (expanded && lines.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                // Every line spans the longest line (at least the box), so +/- highlights form full-width rows.
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.bg),
                ) {
                val boxWidth = maxWidth
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Column(Modifier.widthIn(min = boxWidth).width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                    lines.take(250).forEach { ln ->
                        val bg = when {
                            ln.startsWith("@@") -> c.accentWash
                            ln.startsWith("+") -> c.positive.copy(alpha = 0.15f)
                            ln.startsWith("-") -> c.danger.copy(alpha = 0.15f)
                            else -> Color.Transparent
                        }
                        val fg = when {
                            ln.startsWith("@@") -> c.accent
                            ln.startsWith("+") -> c.positive
                            ln.startsWith("-") -> c.danger
                            else -> c.ink2
                        }
                        Box(Modifier.fillMaxWidth().background(bg).padding(horizontal = 10.dp, vertical = 1.5.dp)) {
                            Text(
                                ln.ifEmpty { " " },
                                style = Theme.t.small.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = fg,
                                softWrap = false,
                            )
                        }
                    }
                    if (lines.size > 250) {
                        Text(
                            "… ${lines.size - 250} more lines (open in browser for full diff)",
                            style = Theme.t.small,
                            color = c.ink3,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
                }
                }
            }
        }
    }
}

private fun formatMarkdown(md: String, c: Palette): AnnotatedString = buildAnnotatedString {
    val cleaned = md.replace(Regex("""<!--[\s\S]*?-->"""), "").replace(Regex("""(?i)<br\s*/?>"""), "\n").trim()
    val lines = cleaned.replace("\r\n", "\n").split("\n")
    var inCode = false
    lines.forEachIndexed { idx, rawLine ->
        val trimmed = rawLine.trimStart()
        if (trimmed.startsWith("```")) {
            inCode = !inCode
            if (idx < lines.lastIndex) append("\n")
            return@forEachIndexed
        }
        if (inCode) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.fill, fontSize = 13.sp)) {
                append(rawLine)
            }
        } else {
            when {
                trimmed.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)) {
                    appendInlineMd(trimmed.removePrefix("### "), c)
                }
                trimmed.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                    appendInlineMd(trimmed.removePrefix("## "), c)
                }
                trimmed.startsWith("# ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
                    appendInlineMd(trimmed.removePrefix("# "), c)
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("• ")
                    appendInlineMd(trimmed.drop(2), c)
                }
                trimmed.startsWith("> ") -> withStyle(SpanStyle(color = c.ink2, fontStyle = FontStyle.Italic)) {
                    append("▎ ")
                    appendInlineMd(trimmed.drop(2), c)
                }
                else -> appendInlineMd(rawLine, c)
            }
        }
        if (idx < lines.lastIndex) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInlineMd(text: String, c: Palette) {
    val token = Regex("""(\\[\\`*_{}\[\]()#+\-.!~|>]|\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[[^\]]+\]\([^)]+\))""")
    var pos = 0
    for (m in token.findAll(text)) {
        if (m.range.first > pos) append(text.substring(pos, m.range.first))
        val v = m.value
        when {
            v.length == 2 && v[0] == '\\' -> append(v[1])
            v.startsWith("**") && v.endsWith("**") ->
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(v.drop(2).dropLast(2)) }
            v.startsWith("*") && v.endsWith("*") ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(v.drop(1).dropLast(1)) }
            v.startsWith("`") && v.endsWith("`") ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = c.fill, fontSize = 14.sp)) {
                    append(" ${v.drop(1).dropLast(1)} ")
                }
            v.startsWith("[") -> {
                val label = v.substringAfter("[").substringBefore("]")
                withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Medium)) { append(label) }
            }
            else -> append(v)
        }
        pos = m.range.last + 1
    }
    if (pos < text.length) append(text.substring(pos))
}
