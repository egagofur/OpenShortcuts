package app.openshortcuts.engine

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import app.openshortcuts.model.ActionType
import app.openshortcuts.model.Shortcut

/** Everything the app may need, with plain reasons. `runtime` = ask with the system dialog. */
enum class Perm(val title: String, val why: String, val runtime: String? = null) {
    CallAccess("Notification access", "Lets OpenShortcuts see WhatsApp's call and press its Hang up button."),
    Notifications("Notifications", "Shows results and the one-minute warning before an automation runs.",
        Manifest.permission.POST_NOTIFICATIONS),
    Dnd("Do Not Disturb", "Turns Do Not Disturb on and off, and sets the ringer to silent."),
    WriteSettings("Modify system settings", "Changes screen brightness."),
    PhoneCalls("Phone calls", "Ends regular cellular calls.", Manifest.permission.ANSWER_PHONE_CALLS),
    Alarms("Alarms & reminders", "Runs automations at the exact minute you set."),
    Battery("Unrestricted battery", "Stops the system from putting automations to sleep overnight."),
    Accessibility("Lock screen", "Turn on OpenShortcuts under Accessibility to use the Lock Screen action. It reads nothing on screen.");

    fun granted(ctx: Context): Boolean = when (this) {
        CallAccess -> CallWatcher.isEnabled(ctx)
        Notifications -> Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(runtime!!) == PackageManager.PERMISSION_GRANTED
        Dnd -> ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        WriteSettings -> Settings.System.canWrite(ctx)
        PhoneCalls -> ctx.checkSelfPermission(runtime!!) == PackageManager.PERMISSION_GRANTED
        Alarms -> Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Battery -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        Accessibility -> LockService.isEnabled(ctx)
    }

    /** Settings screen for the special-access ones. */
    fun settingsIntent(ctx: Context): Intent {
        val pkg = Uri.parse("package:${ctx.packageName}")
        return when (this) {
            CallAccess -> if (Build.VERSION.SDK_INT >= 30)
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(ctx, CallWatcher::class.java).flattenToString(),
                )
            else Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Dnd -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            WriteSettings -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            Alarms -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            @Suppress("BatteryLife")
            Battery -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            Accessibility -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
    }

    companion object {
        fun forAction(t: ActionType): List<Perm> = when (t) {
            ActionType.END_WHATSAPP_CALL -> listOf(CallAccess)
            ActionType.END_PHONE_CALL -> listOf(PhoneCalls)
            ActionType.DND, ActionType.RINGER -> listOf(Dnd)
            ActionType.BRIGHTNESS -> listOf(WriteSettings)
            ActionType.NOTIFY -> listOf(Notifications)
            ActionType.LOCK -> listOf(Accessibility)
            else -> emptyList()
        }

        fun missing(ctx: Context, s: Shortcut): List<Perm> =
            s.actions.flatMap { forAction(it.type) }.distinct().filterNot { it.granted(ctx) }
    }
}
