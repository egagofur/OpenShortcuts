package app.openshortcuts.engine

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import app.openshortcuts.model.Store
import app.openshortcuts.model.TriggerKind

/**
 * Event triggers. They live inside the notification-listener service because Android keeps
 * that service running on its own: no extra always-on service, no persistent notification.
 */
class EventHost(private val ctx: Context) {
    private var lastLevel = -1
    private var registeredAt = 0L
    private var wifiUp: Boolean? = null

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED -> Events.fire(ctx, TriggerKind.Charger, on = true)
                Intent.ACTION_POWER_DISCONNECTED -> Events.fire(ctx, TriggerKind.Charger, on = false)
                Intent.ACTION_BATTERY_CHANGED -> battery(i)
            }
        }
    }

    private fun battery(i: Intent) {
        val raw = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (raw < 0) return
        val level = raw * 100 / scale
        val prev = lastLevel
        lastLevel = level
        if (prev < 0 || prev == level) return
        Store.library.value.automations
            .filter { it.enabled && it.kind == TriggerKind.BatteryLevel }
            .forEach { a ->
                val crossed = if (a.on) prev <= a.level && level > a.level else prev >= a.level && level < a.level
                if (crossed) Events.run(ctx, a, "$level")
            }
    }

    private val net = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = wifi(true)
        override fun onLost(network: Network) = wifi(false)
    }

    private fun wifi(up: Boolean) {
        val was = wifiUp
        wifiUp = up
        // Registering reports the current state at once; that isn't a change.
        if (was == up || SystemClock.elapsedRealtime() - registeredAt < 3_000) return
        Events.fire(ctx, TriggerKind.WiFi, on = up)
    }

    fun start() {
        registeredAt = SystemClock.elapsedRealtime()
        ctx.registerReceiver(power, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        })
        runCatching {
            ctx.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), net,
            )
        }
        Log.i(Events.TAG, "event host started")
    }

    fun stop() {
        runCatching { ctx.unregisterReceiver(power) }
        runCatching { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(net) }
    }
}

object Events {
    const val TAG = "Events"
    private const val MIN_GAP_MS = 10_000L
    private val lastRun = HashMap<String, Long>()
    private val seenMessages = HashMap<String, Long>()

    /** Run every enabled automation of [kind] whose on/off side matches. */
    fun fire(ctx: Context, kind: TriggerKind, on: Boolean, input: String = "") {
        Log.i(TAG, "$kind ${if (on) "on" else "off"}")
        Store.library.value.automations
            .filter { it.enabled && it.kind == kind && it.on == on }
            .forEach { run(ctx, it, input) }
    }

    fun run(ctx: Context, a: app.openshortcuts.model.Automation, input: String, vars: Map<String, String> = emptyMap(), key: String = a.id, gap: Long = MIN_GAP_MS) {
        // A shortcut that flips the same switch (e.g. DND) must not set itself off forever.
        val now = SystemClock.elapsedRealtime()
        if (now - (lastRun[key] ?: -gap) < gap) return
        lastRun[key] = now
        Scheduler.fireEvent(ctx, a, input, vars)
    }

    fun dnd(ctx: Context, filter: Int) {
        if (filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        fire(ctx, TriggerKind.Dnd, on = filter != NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    /** A new WhatsApp chat message. Its text becomes the shortcut's input. */
    fun message(ctx: Context, sbn: StatusBarNotification) {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category != null && n.category != Notification.CATEGORY_MESSAGE) return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val last = style?.messages?.lastOrNull()
        // Our own replies show up in the same notification; never react to them (no reply loops).
        if (last != null && (last.person == null || last.person?.name == style.user.name)) return
        val sender = (last?.person?.name ?: n.extras.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        val chat = style?.conversationTitle?.toString().orEmpty()
        val text = (last?.text ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: return
        // WhatsApp re-posts the same notification when it updates; only react to newer messages.
        val stamp = last?.timestamp ?: sbn.postTime
        if (stamp <= (seenMessages[sbn.key] ?: 0L)) return
        seenMessages[sbn.key] = stamp
        Log.i(TAG, "WhatsApp message")
        val vars = mapOf("Message Sender" to sender, "Message Chat" to chat.ifEmpty { sender })
        Store.library.value.automations
            .filter { it.enabled && it.kind == TriggerKind.WhatsAppMessage }
            .filter { a -> a.text.isBlank() || sender.contains(a.text, true) || chat.contains(a.text, true) }
            // At most one run per chat per minute, so a busy group can't flood auto-replies.
            .forEach { run(ctx, it, text, vars, key = "${it.id}|${chat.ifEmpty { sender }}", gap = 60_000) }
    }
}
