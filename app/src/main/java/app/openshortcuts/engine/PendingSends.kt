package app.openshortcuts.engine

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject

object PendingSends {
    data class Item(val number: String, val name: String, val text: String, val auto: Boolean)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pending_sends", Context.MODE_PRIVATE)

    fun locked(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked || !ctx.getSystemService(PowerManager::class.java).isInteractive

    fun add(ctx: Context, item: Item) {
        val arr = runCatching { JSONArray(prefs(ctx).getString("items", "[]")) }.getOrDefault(JSONArray())
        arr.put(JSONObject().put("number", item.number).put("name", item.name).put("text", item.text).put("auto", item.auto))
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }

    fun take(ctx: Context): List<Item> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("items", "[]")) }.getOrDefault(JSONArray())
        prefs(ctx).edit().remove("items").apply()
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Item(it.optString("number"), it.optString("name"), it.optString("text"), it.optBoolean("auto"))
        }
    }

    fun count(ctx: Context) = runCatching { JSONArray(prefs(ctx).getString("items", "[]")).length() }.getOrDefault(0)
}
