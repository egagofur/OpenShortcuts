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
enum class Perm(val title: String, val why: String, val runtime: String? = null, val more: List<String> = emptyList()) {
    CallAccess("Notification access", "Lets OpenShortcuts see WhatsApp's call and press its Hang up button."),
    Notifications("Notifications", "Shows what an automation did, and the optional “Ask before running” notice.",
        Manifest.permission.POST_NOTIFICATIONS),
    Dnd("Do Not Disturb", "Turns Do Not Disturb on and off, and sets the ringer to silent."),
    WriteSettings("Modify system settings", "Changes screen brightness."),
    PhoneCalls("End phone calls", "Lets End Phone Call hang up regular cellular calls.", Manifest.permission.ANSWER_PHONE_CALLS),
    Alarms("Alarms & reminders", "Runs automations at the exact minute you set."),
    Battery("Unrestricted battery", "Stops the system from putting automations to sleep overnight."),
    Accessibility("Lock screen", "Turn on OpenShortcuts under Accessibility to use the Lock Screen action. It reads nothing on screen."),
    Call("Make phone calls", "Lets the Call action dial straight away instead of opening the dialer.", Manifest.permission.CALL_PHONE),
    Sms("Send SMS", "Lets “Send SMS … automatically” send without opening Messages. Standard SMS charges apply.", Manifest.permission.SEND_SMS),
    Calendar("Calendar", "Adds events and reads your upcoming ones.", Manifest.permission.WRITE_CALENDAR, listOf(Manifest.permission.READ_CALENDAR)),
    Contacts("Contacts", "Lets you pick a person when a shortcut calls, texts, emails or sends a WhatsApp message. Only what you pick is saved.", Manifest.permission.READ_CONTACTS),
    Location("Location", "Gets your current location for “Get Current Location”.", Manifest.permission.ACCESS_FINE_LOCATION, listOf(Manifest.permission.ACCESS_COARSE_LOCATION)),
    AutoSend("Automatic WhatsApp sending", "Optional. Lets “Send WhatsApp … automatically” press Send for you. It can only see WhatsApp, and only acts on a message your shortcut just typed.");

    fun granted(ctx: Context): Boolean = when (this) {
        CallAccess -> CallWatcher.isEnabled(ctx)
        Notifications -> Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(runtime!!) == PackageManager.PERMISSION_GRANTED
        Dnd -> ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        WriteSettings -> Settings.System.canWrite(ctx)
        PhoneCalls, Call, Sms, Calendar, Location, Contacts -> (listOf(runtime!!) + more).all {
            ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        Alarms -> Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        Battery -> ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        Accessibility -> LockService.isEnabled(ctx)
        AutoSend -> SendService.isEnabled(ctx)
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
            Accessibility, AutoSend -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
    }

    companion object {
        fun forAction(a: app.openshortcuts.model.Action): List<Perm> = when (a.type) {
            ActionType.SEND_WHATSAPP -> if (a.p("mode") == "automatically") listOf(AutoSend) else emptyList()
            ActionType.SEND_SMS -> if (a.p("mode") == "automatically") listOf(Sms) else emptyList()
            else -> forAction(a.type)
        }

        fun forAction(t: ActionType): List<Perm> = when (t) {
            ActionType.END_WHATSAPP_CALL, ActionType.REPLY_WHATSAPP -> listOf(CallAccess)
            ActionType.END_PHONE_CALL -> listOf(PhoneCalls)
            ActionType.DND, ActionType.RINGER -> listOf(Dnd)
            ActionType.BRIGHTNESS -> listOf(WriteSettings)
            ActionType.NOTIFY -> listOf(Notifications)
            ActionType.LOCK -> listOf(Accessibility)
            ActionType.ADD_EVENT, ActionType.UPCOMING_EVENTS -> listOf(Calendar)
            ActionType.LOCATION -> listOf(Location)
            else -> emptyList()
        }

        fun missing(ctx: Context, s: Shortcut): List<Perm> =
            s.actions.flatMap { forAction(it) }.distinct().filterNot { it.granted(ctx) }
    }
}
