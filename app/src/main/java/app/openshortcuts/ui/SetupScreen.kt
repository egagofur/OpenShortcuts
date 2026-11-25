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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Tint

/** [smartLock]: also show the Smart Lock row (it isn't a permission the app can check). */
private data class Group(val title: String, val footer: String?, val perms: List<Perm>, val smartLock: Boolean = false)

private val GROUPS = listOf(
    Group("Automations", "These keep night automations on time and alive while the screen is off.",
        listOf(Perm.CallAccess, Perm.Notifications, Perm.Alarms, Perm.Battery, Perm.Autostart, Perm.UsageAccess, Perm.Bluetooth)),
    Group("Send WhatsApp while locked",
        "Then a WhatsApp message goes out even while the phone is locked: it wakes, sends and locks again. When the phone asks for a PIN anyway (after a restart, or after a few hours unused), the message waits and sends when you unlock.",
        listOf(Perm.AutoSend, Perm.Accessibility), smartLock = true),
    Group("Phone control", null,
        listOf(Perm.Dnd, Perm.WriteSettings, Perm.PhoneCalls)),
    Group("Personal data", "Needed only by shortcuts that use them.",
        listOf(Perm.Contacts, Perm.Call, Perm.Sms, Perm.Calendar, Perm.Location, Perm.BackgroundLocation)),
)

private fun Perm.look(): Pair<String, Tint> = when (this) {
    Perm.CallAccess -> "phoneOff" to Tint.Green
    Perm.Notifications -> "bell" to Tint.Red
    Perm.Alarms -> "clock" to Tint.Orange
    Perm.Battery -> "battery" to Tint.Green
    Perm.Autostart -> "bolt" to Tint.Orange
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
    val granted = remember(tick, Primer.queue) { Perm.entries.associateWith { it.granted(ctx) } }
    val lib by app.openshortcuts.model.Store.library.collectAsState()
    val locked = setOf(Perm.AutoSend, Perm.Accessibility)
    // Only what your shortcuts and automations actually use is shown; the rest waits, folded away.
    val needed = remember(lib) { Perm.neededBy(lib).filterNot { it in locked || !it.applies() } }
    val others = Perm.entries.filterNot { it in needed || it in locked || !it.applies() }
    var showOthers by remember { mutableStateOf(false) }

    fun open(p: Perm) {
        // Not yet allowed: explain first (Primer). Already allowed: open its settings to change it.
        if (granted[p] != true) { Primer.ask(listOf(p)); return }
        runCatching { ctx.startActivity(p.settingsIntent(ctx)) }
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
                Text("Only what your shortcuts use is listed. Everything else stays off until a shortcut needs it.",
                    style = Theme.t.small, color = c.ink2)
            }

            val groups = buildList {
                add(Group("Your shortcuts use", if (needed.isEmpty()) "Nothing yet. You'll be asked when a shortcut needs something." else "Each one is asked for with a short explanation, one at a time.", needed))
                add(GROUPS.first { it.smartLock })
                if (showOthers) add(Group("Other permissions", "Not needed by anything you've made.", others))
            }
            groups.forEachIndexed { gi, g ->
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
                    if (g.smartLock) {
                        SettingsDivider()
                        SmartLockRow()
                    }
                }
            }

            if (!showOthers && others.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Show ${others.size} other permissions", style = Theme.t.label, color = c.accent,
                    modifier = Modifier.heightIn(min = 44.dp).pressable(label = "Show other permissions") { showOthers = true }.padding(horizontal = 14.dp, vertical = 12.dp))
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

/**
 * Smart Lock / Extend Unlock: Android lets no app check or open it directly, so the row explains
 * the one thing to do and opens Password & security, where the phone keeps it.
 */
@Composable
private fun SmartLockRow() {
    val c = Theme.c
    val ctx = LocalContext.current
    val locationOn = remember { ctx.getSystemService(android.location.LocationManager::class.java).isLocationEnabled }
    SettingsRow(
        "Smart Lock or Extend Unlock", "lock", Tint.Blue.color(),
        "Tap Open and search for Smart Lock (on Oppo: Password & biometrics → Smart Lock). Enter your PIN, then add Home as a trusted place. The phone then stays open without a PIN while you're home." +
            if (locationOn) "" else " Turn Location on too: trusted places need it.",
        actionLabel = "Search Settings for Smart Lock",
        onClick = {
            // Phones keep Smart Lock in different places and don't let apps open it directly,
            // so open Settings search (where "Smart Lock" always turns up), else Settings itself.
            listOf("com.android.settings.action.SETTINGS_SEARCH", android.provider.Settings.ACTION_SETTINGS)
                .firstOrNull { a -> runCatching { ctx.startActivity(android.content.Intent(a)) }.isSuccess }
        },
    ) {
        Box(
            Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).background(c.accentWash).padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Open", style = Theme.t.label, color = c.accent, maxLines = 1) }
    }
}
