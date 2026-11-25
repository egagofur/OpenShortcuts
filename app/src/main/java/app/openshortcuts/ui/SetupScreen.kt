package app.openshortcuts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.openshortcuts.engine.Perm

/** `tick` changes on every resume so states re-read after returning from Settings. */
@Composable
fun SetupScreen(tick: Int, onBack: () -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    Row(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.gutter, end = 4.dp, bottom = 32.dp),
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 18.dp)) {
                Text("Setup", style = Theme.t.large, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text("Android asks before an app can control calls and settings. Allow only what you use.",
                    style = Theme.t.small, color = c.ink2)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Perm.entries.forEach { p ->
                    val ok = remember(tick, p) { p.granted(ctx) }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.title, style = Theme.t.label, color = c.ink)
                                Spacer(Modifier.height(2.dp))
                                Text(p.why, style = Theme.t.small, color = c.ink2)
                            }
                            Spacer(Modifier.width(12.dp))
                            if (ok) Row(verticalAlignment = Alignment.CenterVertically) {
                                Glyph("check", c.positive, size = 18.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("Allowed", style = Theme.t.small, color = c.positive)
                            } else Box(
                                Modifier.height(36.dp)
                                    .pressable(scale = 0.95f, label = "Allow ${p.title}") {
                                        val r = p.runtime
                                        if (r != null) ask.launch((listOf(r) + p.more).toTypedArray())
                                        else runCatching { ctx.startActivity(p.settingsIntent(ctx)) }
                                            .onFailure { ctx.startActivity(Perm.Notifications.settingsIntent(ctx)) }
                                    }
                                    .clip(RoundedCornerShape(18.dp)).background(c.accent).padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Allow", style = Theme.t.label, color = Color.White) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "On Xiaomi, Oppo, Vivo and Samsung phones, also turn on Autostart (or “Allow background activity”) for OpenShortcuts in the phone's own settings. Otherwise night automations can be killed.\n\n" +
                    "If Android says “Restricted setting” when you turn on Accessibility or notification access, open App info for OpenShortcuts, tap ⋮, choose “Allow restricted settings”, then try again.",
                style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        Rail({ GlassCircle("chevronLeft", "Back", onClick = onBack) })
    }
}
