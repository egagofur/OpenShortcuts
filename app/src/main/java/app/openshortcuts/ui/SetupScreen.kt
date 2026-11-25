package app.openshortcuts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Tint

private data class Group(val title: String, val footer: String?, val perms: List<Perm>)

private val GROUPS = listOf(
    Group("Automations", "These keep night automations on time and alive while the screen is off.",
        listOf(Perm.CallAccess, Perm.Notifications, Perm.Alarms, Perm.Battery, Perm.UsageAccess, Perm.Bluetooth)),
    Group("Phone control", null,
        listOf(Perm.Dnd, Perm.WriteSettings, Perm.PhoneCalls, Perm.Accessibility, Perm.AutoSend)),
    Group("Personal data", "Needed only by shortcuts that use them.",
        listOf(Perm.Contacts, Perm.Call, Perm.Sms, Perm.Calendar, Perm.Location, Perm.BackgroundLocation)),
)

private fun Perm.look(): Pair<String, Tint> = when (this) {
    Perm.CallAccess -> "phoneOff" to Tint.Green
    Perm.Notifications -> "bell" to Tint.Red
    Perm.Alarms -> "clock" to Tint.Orange
    Perm.Battery -> "battery" to Tint.Green
    Perm.Dnd -> "moon" to Tint.Indigo
    Perm.WriteSettings -> "sun" to Tint.Yellow
    Perm.PhoneCalls -> "phone" to Tint.Green
    Perm.Accessibility -> "lock" to Tint.Gray
    Perm.AutoSend -> "send" to Tint.Teal
    Perm.Call -> "phone" to Tint.Blue
    Perm.Sms -> "quote" to Tint.Green
    Perm.Calendar -> "calendar" to Tint.Red
    Perm.Location -> "pin" to Tint.Blue
    Perm.BackgroundLocation -> "pin" to Tint.Indigo
    Perm.Contacts -> "person" to Tint.Gray
    Perm.UsageAccess -> "grid" to Tint.Indigo
    Perm.Bluetooth -> "bluetooth" to Tint.Blue
}

/** `tick` changes on every resume so states re-read after returning from Settings. */
@Composable
fun SetupScreen(tick: Int, onBack: () -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val granted = remember(tick) { Perm.entries.associateWith { it.granted(ctx) } }
    val allowed = granted.count { it.value }
    val progress by animateFloatAsState(allowed / Perm.entries.size.toFloat(), if (Theme.reduced) tween(0) else Motion.settle(), label = "setup")

    fun open(p: Perm) {
        val r = p.runtime
        if (r != null) ask.launch((listOf(r) + p.more).toTypedArray())
        else runCatching { ctx.startActivity(p.settingsIntent(ctx)) }
            .onFailure { ctx.startActivity(Perm.Notifications.settingsIntent(ctx)) }
    }

    Row(Modifier.fillMaxSize().background(c.bg)) {
        Column(
            Modifier.weight(1f).fillMaxSize()
                .windowInsetsPadding(WindowInsets.topBar.only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(start = Dimens.gutter, end = 4.dp, bottom = 32.dp),
        ) {
            Column(Modifier.padding(top = 18.dp, bottom = 20.dp)) {
                Text("Setup", style = Theme.t.large, color = c.ink)
                Spacer(Modifier.height(2.dp))
                Text("Android asks before an app can control calls and settings. Allow only what you use.",
                    style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(c.fill)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(CircleShape).background(c.positive))
                    }
                    Spacer(Modifier.size(10.dp))
                    Text("$allowed of ${Perm.entries.size} allowed", style = Theme.t.small, color = c.ink2)
                }
            }

            GROUPS.forEachIndexed { gi, g ->
                if (gi > 0) Spacer(Modifier.height(22.dp))
                SettingsGroup(g.title, g.footer, surface = c.card) {
                    g.perms.forEachIndexed { i, p ->
                        if (i > 0) SettingsDivider()
                        val ok = granted[p] == true
                        val (glyph, tint) = p.look()
                        SettingsRow(p.title, glyph, tint.color(), p.why, actionLabel = if (ok) "Open ${p.title} settings" else "Allow ${p.title}", onClick = { open(p) }) {
                            AnimatedContent(
                                ok, label = "perm",
                                transitionSpec = {
                                    (fadeIn(tween(150)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.85f)) togetherWith fadeOut(tween(100))
                                },
                            ) { yes ->
                                if (yes) Box(Modifier.size(26.dp).clip(CircleShape).background(c.positive), contentAlignment = Alignment.Center) {
                                    Glyph("check", Color.White, size = 16.dp, label = "Allowed")
                                } else Box(
                                    Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(c.accentWash)
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Allow", style = Theme.t.label, color = c.accent, maxLines = 1) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsGroup("Getting started", surface = c.card) {
                SettingsRow("Show Tour Again", "layers", Tint.Blue.color(), "Walk through making a shortcut and an automation.",
                    onClick = { Tour.restart(); onBack() }) { Glyph("chevronRight", c.ink3, size = 16.dp) }
            }
            Spacer(Modifier.height(22.dp))
            SettingsGroup("If automations don't run", surface = c.card) {
                SettingsRow("Autostart", "flash", Tint.Orange.color(),
                    "On Xiaomi, Oppo, Vivo and Samsung, also allow Autostart (or “background activity”) for OpenShortcuts in the phone's own settings.")
                SettingsDivider()
                SettingsRow("“Restricted setting”", "info", Tint.Gray.color(),
                    "Open App info for OpenShortcuts, tap ⋮, choose “Allow restricted settings”, then turn the access on again.",
                    onClick = {
                        runCatching {
                            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${ctx.packageName}")))
                        }
                    }) { Glyph("chevronRight", c.ink3, size = 16.dp) }
            }
        }
        Rail({ GlassCircle("chevronLeft", "Back", onClick = onBack) })
    }
}
