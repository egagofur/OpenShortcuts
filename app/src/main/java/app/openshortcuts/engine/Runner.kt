package app.openshortcuts.engine

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.KeyEvent
import app.openshortcuts.model.Action
import app.openshortcuts.model.ActionType
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.Store
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.util.Calendar

object Runner {

    data class Result(
        val shortcut: String,
        val shown: List<String>,
        val log: List<String>,
        val error: String? = null,
    )

    /** Questions a running shortcut asks the person. `null` answer = they cancelled. */
    sealed interface Prompt {
        data class Menu(val title: String, val options: List<String>) : Prompt
        data class Input(val title: String, val number: Boolean) : Prompt
    }

    private class Stop : Exception()

    private class Block(val mids: List<Int>, val end: Int)

    private class State(
        val ctx: Context,
        val actions: List<Action>,
        val blocks: Map<Int, Block>,
        val depth: Int,
        val ask: (suspend (Prompt) -> String?)?,
        val foreground: Boolean,
    ) {
        val shown = mutableListOf<String>()
        val log = mutableListOf<String>()
        val vars = HashMap<String, String>()
        var prev = ""
    }

    /** Match every If / Repeat / Menu with its middles and end. Unclosed blocks run to the end. */
    private fun structure(actions: List<Action>): Map<Int, Block> {
        val out = HashMap<Int, Block>()
        val open = ArrayDeque<Pair<Int, MutableList<Int>>>()
        actions.forEachIndexed { i, a ->
            when {
                a.type.opensBlock -> open.addLast(i to mutableListOf())
                a.type.isMidBlock -> open.lastOrNull { actions[it.first].type == a.type.opener }?.second?.add(i)
                a.type.closesBlock -> {
                    val idx = open.indexOfLast { actions[it.first].type == a.type.opener }
                    if (idx >= 0) {
                        // Anything opened inside and never closed ends here too.
                        while (open.size > idx + 1) open.removeLast().let { (o, m) -> out[o] = Block(m, i) }
                        open.removeLast().let { (o, m) -> out[o] = Block(m, i) }
                    }
                }
            }
        }
        open.forEach { (o, m) -> out[o] = Block(m, actions.size) }
        return out
    }

    /**
     * `foreground` = the app is on screen, so Show results go to a dialog instead of a notification.
     * `input` is the Shortcut Input (a WhatsApp message, a battery level…).
     * `ask` shows menus and text boxes; without it, actions that ask fail with a clear message.
     */
    suspend fun run(
        context: Context, shortcut: Shortcut, foreground: Boolean, depth: Int = 0, input: String = "",
        ask: (suspend (Prompt) -> String?)? = null,
    ): Result {
        val st = State(context, shortcut.actions, structure(shortcut.actions), depth, ask, foreground)
        st.prev = input
        st.vars["Shortcut Input"] = input
        try {
            exec(st, 0, st.actions.size)
        } catch (_: Stop) {
            st.log += "Stopped"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result(shortcut.name, st.shown, st.log, e.message ?: e.javaClass.simpleName)
        }
        if (!foreground) st.shown.forEach { Notifs.result(context, shortcut.name, it) }
        return Result(shortcut.name, st.shown, st.log)
    }

    private suspend fun exec(st: State, from: Int, to: Int) {
        var i = from
        while (i < to) {
            currentCoroutineContext().ensureActive()
            val a = st.actions[i]
            val b = st.blocks[i]
            when (a.type) {
                ActionType.IF -> {
                    b ?: return
                    val otherwise = b.mids.firstOrNull()
                    if (evaluate(st, a)) exec(st, i + 1, otherwise ?: b.end)
                    else if (otherwise != null) exec(st, otherwise + 1, b.end)
                    i = b.end + 1
                }
                ActionType.REPEAT -> {
                    b ?: return
                    val n = (fill(st, a.p("count")).trim().toIntOrNull() ?: 1).coerceIn(0, 1000)
                    for (k in 1..n) {
                        st.vars["Repeat Index"] = "$k"
                        exec(st, i + 1, b.end)
                    }
                    i = b.end + 1
                }
                ActionType.MENU -> {
                    b ?: return
                    val labels = b.mids.map { fill(st, st.actions[it].p("label")) }
                    if (labels.isEmpty()) fail("Add an option to “Choose from Menu”.")
                    val ask = st.ask ?: fail("“Choose from Menu” needs you to answer. Run this shortcut from the app.")
                    val pick = ask(Prompt.Menu(fill(st, a.p("prompt")), labels)) ?: throw Stop()
                    val at = labels.indexOf(pick).coerceAtLeast(0)
                    st.vars["Menu Result"] = pick
                    st.prev = pick
                    exec(st, b.mids[at] + 1, b.mids.getOrNull(at + 1) ?: b.end)
                    i = b.end + 1
                }
                // Stray markers (e.g. an End If without its If) do nothing.
                ActionType.OTHERWISE, ActionType.END_IF, ActionType.END_REPEAT, ActionType.CASE, ActionType.END_MENU -> i++
                else -> {
                    st.prev = step(st, a)
                    i++
                }
            }
        }
    }

    /** Replace {Name} with variables; unknown names stay as typed. */
    private fun fill(st: State, text: String): String = Regex("\\{([^{}]+)\\}").replace(text) { m ->
        val name = m.groupValues[1].trim()
        st.vars[name] ?: special(st, name) ?: m.value
    }

    private fun special(st: State, name: String): String? = when (name) {
        "Previous Result" -> st.prev
        "Current Date" -> java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG).format(java.util.Date())
        "Current Time" -> android.text.format.DateFormat.getTimeFormat(st.ctx).format(java.util.Date())
        // Android only lets the app on screen read the clipboard.
        "Clipboard" -> runCatching {
            st.ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(st.ctx)?.toString()
        }.getOrNull().orEmpty()
        else -> null
    }

    private fun input(st: State, a: Action, key: String) = a.p(key).let { if (it == "@prev") st.prev else fill(st, it) }

    private fun fail(msg: String): Nothing = throw IllegalStateException(msg)

    @SuppressLint("MissingPermission")
    private suspend fun step(st: State, a: Action): String {
        val ctx = st.ctx; val prev = st.prev; val depth = st.depth; val shown = st.shown; val log = st.log
        val audio = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return when (a.type) {
            ActionType.END_WHATSAPP_CALL -> {
                var r = CallWatcher.endWhatsAppCall()
                if (r == CallWatcher.EndResult.NotConnected && CallWatcher.isEnabled(ctx)) {
                    CallWatcher.rebind(ctx); delay(1500); r = CallWatcher.endWhatsAppCall()
                }
                when (r) {
                    CallWatcher.EndResult.Ended -> log += "WhatsApp call ended"
                    CallWatcher.EndResult.NoCall -> log += "No WhatsApp call in progress"
                    CallWatcher.EndResult.NotConnected -> fail("Allow notification access so OpenShortcuts can hang up WhatsApp calls.")
                    CallWatcher.EndResult.Failed -> fail("Couldn't find WhatsApp's hang-up button.")
                }
                prev
            }
            ActionType.END_PHONE_CALL -> {
                val tm = ctx.getSystemService(TelecomManager::class.java)
                val ok = runCatching { @Suppress("DEPRECATION") tm.endCall() }
                    .getOrElse { fail("Allow phone call control to end calls.") }
                log += if (ok) "Phone call ended" else "No phone call in progress"
                prev
            }
            ActionType.DND -> {
                if (!nm.isNotificationPolicyAccessGranted) fail("Allow Do Not Disturb access.")
                nm.setInterruptionFilter(
                    if (a.p("state") == "On") NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    else NotificationManager.INTERRUPTION_FILTER_ALL,
                )
                prev
            }
            ActionType.RINGER -> {
                val mode = when (a.p("mode")) {
                    "Silent" -> AudioManager.RINGER_MODE_SILENT
                    "Vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                runCatching { audio.ringerMode = mode }.onFailure { fail("Allow Do Not Disturb access to change ringer mode.") }
                prev
            }
            ActionType.MEDIA -> {
                val key = when (a.p("op")) {
                    "Play" -> KeyEvent.KEYCODE_MEDIA_PLAY
                    "Next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "Previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    else -> KeyEvent.KEYCODE_MEDIA_PAUSE
                }
                val t = SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, key, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, key, 0))
                prev
            }
            ActionType.VOLUME -> {
                val stream = when (a.p("stream")) {
                    "Ring" -> AudioManager.STREAM_RING
                    "Alarm" -> AudioManager.STREAM_ALARM
                    "Call" -> AudioManager.STREAM_VOICE_CALL
                    else -> AudioManager.STREAM_MUSIC
                }
                val max = audio.getStreamMaxVolume(stream)
                val level = (a.p("level").toIntOrNull() ?: 30).coerceIn(0, 100)
                runCatching { audio.setStreamVolume(stream, (max * level + 50) / 100, 0) }
                    .onFailure { fail("Allow Do Not Disturb access to change this volume.") }
                prev
            }
            ActionType.BRIGHTNESS -> {
                if (!Settings.System.canWrite(ctx)) fail("Allow OpenShortcuts to modify system settings.")
                val level = (a.p("level").toIntOrNull() ?: 50).coerceIn(0, 100)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS,
                    (1 + level * 254 / 100))
                prev
            }
            ActionType.FLASHLIGHT -> {
                val cm = ctx.getSystemService(CameraManager::class.java)
                val id = cm.cameraIdList.firstOrNull {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: fail("This device has no flashlight.")
                cm.setTorchMode(id, a.p("state") == "On")
                prev
            }
            ActionType.VIBRATE -> {
                val v = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
                v.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                prev
            }
            ActionType.BATTERY -> battery(ctx).toString()
            ActionType.WAIT -> {
                delay((a.p("seconds").toLongOrNull() ?: 1).coerceIn(1, 3600) * 1000); prev
            }
            ActionType.STOP -> throw Stop()
            ActionType.RUN_SHORTCUT -> {
                if (depth >= 4) fail("Shortcuts are nested too deeply.")
                val child = Store.shortcut(a.p("shortcut")) ?: fail("Choose a shortcut to run.")
                val r = run(ctx, child, foreground = true, depth = depth + 1, input = prev, ask = st.ask)
                shown += r.shown; log += r.log
                r.error?.let { fail(it) }
                r.shown.lastOrNull() ?: prev
            }
            ActionType.OPEN_APP -> {
                val pkg = a.p("app").ifBlank { fail("Choose an app to open.") }
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: fail("That app isn't installed.")
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                prev
            }
            ActionType.OPEN_URL -> {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fill(st, a.p("url")))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                prev
            }
            ActionType.TEXT -> fill(st, a.p("body"))
            ActionType.SHOW -> {
                val text = input(st, a, "input")
                shown += text.ifBlank { "(empty)" }
                text
            }
            ActionType.NOTIFY -> {
                Notifs.result(ctx, "OpenShortcuts", fill(st, a.p("body"))); prev
            }
            ActionType.ASK -> {
                val ask = st.ask ?: fail("“Ask for Input” needs you to answer. Run this shortcut from the app.")
                ask(Prompt.Input(fill(st, a.p("prompt")), a.p("kind") == "Number")) ?: throw Stop()
            }
            ActionType.SET_VARIABLE -> {
                val name = a.p("name").trim().ifEmpty { fail("Give the variable a name.") }
                st.vars[name] = input(st, a, "value")
                prev
            }
            ActionType.GET_VARIABLE -> a.p("name").trim().let { st.vars[it] ?: special(st, it) ?: "" }
            ActionType.COPY -> {
                val text = input(st, a, "input")
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OpenShortcuts", text))
                text
            }
            ActionType.IF, ActionType.OTHERWISE, ActionType.END_IF, ActionType.REPEAT, ActionType.END_REPEAT,
            ActionType.MENU, ActionType.CASE, ActionType.END_MENU -> prev
        }
    }

    fun battery(ctx: Context) =
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun charging(ctx: Context) = ctx.getSystemService(BatteryManager::class.java).isCharging

    private fun evaluate(st: State, a: Action): Boolean {
        val ctx = st.ctx; val prev = st.prev
        val lhs = when (a.p("subject")) {
            "Battery Level" -> battery(ctx).toString()
            "Hour" -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString()
            "Charging" -> if (charging(ctx)) "yes" else "no"
            "WhatsApp Call" -> if (CallWatcher.callActive) "yes" else "no"
            else -> prev
        }
        val rhs = fill(st, a.p("value"))
        fun norm(s: String) = when (s.trim().lowercase()) { "true", "on", "ya", "yes", "active" -> "yes"; "false", "off", "tidak", "no" -> "no"; else -> s.trim().lowercase() }
        val ln = lhs.trim().toDoubleOrNull(); val rn = rhs.trim().toDoubleOrNull()
        return when (a.p("op")) {
            "is below" -> ln != null && rn != null && ln < rn
            "is above" -> ln != null && rn != null && ln > rn
            "is not" -> norm(lhs) != norm(rhs)
            else -> norm(lhs) == norm(rhs)
        }
    }
}
