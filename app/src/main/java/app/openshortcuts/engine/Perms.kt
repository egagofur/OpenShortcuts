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
    Autostart("Auto launch", "Lets OpenShortcuts start again by itself after the phone clears recent apps, so night automations still run."),
    Accessibility("Lock screen", "Turn on OpenShortcuts under Accessibility to use the Lock Screen action. It reads nothing on screen."),
    Call("Make phone calls", "Lets the Call action dial straight away instead of opening the dialer.", Manifest.permission.CALL_PHONE),
    Sms("Send SMS", "Lets “Send SMS … automatically” send without opening Messages. Standard SMS charges apply.", Manifest.permission.SEND_SMS),
    Calendar("Calendar", "Adds events and reads your upcoming ones.", Manifest.permission.WRITE_CALENDAR, listOf(Manifest.permission.READ_CALENDAR)),
    Contacts("Contacts", "Lets you pick a person when a shortcut calls, texts, emails or sends a WhatsApp message. Only what you pick is saved.", Manifest.permission.READ_CONTACTS),
    UsageAccess("Usage access", "Lets the App trigger see which app is on screen. Nothing is recorded or sent anywhere."),
    Bluetooth("Nearby devices", "Lets the Bluetooth trigger see which device connected.", "android.permission.BLUETOOTH_CONNECT"),
    BackgroundLocation("Location all the time", "Lets the Location trigger notice when you arrive or leave while the app is closed. Choose “Allow all the time”.", "android.permission.ACCESS_BACKGROUND_LOCATION"),
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
        Accessibility -> LockService.isEnabled(ctx) && !LockService.stuck(ctx)
        AutoSend -> SendService.isEnabled(ctx) && !SendService.stuck(ctx)
        UsageAccess -> ctx.getSystemService(android.app.AppOpsManager::class.java).unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName,
        ) == android.app.AppOpsManager.MODE_ALLOWED
        Bluetooth -> Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(runtime!!) == PackageManager.PERMISSION_GRANTED
        BackgroundLocation -> ctx.checkSelfPermission(runtime!!) == PackageManager.PERMISSION_GRANTED
        // No API reports this switch, so we trust the person once they say it's on.
        Autostart -> !OemAutostart.needed() || ctx.getSharedPreferences("perms", 0).getBoolean("autostart", false)
    }

    /** Whether Android can tell us it's on. If not, the person confirms it themselves. */
    val checkable: Boolean get() = this != Autostart

    /** On in Settings, but Android stopped the service; it needs switching off and on again. */
    fun stuck(ctx: Context): Boolean = when (this) {
        Accessibility -> LockService.stuck(ctx)
        AutoSend -> SendService.stuck(ctx)
        else -> false
    }

    /** Whether this phone has the setting at all. */
    fun applies(): Boolean = this != Autostart || OemAutostart.needed()

    fun confirm(ctx: Context) {
        if (this == Autostart) ctx.getSharedPreferences("perms", 0).edit().putBoolean("autostart", true).apply()
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
            UsageAccess -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, pkg)
            Autostart -> OemAutostart.intent(ctx) ?: OemAutostart.search(ctx) ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
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
            ActionType.END_WHATSAPP_CALL, ActionType.REPLY_WHATSAPP -> listOf(CallAccess, Autostart)
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

        /** What an automation's trigger needs to fire reliably (its shortcut's needs come separately). */
        fun forAutomation(a: app.openshortcuts.model.Automation): List<Perm> = buildList {
            val k = a.kind
            // Every trigger except clock time runs inside the notification-listener service.
            if (k != app.openshortcuts.model.TriggerKind.Time) add(CallAccess)
            if (k == app.openshortcuts.model.TriggerKind.App) add(UsageAccess)
            if (k == app.openshortcuts.model.TriggerKind.Bluetooth) add(Bluetooth)
            if (k == app.openshortcuts.model.TriggerKind.Location) { add(Location); add(BackgroundLocation) }
            if (k == app.openshortcuts.model.TriggerKind.Time && (a.timeMode == "sunrise" || a.timeMode == "sunset")) add(Location)
            add(Notifications); add(Alarms); add(Battery); add(Autostart)
        }

        /** Everything the library actually uses, granted or not, in a sensible asking order. */
        fun neededBy(lib: app.openshortcuts.model.Library): List<Perm> {
            val used = (lib.shortcuts.flatMap { s -> s.actions.flatMap { forAction(it) } } +
                lib.automations.filter { it.enabled }.flatMap { forAutomation(it) }).toSet()
            return entries.filter { it in used }
        }
    }
}

/**
 * Phones from Oppo, OnePlus, Realme, Xiaomi, Vivo, Huawei and Honor block apps from starting
 * themselves after "clear all" in recents, which silently stops the notification listener.
 * Each hides the switch somewhere else; try the known pages, newest first.
 */
object OemAutostart {
    private val brand get() = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()

    fun needed(): Boolean = listOf("oppo", "oneplus", "realme", "xiaomi", "redmi", "poco", "vivo", "iqoo", "huawei", "honor")
        .any { it in brand }

    private val candidates = listOf(
        Intent("com.oplus.battery.permission.startup.StartupAppListActivity"),
        Intent().setClassName("com.oplus.battery", "com.oplus.startupapp.view.StartupAppListActivity"),
        Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        Intent().setClassName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
    )

    /** ColorOS locks its Auto launch page to system apps, but Settings search finds it. */
    fun search(ctx: Context): Intent? = Intent("com.android.settings.action.SETTINGS_SEARCH")
        .takeIf { it.resolveActivity(ctx.packageManager) != null }

    /** The first page this phone has that we're allowed to open. */
    fun intent(ctx: Context): Intent? = candidates.firstOrNull { i ->
        val info = ctx.packageManager.resolveActivity(i, 0)?.activityInfo ?: return@firstOrNull false
        info.exported && (info.permission == null || ctx.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
