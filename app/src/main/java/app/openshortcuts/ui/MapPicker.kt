package app.openshortcuts.ui

import android.location.Geocoder
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.openshortcuts.engine.Scheduler
import app.openshortcuts.model.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

data class Picked(val lat: Double, val lon: Double, val radius: Int, val name: String)

private val RADII = listOf(100, 150, 200, 300, 500, 750, 1000, 1500, 2000)

private fun radiusLabel(m: Int) = if (m < 1000) "$m m" else if (m % 1000 == 0) "${m / 1000} km" else "%.1f km".format(m / 1000f)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MapPicker(start: Picked?, onDismiss: () -> Unit, onDone: (Picked) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reduced = Theme.reduced
    remember {
        // OpenStreetMap's tile servers ask every app to identify itself.
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = ctx.packageName
    }
    var radius by remember { mutableIntStateOf(start?.radius ?: 150) }
    var name by remember { mutableStateOf(start?.name.orEmpty()) }
    var dragging by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var map by remember { mutableStateOf<MapView?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<android.location.Address>>(emptyList()) }

    val origin = remember {
        start?.takeIf { it.lat != 0.0 || it.lon != 0.0 }?.let { GeoPoint(it.lat, it.lon) }
            ?: Scheduler.place(ctx)?.let { GeoPoint(it.lat, it.lon) }
            ?: GeoPoint(-6.2, 106.82)
    }
    val lift by animateFloatAsState(if (dragging && !reduced) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "pin")

    fun center() = map?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) } ?: origin

    LaunchedEffect(tick) {
        if (tick == 0) return@LaunchedEffect
        delay(450)
        val p = center()
        name = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(ctx).getFromLocation(p.latitude, p.longitude, 1)?.firstOrNull()?.let { a ->
                    listOfNotNull(a.featureName?.takeIf { f -> !f.all(Char::isDigit) }, a.thoroughfare, a.subLocality ?: a.locality)
                        .distinct().take(2).joinToString(", ")
                }
            }.getOrNull()
        } ?: "Pinned place"
        dragging = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            AndroidView(
                factory = { context ->
                    MapView(context).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        isTilesScaledToDpi = true
                        controller.setZoom(17.0)
                        controller.setCenter(origin)
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean { dragging = true; tick++; return false }
                            override fun onZoom(event: ZoomEvent?): Boolean { tick++; return false }
                        })
                        map = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) { onDispose { map?.onDetach() } }

            Canvas(Modifier.fillMaxSize()) {
                tick
                val px = map?.projection?.metersToPixels(radius.toFloat()) ?: 0f
                drawCircle(c.accent.copy(alpha = 0.16f), px, center)
                drawCircle(c.accent.copy(alpha = 0.7f), px, center, style = Stroke(2.dp.toPx()))
            }
            Box(Modifier.align(Alignment.Center).graphicsLayer { translationY = -16.dp.toPx() - 8.dp.toPx() * lift }) {
                Box(Modifier.size(34.dp).shadow(6.dp, androidx.compose.foundation.shape.CircleShape).clip(androidx.compose.foundation.shape.CircleShape).background(Tint.Red.color()),
                    contentAlignment = Alignment.Center) { Glyph("pin", Color.White, size = 20.dp) }
            }
            Box(Modifier.align(Alignment.Center).size(6.dp).graphicsLayer { alpha = 0.35f + 0.3f * lift }.clip(androidx.compose.foundation.shape.CircleShape).background(Color.Black))

            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).widthIn(max = 520.dp).align(Alignment.TopCenter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Field(query, { query = it }, "Search for a place", autofocus = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassCircle("search", "Search", size = 48.dp) {
                        val q = query.trim(); if (q.isEmpty()) return@GlassCircle
                        scope.launch {
                            results = withContext(Dispatchers.IO) {
                                @Suppress("DEPRECATION")
                                runCatching { Geocoder(ctx).getFromLocationName(q, 5).orEmpty() }.getOrDefault(emptyList())
                            }
                        }
                    }
                }
                if (results.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.raised)) {
                        results.forEach { r ->
                            SettingsRow((0..r.maxAddressLineIndex).joinToString(", ") { r.getAddressLine(it) }.ifBlank { r.featureName ?: "Place" }, "pin", Tint.Red.color(), onClick = {
                                map?.controller?.animateTo(GeoPoint(r.latitude, r.longitude), 17.0, 600L)
                                results = emptyList(); query = ""
                            })
                        }
                    }
                }
            }

            Column(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).widthIn(max = 520.dp).fillMaxWidth()
                    .shadow(if (c.dark) 0.dp else 20.dp, RoundedCornerShape(26.dp), ambientColor = c.shadow, spotColor = c.shadow)
                    .clip(RoundedCornerShape(26.dp)).background(c.raised).padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (dragging) "Moving…" else name.ifBlank { "Drag the map to place the pin" }, style = Theme.t.label, color = c.ink, maxLines = 2)
                        Text("Within ${radiusLabel(radius)}", style = Theme.t.small, color = c.ink2)
                    }
                    GlassCircle("pin", "Go to my location", size = 44.dp) {
                        scope.launch {
                            Scheduler.place(ctx)?.let { map?.controller?.animateTo(GeoPoint(it.lat, it.lon), 17.0, 600L) }
                                ?: Scheduler.refreshPlace(ctx) { ok -> if (ok) Scheduler.place(ctx)?.let { p -> map?.controller?.animateTo(GeoPoint(p.lat, p.lon), 17.0, 600L) } }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = RADII.indexOf(radius).coerceAtLeast(0).toFloat(),
                    onValueChange = {
                        val r = RADII[it.toInt().coerceIn(0, RADII.lastIndex)]
                        if (r != radius) { radius = r; view.tick(); tick++ }
                    },
                    valueRange = 0f..RADII.lastIndex.toFloat(), steps = RADII.size - 2,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = c.accent, inactiveTrackColor = c.fill),
                    thumb = { IosSliderThumb() },
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietButton("Cancel", Modifier.weight(1f), color = c.ink, onClick = onDismiss)
                    PrimaryButton("Done", Modifier.weight(1f)) {
                        val p = center()
                        onDone(Picked(p.latitude, p.longitude, radius, name.ifBlank { "Pinned place" }))
                    }
                }
                Text("Map © OpenStreetMap contributors", style = Theme.t.small, color = c.ink3, modifier = Modifier.heightIn(min = 20.dp).padding(top = 6.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}
