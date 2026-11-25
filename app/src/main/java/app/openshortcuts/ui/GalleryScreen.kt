package app.openshortcuts.ui

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Gallery
import app.openshortcuts.model.Portable
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.Store

@Composable
fun GalleryScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = Theme.c
    val view = LocalView.current
    var reviewing by remember { mutableStateOf<Pair<Gallery.Item, Pair<Shortcut, Automation?>>?>(null) }
    val added = remember { mutableStateListOf<String>() }
    var lastAdded by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            Modifier.weight(1f).fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom)),
            contentPadding = PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                    Text("Gallery", style = Theme.t.large, color = c.ink)
                    Spacer(Modifier.height(2.dp))
                    Text("Ready-made shortcuts. Tap one to see what it does before adding it.", style = Theme.t.small, color = c.ink2)
                }
            }
            Gallery.sections.forEach { section ->
                item(key = "h$section") {
                    Text(section, style = Theme.t.label, color = c.ink2, modifier = Modifier.padding(start = 6.dp, top = 12.dp))
                }
                items(Gallery.items.filter { it.section == section }, key = { it.title }) { item ->
                    val isAdded = item.title in added
                    Card(Modifier.fillMaxWidth().pressable(label = "Preview ${item.title}") { reviewing = item to item.build() }) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(item.glyph, item.tint.color(), size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = Theme.t.label, color = c.ink)
                                Text(item.description, style = Theme.t.small, color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(34.dp).clip(CircleShape).background(if (isAdded) c.positive else c.accentWash),
                                contentAlignment = Alignment.Center,
                            ) { Glyph(if (isAdded) "check" else "plus", if (isAdded) androidx.compose.ui.graphics.Color.White else c.accent, size = 18.dp) }
                        }
                    }
                }
            }
        }
        Rail({ GlassCircle("chevronLeft", "Back", onClick = onBack) })
    }

    reviewing?.let { (item, built) ->
        val (s, auto) = built
        ImportDialog(
            Portable.Imported(s, 0), note = item.description, automation = auto,
            onCancel = { reviewing = null },
            onAdd = {
                reviewing = null
                val unique = s.copy(name = Portable.uniqueName(s.name, Store.library.value))
                Store.upsert(unique)
                auto?.let { Store.upsert(it) }
                added += item.title
                lastAdded = unique.id
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            },
        )
    }
    androidx.compose.runtime.LaunchedEffect(lastAdded) {
        if (lastAdded != null) { kotlinx.coroutines.delay(4000); lastAdded = null }
    }
    Box(Modifier.fillMaxSize()) {
        UndoBar(lastAdded?.let { "Added to your shortcuts" }, "Open") { lastAdded?.let { id -> lastAdded = null; onOpen(id) } }
    }
}
