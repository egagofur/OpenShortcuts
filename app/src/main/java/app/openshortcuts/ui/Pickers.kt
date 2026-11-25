package app.openshortcuts.ui

import android.Manifest
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openshortcuts.engine.Contacts
import app.openshortcuts.model.Tint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// --- Contacts -----------------------------------------------------------------------

/** Stable colour per name, like Contacts' monogram circles. */
fun monogramTint(name: String): Color {
    val tints = listOf(Tint.Blue, Tint.Green, Tint.Indigo, Tint.Orange, Tint.Pink, Tint.Purple, Tint.Teal, Tint.Red)
    return tints[(name.hashCode() and 0x7fffffff) % tints.size].color()
}

private fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.firstOrNull()?.isLetter() == true }
    val first = words.firstOrNull()?.let { String(Character.toChars(it.codePointAt(0))) }.orEmpty()
    val last = words.drop(1).lastOrNull()?.let { String(Character.toChars(it.codePointAt(0))) }.orEmpty()
    return (first + last).uppercase()
}

@Composable
fun Avatar(name: String, photo: android.net.Uri?, size: Dp = 40.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let { u ->
            withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
            }
        }
    }
    Box(Modifier.size(size).clip(CircleShape).background(monogramTint(name)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.size(size), contentScale = ContentScale.Crop)
        else {
            val ini = initials(name)
            if (ini.isEmpty()) Glyph("person", Color.White, size = size * 0.55f)
            else Text(ini, style = Theme.t.label.copy(fontSize = (size.value * 0.38f).sp), color = Color.White)
        }
    }
}

/** Small green "WhatsApp" mark used on rows and numbers that have it. */
@Composable
private fun WaBadge() {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Tint.Green.color()).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text("WhatsApp", style = Theme.t.small.copy(fontSize = 11.sp, lineHeight = 14.sp), color = Color.White)
    }
}

/** Two-segment control with a sliding pill (the iOS UISegmentedControl look). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Theme.c
    val view = LocalView.current
    val density = LocalDensity.current
    var w by remember { mutableIntStateOf(0) }
    val x by animateDpAsState(with(density) { (w * selected).toDp() }, Motion.settle(), label = "seg")
    Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(c.fill).padding(2.dp)) {
        if (w > 0) Box(
            Modifier.offset(x = x).width(with(density) { w.toDp() }).height(32.dp)
                .clip(RoundedCornerShape(8.dp)).background(c.raised),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).height(32.dp).onSizeChanged { if (i == 0) w = it.width }
                        .pressable(scale = 1f, label = o, role = androidx.compose.ui.semantics.Role.Tab) {
                            if (i != selected) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onSelect(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(o, style = Theme.t.label.copy(fontSize = 13.sp), color = if (i == selected) c.ink else c.ink2) }
            }
        }
    }
}

/**
 * Pick a person's number (or email) the way Contacts does: search, monograms, WhatsApp marks,
 * and a row to use whatever you typed instead. [onPick] gets the value and the person's name.
 */
@Composable
fun ContactPicker(
    email: Boolean,
    whatsapp: Boolean,
    current: String,
    variables: List<String>,
    onPick: (value: String, name: String?) -> Unit,
) {
    val c = Theme.c
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val allowed = remember(tick) { Contacts.allowed(ctx) }
    val people by produceState<List<Contacts.Person>?>(null, tick) {
        value = withContext(Dispatchers.IO) { Contacts.load(ctx) }
    }
    var q by remember { mutableStateOf(current.takeIf { it.startsWith("{") }.orEmpty()) }
    var onlyWa by remember { mutableStateOf(whatsapp) }
    var open by remember { mutableStateOf<Long?>(null) }

    Field(q, { q = it }, if (email) "Search or type an address" else "Search or type a number", autofocus = false)
    Spacer(Modifier.height(8.dp))
    VariableChips(variables) { q += it }

    if (!allowed) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile("person", Tint.Gray.color(), size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text("Allow Contacts to pick a person instead of typing.", style = Theme.t.small, color = c.ink, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.heightIn(min = 34.dp).pressable(label = "Allow Contacts") { ask.launch(Manifest.permission.READ_CONTACTS) }
                    .clip(RoundedCornerShape(17.dp)).background(c.accentWash).padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Allow", style = Theme.t.label, color = c.accent) }
        }
    } else if (!email) {
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("All Contacts", "On WhatsApp"), if (onlyWa) 1 else 0) { onlyWa = it == 1 }
    }

    val typed = q.trim()
    val needle = typed.lowercase()
    val shown = people.orEmpty().filter { p ->
        (if (email) p.emails.isNotEmpty() else p.phones.isNotEmpty()) &&
            (!onlyWa || email || p.whatsapp.isNotEmpty()) &&
            (needle.isEmpty() || p.name.lowercase().contains(needle) ||
                (Contacts.digits(typed).length >= 3 && p.phones.any { Contacts.digits(it).contains(Contacts.digits(typed)) }) ||
                p.emails.any { it.lowercase().contains(needle) })
    }

    Spacer(Modifier.height(10.dp))
    LazyColumn(Modifier.heightIn(max = if (isShort) 190.dp else 360.dp)) {
        // Offer to use the typed text only when it could be the value itself, never for a name search.
        val usable = typed.startsWith("{") ||
            (if (email) '@' in typed else Contacts.digits(typed).length >= 5 && typed.all { it.isDigit() || it in "+-() " })
        if (usable) item(key = "typed") {
            PickRow(title = "Use “$typed”", sub = if (typed.startsWith("{")) "Variable" else null, avatar = {
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.accentWash), contentAlignment = Alignment.Center) {
                    Glyph(if (typed.startsWith("{")) "braces" else if (email) "send" else "phone", c.accent, size = 20.dp)
                }
            }) { onPick(typed, null) }
        }
        if (allowed && people == null) item(key = "loading") {
            Text("Loading contacts…", style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(12.dp))
        }
        if (allowed && people != null && shown.isEmpty()) item(key = "none") {
            Text(
                when {
                    needle.isNotEmpty() -> "No contacts match “$typed”."
                    onlyWa -> "None of your contacts show up as WhatsApp users. Open WhatsApp once so it syncs, or choose All Contacts."
                    else -> "No contacts with ${if (email) "an email address" else "a phone number"}."
                },
                style = Theme.t.small, color = c.ink2, modifier = Modifier.padding(12.dp),
            )
        }
        items(shown, key = { it.id }) { p ->
            val values = if (email) p.emails else if (onlyWa) p.phones.filter(p::hasWhatsApp).ifEmpty { p.phones } else p.phones
            Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                // A contact saved as only a number shouldn't show that number twice.
                val nameIsNumber = Contacts.digits(p.name).length >= 6 && p.name.none(Char::isLetter)
                PickRow(
                    title = p.name,
                    sub = if (values.size > 1) "${values.size} ${if (email) "addresses" else "numbers"}" else if (nameIsNumber) null else values[0],
                    avatar = { Avatar(p.name, p.photo) },
                    trailing = {
                        // On the WhatsApp tab every row has it, so the mark would be noise.
                        if (!email && !onlyWa && p.whatsapp.isNotEmpty()) WaBadge()
                        if (values.size > 1) {
                            Spacer(Modifier.width(6.dp))
                            Glyph(if (open == p.id) "chevronDown" else "chevronRight", c.ink3, size = 16.dp)
                        }
                    },
                ) {
                    if (values.size == 1) onPick(values[0], p.name) else open = if (open == p.id) null else p.id
                }
                AnimatedVisibility(
                    open == p.id,
                    enter = expandVertically(tween(200, easing = Motion.easeOut)) + fadeIn(tween(150)),
                    exit = shrinkVertically(tween(150, easing = Motion.easeOut)) + fadeOut(tween(100)),
                ) {
                    Column(Modifier.padding(start = 52.dp)) {
                        values.forEach { v ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                    .pressable(scale = 0.98f, label = "Use $v") { onPick(v, p.name) }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(v, style = Theme.t.body, color = c.accent, modifier = Modifier.weight(1f))
                                if (!email && p.hasWhatsApp(v)) WaBadge()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(
    title: String,
    sub: String?,
    avatar: @Composable () -> Unit,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .pressable(scale = 0.98f, label = title, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatar()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.t.body, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = Theme.t.small, color = c.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

// --- Day and time ----------------------------------------------------------------------

private val ISO_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** "tomorrow 09:00" → "Tomorrow, 09:00"; "2026-11-28 09:00" → "Sat 28 Nov, 09:00". Variables pass through. */
fun dayTimeLabel(v: String): String {
    val m = Regex("^(today|tomorrow|\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}:\\d{2})$", RegexOption.IGNORE_CASE).find(v.trim()) ?: return v
    val (day, time) = m.destructured
    val d = when (day.lowercase()) {
        "today" -> "Today"
        "tomorrow" -> "Tomorrow"
        else -> runCatching { SHORT_DAY.format(LocalDate.parse(day, ISO_DAY)) }.getOrDefault(day)
    }
    return "$d, ${time.padStart(5, '0')}"
}

/** Today, Tomorrow and the next five days as chips, then the time on wheels. */
@Composable
fun DayTimePicker(current: String, onDone: (String) -> Unit) {
    val c = Theme.c
    val m = Regex("^(today|tomorrow|\\d{4}-\\d{2}-\\d{2})\\s+(\\d{1,2}):(\\d{2})$", RegexOption.IGNORE_CASE).find(current.trim())
    val today = LocalDate.now()
    val days = listOf("today", "tomorrow") + (2..6).map { ISO_DAY.format(today.plusDays(it.toLong())) }
    var day by remember { mutableStateOf(m?.groupValues?.get(1)?.lowercase()?.takeIf { it in days } ?: "tomorrow") }
    var h by remember { mutableIntStateOf(m?.groupValues?.get(2)?.toIntOrNull() ?: 9) }
    var min by remember { mutableIntStateOf(m?.groupValues?.get(3)?.toIntOrNull() ?: 0) }
    val view = LocalView.current

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days) { d ->
            val on = d == day
            val label = when (d) { "today" -> "Today"; "tomorrow" -> "Tomorrow"; else -> SHORT_DAY.format(LocalDate.parse(d, ISO_DAY)) }
            Box(
                Modifier.heightIn(min = 36.dp).pressable(scale = 0.95f, label = label) {
                    if (!on) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); day = d }
                }.clip(RoundedCornerShape(18.dp)).background(if (on) c.accent else c.fill).padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Theme.t.label, color = if (on) Color.White else c.ink) }
        }
    }
    Spacer(Modifier.height(14.dp))
    TimeWheels(h, min, { h = it }, { min = it })
    Spacer(Modifier.height(16.dp))
    PrimaryButton("Done", Modifier.fillMaxWidth()) { onDone("$day ${"%02d:%02d".format(h, min)}") }
}

// --- Numbers ------------------------------------------------------------------------------

/** − value + like UIStepper, with the value large between the buttons. */
@Composable
fun NumberStepper(value: Int, min: Int, max: Int, step: Int, suffix: String, onChange: (Int) -> Unit) {
    val c = Theme.c
    val view = LocalView.current
    fun go(d: Int) {
        val n = (value + d).coerceIn(min, max)
        if (n != value) { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); onChange(n) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GlassCircle("minus", "Decrease", size = 48.dp, fill = c.fill) { go(-step) }
        Text(
            "$value${suffix}",
            style = Theme.t.large.copy(fontFeatureSettings = "tnum"), color = c.ink,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f),
        )
        GlassCircle("plus", "Increase", size = 48.dp, fill = c.fill) { go(step) }
    }
}
