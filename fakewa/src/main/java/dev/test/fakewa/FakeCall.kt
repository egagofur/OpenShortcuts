package dev.test.fakewa

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log

/**
 * adb shell am start -n com.whatsapp/dev.test.fakewa.CallActivity --es mode <mode>
 *   ongoing  – CallStyle ongoing call (system labels the button "Hang up")
 *   legacy   – plain ongoing notification, category=call, button "Tutup" (older style)
 *   incoming – CallStyle incoming call with Decline/Answer (must NOT be hung up)
 *   message  – chat message (MessagingStyle); --es from <name> --es text <message>
 *   clear    – remove the call
 */
class CallActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "ongoing"
        if (mode == "message") FakeCall.message(this, intent.getStringExtra("from") ?: "Mama", intent.getStringExtra("text") ?: "Hi")
        else FakeCall.post(this, mode)
        finish()
    }
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(FakeCall.TAG, "ACTION ${intent.action} pressed")
        context.getSystemService(NotificationManager::class.java).cancel(FakeCall.ID)
    }
}

object FakeCall {
    const val TAG = "FakeWA"
    const val ID = 42

    private fun action(ctx: Context, name: String) = PendingIntent.getBroadcast(
        ctx, name.hashCode(), Intent(ctx, CallActionReceiver::class.java).setAction(name),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun message(ctx: Context, from: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("msgs", "Messages", NotificationManager.IMPORTANCE_HIGH))
        val me = Person.Builder().setName("You").build()
        val sender = Person.Builder().setName(from).build()
        val n = Notification.Builder(ctx, "msgs")
            .setSmallIcon(Icon.createWithResource(ctx, android.R.drawable.sym_action_chat))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(Notification.MessagingStyle(me).addMessage(text, System.currentTimeMillis(), sender))
            .build()
        nm.notify(from.hashCode(), n)
        Log.i(TAG, "message from $from")
    }

    fun post(ctx: Context, mode: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("calls", "Calls", NotificationManager.IMPORTANCE_HIGH))
        if (mode == "clear") { nm.cancel(ID); Log.i(TAG, "cleared"); return }
        val caller = Person.Builder().setName("Test Caller").setImportant(true).build()
        val full = PendingIntent.getActivity(ctx, 0, Intent(ctx, CallActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(ctx, "calls")
            .setSmallIcon(Icon.createWithResource(ctx, android.R.drawable.sym_call_outgoing))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(full, true)
        when (mode) {
            "incoming" -> b.setStyle(Notification.CallStyle.forIncomingCall(caller, action(ctx, "DECLINE"), action(ctx, "ANSWER")))
            "legacy" -> b.setContentTitle("Ongoing voice call").setContentText("Test Caller")
                .setUsesChronometer(true).setWhen(System.currentTimeMillis())
                .addAction(Notification.Action.Builder(null, "Tutup", action(ctx, "HANGUP")).build())
            else -> b.setStyle(Notification.CallStyle.forOngoingCall(caller, action(ctx, "HANGUP")))
                .setUsesChronometer(true).setWhen(System.currentTimeMillis())
        }
        nm.notify(ID, b.build())
        Log.i(TAG, "posted $mode")
    }
}
