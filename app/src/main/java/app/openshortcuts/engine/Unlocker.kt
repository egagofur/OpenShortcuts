package app.openshortcuts.engine

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens the phone without a PIN when the phone itself allows it: Smart Lock / Extend Unlock
 * (a trusted place or device) or a swipe-only lock screen. Never bypasses a PIN, pattern or
 * fingerprint the phone is asking for — Android doesn't allow that, and we wouldn't want it.
 */
object Unlocker {
    private const val TAG = "Unlocker"
    @Volatile private var waiting: CompletableDeferred<Boolean>? = null

    /** True when the screen is locked but the phone would open without a PIN right now. */
    fun canOpenWithoutPin(ctx: Context): Boolean {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        return !km.isDeviceLocked
    }

    /** Wake the screen and dismiss the lock screen. True once the phone is open. */
    suspend fun open(ctx: Context): Boolean {
        if (!canOpenWithoutPin(ctx)) return false
        val done = CompletableDeferred<Boolean>()
        waiting = done
        ctx.startActivity(
            Intent(ctx, WakeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
        )
        val ok = withTimeoutOrNull(6_000) { done.await() } == true
        Log.i(TAG, if (ok) "opened without PIN" else "couldn't open")
        return ok
    }

    internal fun result(ok: Boolean) { waiting?.complete(ok); waiting = null }
}

/** Invisible: turns the screen on over the lock screen and asks the system to dismiss it. */
class WakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) { Unlocker.result(true); finish(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { Unlocker.result(true); finish() }
            override fun onDismissError() { Unlocker.result(false); finish() }
            override fun onDismissCancelled() { Unlocker.result(false); finish() }
        })
    }
}
