package app.openshortcuts.engine

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import app.openshortcuts.ShortcutsApp
import app.openshortcuts.model.Library
import app.openshortcuts.model.Shortcut
import app.openshortcuts.model.Store
import app.openshortcuts.ui.Glyphs
import app.openshortcuts.ui.color
import kotlinx.coroutines.launch
import java.util.UUID

object Launchers {
    const val ACTION_RUN = "app.openshortcuts.RUN"
    private const val PREFS = "launchers"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * RunActivity has to be exported for launchers to open it. A per-install secret in each
     * intent stops other apps from running your shortcuts (hanging up calls, DND…).
     */
    private fun secret(ctx: Context): String = prefs(ctx).getString("secret", null)
        ?: UUID.randomUUID().toString().also { prefs(ctx).edit().putString("secret", it).apply() }

    fun isTrusted(ctx: Context, intent: Intent) = intent.getStringExtra("key") == secret(ctx)

    fun runIntent(ctx: Context, id: String): Intent =
        Intent(ACTION_RUN).setClass(ctx, RunActivity::class.java)
            .putExtra("id", id).putExtra("key", secret(ctx))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)

    fun icon(s: Shortcut): Icon = Icon.createWithAdaptiveBitmap(draw(s, 216, s.tint.color().toArgb(), 0.4f))

    /** White glyph on transparent: Quick Settings tints tile icons itself. */
    fun glyphIcon(s: Shortcut): Icon = Icon.createWithBitmap(draw(s, 96, null, 1f))

    private fun draw(s: Shortcut, size: Int, background: Int?, fill: Float): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        background?.let(canvas::drawColor)
        val (paths, filled) = Glyphs.pathData(s.glyph)
        val scale = size * fill / 24f
        canvas.translate((size - 24 * scale) / 2, (size - 24 * scale) / 2)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        paths.forEach { canvas.drawPath(PathParser.createPathFromPathData(it), paint) }
        return bmp
    }

    private fun info(ctx: Context, s: Shortcut, rank: Int = 0) = ShortcutInfo.Builder(ctx, s.id)
        .setShortLabel(s.name.take(24))
        .setLongLabel("Run ${s.name}".take(40))
        .setIcon(icon(s))
        .setIntent(runIntent(ctx, s.id))
        .setRank(rank)
        .build()

    fun sync(ctx: Context, lib: Library = Store.library.value) {
        val sm = ctx.getSystemService(ShortcutManager::class.java) ?: return
        runCatching {
            val max = sm.maxShortcutCountPerActivity.coerceAtMost(4)
            sm.dynamicShortcuts = lib.shortcuts.take(max).mapIndexed { i, s -> info(ctx, s, i) }
            val ids = lib.shortcuts.map { it.id }.toSet()
            val pinned = sm.pinnedShortcuts.map { it.id }
            sm.updateShortcuts(lib.shortcuts.filter { it.id in pinned }.map { info(ctx, it) })
            val gone = pinned.filterNot { it in ids }
            if (gone.isNotEmpty()) sm.disableShortcuts(gone, "This shortcut was deleted")
        }
        if (tileShortcutId(ctx)?.let { id -> lib.shortcuts.none { it.id == id } } == true) setTileShortcut(ctx, null)
        TileService.requestListeningState(ctx, ComponentName(ctx, RunTile::class.java))
    }

    fun canPin(ctx: Context) = ctx.getSystemService(ShortcutManager::class.java)?.isRequestPinShortcutSupported == true

    fun pin(ctx: Context, s: Shortcut) {
        ctx.getSystemService(ShortcutManager::class.java)?.requestPinShortcut(info(ctx, s), null)
    }

    fun tileShortcutId(ctx: Context): String? = prefs(ctx).getString("tile", null)

    fun setTileShortcut(ctx: Context, id: String?) {
        prefs(ctx).edit().putString("tile", id).apply()
        TileService.requestListeningState(ctx, ComponentName(ctx, RunTile::class.java))
    }

    fun offerTile(ctx: Context, s: Shortcut) {
        setTileShortcut(ctx, s.id)
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(ctx, RunTile::class.java), s.name.take(24), glyphIcon(s), ctx.mainExecutor,
            ) { }
        }
    }

    fun launch(ctx: Context, s: Shortcut, onDone: (Runner.Result) -> Unit = {}) {
        val app = ctx.applicationContext as ShortcutsApp
        app.scope.launch {
            val r = Runner.run(app, s, foreground = false)
            if (r.error != null) Notifs.ran(app, r)
            onDone(r)
        }
    }
}

class RunActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val s = intent.getStringExtra("id")?.let(Store::shortcut)
        when {
            !Launchers.isTrusted(this, intent) -> Unit
            s == null -> Toast.makeText(this, "That shortcut was deleted", Toast.LENGTH_SHORT).show()
            else -> {
                Toast.makeText(this, "Running ${s.name}", Toast.LENGTH_SHORT).show()
                Launchers.launch(this, s)
            }
        }
        finish()
    }
}

class RunTile : TileService() {
    private var running = false

    override fun onStartListening() = render()

    private fun render() {
        val tile = qsTile ?: return
        Store.init(this)
        val s = Launchers.tileShortcutId(this)?.let(Store::shortcut)
        tile.label = s?.name ?: "OpenShortcuts"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = when {
            running -> "Running…"
            s == null -> "Choose a shortcut"
            else -> "Tap to run"
        }
        tile.icon = s?.let(Launchers::glyphIcon) ?: Icon.createWithResource(this, app.openshortcuts.R.drawable.ic_stat)
        tile.state = if (running) Tile.STATE_ACTIVE else if (s == null) Tile.STATE_UNAVAILABLE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val s = Launchers.tileShortcutId(this)?.let(Store::shortcut) ?: return
        if (running) return
        val go = {
            running = true; render()
            Launchers.launch(this, s) { running = false; render() }
        }
        if (isLocked) unlockAndRun(go) else go()
    }
}
