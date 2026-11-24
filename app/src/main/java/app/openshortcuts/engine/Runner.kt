package app.openshortcuts.engine

import android.annotation.SuppressLint
import android.app.NotificationManager
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
import kotlinx.coroutines.delay
import java.util.Calendar

object Runner {

    data class Result(
        val shortcut: String,
        val shown: List<String>,
        val log: List<String>,
        val error: String? = null,
    )

    private class Frame(val parentActive: Boolean, val cond: Boolean, var inElse: Boolean = false) {
        val active get() = parentActive && (if (inElse) !cond else cond)
    }

    private class Stop : Exception()

    /** `foreground` = the app is on screen, so Show results go to a dialog instead of a notification. */
    suspend fun run(context: Context, shortcut: Shortcut, foreground: Boolean, depth: Int = 0): Result {
        val shown = mutableListOf<String>()
        val log = mutableListOf<String>()
        var prev = ""
        val stack = ArrayDeque<Frame>()
        fun active() = stack.lastOrNull()?.active ?: true

        try {
            for (a in shortcut.actions) {
                when (a.type) {
                    ActionType.IF -> { stack.addLast(Frame(active(), active() && evaluate(context, a, prev))); continue }
                    ActionType.OTHERWISE -> { stack.lastOrNull()?.inElse = true; continue }
                    ActionType.END_IF -> { stack.removeLastOrNull(); continue }
                    else -> Unit
                }
                if (!active()) continue
                prev = step(context, a, prev, depth, shown, log)
            }
        } catch (_: Stop) {
            log += "Stopped"
        } catch (e: Exception) {
            return Result(shortcut.name, shown, log, e.message ?: e.javaClass.simpleName)
        }
        if (!foreground) shown.forEach { Notifs.result(context, shortcut.name, it) }
        return Result(shortcut.name, shown, log)
    }

    private fun fail(msg: String): Nothing = throw IllegalStateException(msg)

    @SuppressLint("MissingPermission")
    private suspend fun step(
        ctx: Context, a: Action, prev: String, depth: Int,
        shown: MutableList<String>, log: MutableList<String>,
    ): String {
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
                val r = run(ctx, child, foreground = true, depth = depth + 1)
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
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.p("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                prev
            }
            ActionType.TEXT -> a.p("body")
            ActionType.SHOW -> {
                val text = a.p("input").let { if (it == "@prev") prev else it }
                shown += text.ifBlank { "(empty)" }
                text
            }
            ActionType.NOTIFY -> {
                Notifs.result(ctx, "OpenShortcuts", a.p("body")); prev
            }
            ActionType.IF, ActionType.OTHERWISE, ActionType.END_IF -> prev
        }
    }

    fun battery(ctx: Context) =
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun charging(ctx: Context) = ctx.getSystemService(BatteryManager::class.java).isCharging

    private fun evaluate(ctx: Context, a: Action, prev: String): Boolean {
        val lhs = when (a.p("subject")) {
            "Battery Level" -> battery(ctx).toString()
            "Hour" -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString()
            "Charging" -> if (charging(ctx)) "yes" else "no"
            "WhatsApp Call" -> if (CallWatcher.callActive) "yes" else "no"
            else -> prev
        }
        val rhs = a.p("value")
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
