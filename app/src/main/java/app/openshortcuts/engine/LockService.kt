package app.openshortcuts.engine

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Exists only for the Lock Screen action. It listens to no events and can't read the
 * screen (see res/xml/lock_service.xml); locking this way keeps fingerprint/face unlock,
 * unlike device-admin lockNow(), which forces the PIN.
 */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        @Volatile var instance: LockService? = null
            private set

        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val me = ComponentName(ctx, LockService::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Switched on in Settings but not connected. Android leaves a service like this after the app
         * is updated or killed ("crashed"), and won't reconnect it until the switch is turned off and on.
         */
        fun stuck(ctx: Context): Boolean = isEnabled(ctx) && instance == null

        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true
    }
}
