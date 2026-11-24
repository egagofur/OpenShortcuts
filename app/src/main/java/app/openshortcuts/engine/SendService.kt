package app.openshortcuts.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred

/**
 * Optional, separate from LockService. It can only see WhatsApp (res/xml/send_service.xml) and does
 * one thing: press Send, and only while a shortcut is waiting for a message it just typed.
 */
class SendService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }
    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val job = pending ?: return
        if (SystemClock.elapsedRealtime() > job.deadline) { pending = null; job.done.complete(false); return }
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg !in CallWatcher.WHATSAPP) return
        // Only press Send once the box holds exactly the text this shortcut asked for.
        val entry = root.findAccessibilityNodeInfosByViewId("$pkg:id/entry").firstOrNull()
            ?: find(root) { it.isEditable }
        if (entry?.text?.toString()?.trim() != job.text.trim()) return
        val send = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull()
            ?: find(root) { n -> n.isClickable && n.contentDescription?.toString()?.trim()?.lowercase() in SEND_LABELS }
            ?: return
        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.i("SendService", "pressed Send")
            pending = null
            job.done.complete(true)
        }
    }

    private fun find(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { c -> find(c, match)?.let { return it } }
        return null
    }

    class Job(val text: String, val deadline: Long, val done: CompletableDeferred<Boolean> = CompletableDeferred())

    companion object {
        private val SEND_LABELS = setOf("send", "kirim", "enviar", "senden", "envoyer")
        @Volatile var instance: SendService? = null
            private set
        @Volatile private var pending: Job? = null

        fun expect(text: String, timeoutMs: Long = 15_000): Job =
            Job(text, SystemClock.elapsedRealtime() + timeoutMs).also { pending = it }

        fun cancel() { pending?.done?.complete(false); pending = null }

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, SendService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
