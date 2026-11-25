package app.openshortcuts.engine

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.pow
import kotlin.random.Random

object Data {

    fun replace(input: String, find: String, with: String, regex: Boolean): String =
        if (find.isEmpty()) input
        else if (regex) Regex(find).replace(input, with)
        else input.replace(find, with)

    fun split(input: String, by: String): String = when (by) {
        "spaces" -> input.trim().split(Regex("\\s+"))
        "commas" -> input.split(',').map { it.trim() }
        "semicolons" -> input.split(';').map { it.trim() }
        "characters" -> input.map { it.toString() }
        else -> input.lines()
    }.filter { it.isNotEmpty() }.joinToString("\n")

    fun combine(input: String, with: String): String = lines(input).joinToString(
        when (with) { "spaces" -> " "; "commas" -> ", "; "nothing" -> ""; else -> "\n" },
    )

    fun match(input: String, pattern: String): String =
        if (pattern.isEmpty()) "" else Regex(pattern).findAll(input).joinToString("\n") { it.value }

    fun changeCase(input: String, case: String): String = when (case) {
        "UPPERCASE" -> input.uppercase()
        "lowercase" -> input.lowercase()
        "Title Case" -> input.split(" ").joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.titlecase() } }
        else -> input.lowercase().replaceFirstChar { it.titlecase() }
    }

    fun extract(input: String, what: String): String = when (what) {
        "Numbers" -> Regex("-?\\d+(?:[.,]\\d+)?")
        "URLs" -> Regex("https?://[^\\s]+|www\\.[^\\s]+")
        "Phone Numbers" -> Regex("\\+?\\d[\\d\\s-]{7,}\\d")
        else -> Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+")
    }.findAll(input).joinToString("\n") { it.value.trim() }

    fun lines(input: String): List<String> = input.lines().filter { it.isNotBlank() }

    fun item(input: String, which: String, random: Random = Random): String {
        val items = lines(input)
        if (items.isEmpty()) return ""
        return when (val w = which.trim().lowercase()) {
            "first", "" -> items.first()
            "last" -> items.last()
            "random" -> items[random.nextInt(items.size)]
            else -> {
                val n = w.toIntOrNull() ?: error("Use a number, \"last\" or \"random\" for the item.")
                val i = if (n < 0) items.size + n else n - 1
                items.getOrNull(i) ?: error("There's no item $n; the list has ${items.size}.")
            }
        }
    }

    fun count(input: String, what: String): Int = when (what) {
        "characters" -> input.length
        "words" -> input.split(Regex("\\s+")).count { it.isNotBlank() }
        else -> lines(input).size
    }

    fun append(list: String, value: String): String = if (list.isEmpty()) value else "$list\n$value"

    fun dictionary(body: String): String {
        val t = body.trim()
        if (t.startsWith("{")) return JSONObject(t).toString()
        val o = JSONObject()
        lines(t).forEach { line ->
            val i = line.indexOf(':')
            if (i > 0) o.put(line.substring(0, i).trim(), line.substring(i + 1).trim())
        }
        return o.toString()
    }

    fun getValue(json: String, key: String): String {
        var node: Any? = parse(json)
        if (key.isBlank()) return (node as? JSONObject)?.keys()?.asSequence()?.joinToString("\n").orEmpty()
        for (part in key.split('.')) {
            node = when (node) {
                is JSONObject -> node.opt(part)
                is JSONArray -> part.toIntOrNull()?.let { node.opt(it) }
                else -> null
            }
            if (node == null || node == JSONObject.NULL) return ""
        }
        return when (node) {
            is JSONArray -> (0 until node.length()).joinToString("\n") { node.get(it).toString() }
            else -> node.toString()
        }
    }

    fun setValue(json: String, key: String, value: String): String {
        val o = (parse(json) as? JSONObject) ?: JSONObject()
        o.put(key, value)
        return o.toString()
    }

    private fun parse(json: String): Any? = json.trim().let {
        when {
            it.startsWith("{") -> runCatching { JSONObject(it) }.getOrNull()
            it.startsWith("[") -> runCatching { JSONArray(it) }.getOrNull()
            else -> null
        }
    }

    fun calculate(expression: String): Double = Calc(expression.replace(',', '.').replace('×', '*').replace('÷', '/')).run()

    fun number(d: Double): String =
        if (d.isNaN() || d.isInfinite()) error("That calculation has no answer (division by zero?).")
        else if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString()
        else "%.10f".format(Locale.ROOT, d).trimEnd('0').trimEnd('.')

    fun round(input: String, places: Int): String {
        val d = input.trim().replace(',', '.').toDoubleOrNull() ?: error("“$input” isn't a number.")
        val f = 10.0.pow(places.coerceIn(0, 10))
        return number(Math.round(d * f) / f)
    }

    fun random(min: String, max: String, random: Random = Random): String {
        val lo = min.trim().toLongOrNull() ?: error("Minimum must be a whole number.")
        val hi = max.trim().toLongOrNull() ?: error("Maximum must be a whole number.")
        if (hi < lo) error("Maximum is smaller than minimum.")
        return random.nextLong(lo, hi + 1).toString()
    }

    private class Calc(val s: String) {
        var i = 0
        fun run(): Double {
            val v = expr()
            skip()
            if (i < s.length) error("Can't read “${s.substring(i)}” in the calculation.")
            return v
        }
        fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun expr(): Double {
            var v = term()
            while (true) {
                skip()
                v = when (s.getOrNull(i)) { '+' -> { i++; v + term() }; '-' -> { i++; v - term() }; else -> return v }
            }
        }
        fun term(): Double {
            var v = power()
            while (true) {
                skip()
                v = when (s.getOrNull(i)) {
                    '*' -> { i++; v * power() }
                    '/' -> { i++; v / power() }
                    '%' -> { i++; v % power() }
                    else -> return v
                }
            }
        }
        fun power(): Double {
            val base = unary()
            skip()
            return if (s.getOrNull(i) == '^') { i++; base.pow(power()) } else base
        }
        fun unary(): Double {
            skip()
            return if (s.getOrNull(i) == '-') { i++; -unary() } else atom()
        }
        fun atom(): Double {
            skip()
            if (s.getOrNull(i) == '(') {
                i++
                val v = expr()
                skip()
                if (s.getOrNull(i) != ')') error("A bracket is missing in the calculation.")
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) error("Expected a number in the calculation.")
            return s.substring(start, i).toDouble()
        }
    }

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun parseDate(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val t = text.trim()
        Regex("^(today|tomorrow|yesterday)\\s+(\\d{1,2}:\\d{2})$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            return parseDate(m.groupValues[1], now).toLocalDate().atTime(LocalTime.parse(m.groupValues[2].padStart(5, '0')))
        }
        return when (t.lowercase()) {
            "", "now" -> now
            "today" -> now.toLocalDate().atStartOfDay()
            "tomorrow" -> now.toLocalDate().plusDays(1).atStartOfDay()
            "yesterday" -> now.toLocalDate().minusDays(1).atStartOfDay()
            else -> runCatching { LocalDateTime.parse(t.replace(' ', 'T').let { if (it.length == 16) "$it:00" else it }, ISO) }
                .recoverCatching { LocalDate.parse(t).atStartOfDay() }
                .recoverCatching { now.toLocalDate().atTime(LocalTime.parse(t)) }
                .getOrElse { error("Can't read “$t” as a date. Try 2026-11-25 23:00, 23:00, today or now.") }
        }.withNano(0)
    }

    fun date(text: String, now: LocalDateTime = LocalDateTime.now()): String = ISO.format(parseDate(text, now))

    fun adjust(input: String, op: String, amount: String, unit: String, now: LocalDateTime = LocalDateTime.now()): String {
        val n = amount.trim().toLongOrNull() ?: error("“$amount” isn't a whole number.")
        val signed = if (op == "Subtract") -n else n
        return ISO.format(parseDate(input, now).plus(signed, chrono(unit)))
    }

    fun format(input: String, style: String, now: LocalDateTime = LocalDateTime.now(), locale: Locale = Locale.getDefault()): String {
        val d = parseDate(input, now)
        return when (style) {
            "Short" -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).format(d)
            "Time" -> DateTimeFormatter.ofPattern("HH:mm", locale).format(d)
            "Day name" -> DateTimeFormatter.ofPattern("EEEE", locale).format(d)
            "yyyy-MM-dd" -> DateTimeFormatter.ofPattern("yyyy-MM-dd", locale).format(d)
            "ISO 8601" -> ISO.format(d)
            else -> DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", locale).format(d)
        }
    }

    fun between(from: String, to: String, unit: String, now: LocalDateTime = LocalDateTime.now()): String =
        chrono(unit).between(parseDate(from, now), parseDate(to, now)).toString()

    private fun chrono(unit: String) = when (unit) {
        "seconds" -> ChronoUnit.SECONDS
        "minutes" -> ChronoUnit.MINUTES
        "hours" -> ChronoUnit.HOURS
        "weeks" -> ChronoUnit.WEEKS
        "months" -> ChronoUnit.MONTHS
        "years" -> ChronoUnit.YEARS
        else -> ChronoUnit.DAYS
    }

}

object When {
    data class Place(val lat: Double, val lon: Double)

    fun sun(day: java.time.LocalDate, place: Place, zone: java.time.ZoneId): Pair<java.time.LocalTime, java.time.LocalTime>? {
        val rad = Math.PI / 180
        val jDate = day.toEpochDay() + 2440587.5 + 0.5
        val n = kotlin.math.ceil(jDate - 2451545.0 + 0.0008)
        val jStar = n - place.lon / 360.0
        val m = ((357.5291 + 0.98560028 * jStar) % 360 + 360) % 360
        val c = 1.9148 * kotlin.math.sin(m * rad) + 0.02 * kotlin.math.sin(2 * m * rad) + 0.0003 * kotlin.math.sin(3 * m * rad)
        val lambda = ((m + c + 180 + 102.9372) % 360 + 360) % 360
        val jTransit = 2451545.0 + jStar + 0.0053 * kotlin.math.sin(m * rad) - 0.0069 * kotlin.math.sin(2 * lambda * rad)
        val sinDec = kotlin.math.sin(lambda * rad) * kotlin.math.sin(23.4397 * rad)
        val cosDec = kotlin.math.cos(kotlin.math.asin(sinDec))
        val cosW = (kotlin.math.sin(-0.833 * rad) - kotlin.math.sin(place.lat * rad) * sinDec) / (kotlin.math.cos(place.lat * rad) * cosDec)
        if (cosW < -1 || cosW > 1) return null
        val w = kotlin.math.acos(cosW) / rad
        fun local(j: Double) = java.time.Instant.ofEpochMilli(((j - 2440587.5) * 86_400_000).toLong()).atZone(zone).toLocalTime()
        return local(jTransit - w / 360) to local(jTransit + w / 360)
    }

    private fun dayOk(a: app.openshortcuts.model.Automation, d: java.time.LocalDate): Boolean {
        val cal = d.dayOfWeek.value % 7 + 1
        return when {
            a.timeMode == "time" && a.monthDay > 0 -> d.dayOfMonth == minOf(a.monthDay, d.lengthOfMonth())
            else -> a.days.isEmpty() || cal in a.days
        }
    }

    fun next(
        a: app.openshortcuts.model.Automation,
        from: java.time.LocalDateTime,
        place: Place?,
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): java.time.LocalDateTime? {
        val at = java.time.LocalTime.of(a.timeOfDay / 60 % 24, a.timeOfDay % 60)
        when (a.timeMode) {
            "once" -> {
                val d = runCatching { java.time.LocalDate.parse(a.date) }.getOrNull() ?: return null
                return d.atTime(at).takeIf { it.isAfter(from) }
            }
            "every" -> {
                val step = a.every.coerceAtLeast(15).toLong()
                val allDay = a.windowStart == a.windowEnd
                for (i in 0..8) {
                    val d = from.toLocalDate().plusDays(i.toLong())
                    if (!dayOk(a, d)) continue
                    val start = if (allDay) d.atStartOfDay() else d.atTime(a.windowStart / 60, a.windowStart % 60)
                    val end = if (allDay) d.plusDays(1).atStartOfDay()
                        else d.atTime(a.windowEnd / 60, a.windowEnd % 60).let { if (a.windowEnd <= a.windowStart) it.plusDays(1) else it }
                    var t = start
                    if (from >= start) {
                        val k = java.time.Duration.between(start, from).toMinutes() / step + 1
                        t = start.plusMinutes(k * step)
                    }
                    if (t.isAfter(from) && !t.isAfter(end)) return t
                }
                return null
            }
            else -> {
                for (i in 0..62) {
                    val d = from.toLocalDate().plusDays(i.toLong())
                    if (!dayOk(a, d)) continue
                    val t = when (a.timeMode) {
                        "sunrise", "sunset" -> {
                            val s = place?.let { sun(d, it, zone) }
                            val base = if (a.timeMode == "sunrise") s?.first ?: java.time.LocalTime.of(6, 0) else s?.second ?: java.time.LocalTime.of(18, 0)
                            d.atTime(base).plusMinutes(a.offset.toLong()).withSecond(0).withNano(0)
                        }
                        else -> d.atTime(at)
                    }
                    if (t.isAfter(from)) return t
                }
                return null
            }
        }
    }
}
