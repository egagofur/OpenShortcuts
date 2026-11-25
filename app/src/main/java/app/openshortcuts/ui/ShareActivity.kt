package app.openshortcuts.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openshortcuts.model.Store
import kotlinx.coroutines.delay

/**
 * "Run with OpenShortcuts" in Android's share sheet, and "Run Shortcut" in the menu that appears
 * when you select text. The shared or selected text becomes the shortcut's {Shortcut Input}.
 */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.init(this)
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }.orEmpty().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to run a shortcut on.", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        setContent { ShortcutsTheme { SharePicker(text, subject, onClose = ::finish) } }
    }
}

@Composable
private fun SharePicker(text: String, subject: String, onClose: () -> Unit) {
    val c = Theme.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val lib by Store.library.collectAsStateWithLifecycle()
    val runs = remember { RunController(ctx.applicationContext) }
    var started by remember { mutableStateOf<String?>(null) }
    val listed = lib.shortcuts.filter { it.inShareSheet }
    val shown = listed.ifEmpty { lib.shortcuts }

    // Finished quietly (nothing to show): say so and get out of the way.
    LaunchedEffect(runs.done) {
        if (runs.done != null) {
            Toast.makeText(ctx, "Ran ${lib.shortcuts.firstOrNull { it.id == runs.done }?.name.orEmpty()}", Toast.LENGTH_SHORT).show()
            delay(150); onClose()
        }
    }

    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .pressable(scale = 1f, label = "Close") { if (started == null) onClose() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (started == null) Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(c.card)
                .pressable(scale = 1f, label = "Sheet") {}
                .padding(20.dp),
        ) {
            Text("Run with OpenShortcuts", style = Theme.t.title, color = c.ink)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.bg).padding(12.dp)) {
                Text(text, style = Theme.t.small.copy(fontSize = 14.sp), color = c.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (listed.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Tip: turn on “Show in Share Sheet” in a shortcut's details to list only the ones you use here.",
                    style = Theme.t.small, color = c.ink2)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(shown, key = { it.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .pressable(scale = 0.98f, label = "Run ${s.name}") {
                                started = s.id
                                runs.run(s, input = text, vars = mapOf("Shared Subject" to subject))
                            }
                            .clip(RoundedCornerShape(14.dp)).background(c.bg).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(s.glyph, s.tint.color(), size = 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(s.name, style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink, modifier = Modifier.weight(1f))
                        Glyph("playFill", c.accent, size = 18.dp)
                    }
                }
            }
            if (shown.isEmpty()) Text("Make a shortcut in OpenShortcuts first.", style = Theme.t.body, color = c.ink2)
        }
    }

    runs.prompt?.let { (p, _) -> PromptDialog(p) { runs.answer(it) } }
    runs.result?.let { r -> ResultDialog(r, onDismiss = { runs.result = null; onClose() }, onSetup = onClose) }
}
