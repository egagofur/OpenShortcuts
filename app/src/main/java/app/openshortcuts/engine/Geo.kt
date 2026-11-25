package app.openshortcuts.engine

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import app.openshortcuts.model.Library
import app.openshortcuts.model.Store
import app.openshortcuts.model.TriggerKind

object Geo {
    private const val TAG = "Geo"

    private fun pending(ctx: Context, id: String, create: Boolean): PendingIntent? = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, GeoReceiver::class.java).setData(Uri.parse("shortcuts://geo/$id")).putExtra("id", id),
        // Mutable: the system adds whether we're entering or leaving.
        PendingIntent.FLAG_MUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE),
    )

    fun sync(ctx: Context, lib: Library = Store.library.value) {
        val lm = ctx.getSystemService(LocationManager::class.java)
        val allowed = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        lib.automations.filter { it.kind == TriggerKind.Location }.forEach { a ->
            pending(ctx, a.id, false)?.let { runCatching { lm.removeProximityAlert(it) }; it.cancel() }
            if (!a.enabled || !allowed || (a.lat == 0.0 && a.lon == 0.0)) return@forEach
            runCatching {
                lm.addProximityAlert(a.lat, a.lon, a.radius.toFloat(), -1, pending(ctx, a.id, true)!!)
                Log.i(TAG, "watching ${a.place.ifBlank { "a place" }} (${a.radius} m)")
            }.onFailure { Log.w(TAG, "couldn't watch place", it) }
        }
    }
}

class GeoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val a = Store.automation(intent.getStringExtra("id") ?: return) ?: return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        Log.i("Geo", if (entering) "arrived" else "left")
        if (a.enabled && a.on == entering) Events.run(context, a, a.place)
    }
}
