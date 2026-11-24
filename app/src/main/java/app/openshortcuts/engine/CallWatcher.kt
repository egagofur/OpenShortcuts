package app.openshortcuts.engine

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Watches WhatsApp's ongoing-call notification. That notification carries WhatsApp's own
 * "Hang up" button, so ending the call = pressing that button for the user. No root,
 * no accessibility service, and it works with the screen off.
 */
class CallWatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        runCatching { activeNotifications }.getOrNull()?.firstOrNull(::isOngoingCall)?.let(::markStarted)
            ?: markEnded()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP) return
        if (isOngoingCall(sbn)) {
            if (callKey != sbn.key) markStarted(sbn)
        } else if (sbn.key == callKey) {
            markEnded()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == callKey) markEnded()
    }

    private fun markStarted(sbn: StatusBarNotification) {
        callKey = sbn.key
        val n = sbn.notification
        // WhatsApp shows a running call timer; its base is the real call start.
        callStartedAt = if (n.`when` in 1..System.currentTimeMillis()) n.`when` else System.currentTimeMillis()
        Log.i(TAG, "call started")
        Scheduler.armCallTriggers(applicationContext, callStartedAt)
    }

    private fun markEnded() {
        if (callKey == null) return
        callKey = null
        callStartedAt = 0
        Log.i(TAG, "call ended")
        Scheduler.disarmCallTriggers(applicationContext)
    }

    enum class EndResult { Ended, NoCall, NotConnected, Failed }

    companion object {
        private const val TAG = "CallWatcher"
        val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val HANG = Regex("hang ?up|end call|^end$|leave|akhiri|tutup|putus|keluar|beenden|colgar|raccroch")
        private val ANSWER = Regex("answer|accept|jawab|terima|annehmen|contestar|répondre")

        @Volatile var instance: CallWatcher? = null
            private set
        @Volatile var callKey: String? = null
            private set
        @Volatile var callStartedAt: Long = 0
            private set

        val callActive get() = callKey != null

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, CallWatcher::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }

        fun isOngoingCall(sbn: StatusBarNotification): Boolean {
            if (sbn.packageName !in WHATSAPP) return false
            val n = sbn.notification
            if (Build.VERSION.SDK_INT >= 31) {
                when (n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0)) {
                    Notification.CallStyle.CALL_TYPE_ONGOING -> return true
                    Notification.CallStyle.CALL_TYPE_INCOMING,
                    Notification.CallStyle.CALL_TYPE_SCREENING -> return false
                }
            }
            val labels = n.actions?.map { it.title?.toString()?.trim()?.lowercase().orEmpty() } ?: return false
            val ongoingFlag = n.flags and Notification.FLAG_ONGOING_EVENT != 0 || n.category == Notification.CATEGORY_CALL
            return ongoingFlag && labels.any(HANG::containsMatchIn) && labels.none(ANSWER::containsMatchIn)
        }

        fun endWhatsAppCall(): EndResult {
            val svc = instance ?: return EndResult.NotConnected
            val sbn = runCatching { svc.activeNotifications }.getOrNull()?.firstOrNull(::isOngoingCall)
                ?: return EndResult.NoCall
            val action = sbn.notification.actions?.firstOrNull {
                HANG.containsMatchIn(it.title?.toString()?.trim()?.lowercase().orEmpty())
            } ?: return EndResult.Failed
            Log.i(TAG, "pressing \"${action.title}\"")
            return runCatching { action.actionIntent.send(); EndResult.Ended }.getOrDefault(EndResult.Failed)
        }

        /** Ask the system to bind us again after the process was killed. */
        fun rebind(context: Context) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, CallWatcher::class.java)) }
        }
    }
}
