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
import kotlinx.coroutines.launch

/**
 * Event triggers. They live inside the notification-listener service because Android keeps
 * that service running on its own: no extra always-on service, no persistent notification.
 */
class EventHost(private val ctx: Context) {
    companion object {
        @Volatile var current: EventHost? = null
    }

    private var lastLevel = -1
    private var registeredAt = 0L
    private var wifiUp: Boolean? = null
    private var headphones: Boolean? = null
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var foreground: String? = null
    private var lastPoll = 0L

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

    private val system = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> Events.fire(ctx, TriggerKind.AirplaneMode, on = i.getBooleanExtra("state", false))
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                    Events.fire(ctx, TriggerKind.BatterySaver, on = ctx.getSystemService(android.os.PowerManager::class.java).isPowerSaveMode)
                android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED, android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val dev = if (android.os.Build.VERSION.SDK_INT >= 33)
                        i.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") i.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                    val name = runCatching { dev?.name }.getOrNull().orEmpty()
                    Events.bluetooth(ctx, on = i.action == android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED, name)
                }
                Intent.ACTION_SCREEN_ON -> watchApps()
                Intent.ACTION_USER_PRESENT -> if (PendingSends.count(ctx) > 0)
                    (ctx.applicationContext as app.openshortcuts.ShortcutsApp).scope.launch { kotlinx.coroutines.delay(700); Runner.sendWaiting(ctx) }
                Intent.ACTION_SCREEN_OFF -> main.removeCallbacks(poll)
            }
        }
    }

    private val HEADSET = setOf(
        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
        android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    private val audio = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) = headset()
        override fun onAudioDevicesRemoved(removed: Array<out android.media.AudioDeviceInfo>) = headset()
    }

    private fun headset() {
        val now = ctx.getSystemService(android.media.AudioManager::class.java)
            .getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADSET }
        val was = headphones
        headphones = now
        // The callback reports what's already plugged in when it registers; that isn't a change.
        if (was == null || was == now || SystemClock.elapsedRealtime() - registeredAt < 3_000) return
        Events.fire(ctx, TriggerKind.Headphones, on = now)
    }

    /**
     * App opened/closed: Android has no broadcast for this, so while the screen is on and an App
     * automation exists, read the usage events every 1.5 s (cheap: only what changed since last time).
     */
    private val poll = object : Runnable {
        override fun run() {
            val wanted = Store.library.value.automations.any { it.enabled && it.kind == TriggerKind.App }
            if (!wanted || !Perm.UsageAccess.granted(ctx)) return
            val usm = ctx.getSystemService(android.app.usage.UsageStatsManager::class.java)
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(if (lastPoll == 0L) now - 10_000 else lastPoll, now)
            lastPoll = now
            val e = android.app.usage.UsageEvents.Event()
            var top: String? = null
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) top = e.packageName
            }
            if (top != null && top != foreground) {
                val was = foreground
                foreground = top
                if (was != null) Events.appChanged(ctx, closed = was, opened = top)
            }
            main.postDelayed(this, 1_500)
        }
    }

    fun watchApps() {
        main.removeCallbacks(poll)
        val power = ctx.getSystemService(android.os.PowerManager::class.java)
        if (power.isInteractive) main.post(poll)
    }

    fun start() {
        registeredAt = SystemClock.elapsedRealtime()
        ctx.registerReceiver(system, IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        runCatching { ctx.getSystemService(android.media.AudioManager::class.java).registerAudioDeviceCallback(audio, main) }
        current = this
        watchApps()
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
        main.removeCallbacks(poll)
        if (current === this) current = null
        runCatching { ctx.unregisterReceiver(system) }
        runCatching { ctx.getSystemService(android.media.AudioManager::class.java).unregisterAudioDeviceCallback(audio) }
        runCatching { ctx.unregisterReceiver(power) }
        runCatching { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(net) }
    }
}

object Events {
    const val TAG = "Events"
    private const val RETRIGGER_COOLDOWN_MS = 10_000L
    private const val NOTIFICATION_COOLDOWN_MS = 30_000L
    private val lastRun = HashMap<String, Long>()
    private val seenMessages = HashMap<String, Long>()

    fun fire(ctx: Context, kind: TriggerKind, on: Boolean, input: String = "") {
        Log.i(TAG, "$kind ${if (on) "on" else "off"}")
        Store.library.value.automations
            .filter { it.enabled && it.kind == kind && it.on == on }
            .forEach { run(ctx, it, input) }
    }

    fun run(ctx: Context, a: app.openshortcuts.model.Automation, input: String, vars: Map<String, String> = emptyMap(), key: String = a.id, gap: Long = RETRIGGER_COOLDOWN_MS) {
        val now = SystemClock.elapsedRealtime()
        if (now - (lastRun[key] ?: -gap) < gap) return
        lastRun[key] = now
        Scheduler.fireEvent(ctx, a, input, vars)
    }

    fun dnd(ctx: Context, filter: Int) {
        if (filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        fire(ctx, TriggerKind.Dnd, on = filter != NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    fun bluetooth(ctx: Context, on: Boolean, name: String) {
        Log.i(TAG, "bluetooth ${if (on) "on" else "off"}")
        Store.library.value.automations
            .filter { it.enabled && it.kind == TriggerKind.Bluetooth && it.on == on }
            .filter { it.device.isBlank() || name.contains(it.device, true) }
            .forEach { run(ctx, it, name) }
    }

    private fun label(ctx: Context, pkg: String) = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun appChanged(ctx: Context, closed: String, opened: String) {
        if (closed == ctx.packageName && opened == ctx.packageName) return
        Store.library.value.automations.filter { it.enabled && it.kind == TriggerKind.App }.forEach { a ->
            when {
                a.on && a.app == opened -> run(ctx, a, label(ctx, opened))
                !a.on && a.app == closed -> run(ctx, a, label(ctx, closed))
            }
        }
    }

    private val seenNotifications = HashMap<String, Long>()
    private val ringing = HashSet<String>()

    fun notification(ctx: Context, sbn: StatusBarNotification) {
        val n = sbn.notification
        // An alarm ringing: the clock posts an alarm-category, full-screen notification.
        if (n.category == Notification.CATEGORY_ALARM && (n.fullScreenIntent != null || n.flags and Notification.FLAG_ONGOING_EVENT != 0)) {
            if (ringing.add(sbn.key)) { Log.i(TAG, "alarm on"); fire(ctx, TriggerKind.Alarm, on = true, input = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()) }
            return
        }
        if (sbn.packageName == ctx.packageName || sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.postTime <= (seenNotifications[sbn.key] ?: 0L)) return
        seenNotifications[sbn.key] = sbn.postTime
        val autos = Store.library.value.automations.filter { it.enabled && it.kind == TriggerKind.AppNotification }
        if (autos.isEmpty()) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val app = label(ctx, sbn.packageName)
        val vars = mapOf("Notification Title" to title, "Notification App" to app)
        autos.filter { a -> (a.app.isBlank() || a.app == sbn.packageName) && (a.keyword.isBlank() || "$title $text".contains(a.keyword, true)) }
            .forEach { run(ctx, it, text.ifBlank { title }, vars, key = "${it.id}|${sbn.packageName}|$title", gap = NOTIFICATION_COOLDOWN_MS) }
    }

    fun removed(ctx: Context, sbn: StatusBarNotification) {
        if (ringing.remove(sbn.key)) { Log.i(TAG, "alarm off"); fire(ctx, TriggerKind.Alarm, on = false) }
    }

    fun recentChats(ctx: Context): List<String> =
        ctx.getSharedPreferences("history", Context.MODE_PRIVATE).getString("wa_chats", "").orEmpty().split('\n').filter { it.isNotBlank() }

    private fun rememberChat(ctx: Context, name: String) {
        if (name.isBlank()) return
        val list = (listOf(name) + recentChats(ctx).filterNot { it == name }).take(40)
        ctx.getSharedPreferences("history", Context.MODE_PRIVATE).edit().putString("wa_chats", list.joinToString("\n")).apply()
    }

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
        rememberChat(ctx, chat.ifEmpty { sender })
        val vars = mapOf("Message Sender" to sender, "Message Chat" to chat.ifEmpty { sender })
        Store.library.value.automations
            .filter { it.enabled && it.kind == TriggerKind.WhatsAppMessage }
            .filter { a -> a.sender.isBlank() || sender.contains(a.sender, true) || chat.contains(a.sender, true) }
            .forEach { run(ctx, it, text, vars, key = "${it.id}|${chat.ifEmpty { sender }}", gap = 60_000) }
    }
}
