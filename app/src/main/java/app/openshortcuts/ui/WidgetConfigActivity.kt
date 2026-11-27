package app.openshortcuts.ui

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openshortcuts.ShortcutsApp
import app.openshortcuts.engine.ValueWidget
import app.openshortcuts.model.WorkspaceRead
import kotlinx.coroutines.launch

/** Choosing what a value widget shows, when it's added or reconfigured. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out leaves no widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val current = ValueWidget.read(this, id)?.encode().orEmpty()
        setContent {
            ShortcutsTheme {
                Column(
                    Modifier.fillMaxSize().background(Theme.c.bg).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text("Widget", style = Theme.t.large, color = Theme.c.ink)
                    Spacer(Modifier.height(16.dp))
                    WorkspaceDataPicker(current, "Shown on your home screen and updated every half hour. Tap the widget to open its workspace.") { raw ->
                        val read = WorkspaceRead.decode(raw) ?: return@WorkspaceDataPicker
                        ValueWidget.save(this@WidgetConfigActivity, id, read)
                        ValueWidget.render(this@WidgetConfigActivity, AppWidgetManager.getInstance(this@WidgetConfigActivity), id)
                        (applicationContext as ShortcutsApp).scope.launch { ValueWidget.refresh(applicationContext, id) }
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }
                    Spacer(Modifier.height(12.dp))
                    QuietButton("Cancel", Modifier.fillMaxWidth()) { finish() }
                }
            }
        }
    }
}
