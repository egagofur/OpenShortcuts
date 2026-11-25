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
    private var events: EventHost? = null

    override fun onListenerConnected() {
        instance = this
        app.openshortcuts.model.Store.init(applicationContext)
        events?.stop()
        events = EventHost(applicationContext).also { it.start() }
        runCatching { activeNotifications }.getOrNull()?.firstOrNull(::isOngoingCall)?.let(::markStarted)
            ?: markEnded()
    }

    override fun onListenerDisconnected() {
        events?.stop(); events = null
        if (instance === this) instance = null
    }

    override fun onInterruptionFilterChanged(interruptionFilter: Int) =
        Events.dnd(applicationContext, interruptionFilter)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Events.notification(applicationContext, sbn)
        if (sbn.packageName !in WHATSAPP) return
        if (isOngoingCall(sbn)) {
            if (callKey != sbn.key) markStarted(sbn)
        } else if (sbn.key == callKey) {
            markEnded()
        } else if (!isCallNotification(sbn)) {
            Events.message(applicationContext, sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Events.removed(applicationContext, sbn)
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

        /** Ringing, ongoing or missed-call notifications: never treated as chat messages. */
        fun isCallNotification(sbn: StatusBarNotification): Boolean {
            val n = sbn.notification
            return n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_MISSED_CALL ||
                (Build.VERSION.SDK_INT >= 31 && n.extras.getInt(Notification.EXTRA_CALL_TYPE, 0) != 0)
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

        enum class ReplyResult { Sent, NoMessage, NotConnected, NoReplyButton }

        /**
         * Reply through the Reply button on WhatsApp's message notification (works while locked).
         * `chat` matches the chat or sender name; blank = the newest message.
         */
        fun replyWhatsApp(ctx: Context, chat: String, text: String): ReplyResult {
            val svc = instance ?: return ReplyResult.NotConnected
            val all = runCatching { svc.activeNotifications }.getOrNull().orEmpty().filter {
                it.packageName in WHATSAPP && !isCallNotification(it) &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
            fun names(sbn: StatusBarNotification): List<String> {
                val n = sbn.notification
                val style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
                return listOfNotNull(
                    n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
                    n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString(),
                    style?.conversationTitle?.toString(),
                    style?.messages?.lastOrNull()?.person?.name?.toString(),
                )
            }
            val target = all.filter { sbn -> chat.isBlank() || names(sbn).any { it.contains(chat.trim(), true) } }
                .maxByOrNull { it.postTime } ?: return ReplyResult.NoMessage
            val n = target.notification
            val action = (n.actions.orEmpty().toList() + Notification.WearableExtender(n).actions)
                .firstOrNull { it.remoteInputs?.isNotEmpty() == true } ?: return ReplyResult.NoReplyButton
            val results = android.os.Bundle().apply { action.remoteInputs.forEach { putCharSequence(it.resultKey, text) } }
            val fill = android.content.Intent()
            android.app.RemoteInput.addResultsToIntent(action.remoteInputs, fill, results)
            Log.i(TAG, "replying via \"${action.title}\"")
            action.actionIntent.send(ctx, 0, fill)
            return ReplyResult.Sent
        }

        /**
         * Make sure the listener is connected, asking Android to bind it again and waiting up to
         * [waitMs]. True when it's ready. Access switched off can't be fixed here.
         */
        suspend fun ensureConnected(context: Context, waitMs: Long = 6_000): Boolean {
            if (instance != null) return true
            if (!isEnabled(context)) return false
            rebind(context)
            val until = System.currentTimeMillis() + waitMs
            while (instance == null && System.currentTimeMillis() < until) kotlinx.coroutines.delay(150)
            return instance != null
        }

        /** Ask the system to bind us again after the process was killed. */
        fun rebind(context: Context) {
            val me = ComponentName(context, CallWatcher::class.java)
            // Some phones (Oppo/ColorOS) ignore requestRebind. Switching the component off and on
            // makes the system drop its stale record and bind the listener again.
            runCatching {
                val pm = context.packageManager
                pm.setComponentEnabledSetting(me, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(me, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            }
            runCatching { NotificationListenerService.requestRebind(me) }
        }
    }
}
