package app.openshortcuts.engine

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

/**
 * Alarm stages per automation:
 *  time  – a time-of-day trigger fired
 *  call  – a WhatsApp call has lasted long enough; check the quiet-hours window
 *  warn  – post the "about to run" notification, then schedule `run`
 *  run   – run the shortcut now
 */
object Scheduler {
    const val WARN_LEAD_MS = 60_000L
    const val SNOOZE_MS = 30 * 60_000L
    private val STAGES = listOf("time", "call", "warn", "run")

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun pending(ctx: Context, stage: String, id: String, create: Boolean = true): PendingIntent? {
        val intent = Intent(ctx, AlarmReceiver::class.java)
            .setData(Uri.parse("shortcuts://$stage/$id"))
            .putExtra("stage", stage).putExtra("id", id)
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(ctx, 0, intent, flags)
    }

    fun at(ctx: Context, stage: String, id: String, whenMs: Long) {
        val pi = pending(ctx, stage, id) ?: return
        val am = alarms(ctx)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    fun cancel(ctx: Context, stage: String, id: String) {
        pending(ctx, stage, id, create = false)?.let { alarms(ctx).cancel(it); it.cancel() }
    }

    fun cancelAll(ctx: Context, id: String) = STAGES.forEach { cancel(ctx, it, id) }

    /** Re-derive every alarm from the library. Cheap; called on every edit and on boot. */
    fun rescheduleAll(ctx: Context, lib: Library = Store.library.value) {
        lib.automations.forEach { a ->
            cancel(ctx, "time", a.id)
            if (a.kind == TriggerKind.Time && a.enabled) at(ctx, "time", a.id, nextTime(a))
        }
        if (CallWatcher.callActive) armCallTriggers(ctx, CallWatcher.callStartedAt) else disarmCallTriggers(ctx)
    }

    fun nextTime(a: Automation, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, a.timeOfDay / 60); set(Calendar.MINUTE, a.timeOfDay % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        repeat(7) {
            if (a.days.isEmpty() || c.get(Calendar.DAY_OF_WEEK) in a.days) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun armCallTriggers(ctx: Context, startedAt: Long) {
        val now = System.currentTimeMillis()
        Store.library.value.automations.forEach { a ->
            if (a.kind != TriggerKind.WhatsAppCallLong) return@forEach
            if (!a.enabled) { cancelAll(ctx, a.id); return@forEach }
            // Don't clobber a pending warn/run for this same call.
            if (pending(ctx, "warn", a.id, false) != null || pending(ctx, "run", a.id, false) != null) return@forEach
            at(ctx, "call", a.id, maxOf(startedAt + a.callMinutes * 60_000L, now + 2_000))
        }
    }

    fun disarmCallTriggers(ctx: Context) {
        Store.library.value.automations.filter { it.kind == TriggerKind.WhatsAppCallLong }.forEach {
            cancelAll(ctx, it.id); Notifs.cancelWarn(ctx, it.id)
        }
    }

    /** Next time the clock enters [start, end). */
    fun nextWindowStart(start: Int, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, start / 60); set(Calendar.MINUTE, start % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun minuteOfDay(now: Long = System.currentTimeMillis()) =
        Calendar.getInstance().apply { timeInMillis = now }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    /** Returns the run job when this stage started one, so receivers can wait for it. */
    fun handle(ctx: Context, stage: String, a: Automation): Job? {
        // A fired alarm leaves its PendingIntent alive; drop it so "is one pending?" stays truthful.
        cancel(ctx, stage, a.id)
        val now = System.currentTimeMillis()
        val isCall = a.kind == TriggerKind.WhatsAppCallLong
        if (!a.enabled) { cancelAll(ctx, a.id); return null }
        if (isCall && !CallWatcher.callActive) { Notifs.cancelWarn(ctx, a.id); return null }
        return when (stage) {
            "time" -> {
                at(ctx, "time", a.id, nextTime(a, now + 60_000))
                if (a.warnFirst) handle(ctx, "warn", a) else runNow(ctx, a)
            }
            "call" -> {
                if (inWindow(minuteOfDay(now), a.windowStart, a.windowEnd)) {
                    if (a.warnFirst) handle(ctx, "warn", a) else runNow(ctx, a)
                } else {
                    at(ctx, "call", a.id, nextWindowStart(a.windowStart, now)); null
                }
            }
            "warn" -> {
                Notifs.warn(ctx, a)
                at(ctx, "run", a.id, now + WARN_LEAD_MS); null
            }
            "run" -> runNow(ctx, a)
            else -> null
        }
    }

    /** Event triggers run now, or warn first; the input waits in prefs across the warning. */
    fun fireEvent(ctx: Context, a: Automation, input: String, vars: Map<String, String> = emptyMap()) {
        // Input and trigger variables wait in prefs across the one-minute warning.
        val saved = org.json.JSONObject().put("input", input).put("vars", org.json.JSONObject(vars as Map<*, *>))
        inputs(ctx).edit().putString(a.id, saved.toString()).apply()
        if (a.warnFirst) handle(ctx, "warn", a) else runNow(ctx, a)
    }

    private fun inputs(ctx: Context) = ctx.getSharedPreferences("pending_inputs", Context.MODE_PRIVATE)

    fun runNow(ctx: Context, a: Automation): Job? {
        Notifs.cancelWarn(ctx, a.id)
        cancel(ctx, "run", a.id); cancel(ctx, "warn", a.id)
        val s = Store.shortcut(a.shortcutId) ?: return null
        val saved = runCatching { org.json.JSONObject(inputs(ctx).getString(a.id, "{}")!!) }.getOrDefault(org.json.JSONObject())
        inputs(ctx).edit().remove(a.id).apply()
        val input = saved.optString("input")
        val vars = saved.optJSONObject("vars")?.let { o -> o.keys().asSequence().associateWith { o.getString(it) } }.orEmpty()
        val app = ctx.applicationContext as ShortcutsApp
        return app.scope.launch {
            val r = Runner.run(ctx, s, foreground = app.inForeground, input = input, vars = vars)
            Notifs.ran(ctx, r)
        }
    }

    fun snooze(ctx: Context, a: Automation) {
        Notifs.cancelWarn(ctx, a.id)
        cancel(ctx, "run", a.id)
        val stage = if (a.kind == TriggerKind.WhatsAppCallLong) "call" else "warn"
        at(ctx, stage, a.id, System.currentTimeMillis() + SNOOZE_MS)
    }

    fun skip(ctx: Context, a: Automation) {
        Notifs.cancelWarn(ctx, a.id)
        cancel(ctx, "run", a.id); cancel(ctx, "warn", a.id); cancel(ctx, "call", a.id)
    }
}

/** Keeps the process alive until the shortcut finishes. */
private fun BroadcastReceiver.await(job: Job?) {
    job ?: return
    val pending = goAsync()
    job.invokeOnCompletion { pending.finish() }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val a = Store.automation(intent.getStringExtra("id") ?: return) ?: return
        await(Scheduler.handle(context, intent.getStringExtra("stage") ?: return, a))
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
    }
}
