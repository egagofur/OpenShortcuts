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
        vars: Map<String, String> = emptyMap(),
    ): Result {
        val st = State(context, shortcut.actions, structure(shortcut.actions), depth, ask, foreground)
        st.prev = input
        st.vars.putAll(vars)
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
                ActionType.REPEAT_EACH -> {
                    b ?: return
                    val items = Data.lines(input(st, a, "input"))
                    items.forEachIndexed { k, item ->
                        st.vars["Repeat Item"] = item
                        st.vars["Repeat Index"] = "${k + 1}"
                        st.prev = item
                        exec(st, i + 1, b.end)
                    }
                    i = b.end + 1
                }
                // Stray markers (e.g. an End If without its If) do nothing.
                ActionType.OTHERWISE, ActionType.END_IF, ActionType.END_REPEAT, ActionType.CASE, ActionType.END_MENU,
                ActionType.END_EACH -> i++
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
                if (r == CallWatcher.EndResult.NotConnected && CallWatcher.ensureConnected(ctx)) r = CallWatcher.endWhatsAppCall()
                when (r) {
                    CallWatcher.EndResult.Ended -> log += "WhatsApp call ended"
                    CallWatcher.EndResult.NoCall -> log += "No WhatsApp call in progress"
                    CallWatcher.EndResult.NotConnected -> fail(
                        if (CallWatcher.isEnabled(ctx)) "Android paused OpenShortcuts in the background, so it couldn't reach WhatsApp's call. Allow Autostart and unrestricted battery for OpenShortcuts in Setup, then try again."
                        else "Allow notification access so OpenShortcuts can hang up WhatsApp calls.",
                    )
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
            ActionType.REPLY_WHATSAPP -> {
                val text = fill(st, a.p("body")).trim().ifEmpty { fail("Type the reply.") }
                val chat = fill(st, a.p("chat")).trim()
                CallWatcher.ensureConnected(ctx)
                when (CallWatcher.replyWhatsApp(ctx, chat, text)) {
                    CallWatcher.Companion.ReplyResult.Sent -> log += "Replied on WhatsApp"
                    CallWatcher.Companion.ReplyResult.NoMessage -> log += "No WhatsApp message${if (chat.isNotEmpty()) " from $chat" else ""} to reply to"
                    CallWatcher.Companion.ReplyResult.NoReplyButton -> fail("WhatsApp's notification has no Reply button.")
                    CallWatcher.Companion.ReplyResult.NotConnected -> fail("Allow notification access so OpenShortcuts can reply on WhatsApp.")
                }
                text
            }
            ActionType.SEND_WHATSAPP -> {
                sendWhatsApp(st, fill(st, a.p("number")), fill(st, a.p("body")), auto = a.p("mode") == "automatically", name = a.params["number.name"].orEmpty())
                prev
            }
            ActionType.CALL -> { open(st, Phone.callIntent(ctx, fill(st, a.p("number"))), "Call ${fill(st, a.p("number"))}"); prev }
            ActionType.SEND_SMS -> {
                val number = fill(st, a.p("number")); val body = fill(st, a.p("body"))
                if (a.p("mode") == "automatically") { Phone.sendSms(ctx, number, body); log += "SMS sent" }
                else open(st, Phone.smsIntent(number, body), "Send SMS")
                prev
            }
            ActionType.EMAIL -> {
                open(st, Phone.emailIntent(fill(st, a.p("to")), fill(st, a.p("subject")), fill(st, a.p("body"))), "Send email"); prev
            }
            ActionType.SHARE -> input(st, a, "input").also { open(st, Phone.shareIntent(it), "Share") }
            ActionType.TIMER -> {
                open(st, Phone.timerIntent(a.p("minutes").toIntOrNull() ?: 10, fill(st, a.p("label"))), "Start timer"); prev
            }
            ActionType.ALARM -> { open(st, Phone.alarmIntent(fill(st, a.p("time")), fill(st, a.p("label"))), "Set alarm"); prev }
            ActionType.ADD_EVENT -> {
                val title = fill(st, a.p("title"))
                Phone.addEvent(ctx, title, fill(st, a.p("start")), a.p("minutes").toIntOrNull() ?: 60)
                    ?.let { open(st, it, "Add “$title” to your calendar") } ?: run { log += "Event added" }
                prev
            }
            ActionType.UPCOMING_EVENTS -> Phone.upcoming(ctx, a.p("hours").toIntOrNull() ?: 24)
            ActionType.LOCATION -> Phone.location(ctx)
            ActionType.OPEN_MAPS -> input(st, a, "input").also { open(st, Phone.mapsIntent(it), "Open in Maps") }
            ActionType.LOCK -> {
                if (!LockService.lock()) fail("Allow lock screen access: turn on OpenShortcuts under Accessibility.")
                prev
            }
            ActionType.SPEAK -> {
                val text = input(st, a, "input")
                if (text.isNotBlank()) speak(ctx, text)
                text
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
                val r = run(ctx, child, foreground = true, depth = depth + 1, input = prev, ask = st.ask, vars = st.vars)
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
            // --- Text ---
            ActionType.REPLACE_TEXT -> {
                val find = fill(st, a.p("find"))
                if (a.p("mode") == "regex") regex(find)
                Data.replace(input(st, a, "input"), find, fill(st, a.p("with")), a.p("mode") == "regex")
            }
            ActionType.SPLIT_TEXT -> Data.split(input(st, a, "input"), a.p("by"))
            ActionType.COMBINE_TEXT -> Data.combine(input(st, a, "input"), a.p("with"))
            ActionType.MATCH_TEXT -> fill(st, a.p("pattern")).let { regex(it); Data.match(input(st, a, "input"), it) }
            ActionType.CHANGE_CASE -> Data.changeCase(input(st, a, "input"), a.p("case"))
            ActionType.EXTRACT -> Data.extract(input(st, a, "input"), a.p("what"))
            // --- Lists & dictionaries ---
            ActionType.LIST -> Data.lines(fill(st, a.p("body"))).joinToString("\n")
            ActionType.GET_ITEM -> Data.item(input(st, a, "input"), fill(st, a.p("which")))
            ActionType.COUNT -> Data.count(input(st, a, "input"), a.p("what")).toString()
            ActionType.CHOOSE_LIST -> {
                val items = Data.lines(input(st, a, "input"))
                if (items.isEmpty()) fail("“Choose from List” got an empty list.")
                val ask = st.ask ?: fail("“Choose from List” needs you to answer. Run this shortcut from the app.")
                ask(Prompt.Menu(fill(st, a.p("prompt")), items)) ?: throw Stop()
            }
            ActionType.ADD_TO_VARIABLE -> {
                val name = a.p("name").trim().ifEmpty { fail("Give the variable a name.") }
                st.vars[name] = Data.append(st.vars[name].orEmpty(), input(st, a, "value"))
                prev
            }
            ActionType.DICTIONARY -> Data.dictionary(fill(st, a.p("body")))
            ActionType.GET_VALUE -> Data.getValue(input(st, a, "input"), fill(st, a.p("key")).trim())
            ActionType.SET_VALUE -> Data.setValue(input(st, a, "input"), fill(st, a.p("key")).trim(), fill(st, a.p("value")))
            // --- Numbers & dates ---
            ActionType.CALCULATE -> Data.number(Data.calculate(fill(st, a.p("expression"))))
            ActionType.ROUND -> Data.round(input(st, a, "input"), a.p("places").toIntOrNull() ?: 0)
            ActionType.RANDOM -> Data.random(fill(st, a.p("min")), fill(st, a.p("max")))
            ActionType.DATE -> Data.date(fill(st, a.p("value")))
            ActionType.ADJUST_DATE -> Data.adjust(input(st, a, "input"), a.p("op"), fill(st, a.p("amount")), a.p("unit"))
            ActionType.FORMAT_DATE -> Data.format(input(st, a, "input"), a.p("style"))
            ActionType.TIME_BETWEEN -> Data.between(input(st, a, "input"), fill(st, a.p("other")), a.p("unit"))
            // --- Scripting extras ---
            ActionType.COMMENT -> prev
            ActionType.STOP_OUTPUT -> {
                val out = input(st, a, "input")
                shown += out.ifBlank { "(empty)" }
                st.prev = out
                throw Stop()
            }
            ActionType.IF, ActionType.OTHERWISE, ActionType.END_IF, ActionType.REPEAT, ActionType.END_REPEAT,
            ActionType.MENU, ActionType.CASE, ActionType.END_MENU, ActionType.REPEAT_EACH, ActionType.END_EACH -> prev
        }
    }

    /** Digits with country code. A local 08… number gets the SIM's country code (Indonesia: 62). */
    /** Send what waited for the unlock. Runs from the unlock broadcast, so the screen is on and open. */
    suspend fun sendWaiting(ctx: Context) {
        val items = PendingSends.take(ctx)
        if (items.isEmpty()) return
        Notifs.cancelWaiting(ctx)
        items.forEach { item ->
            val st = State(ctx, emptyList(), emptyMap(), 0, null, foreground = true)
            runCatching { sendWhatsApp(st, item.number, item.text, item.auto, "") }
                .onFailure { e -> Notifs.ran(ctx, Result(item.name.ifBlank { "WhatsApp" }, listOf(), listOf(), e.message ?: "Couldn't send")) }
            kotlinx.coroutines.delay(1_500)
        }
    }

    private fun waNumber(ctx: Context, raw: String): String {
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 8) fail("Enter a phone number with country code, e.g. 62812…")
        if (!digits.startsWith("0")) return digits
        val iso = runCatching { ctx.getSystemService(android.telephony.TelephonyManager::class.java).simCountryIso }.getOrNull().orEmpty().lowercase()
        val code = mapOf("id" to "62", "my" to "60", "sg" to "65", "ph" to "63", "th" to "66", "vn" to "84", "in" to "91", "gb" to "44", "au" to "61")[iso]
            ?: fail("Add the country code to the number, e.g. 62812… instead of 0812…")
        return code + digits.drop(1)
    }

    private suspend fun sendWhatsApp(st: State, number: String, text: String, auto: Boolean, name: String = "") {
        val ctx = st.ctx
        if (text.isBlank()) fail("Type the message to send.")
        if (PendingSends.locked(ctx)) {
            // 1. Locked, but this person has a WhatsApp notification: reply through it, right from the lock screen.
            if (name.isNotBlank() && CallWatcher.ensureConnected(ctx, 3_000) &&
                CallWatcher.replyWhatsApp(ctx, name, text) == CallWatcher.Companion.ReplyResult.Sent
            ) { st.log += "Sent to $name from the lock screen"; return }
            // 2. Otherwise hold it: it goes out by itself the moment the phone is unlocked.
            waNumber(ctx, number)
            PendingSends.add(ctx, PendingSends.Item(number, name, text, auto))
            Notifs.waiting(ctx, name.ifBlank { number }, PendingSends.count(ctx))
            st.log += "Waiting to send to ${name.ifBlank { number }} when you unlock"
            return
        }
        val pkg = CallWatcher.WHATSAPP.firstOrNull { p ->
            runCatching { ctx.packageManager.getPackageInfo(p, 0) }.isSuccess
        } ?: fail("WhatsApp isn't installed.")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send?phone=${waNumber(ctx, number)}&text=${Uri.encode(text)}"))
            .setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (auto && !SendService.isEnabled(ctx)) fail("Allow automatic WhatsApp sending: turn on “OpenShortcuts WhatsApp auto-send” under Accessibility.")
        // Android blocks background apps from opening screens unless an accessibility service is on;
        // then the best we can do is a notification that opens the chat.
        if (!st.foreground && !SendService.isEnabled(ctx) && !LockService.isEnabled(ctx)) {
            Notifs.openLater(ctx, "Send WhatsApp message", "Tap to open the chat with your message ready.", intent)
            st.log += "Message ready in a notification"
            return
        }
        val job = if (auto) SendService.expect(text) else null
        ctx.startActivity(intent)
        if (job != null) {
            val sent = kotlinx.coroutines.withTimeoutOrNull(16_000) { job.done.await() } == true
            if (!sent) { SendService.cancel(); fail("Couldn't press Send in WhatsApp. Is the phone unlocked?") }
            st.log += "WhatsApp message sent"
        } else st.log += "WhatsApp opened with your message"
    }

    /**
     * Open another app's screen. Android blocks this from the background unless one of our
     * accessibility services is on; then a notification opens it on tap instead.
     */
    private fun open(st: State, intent: Intent, title: String) {
        val ctx = st.ctx
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!st.foreground && !SendService.isEnabled(ctx) && !LockService.isEnabled(ctx)) {
            Notifs.openLater(ctx, title, "Tap to continue.", intent)
            st.log += "$title: waiting in a notification"
            return
        }
        try { ctx.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            fail("No app on this phone can do “$title”.")
        }
    }

    /** Speak and wait until the voice finishes (so a following Lock Screen doesn't cut it off). */
    private suspend fun speak(ctx: Context, text: String) {
        var tts: android.speech.tts.TextToSpeech? = null
        try {
            kotlinx.coroutines.withTimeout<Unit>(120_000) {
                kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                    tts = android.speech.tts.TextToSpeech(ctx) { status ->
                        val engine = tts ?: return@TextToSpeech
                        if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                            if (cont.isActive) cont.resumeWith(kotlin.Result.failure(IllegalStateException("This phone has no text-to-speech voice installed.")))
                            return@TextToSpeech
                        }
                        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(id: String?) = Unit
                            override fun onDone(id: String?) { if (cont.isActive) cont.resumeWith(kotlin.Result.success(Unit)) }
                            @Deprecated("Deprecated in Java")
                            override fun onError(id: String?) { if (cont.isActive) cont.resumeWith(kotlin.Result.success(Unit)) }
                        })
                        engine.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "speak")
                    }
                    cont.invokeOnCancellation { tts?.stop() }
                }
            }
        } finally {
            tts?.shutdown()
        }
    }

    fun battery(ctx: Context) =
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun charging(ctx: Context) = ctx.getSystemService(BatteryManager::class.java).isCharging

    private fun regex(p: String): Regex =
        runCatching { Regex(p) }.getOrElse { fail("“$p” isn't a valid regular expression.") }

    private fun evaluate(st: State, a: Action): Boolean {
        val ctx = st.ctx; val prev = st.prev
        val lhs = when (a.p("subject")) {
            "Battery Level" -> battery(ctx).toString()
            "Hour" -> Calendar.getInstance().get(Calendar.HOUR_OF_DAY).toString()
            "Charging" -> if (charging(ctx)) "yes" else "no"
            "WhatsApp Call" -> if (CallWatcher.callActive) "yes" else "no"
            "Shortcut Input" -> st.vars["Shortcut Input"].orEmpty()
            else -> prev
        }
        val rhs = fill(st, a.p("value"))
        fun norm(s: String) = when (s.trim().lowercase()) { "true", "on", "ya", "yes", "active" -> "yes"; "false", "off", "tidak", "no" -> "no"; else -> s.trim().lowercase() }
        val ln = lhs.trim().toDoubleOrNull(); val rn = rhs.trim().toDoubleOrNull()
        return when (a.p("op")) {
            "is below" -> ln != null && rn != null && ln < rn
            "is above" -> ln != null && rn != null && ln > rn
            "is not" -> norm(lhs) != norm(rhs)
            "contains" -> lhs.contains(rhs.trim(), ignoreCase = true)
            "does not contain" -> !lhs.contains(rhs.trim(), ignoreCase = true)
            "begins with" -> lhs.trim().startsWith(rhs.trim(), ignoreCase = true)
            "ends with" -> lhs.trim().endsWith(rhs.trim(), ignoreCase = true)
            "matches" -> regex(rhs).containsMatchIn(lhs)
            "has any value" -> lhs.isNotBlank()
            "has no value" -> lhs.isBlank()
            else -> norm(lhs) == norm(rhs)
        }
    }
}
