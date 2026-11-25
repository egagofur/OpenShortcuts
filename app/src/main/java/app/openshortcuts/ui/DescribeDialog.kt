package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.Describe
import app.openshortcuts.model.Store
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val EXAMPLES = listOf(
    "Kalau Mama kirim WhatsApp setelah jam 23:00, balas aku sudah tidur",
    "Every weekday at 07:00, turn off Do Not Disturb and read my calendar for today",
    "Ambil semua link dari teks yang aku bagikan lalu salin",
    "When the charger is unplugged and battery is under 30%, turn brightness down",
)

/** Write a sentence, get a shortcut to review. Asks for a Claude API key the first time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescribeDialog(onDismiss: () -> Unit, onDraft: (Describe.Draft) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasKey by remember { mutableStateOf(Describe.key(ctx) != null) }
    var keyText by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val busy = job?.isActive == true

    DuoDialog(onDismiss = { job?.cancel(); onDismiss() }) {
        DialogTitle("Describe a Shortcut", "Say what it should do, and when. You'll see it before it's added.")
        if (!hasKey) {
            Text("Uses Claude with your own API key from console.anthropic.com. Your description and the names of your shortcuts are sent to Anthropic; nothing else.",
                style = Theme.t.small, color = c.ink2)
            Spacer(Modifier.height(10.dp))
            Field(keyText, { keyText = it.trim() }, "sk-ant-…", secret = true)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Save Key", Modifier.fillMaxWidth()) {
                if (keyText.startsWith("sk-ant-")) { Describe.saveKey(ctx, keyText); hasKey = true; error = null }
                else error = "That doesn't look like a Claude API key (it starts with sk-ant-)."
            }
        } else {
            val focus = remember { FocusRequester() }
            Box(
                Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp),
            ) {
                if (text.isEmpty()) Text("e.g. When Mama messages me after 23:00, reply that I'm asleep",
                    style = Theme.t.body.copy(fontSize = 17.sp), color = c.ink3)
                BasicTextField(
                    value = text, onValueChange = { text = it; error = null }, enabled = !busy,
                    textStyle = Theme.t.body.copy(fontSize = 17.sp, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
            if (text.isEmpty() && !busy) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EXAMPLES.forEach { e ->
                        Box(
                            Modifier.pressable(scale = 0.97f, label = "Use example") { text = e }
                                .clip(RoundedCornerShape(12.dp)).background(c.accentWash).padding(horizontal = 10.dp, vertical = 7.dp),
                        ) { Text(e, style = Theme.t.small, color = c.accent) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (busy) Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Building your shortcut…", style = Theme.t.label, color = c.ink2)
            } else PrimaryButton("Create", Modifier.fillMaxWidth()) {
                error = null
                job = scope.launch {
                    runCatching { Describe.describe(ctx, text, Store.library.value) }
                        .onSuccess(onDraft)
                        .onFailure { if (it !is kotlinx.coroutines.CancellationException) error = it.message ?: "Something went wrong." }
                    job = null
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Remove API key", style = Theme.t.small, color = c.ink3,
                modifier = Modifier.align(Alignment.CenterHorizontally).pressable(label = "Remove API key") {
                    Describe.clearKey(ctx); hasKey = false
                }.padding(8.dp))
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Glyph("info", c.danger, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(it, style = Theme.t.small, color = c.ink)
            }
        }
    }
}
