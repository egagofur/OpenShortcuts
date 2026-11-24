package app.openshortcuts.model

import java.util.UUID

/** How a parameter is edited when its token pill is tapped. */
enum class ParamKind { TEXT, NUMBER, CHOICE, TIME, APP, SHORTCUT, INPUT }

data class ParamSpec(
    val key: String,
    val kind: ParamKind,
    val default: String,
    val choices: List<String> = emptyList(),
    val suffix: String = "",
    val min: Int = 0,
    val max: Int = 100,
    val placeholder: String = "",
)

enum class Category(val title: String) {
    Calls("Calls"), Sleep("Sleep & Focus"), Device("Device"),
    Scripting("Scripting"), Apps("Apps & Web"), Text("Text & Results"),
}

/** Tint names map to palette colours in the UI layer. */
enum class Tint { Blue, Green, Indigo, Orange, Red, Gray, Teal, Yellow, Pink, Purple }

/**
 * Every action is a sentence. `{key}` marks a token pill bound to a param.
 * A param named `body` renders as a text block under the header (like the Text action).
 */
enum class ActionType(
    val title: String,
    val template: String,
    val category: Category,
    val tint: Tint,
    val glyph: String,
    val params: List<ParamSpec> = emptyList(),
    val output: String? = null,
    val summary: String = "",
) {
    END_WHATSAPP_CALL(
        "End WhatsApp Call", "End WhatsApp call", Category.Calls, Tint.Green, "phoneOff",
        summary = "Hangs up an ongoing WhatsApp voice or video call. Needs notification access.",
    ),
    END_PHONE_CALL(
        "End Phone Call", "End phone call", Category.Calls, Tint.Green, "phoneOff",
        summary = "Hangs up a regular cellular call.",
    ),
    DND(
        "Do Not Disturb", "Turn {state} Do Not Disturb", Category.Sleep, Tint.Indigo, "moon",
        listOf(ParamSpec("state", ParamKind.CHOICE, "On", listOf("On", "Off"))),
    ),
    RINGER(
        "Set Ringer Mode", "Set ringer to {mode}", Category.Sleep, Tint.Indigo, "bell",
        listOf(ParamSpec("mode", ParamKind.CHOICE, "Vibrate", listOf("Normal", "Vibrate", "Silent"))),
    ),
    MEDIA(
        "Control Media", "{op} media", Category.Sleep, Tint.Pink, "pause",
        listOf(ParamSpec("op", ParamKind.CHOICE, "Pause", listOf("Pause", "Play", "Next", "Previous"))),
        summary = "Pauses YouTube, Spotify or whatever is playing.",
    ),
    VOLUME(
        "Set Volume", "Set {stream} volume to {level}", Category.Device, Tint.Pink, "volume",
        listOf(
            ParamSpec("stream", ParamKind.CHOICE, "Media", listOf("Media", "Ring", "Alarm", "Call")),
            ParamSpec("level", ParamKind.NUMBER, "30", suffix = "%", min = 0, max = 100),
        ),
    ),
    BRIGHTNESS(
        "Set Brightness", "Set brightness to {level}", Category.Device, Tint.Yellow, "sun",
        listOf(ParamSpec("level", ParamKind.NUMBER, "10", suffix = "%", min = 0, max = 100)),
    ),
    FLASHLIGHT(
        "Flashlight", "Turn {state} flashlight", Category.Device, Tint.Blue, "flash",
        listOf(ParamSpec("state", ParamKind.CHOICE, "On", listOf("On", "Off"))),
    ),
    VIBRATE("Vibrate Device", "Vibrate device", Category.Device, Tint.Gray, "vibrate"),
    BATTERY(
        "Get Battery Level", "Get battery level", Category.Device, Tint.Green, "battery",
        output = "Battery Level",
    ),
    WAIT(
        "Wait", "Wait {seconds}", Category.Scripting, Tint.Gray, "timer",
        listOf(ParamSpec("seconds", ParamKind.NUMBER, "5", suffix = " sec", min = 1, max = 3600)),
    ),
    IF(
        "If", "If {subject} {op} {value}", Category.Scripting, Tint.Gray, "branch",
        listOf(
            ParamSpec("subject", ParamKind.CHOICE, "Battery Level",
                listOf("Battery Level", "Hour", "Charging", "Previous Result", "WhatsApp Call")),
            ParamSpec("op", ParamKind.CHOICE, "is below", listOf("is", "is not", "is below", "is above")),
            ParamSpec("value", ParamKind.TEXT, "20", placeholder = "Value"),
        ),
    ),
    OTHERWISE("Otherwise", "Otherwise", Category.Scripting, Tint.Gray, "branch"),
    END_IF("End If", "End If", Category.Scripting, Tint.Gray, "branch"),
    STOP("Stop This Shortcut", "Stop this shortcut", Category.Scripting, Tint.Gray, "stop"),
    RUN_SHORTCUT(
        "Run Shortcut", "Run {shortcut}", Category.Scripting, Tint.Purple, "layers",
        listOf(ParamSpec("shortcut", ParamKind.SHORTCUT, "", placeholder = "Shortcut")),
    ),
    OPEN_APP(
        "Open App", "Open {app}", Category.Apps, Tint.Blue, "grid",
        listOf(ParamSpec("app", ParamKind.APP, "", placeholder = "App")),
    ),
    OPEN_URL(
        "Open URL", "Open {url}", Category.Apps, Tint.Blue, "link",
        listOf(ParamSpec("url", ParamKind.TEXT, "https://", placeholder = "URL")),
    ),
    TEXT(
        "Text", "Text", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("body", ParamKind.TEXT, "", placeholder = "Text")),
        output = "Text",
    ),
    SHOW(
        "Show Result", "Show {input}", Category.Text, Tint.Yellow, "quote",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Text")),
    ),
    NOTIFY(
        "Show Notification", "Show notification {body}", Category.Text, Tint.Red, "bell",
        listOf(ParamSpec("body", ParamKind.TEXT, "Hello", placeholder = "Message")),
    );

    /** Indentation behaviour for the control-flow family. */
    val opensBlock get() = this == IF
    val isMidBlock get() = this == OTHERWISE
    val closesBlock get() = this == END_IF
}

data class Action(
    val id: String = UUID.randomUUID().toString(),
    val type: ActionType,
    val params: Map<String, String> = type.params.associate { it.key to it.default },
) {
    fun p(key: String): String = params[key] ?: type.params.firstOrNull { it.key == key }?.default.orEmpty()
}

data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tint: Tint = Tint.Blue,
    val glyph: String = "layers",
    val actions: List<Action> = emptyList(),
)

enum class TriggerKind(val title: String) {
    Time("Time of Day"),
    WhatsAppCallLong("WhatsApp Call Runs Long"),
}

/**
 * `days` uses java.util.Calendar day numbers (1 = Sunday). Empty = every day.
 * Times are minutes since midnight.
 */
data class Automation(
    val id: String = UUID.randomUUID().toString(),
    val kind: TriggerKind,
    val timeOfDay: Int = 23 * 60,
    val days: Set<Int> = emptySet(),
    val callMinutes: Int = 30,
    val windowStart: Int = 22 * 60,
    val windowEnd: Int = 6 * 60,
    val shortcutId: String = "",
    val enabled: Boolean = true,
    val warnFirst: Boolean = true,
)

data class Library(
    val shortcuts: List<Shortcut> = emptyList(),
    val automations: List<Automation> = emptyList(),
)

fun Int.hhmm(): String = "%02d:%02d".format(this / 60, this % 60)

fun Set<Int>.daysLabel(): String = when {
    isEmpty() || size == 7 -> "Daily"
    this == setOf(2, 3, 4, 5, 6) -> "Weekdays"
    this == setOf(1, 7) -> "Weekends"
    else -> sorted().joinToString(" ") { listOf("", "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")[it] }
}

/** Inclusive start, exclusive end; wraps past midnight when end < start. */
fun inWindow(minuteOfDay: Int, start: Int, end: Int): Boolean =
    if (start == end) true
    else if (start < end) minuteOfDay in start until end
    else minuteOfDay >= start || minuteOfDay < end

object Seeds {
    fun library(): Library {
        val sleep = Shortcut(
            name = "Sleep Mode", tint = Tint.Indigo, glyph = "moon",
            actions = listOf(
                Action(type = ActionType.MEDIA),
                Action(type = ActionType.END_WHATSAPP_CALL),
                Action(type = ActionType.DND),
                Action(type = ActionType.BRIGHTNESS),
                Action(type = ActionType.NOTIFY, params = mapOf("body" to "Call ended. Sleep well.")),
            ),
        )
        val hangUp = Shortcut(
            name = "Hang Up WhatsApp", tint = Tint.Green, glyph = "phoneOff",
            actions = listOf(Action(type = ActionType.END_WHATSAPP_CALL)),
        )
        val battery = Shortcut(
            name = "Battery Check", tint = Tint.Orange, glyph = "battery",
            actions = listOf(
                Action(type = ActionType.BATTERY),
                Action(type = ActionType.IF),
                Action(type = ActionType.NOTIFY, params = mapOf("body" to "Battery low. Plug in before you sleep.")),
                Action(type = ActionType.OTHERWISE),
                Action(type = ActionType.SHOW),
                Action(type = ActionType.END_IF),
            ),
        )
        val morning = Shortcut(
            name = "Good Morning", tint = Tint.Yellow, glyph = "sun",
            actions = listOf(
                Action(type = ActionType.DND, params = mapOf("state" to "Off")),
                Action(type = ActionType.RINGER, params = mapOf("mode" to "Normal")),
                Action(type = ActionType.BRIGHTNESS, params = mapOf("level" to "60")),
            ),
        )
        return Library(
            shortcuts = listOf(sleep, hangUp, battery, morning),
            automations = listOf(
                Automation(kind = TriggerKind.WhatsAppCallLong, callMinutes = 45,
                    windowStart = 23 * 60, windowEnd = 5 * 60, shortcutId = sleep.id, enabled = false),
                Automation(kind = TriggerKind.Time, timeOfDay = 23 * 60 + 30,
                    shortcutId = sleep.id, enabled = false),
            ),
        )
    }
}
