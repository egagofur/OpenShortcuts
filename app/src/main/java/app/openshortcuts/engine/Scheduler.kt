package app.openshortcuts.engine

import app.openshortcuts.model.TimeMode
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import app.openshortcuts.ShortcutsApp
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Library
import app.openshortcuts.model.Store
import app.openshortcuts.model.TriggerKind
import app.openshortcuts.model.inWindow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar

object Scheduler {
    const val WARN_LEAD_MS = 60_000L
    const val SNOOZE_MS = 30 * 60_000L
    enum class Stage(val key: String) {
        Time("time"), Call("call"), Warn("warn"), Run("run");
        companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } }
    }

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun pending(ctx: Context, stage: Stage, id: String, create: Boolean = true): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setData(Uri.parse("shortcuts://${stage.key}/$id"))
            .putExtra("stage", stage.key).putExtra("id", id)
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }

    fun at(ctx: Context, stage: Stage, id: String, whenMs: Long) {
        val pi = pending(ctx, stage, id) ?: return
        val am = alarms(ctx)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            // Some phones (ColorOS) stretch "exact" alarms into a one-hour window; alarm clocks are never moved.
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, app.openshortcuts.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, show), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    fun cancel(ctx: Context, stage: Stage, id: String) {
        pending(ctx, stage, id, create = false)?.let { alarms(ctx).cancel(it); it.cancel() }
    }

    fun cancelAll(ctx: Context, id: String) = Stage.entries.forEach { cancel(ctx, it, id) }

    fun rescheduleAll(ctx: Context, lib: Library = Store.library.value) {
        lib.automations.forEach { a ->
            cancel(ctx, Stage.Time, a.id)
            if (a.kind == TriggerKind.Time && a.enabled) nextTime(ctx, a)?.let { at(ctx, Stage.Time, a.id, it) }
        }
        if (CallWatcher.callActive) armCallTriggers(ctx, CallWatcher.callStartedAt) else disarmCallTriggers(ctx)
        Geo.sync(ctx, lib)
    }

    fun place(ctx: Context): When.Place? {
        val prefs = ctx.getSharedPreferences("place", Context.MODE_PRIVATE)
        val fresh = runCatching {
            if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) null
            else {
                val lm = ctx.getSystemService(android.location.LocationManager::class.java)
                lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            }
        }.getOrNull()
        if (fresh != null) {
            prefs.edit().putFloat("lat", fresh.latitude.toFloat()).putFloat("lon", fresh.longitude.toFloat()).apply()
            return When.Place(fresh.latitude, fresh.longitude)
        }
        if (!prefs.contains("lat")) return null
        return When.Place(prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble())
    }

    fun refreshPlace(ctx: Context, done: (Boolean) -> Unit) {
        if (ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) { done(false); return }
        val lm = ctx.getSystemService(android.location.LocationManager::class.java)
        val provider = listOf(android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.FUSED_PROVIDER, android.location.LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: run { done(false); return }
        runCatching {
            lm.getCurrentLocation(provider, null, ctx.mainExecutor) { loc ->
                if (loc != null) ctx.getSharedPreferences("place", Context.MODE_PRIVATE).edit()
                    .putFloat("lat", loc.latitude.toFloat()).putFloat("lon", loc.longitude.toFloat()).apply()
                done(loc != null)
            }
        }.onFailure { done(false) }
    }

    fun nextTime(ctx: Context, a: Automation, from: Long = System.currentTimeMillis()): Long? {
        val zone = java.time.ZoneId.systemDefault()
        val start = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone)
        val place = if (a.timeMode.sun) place(ctx) else null
        return When.next(a, start, place, zone)?.atZone(zone)?.toInstant()?.toEpochMilli()
    }

    fun armCallTriggers(ctx: Context, startedAt: Long) {
        val now = System.currentTimeMillis()
        Store.library.value.automations.forEach { a ->
            if (a.kind != TriggerKind.WhatsAppCallLong) return@forEach
            if (!a.enabled) { cancelAll(ctx, a.id); return@forEach }
            if (pending(ctx, Stage.Warn, a.id, false) != null || pending(ctx, Stage.Run, a.id, false) != null) return@forEach
            at(ctx, Stage.Call, a.id, maxOf(startedAt + a.callMinutes * 60_000L, now + 2_000))
        }
    }

    fun disarmCallTriggers(ctx: Context) {
        Store.library.value.automations.filter { it.kind == TriggerKind.WhatsAppCallLong }.forEach {
            cancelAll(ctx, it.id); Notifs.cancelWarn(ctx, it.id)
        }
    }

    fun nextWindowStart(start: java.time.LocalTime, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, start.hour); set(Calendar.MINUTE, start.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun timeOfDay(now: Long = System.currentTimeMillis()): java.time.LocalTime =
        java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalTime()

    fun handle(ctx: Context, stage: Stage, a: Automation): Job? {
        // A fired alarm leaves its PendingIntent alive; drop it so "is one pending?" stays truthful.
        cancel(ctx, stage, a.id)
        val now = System.currentTimeMillis()
        val isCall = a.kind == TriggerKind.WhatsAppCallLong
        if (!a.enabled) { cancelAll(ctx, a.id); return null }
        if (isCall && !CallWatcher.callActive) { Notifs.cancelWarn(ctx, a.id); return null }
        return when (stage) {
            Stage.Time -> {
                val next = nextTime(ctx, a, now + 60_000)
                if (next != null) at(ctx, Stage.Time, a.id, next)
                else if (a.timeMode == TimeMode.Once) Store.upsert(a.copy(enabled = false))
                if (a.warnFirst) handle(ctx, Stage.Warn, a) else runNow(ctx, a)
            }
            Stage.Call -> {
                if (inWindow(timeOfDay(now), a.windowStart, a.windowEnd)) {
                    if (a.warnFirst) handle(ctx, Stage.Warn, a) else runNow(ctx, a)
                } else {
                    at(ctx, Stage.Call, a.id, nextWindowStart(a.windowStart, now)); null
                }
            }
            Stage.Warn -> {
                Notifs.warn(ctx, a)
                at(ctx, Stage.Run, a.id, now + WARN_LEAD_MS); null
            }
            Stage.Run -> runNow(ctx, a)
            else -> null
        }
    }

    fun fireEvent(ctx: Context, a: Automation, input: String, vars: Map<String, String> = emptyMap()) {
        val saved = org.json.JSONObject().put("input", input).put("vars", org.json.JSONObject(vars as Map<*, *>))
        inputs(ctx).edit().putString(a.id, saved.toString()).apply()
        if (a.warnFirst) handle(ctx, Stage.Warn, a) else runNow(ctx, a)
    }

    private fun inputs(ctx: Context) = ctx.getSharedPreferences("pending_inputs", Context.MODE_PRIVATE)

    fun runNow(ctx: Context, a: Automation): Job? {
        Notifs.cancelWarn(ctx, a.id)
        cancel(ctx, Stage.Run, a.id); cancel(ctx, Stage.Warn, a.id)
        val s = Store.shortcut(a.shortcutId) ?: return null
        val saved = runCatching { org.json.JSONObject(inputs(ctx).getString(a.id, "{}")!!) }.getOrDefault(org.json.JSONObject())
        inputs(ctx).edit().remove(a.id).apply()
        val input = saved.optString("input")
        val vars = saved.optJSONObject("vars")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
        val app = ctx.applicationContext as ShortcutsApp
        return app.scope.launch {
            val r = Runner.run(ctx, s, foreground = app.inForeground, input = input, vars = vars, source = sourceOf(a), automationId = a.id)
            Notifs.ran(ctx, r)
        }
    }

    /** How an automation's run reads in the run log: "At 17:00", "Every 60 min", "Charger". */
    fun sourceOf(a: Automation): String = when {
        a.kind == app.openshortcuts.model.TriggerKind.Time -> when (a.timeMode) {
            app.openshortcuts.model.TimeMode.Every -> "Every ${a.every} min"
            app.openshortcuts.model.TimeMode.Sunrise, app.openshortcuts.model.TimeMode.Sunset -> a.timeMode.label
            else -> "At %02d:%02d".format(a.timeOfDay.hour, a.timeOfDay.minute)
        }
        else -> a.kind.title
    }

    fun snooze(ctx: Context, a: Automation) {
        Notifs.cancelWarn(ctx, a.id)
        cancel(ctx, Stage.Run, a.id)
        val stage = if (a.kind == TriggerKind.WhatsAppCallLong) Stage.Call else Stage.Warn
        at(ctx, stage, a.id, System.currentTimeMillis() + SNOOZE_MS)
    }

    fun skip(ctx: Context, a: Automation) {
        Notifs.cancelWarn(ctx, a.id)
        cancel(ctx, Stage.Run, a.id); cancel(ctx, Stage.Warn, a.id); cancel(ctx, Stage.Call, a.id)
    }
}

private fun BroadcastReceiver.await(job: Job?) {
    job ?: return
    val pending = goAsync()
    job.invokeOnCompletion { pending.finish() }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val a = Store.automation(intent.getStringExtra("id") ?: return) ?: return
        await(Scheduler.handle(context, Scheduler.Stage.of(intent.getStringExtra("stage")) ?: return, a))
    }
}

class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val a = Store.automation(intent.getStringExtra("id") ?: return) ?: return
        when (intent.action) {
            Notifs.ACTION_RUN -> await(Scheduler.runNow(context, a))
            Notifs.ACTION_SNOOZE -> Scheduler.snooze(context, a)
            Notifs.ACTION_SKIP -> Scheduler.skip(context, a)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Scheduler.rescheduleAll(context)
        // After an update or reboot some phones (Oppo, Xiaomi) don't reconnect the listener on their own.
        if (CallWatcher.isEnabled(context) && CallWatcher.instance == null) CallWatcher.rebind(context)
    }
}
