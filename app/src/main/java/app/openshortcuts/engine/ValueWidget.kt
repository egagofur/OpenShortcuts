package app.openshortcuts.engine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.openshortcuts.MainActivity
import app.openshortcuts.R
import app.openshortcuts.ShortcutsApp
import app.openshortcuts.model.WorkspaceRead
import app.openshortcuts.model.WorkspaceReads
import kotlinx.coroutines.launch

/**
 * A home-screen widget showing one workspace value (picked like in Get Workspace Data). It refreshes
 * every half hour and when added; the last value stays on screen while offline. Tapping opens the workspace.
 */
class ValueWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
        val pending = goAsync()
        (ctx.applicationContext as ShortcutsApp).scope.launch {
            try { ids.forEach { refresh(ctx, it) } } finally { pending.finish() }
        }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        prefs(ctx).edit().apply { ids.forEach { remove("w$it"); remove("v$it"); remove("t$it"); remove("e$it") } }.apply()
    }

    companion object {
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("value_widgets", Context.MODE_PRIVATE)

        fun read(ctx: Context, id: Int): WorkspaceRead? = prefs(ctx).getString("w$id", null)?.let(WorkspaceRead::decode)

        fun save(ctx: Context, id: Int, read: WorkspaceRead) {
            prefs(ctx).edit().putString("w$id", read.encode()).remove("v$id").remove("t$id").remove("e$id").apply()
        }

        /** Reads the value fresh, keeps it, and redraws. A failed read keeps the last value and says so. */
        suspend fun refresh(ctx: Context, id: Int) {
            val read = read(ctx, id) ?: return
            WorkspaceReads.fetch(read)
                .onSuccess { v -> prefs(ctx).edit().putString("v$id", v).putLong("t$id", System.currentTimeMillis()).remove("e$id").apply() }
                .onFailure { e -> prefs(ctx).edit().putString("e$id", e.message ?: "Couldn't update").apply() }
            render(ctx, AppWidgetManager.getInstance(ctx), id)
        }

        /** Refreshes every value widget, e.g. after the app reads new data. */
        suspend fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.getAppWidgetIds(ComponentName(ctx, ValueWidget::class.java)).forEach { refresh(ctx, it) }
        }

        fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val p = prefs(ctx)
            val read = read(ctx, id)
            val v = RemoteViews(ctx.packageName, R.layout.widget_value)
            val workspace = read?.let { WorkspaceReads.resolve(it)?.first }
            v.setTextViewText(R.id.label, read?.label ?: "Tap to choose a value")
            val value = p.getString("v$id", null)
            v.setTextViewText(R.id.value, value?.lineSequence()?.firstOrNull()?.ifBlank { "–" } ?: "–")
            val at = p.getLong("t$id", 0L)
            val err = p.getString("e$id", null)
            val time = if (at > 0) android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(at)) else null
            v.setTextViewText(R.id.meta, listOfNotNull(workspace?.name, err?.let { "Couldn't update" } ?: time?.let { "at $it" }).joinToString(" · "))
            workspace?.tint?.let { t -> v.setInt(R.id.icon, "setColorFilter", app.openshortcuts.ui.tintArgb(t)) }
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                read?.let { putExtra(MainActivity.EXTRA_WORKSPACE, it.adapterId) }
            }
            v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            mgr.updateAppWidget(id, v)
        }
    }
}
