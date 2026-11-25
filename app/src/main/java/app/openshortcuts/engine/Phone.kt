package app.openshortcuts.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.telephony.SmsManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone

object Phone {
    private fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    fun digits(raw: String): String = raw.filter { it.isDigit() || it == '+' }.ifEmpty { error("Enter a phone number.") }

    fun callIntent(ctx: Context, number: String): Intent {
        val uri = Uri.parse("tel:" + Uri.encode(digits(number)))
        return Intent(if (has(ctx, Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
    }

    fun smsIntent(number: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(digits(number)))).putExtra("sms_body", body)

    fun sendSms(ctx: Context, number: String, body: String) {
        if (!has(ctx, Manifest.permission.SEND_SMS)) error("Allow Send SMS in Setup to send automatically.")
        if (body.isBlank()) error("Type the message to send.")
        val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sms.sendMultipartTextMessage(digits(number), null, sms.divideMessage(body), null, null)
    }

    fun emailIntent(to: String, subject: String, body: String): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to.trim())))
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)

    fun shareIntent(text: String): Intent =
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share")

    fun timerIntent(minutes: Int, label: String): Intent = Intent(AlarmClock.ACTION_SET_TIMER)
        .putExtra(AlarmClock.EXTRA_LENGTH, minutes.coerceIn(1, 1440) * 60)
        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        .apply { if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label) }

    fun alarmIntent(time: String, label: String): Intent {
        val t = Data.parseDate(time)
        return Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, t.hour).putExtra(AlarmClock.EXTRA_MINUTES, t.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label) }
    }

    fun mapsIntent(query: String): Intent {
        val q = query.trim().ifEmpty { error("Say which place to show.") }
        val coords = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)$").find(q)
        return Intent(Intent.ACTION_VIEW, Uri.parse(
            if (coords != null) "geo:${coords.groupValues[1]},${coords.groupValues[2]}?q=${coords.groupValues[1]},${coords.groupValues[2]}"
            else "geo:0,0?q=" + Uri.encode(q),
        ))
    }

    private fun epoch(text: String): Long = Data.parseDate(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun addEvent(ctx: Context, title: String, start: String, minutes: Int): Intent? {
        if (title.isBlank()) error("Give the event a title.")
        val begin = epoch(start)
        val end = begin + minutes.coerceAtLeast(5) * 60_000L
        if (!has(ctx, Manifest.permission.WRITE_CALENDAR) || !has(ctx, Manifest.permission.READ_CALENDAR)) {
            return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        }
        val calendarId = ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${CalendarContract.Calendars.VISIBLE} = 1",
            null, "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: error("There's no calendar to add to. Add an account in Settings.")
        ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }) ?: error("The calendar didn't accept the event.")
        return null
    }

    fun upcoming(ctx: Context, hours: Int): String {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) error("Allow Calendar in Setup to read your events.")
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .let { android.content.ContentUris.appendId(it, now); android.content.ContentUris.appendId(it, now + hours * 3_600_000L); it }.build()
        val fmt = java.text.SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        return ctx.contentResolver.query(
            uri, arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val title = c.getString(0).orEmpty().ifBlank { "(No title)" }
                    add(if (c.getInt(2) == 1) "All day: $title" else "${fmt.format(java.util.Date(c.getLong(1)))} $title")
                }
            }.joinToString("\n")
        }.orEmpty()
    }

    @SuppressLint("MissingPermission")
    suspend fun location(ctx: Context): String {
        if (!has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION))
            error("Allow Location in Setup to get your location.")
        val lm = ctx.getSystemService(LocationManager::class.java)
        if (!lm.isLocationEnabled) error("Turn on Location in your phone's settings.")
        val providers = lm.getProviders(true)
        val provider = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in providers }
            ?: error("No location source is available.")
        val recent = providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            ?.takeIf { System.currentTimeMillis() - it.time < 120_000 }
        val fresh: Location? = recent ?: if (Build.VERSION.SDK_INT >= 30) withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                val cancel = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resumeWith(kotlin.Result.success(loc)) }
            }
        } else null
        val loc = fresh ?: providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            ?: error("Couldn't get a location fix. Try again outdoors or with Wi-Fi on.")
        return "%.5f, %.5f".format(Locale.ROOT, loc.latitude, loc.longitude)
    }
}
