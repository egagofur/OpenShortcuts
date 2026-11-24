package app.openshortcuts.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.openshortcuts.MainActivity
import app.openshortcuts.R
import app.openshortcuts.model.Automation
import app.openshortcuts.model.Store
import app.openshortcuts.model.TriggerKind

object Notifs {
    private const val CH_RESULT = "results"
    private const val CH_WARN = "warnings"
    const val ACTION_RUN = "app.openshortcuts.RUN"
    const val ACTION_SNOOZE = "app.openshortcuts.SNOOZE"
    const val ACTION_SKIP = "app.openshortcuts.SKIP"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_RESULT, "Shortcut results", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(
            NotificationChannel(CH_WARN, "Before an automation runs", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gives you a minute to cancel, e.g. before a call is ended."
                // Must still reach you while Do Not Disturb is on.
                setBypassDnd(true)
            },
        )
    }

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun base(ctx: Context, channel: String) = Notification.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_stat)
        .setContentIntent(openApp(ctx))
        .setAutoCancel(true)

    fun result(ctx: Context, title: String, text: String) {
        nm(ctx).notify(
            (title + text).hashCode(),
            base(ctx, CH_RESULT).setContentTitle(title).setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text)).build(),
        )
    }

    fun ran(ctx: Context, r: Runner.Result) {
        val text = r.error ?: r.log.lastOrNull() ?: "Done"
        nm(ctx).notify(
            ("ran" + r.shortcut).hashCode(),
            base(ctx, CH_RESULT)
                .setContentTitle(if (r.error != null) "${r.shortcut} couldn't finish" else "${r.shortcut} ran")
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(r.log.joinToString("\n").ifBlank { text }))
                .build(),
        )
    }

    private fun action(ctx: Context, action: String, id: String, label: String): Notification.Action {
        val pi = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, NotifActionReceiver::class.java).setAction(action).setData(Uri.parse("shortcuts://n/$action/$id")).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, pi).build()
    }

    fun warn(ctx: Context, a: Automation) {
        val name = Store.shortcut(a.shortcutId)?.name ?: "Shortcut"
        val title = if (a.kind == TriggerKind.WhatsAppCallLong) "Still on the call?" else "$name runs in 1 minute"
        val text = if (a.kind == TriggerKind.WhatsAppCallLong)
            "“$name” will end your WhatsApp call in 1 minute."
        else "Tap “Not now” to skip it this time."
        val n = base(ctx, CH_WARN)
            .setContentTitle(title).setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis() + Scheduler.WARN_LEAD_MS)
            .setUsesChronometer(true).setChronometerCountDown(true)
            .setTimeoutAfter(Scheduler.WARN_LEAD_MS + 5_000)
            .addAction(action(ctx, ACTION_SNOOZE, a.id, "Snooze 30 min"))
            .addAction(action(ctx, ACTION_SKIP, a.id, "Not now"))
            .addAction(action(ctx, ACTION_RUN, a.id, "Run now"))
            .build()
        nm(ctx).notify(warnId(a.id), n)
    }

    fun cancelWarn(ctx: Context, id: String) = nm(ctx).cancel(warnId(id))
    private fun warnId(id: String) = ("warn$id").hashCode()
}
