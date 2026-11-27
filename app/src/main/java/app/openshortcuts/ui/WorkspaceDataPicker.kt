package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.WorkspaceRead
import app.openshortcuts.model.WorkspaceReads

/** "This week from Dotify", for the shortcut's sentence. */
fun workspaceReadLabel(raw: String): String? {
    val read = WorkspaceRead.decode(raw) ?: return null
    val (a, _) = WorkspaceReads.resolve(read) ?: return null
    return "${read.label} from ${a.name}"
}

private class Choice(val workspace: String, val glyph: String, val tint: androidx.compose.ui.graphics.Color, val read: WorkspaceRead, val now: String)

/**
 * Pick a value from the widgets you already have, no paths: every widget offers what it shows (a number,
 * how many items, whether a timer runs), with the value it has right now. Searchable.
 */
@Composable
fun WorkspaceDataPicker(current: String, onDone: (String) -> Unit) {
    val c = Theme.c
    val picked = remember(current) { WorkspaceRead.decode(current) }
    var query by remember { mutableStateOf("") }
    val choices = remember {
        AdapterStore.adapters.value.flatMap { a ->
            val cache = AdapterStore.cachedInMemory(a.id)
            a.tabs.flatMap { it.widgets }.flatMap { w ->
                val payload = cache[WorkspaceReads.endpointOf(a, w)]?.getOrNull()
                WorkspaceReads.outputs(w).map { o ->
                    val now = if (payload == null) "" else runCatching { WorkspaceReads.value(w, payload, o.key) }.getOrDefault("")
                    Choice(a.name, a.glyph, a.tint.color(), WorkspaceRead(a.id, w.id, o.key, o.label), now.lineSequence().firstOrNull().orEmpty().take(32))
                }
            }
        }
    }
    Text("Choose a value", style = Theme.t.label, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text("From the widgets on your workspaces. It's read fresh each time the shortcut runs.", style = Theme.t.small, color = c.ink2)
    Spacer(Modifier.height(10.dp))
    if (choices.isEmpty()) {
        Text("Add a workspace with some widgets first, from the Marketplace or your own API.", style = Theme.t.body, color = c.ink2)
        return
    }
    SearchField(query, { query = it }, "Search, e.g. hours, pull requests", surface = c.fill)
    Spacer(Modifier.height(8.dp))
    val q = query.trim()
    val shown = choices.filter { q.isEmpty() || "${it.workspace} ${it.read.label} ${it.now}".contains(q, ignoreCase = true) }
    LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(shown, key = { "${it.read.adapterId}/${it.read.widgetId}/${it.read.output}" }) { ch ->
            val on = picked?.let { it.adapterId == ch.read.adapterId && it.widgetId == ch.read.widgetId && it.output == ch.read.output } == true
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) c.accent.copy(alpha = 0.12f) else c.fill)
                    .pressable(scale = 0.98f, label = ch.read.label) { onDone(ch.read.encode()) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(ch.glyph, ch.tint, size = 30.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ch.read.label, style = Theme.t.label, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ch.workspace, style = Theme.t.small, color = c.ink3, maxLines = 1)
                }
                if (ch.now.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.bg).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(ch.now, style = Theme.t.small, color = c.ink2, maxLines = 1)
                    }
                }
            }
        }
        if (shown.isEmpty()) item { Text("Nothing matches “$q”.", style = Theme.t.small, color = c.ink3, modifier = Modifier.padding(8.dp)) }
    }
}
