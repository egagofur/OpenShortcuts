package app.openshortcuts.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.openshortcuts.engine.Perm
import app.openshortcuts.model.Tint
import kotlinx.coroutines.delay

/**
 * Asking for a permission the friendly way: say what it gets you, show exactly what to tap next,
 * say plainly what it doesn't do, and celebrate when it's done. One permission at a time.
 */
object Primer {
    var queue by mutableStateOf<List<Perm>>(emptyList()); private set
    /** What the permissions are for, e.g. a shortcut's name; shown as "For Sleep Mode". */
    var forWhat by mutableStateOf(""); private set

    fun ask(perms: List<Perm>, forWhat: String = "") {
        // Location has to come before "all the time", or Android won't offer the second.
        val ordered = perms.distinct().sortedBy { if (it == Perm.BackgroundLocation) 1 else 0 }
        if (ordered.isEmpty()) return
        queue = ordered; this.forWhat = forWhat
    }

    fun close() { queue = emptyList() }
}

private enum class Mock { Dialog, Toggle }

private data class Pitch(
    val headline: String,
    val does: String,
    val doesnt: String,
    val mock: Mock,
    val glyph: String,
    val tint: Tint,
    /** Toggle mocks: the name of the Settings page the switch is on. */
    val page: String = "",
    /** Dialog mocks: the button Android will show. */
    val allow: String = "Allow",
)

private fun Perm.pitch(): Pitch = when (this) {
    Perm.CallAccess -> Pitch("End WhatsApp calls for you", "Sees WhatsApp's call and message notifications, so it can press Hang up or Reply for you.",
        "Doesn't read your chats or send them anywhere.", Mock.Toggle, "phoneOff", Tint.Green, page = "Notification access")
    Perm.Notifications -> Pitch("Know what your automations did", "Shows a short note after an automation runs, or when one needs you.",
        "No ads and no reminders you didn't ask for.", Mock.Dialog, "bell", Tint.Red)
    Perm.Alarms -> Pitch("Run on the exact minute", "Lets 23:00 mean 23:00, not “sometime around then”.",
        "Doesn't touch the alarms in your clock app.", Mock.Toggle, "clock", Tint.Orange, page = "Alarms & reminders")
    Perm.Battery -> Pitch("Keep working while you sleep", "Stops the phone from pausing your automations overnight.",
        "Uses no battery while nothing is happening.", Mock.Dialog, "battery", Tint.Green)
    Perm.Autostart -> Pitch("Keep running after you clear apps", "Lets OpenShortcuts start again on its own when your phone clears recent apps, so the call still ends at night.",
        "Starts only when a trigger fires. Uses no battery otherwise.", Mock.Toggle, "bolt", Tint.Orange, page = "Auto launch")
    Perm.Dnd -> Pitch("Switch Do Not Disturb for you", "Turns Do Not Disturb and silent mode on and off when a shortcut says so.",
        "Doesn't change your Do Not Disturb rules.", Mock.Toggle, "moon", Tint.Indigo, page = "Do Not Disturb access")
    Perm.WriteSettings -> Pitch("Dim the screen for you", "Sets screen brightness when a shortcut says so.",
        "Changes nothing else in your settings.", Mock.Toggle, "sun", Tint.Yellow, page = "Modify system settings")
    Perm.PhoneCalls -> Pitch("End regular phone calls", "Hangs up a cellular call when a shortcut says so.",
        "Doesn't see who you call or record anything.", Mock.Dialog, "phone", Tint.Green)
    Perm.Accessibility -> Pitch("Lock the phone for you", "Presses the lock button when a shortcut ends with Lock Screen.",
        "Can't see or read anything on your screen.", Mock.Toggle, "lock", Tint.Gray, page = "Accessibility")
    Perm.AutoSend -> Pitch("Press Send in WhatsApp for you", "Taps Send on the message your shortcut just typed.",
        "Can only see WhatsApp, and never anything you typed yourself.", Mock.Toggle, "send", Tint.Teal, page = "Accessibility")
    Perm.Call -> Pitch("Call straight away", "Dials the person a shortcut calls, without the extra tap in the dialer.",
        "Never calls anyone on its own.", Mock.Dialog, "phone", Tint.Blue)
    Perm.Sms -> Pitch("Send texts by themselves", "Sends the SMS a shortcut writes, without opening Messages.",
        "Never texts anyone you didn't choose. Normal SMS rates apply.", Mock.Dialog, "quote", Tint.Green)
    Perm.Calendar -> Pitch("Add and read your events", "Lets shortcuts add events and read what's coming up.",
        "Your calendar stays on your phone.", Mock.Dialog, "calendar", Tint.Red)
    Perm.Location -> Pitch("Know where you are", "For sunrise and sunset times, places, and Get Location.",
        "Never shared, and never kept as a history.", Mock.Dialog, "pin", Tint.Blue, allow = "While using the app")
    Perm.BackgroundLocation -> Pitch("Notice when you arrive or leave", "Android watches the place you picked, even with the app closed.",
        "The app doesn't track your movements.", Mock.Dialog, "pin", Tint.Indigo, allow = "Allow all the time")
    Perm.Contacts -> Pitch("Pick people, not numbers", "Choose who a shortcut calls, texts or messages from your contacts.",
        "Only the person you pick is saved. Nothing is uploaded.", Mock.Dialog, "person", Tint.Gray)
    Perm.UsageAccess -> Pitch("Notice when you open an app", "Lets the App trigger see which app is on screen.",
        "Nothing is recorded or kept.", Mock.Toggle, "grid", Tint.Indigo, page = "Usage access")
    Perm.Bluetooth -> Pitch("Know which device connected", "Lets the Bluetooth trigger tell your earbuds from your car.",
        "Doesn't connect to or scan for anything.", Mock.Dialog, "bluetooth", Tint.Blue)
}

/** Shown at the root while [Primer.queue] has something in it. */
@Composable
fun PrimerSheet() {
    val queue = Primer.queue
    if (queue.isEmpty()) return
    val c = Theme.c
    val ctx = LocalContext.current
    val view = LocalView.current
    var index by remember(queue) { mutableIntStateOf(0) }
    val p = queue.getOrNull(index) ?: run { Primer.close(); return }
    val pitch = p.pitch()
    var celebrating by remember(p) { mutableStateOf(false) }
    var check by remember { mutableIntStateOf(0) }
    // For switches Android can't report (Auto launch), remember that Settings was opened, then ask.
    var opened by remember(p) { mutableStateOf(false) }

    fun next() {
        if (index < queue.lastIndex) index++ else Primer.close()
    }
    // Granted (from the system dialog or back from Settings): celebrate, then move on.
    LaunchedEffect(p, check) {
        if (!celebrating && p.granted(ctx)) {
            celebrating = true
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            delay(1100)
            next()
        }
    }
    LifecycleResumeEffect(p) { check++; onPauseOrDispose { } }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { check++ }

    DuoDialog(onDismiss = { Primer.close() }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                listOfNotNull(
                    Primer.forWhat.takeIf { it.isNotBlank() }?.let { "For $it" },
                    if (queue.size > 1) "${index + 1} of ${queue.size}" else null,
                ).joinToString(" · "),
                style = Theme.t.small, color = c.ink2,
            )
            Spacer(Modifier.height(10.dp))
            // The picture: what you'll see next and where to tap. Replaced by a check once it's done.
            Box(
                Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(22.dp)).background(pitch.tint.color().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    celebrating to p, label = "primerArt",
                    transitionSpec = { (fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 420f), initialScale = 0.85f)) togetherWith fadeOut(tween(120)) },
                ) { (done, perm) ->
                    if (done) Celebrate() else when (perm.pitch().mock) {
                        Mock.Dialog -> DialogMock(perm.pitch())
                        Mock.Toggle -> ToggleMock(perm.pitch())
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                if (celebrating) "You're set" else pitch.headline,
                style = Theme.t.title.copy(fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
                color = c.ink, textAlign = TextAlign.Center,
            )
            if (!celebrating) {
                Spacer(Modifier.height(14.dp))
                Point("check", c.positive, pitch.does)
                Spacer(Modifier.height(8.dp))
                Point("x", c.ink3, pitch.doesnt)
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        opened -> "Is OpenShortcuts switched on in ${pitch.page}?"
                        p.stuck(ctx) -> "It's on, but Android paused it after an update. In ${pitch.page}, turn OpenShortcuts off and on again."
                        p == Perm.Autostart && app.openshortcuts.engine.OemAutostart.intent(ctx) == null ->
                            "Next, Settings search opens. Search for “Auto launch”, open it, then turn on OpenShortcuts."
                        pitch.mock == Mock.Toggle -> "Next, Android opens ${pitch.page}. Turn on OpenShortcuts, then come back."
                        else -> "Next, Android asks you. Tap “${pitch.allow}”."
                    },
                    style = Theme.t.small, color = c.ink2, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                if (opened) PrimaryButton("Yes, it's on", Modifier.fillMaxWidth()) { p.confirm(ctx); check++ }
                else PrimaryButton("Continue", Modifier.fillMaxWidth()) {
                    if (!p.checkable) opened = true
                    val needFirst = p == Perm.BackgroundLocation && !Perm.Location.granted(ctx)
                    val r = if (needFirst) Perm.Location.runtime else p.runtime
                    if (r != null && p != Perm.Battery) {
                        ask.launch(((listOf(r) + if (needFirst) Perm.Location.more else p.more)).toTypedArray())
                    } else {
                        runCatching { ctx.startActivity(p.settingsIntent(ctx)) }
                            .onFailure { runCatching { ctx.startActivity(Perm.Battery.settingsIntent(ctx).setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)) } }
                    }
                }
                if (opened) Text(
                    "Open ${pitch.page} again", style = Theme.t.label, color = c.accent,
                    modifier = Modifier.heightIn(min = 44.dp).pressable(label = "Open again") {
                        runCatching { ctx.startActivity(p.settingsIntent(ctx)) }
                    }.padding(top = 12.dp),
                )
                Text(
                    if (index < queue.lastIndex) "Skip for now" else "Not now",
                    style = Theme.t.label, color = c.accent,
                    modifier = Modifier.heightIn(min = 44.dp).pressable(label = "Not now") { next() }.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Point(glyph: String, tint: Color, text: String) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).size(20.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Glyph(glyph, tint, size = 13.dp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = Theme.t.body.copy(fontSize = 15.sp, lineHeight = 21.sp), color = c.ink, modifier = Modifier.weight(1f))
    }
}

/** A finger that travels to a spot, taps (ring), and rests — on a loop that reads as "tap here". */
@Composable
private fun rememberTapLoop(): Float =
    rememberInfiniteTransition(label = "tap").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "tapT",
    ).value

@Composable
private fun Finger(t: Float, reduced: Boolean, modifier: Modifier = Modifier) {
    // 0–0.35 travel in, 0.35–0.5 press, 0.5–0.8 hold, 0.8–1 fade out.
    val travel = if (reduced) 1f else Motion.easeOut.transform((t / 0.35f).coerceIn(0f, 1f))
    val press = if (t in 0.35f..0.5f) 1f - kotlin.math.abs((t - 0.425f) / 0.075f) else 0f
    val alpha = when { reduced -> 1f; t < 0.1f -> t / 0.1f; t > 0.85f -> (1f - t) / 0.15f; else -> 1f }
    Box(modifier.graphicsLayer { translationX = (1f - travel) * 60.dp.toPx(); translationY = (1f - travel) * 50.dp.toPx(); this.alpha = alpha }) {
        // Tap ripple.
        if (t in 0.38f..0.75f && !reduced) {
            val r = (t - 0.38f) / 0.37f
            Box(Modifier.offset((-10).dp, (-10).dp).size(40.dp).graphicsLayer { scaleX = 0.6f + r; scaleY = 0.6f + r; this.alpha = 1f - r }
                .clip(CircleShape).border(2.dp, Color.White, CircleShape))
        }
        Box(
            Modifier.size(20.dp).graphicsLayer { val s = 1f - 0.18f * press; scaleX = s; scaleY = s }
                .clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
        )
    }
}

@Composable
private fun DialogMock(p: Pitch) {
    val c = Theme.c
    val reduced = Theme.reduced
    val t = if (reduced) 0.45f else rememberTapLoop()
    val pressed = t in 0.38f..0.8f
    Box {
        Column(
            Modifier.width(240.dp).clip(RoundedCornerShape(20.dp)).background(c.raised).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconTile(p.glyph, p.tint.color(), size = 30.dp)
            Spacer(Modifier.height(8.dp))
            Text("Allow OpenShortcuts to…", style = Theme.t.small.copy(fontWeight = FontWeight.SemiBold), color = c.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp))
                    .background(if (pressed) c.accent else c.accentWash),
                contentAlignment = Alignment.Center,
            ) { Text(p.allow, style = Theme.t.small.copy(fontWeight = FontWeight.SemiBold), color = if (pressed) Color.White else c.accent) }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(c.fill), contentAlignment = Alignment.Center) {
                Text("Don't allow", style = Theme.t.small, color = c.ink2)
            }
        }
        Finger(t, reduced, Modifier.align(Alignment.Center).offset(40.dp, 30.dp))
    }
}

@Composable
private fun ToggleMock(p: Pitch) {
    val c = Theme.c
    val reduced = Theme.reduced
    val t = if (reduced) 0.6f else rememberTapLoop()
    val on = t in 0.45f..0.95f
    Box {
        Column(Modifier.width(250.dp).clip(RoundedCornerShape(18.dp)).background(c.raised).padding(vertical = 10.dp)) {
            Text(p.page, style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(horizontal = 14.dp))
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile("layers", Tint.Blue.color(), size = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text("OpenShortcuts", style = Theme.t.label, color = c.ink, modifier = Modifier.weight(1f))
                IosSwitch(on, null)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.fill))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.fill))
                Spacer(Modifier.weight(1f))
                IosSwitch(false, null)
            }
        }
        Finger(t, reduced, Modifier.align(Alignment.TopEnd).offset((-28).dp, 50.dp))
    }
}

/** Done: a check that lands with one small bounce. */
@Composable
private fun Celebrate() {
    val c = Theme.c
    val reduced = Theme.reduced
    val k = remember { Animatable(if (reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) { k.animateTo(1f, if (reduced) tween(0) else spring(dampingRatio = 0.5f, stiffness = 420f)) }
    Box(
        Modifier.size(84.dp).graphicsLayer { scaleX = k.value; scaleY = k.value; alpha = ((k.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
            .clip(CircleShape).background(c.positive),
        contentAlignment = Alignment.Center,
    ) { Glyph("check", Color.White, size = 44.dp) }
}

