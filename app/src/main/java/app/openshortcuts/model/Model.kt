package app.openshortcuts.model

import java.util.UUID

enum class ParamKind { TEXT, NUMBER, CHOICE, TIME, APP, SHORTCUT, INPUT, PHONE, EMAIL, DATETIME }

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
    Calls("Calls"), Messages("Messages & Sharing"), Clock("Clock & Calendar"), Location("Location & Maps"), Sleep("Sleep & Focus"), Device("Device"),
    Scripting("Scripting"), Text("Text & Results"), Lists("Lists & Dictionaries"), Math("Numbers & Dates"), Apps("Apps & Web"),
}

enum class Tint { Blue, Green, Indigo, Orange, Red, Gray, Teal, Yellow, Pink, Purple }

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
    REPLY_WHATSAPP(
        "Reply on WhatsApp", "Reply on WhatsApp to {chat}", Category.Messages, Tint.Green, "quote",
        listOf(
            ParamSpec("chat", ParamKind.TEXT, "{Message Chat}", placeholder = "Latest chat"),
            ParamSpec("body", ParamKind.TEXT, "Lagi tidur, besok aku kabari ya.", placeholder = "Reply"),
        ),
        summary = "Replies through WhatsApp's notification, even when the phone is locked. The chat needs an unread message notification.",
    ),
    SEND_WHATSAPP(
        "Send WhatsApp Message", "Send WhatsApp to {number} {mode}", Category.Messages, Tint.Green, "send",
        listOf(
            ParamSpec("number", ParamKind.PHONE, "", placeholder = "Contact"),
            ParamSpec("mode", ParamKind.CHOICE, "and let me tap Send", listOf("and let me tap Send", "automatically")),
            ParamSpec("body", ParamKind.TEXT, "", placeholder = "Message"),
        ),
        summary = "Opens the chat with your message typed in. \"Automatically\" also presses Send (needs Accessibility, phone unlocked).",
    ),
    CALL(
        "Call", "Call {number}", Category.Calls, Tint.Green, "phone",
        listOf(ParamSpec("number", ParamKind.PHONE, "", placeholder = "Contact")),
        summary = "Calls straight away with the Make phone calls permission; otherwise opens the dialer.",
    ),
    SEND_SMS(
        "Send SMS", "Send SMS to {number} {mode}", Category.Messages, Tint.Green, "quote",
        listOf(
            ParamSpec("number", ParamKind.PHONE, "", placeholder = "Contact"),
            ParamSpec("mode", ParamKind.CHOICE, "and let me tap Send", listOf("and let me tap Send", "automatically")),
            ParamSpec("body", ParamKind.TEXT, "", placeholder = "Message"),
        ),
        summary = "\"Automatically\" sends without opening Messages (needs the Send SMS permission).",
    ),
    EMAIL(
        "Send Email", "Email {to} about {subject}", Category.Messages, Tint.Blue, "send",
        listOf(
            ParamSpec("to", ParamKind.EMAIL, "", placeholder = "Contact"),
            ParamSpec("subject", ParamKind.TEXT, "", placeholder = "Subject"),
            ParamSpec("body", ParamKind.TEXT, "", placeholder = "Message"),
        ),
        summary = "Opens your email app with everything filled in.",
    ),
    SHARE(
        "Share", "Share {input}", Category.Messages, Tint.Blue, "share",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Text")),
    ),
    TIMER(
        "Start Timer", "Start a timer for {minutes} {label}", Category.Clock, Tint.Orange, "timer",
        listOf(
            ParamSpec("minutes", ParamKind.NUMBER, "10", suffix = " min", min = 1, max = 1440),
            ParamSpec("label", ParamKind.TEXT, "", placeholder = "Label"),
        ),
        summary = "Starts a countdown in your Clock app.",
    ),
    ALARM(
        "Set Alarm", "Set an alarm for {time} {label}", Category.Clock, Tint.Orange, "clock",
        listOf(
            ParamSpec("time", ParamKind.TIME, "06:00", placeholder = "Time"),
            ParamSpec("label", ParamKind.TEXT, "", placeholder = "Label"),
        ),
        summary = "Adds an alarm to your Clock app.",
    ),
    ADD_EVENT(
        "Add Calendar Event", "Add event {title} at {start} for {minutes}", Category.Clock, Tint.Red, "calendar",
        listOf(
            ParamSpec("title", ParamKind.TEXT, "", placeholder = "Title"),
            ParamSpec("start", ParamKind.DATETIME, "tomorrow 09:00", placeholder = "When"),
            ParamSpec("minutes", ParamKind.NUMBER, "60", suffix = " min", min = 5, max = 1440),
        ),
        summary = "Adds to your calendar quietly with Calendar access; otherwise opens the calendar to confirm.",
    ),
    UPCOMING_EVENTS(
        "Get Upcoming Events", "Get events in the next {hours}", Category.Clock, Tint.Red, "calendar",
        listOf(ParamSpec("hours", ParamKind.NUMBER, "24", suffix = " hours", min = 1, max = 720)),
        output = "Upcoming Events",
    ),
    LOCATION(
        "Get Current Location", "Get current location", Category.Location, Tint.Blue, "pin",
        output = "Current Location", summary = "Latitude and longitude, e.g. -6.20000, 106.81667.",
    ),
    OPEN_MAPS(
        "Open in Maps", "Show {query} in Maps", Category.Location, Tint.Blue, "pin",
        listOf(ParamSpec("query", ParamKind.INPUT, "@prev", placeholder = "Place or address")),
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
    LOCK(
        "Lock Screen", "Lock screen", Category.Device, Tint.Gray, "lock",
        summary = "Turns the screen off and locks it. Fingerprint and face unlock keep working.",
    ),
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
                listOf("Previous Result", "Shortcut Input", "Battery Level", "Hour", "Charging", "WhatsApp Call")),
            ParamSpec("op", ParamKind.CHOICE, "is below",
                listOf("is", "is not", "is below", "is above", "contains", "does not contain", "begins with", "ends with", "matches", "has any value", "has no value")),
            ParamSpec("value", ParamKind.TEXT, "20", placeholder = "Value"),
        ),
    ),
    OTHERWISE("Otherwise", "Otherwise", Category.Scripting, Tint.Gray, "branch"),
    END_IF("End If", "End If", Category.Scripting, Tint.Gray, "branch"),
    REPEAT(
        "Repeat", "Repeat {count} times", Category.Scripting, Tint.Gray, "repeat",
        listOf(ParamSpec("count", ParamKind.NUMBER, "3", min = 1, max = 1000)),
        summary = "Runs the actions inside again and again. {Repeat Index} counts from 1.",
    ),
    END_REPEAT("End Repeat", "End Repeat", Category.Scripting, Tint.Gray, "repeat"),
    MENU(
        "Choose from Menu", "Choose from menu {prompt}", Category.Scripting, Tint.Teal, "menu",
        listOf(ParamSpec("prompt", ParamKind.TEXT, "Choose", placeholder = "Prompt")),
        output = "Menu Result",
        summary = "Asks you to pick an option, then runs that option's actions.",
    ),
    CASE(
        "Menu Option", "Option {label}", Category.Scripting, Tint.Teal, "menu",
        listOf(ParamSpec("label", ParamKind.TEXT, "Option", placeholder = "Label")),
    ),
    END_MENU("End Menu", "End Menu", Category.Scripting, Tint.Teal, "menu"),
    ASK(
        "Ask for Input", "Ask for {kind} with {prompt}", Category.Scripting, Tint.Blue, "quote",
        listOf(
            ParamSpec("kind", ParamKind.CHOICE, "Text", listOf("Text", "Number")),
            ParamSpec("prompt", ParamKind.TEXT, "How long?", placeholder = "Prompt"),
        ),
        output = "Provided Input",
        summary = "Shows a text box while the shortcut runs.",
    ),
    SET_VARIABLE(
        "Set Variable", "Set variable {name} to {value}", Category.Scripting, Tint.Orange, "braces",
        listOf(
            ParamSpec("name", ParamKind.TEXT, "My Variable", placeholder = "Name"),
            ParamSpec("value", ParamKind.INPUT, "@prev", placeholder = "Value"),
        ),
        summary = "Saves a value you can use later as {Name} in any text.",
    ),
    GET_VARIABLE(
        "Get Variable", "Get variable {name}", Category.Scripting, Tint.Orange, "braces",
        listOf(ParamSpec("name", ParamKind.TEXT, "My Variable", placeholder = "Name")),
        output = "Variable",
    ),
    REPLACE_TEXT(
        "Replace Text", "Replace {find} with {with} in {input} using {mode}", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("find", ParamKind.TEXT, "", placeholder = "Find"), ParamSpec("with", ParamKind.TEXT, "", placeholder = "Replacement"), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"),
            ParamSpec("mode", ParamKind.CHOICE, "text", listOf("text", "regex"))),
        output = "Updated Text",
    ),
    SPLIT_TEXT(
        "Split Text", "Split {input} by {by}", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("by", ParamKind.CHOICE, "new lines", listOf("new lines", "spaces", "commas", "semicolons", "characters"))),
        output = "Split Text", summary = "Turns text into a list, one item per line.",
    ),
    COMBINE_TEXT(
        "Combine Text", "Combine {input} with {with}", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("with", ParamKind.CHOICE, "spaces", listOf("new lines", "spaces", "commas", "nothing"))),
        output = "Combined Text",
    ),
    MATCH_TEXT(
        "Match Text", "Match {pattern} in {input}", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("pattern", ParamKind.TEXT, "\\d+", placeholder = "Regular expression"), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Matches", summary = "Finds everything that matches a regular expression.",
    ),
    CHANGE_CASE(
        "Change Case", "Change {input} to {case}", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("case", ParamKind.CHOICE, "UPPERCASE", listOf("UPPERCASE", "lowercase", "Title Case", "Sentence case"))),
        output = "Updated Text",
    ),
    EXTRACT(
        "Get from Input", "Get {what} from {input}", Category.Text, Tint.Yellow, "text",
        listOf(ParamSpec("what", ParamKind.CHOICE, "Numbers", listOf("Numbers", "URLs", "Phone Numbers", "Email Addresses")), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Found Items", summary = "Pulls numbers, links, phone numbers or emails out of text.",
    ),
    LIST(
        "List", "List", Category.Lists, Tint.Orange, "menu",
        listOf(ParamSpec("body", ParamKind.TEXT, "", placeholder = "One item per line")),
        output = "List",
    ),
    GET_ITEM(
        "Get Item from List", "Get item {which} from {input}", Category.Lists, Tint.Orange, "menu",
        listOf(ParamSpec("which", ParamKind.TEXT, "1", placeholder = "1, last or random"), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Item from List",
    ),
    CHOOSE_LIST(
        "Choose from List", "Choose from {input} {prompt}", Category.Lists, Tint.Orange, "menu",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("prompt", ParamKind.TEXT, "Choose an item", placeholder = "Prompt")),
        output = "Chosen Item",
    ),
    COUNT(
        "Count", "Count {what} in {input}", Category.Lists, Tint.Orange, "menu",
        listOf(ParamSpec("what", ParamKind.CHOICE, "items", listOf("items", "characters", "words")), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Count",
    ),
    REPEAT_EACH(
        "Repeat with Each", "Repeat with each item in {input}", Category.Lists, Tint.Gray, "repeat",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")), summary = "Runs the actions inside once per line. {Repeat Item} is the current item.",
    ),
    END_EACH("End Repeat", "End Repeat", Category.Lists, Tint.Gray, "repeat"),
    ADD_TO_VARIABLE(
        "Add to Variable", "Add {value} to variable {name}", Category.Lists, Tint.Orange, "braces",
        listOf(ParamSpec("value", ParamKind.INPUT, "@prev", placeholder = "Value"), ParamSpec("name", ParamKind.TEXT, "My List", placeholder = "Name")),
        summary = "Adds a line to a variable, building a list.",
    ),
    DICTIONARY(
        "Dictionary", "Dictionary", Category.Lists, Tint.Orange, "braces",
        listOf(ParamSpec("body", ParamKind.TEXT, "name: value", placeholder = "key: value, one per line")),
        output = "Dictionary",
    ),
    GET_VALUE(
        "Get Dictionary Value", "Get value for {key} in {input}", Category.Lists, Tint.Orange, "braces",
        listOf(ParamSpec("key", ParamKind.TEXT, "name", placeholder = "Key (blank = all keys)"), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Dictionary Value", summary = "Reads a key from a dictionary or JSON, e.g. user.name or items.0.title.",
    ),
    SET_VALUE(
        "Set Dictionary Value", "Set {key} to {value} in {input}", Category.Lists, Tint.Orange, "braces",
        listOf(ParamSpec("key", ParamKind.TEXT, "name", placeholder = "Key"), ParamSpec("value", ParamKind.TEXT, "", placeholder = "Value"), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Dictionary",
    ),
    CALCULATE(
        "Calculate", "Calculate {expression}", Category.Math, Tint.Gray, "calc",
        listOf(ParamSpec("expression", ParamKind.TEXT, "{Previous Result} * 2", placeholder = "e.g. {Repeat Index} * 10")),
        output = "Result", summary = "Works out + − × ÷ % ^ and brackets. Variables can go inside.",
    ),
    ROUND(
        "Round Number", "Round {input} to {places} decimals", Category.Math, Tint.Gray, "calc",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("places", ParamKind.NUMBER, "0", min = 0, max = 6)),
        output = "Rounded Number",
    ),
    RANDOM(
        "Random Number", "Random number between {min} and {max}", Category.Math, Tint.Gray, "calc",
        listOf(ParamSpec("min", ParamKind.TEXT, "1"), ParamSpec("max", ParamKind.TEXT, "100")),
        output = "Random Number",
    ),
    DATE(
        "Date", "Date {value}", Category.Math, Tint.Red, "calendar",
        listOf(ParamSpec("value", ParamKind.TEXT, "now", placeholder = "now, tomorrow, 23:00, 2026-12-31")),
        output = "Date",
    ),
    ADJUST_DATE(
        "Adjust Date", "{op} {amount} {unit} to {input}", Category.Math, Tint.Red, "calendar",
        listOf(ParamSpec("op", ParamKind.CHOICE, "Add", listOf("Add", "Subtract")), ParamSpec("amount", ParamKind.TEXT, "1"),
            ParamSpec("unit", ParamKind.CHOICE, "days", listOf("minutes", "hours", "days", "weeks", "months", "years")), ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")),
        output = "Adjusted Date",
    ),
    FORMAT_DATE(
        "Format Date", "Format {input} as {style}", Category.Math, Tint.Red, "calendar",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("style", ParamKind.CHOICE, "Long", listOf("Long", "Short", "Time", "Day name", "yyyy-MM-dd", "ISO 8601"))),
        output = "Formatted Date",
    ),
    TIME_BETWEEN(
        "Get Time Between Dates", "Get time between {input} and {other} in {unit}", Category.Math, Tint.Red, "calendar",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input"), ParamSpec("other", ParamKind.TEXT, "now"),
            ParamSpec("unit", ParamKind.CHOICE, "minutes", listOf("seconds", "minutes", "hours", "days", "weeks", "months", "years"))),
        output = "Time Between",
    ),
    COMMENT(
        "Comment", "Comment", Category.Scripting, Tint.Gray, "text",
        listOf(ParamSpec("body", ParamKind.TEXT, "", placeholder = "Notes for yourself")), summary = "Does nothing; leaves a note in the shortcut.",
    ),
    STOP_OUTPUT(
        "Stop and Output", "Stop and output {input}", Category.Scripting, Tint.Gray, "stop",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Input")), summary = "Ends the shortcut and shows the value (or returns it to Run Shortcut).",
    ),
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
    SPEAK(
        "Speak Text", "Speak {input}", Category.Text, Tint.Purple, "volume",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Text")),
        summary = "Reads text aloud with the phone's voice.",
    ),
    COPY(
        "Copy to Clipboard", "Copy {input} to clipboard", Category.Text, Tint.Blue, "copy",
        listOf(ParamSpec("input", ParamKind.INPUT, "@prev", placeholder = "Text")),
    ),
    NOTIFY(
        "Show Notification", "Show notification {body}", Category.Text, Tint.Red, "bell",
        listOf(ParamSpec("body", ParamKind.TEXT, "Hello", placeholder = "Message")),
    );

    val opensBlock get() = this == IF || this == REPEAT || this == MENU || this == REPEAT_EACH
    val isMidBlock get() = this == OTHERWISE || this == CASE
    val closesBlock get() = this == END_IF || this == END_REPEAT || this == END_MENU || this == END_EACH
    val isMarker get() = isMidBlock || closesBlock
    val closer: ActionType? get() = when (this) { IF -> END_IF; REPEAT -> END_REPEAT; MENU -> END_MENU; REPEAT_EACH -> END_EACH; else -> null }
    val opener: ActionType? get() = when (this) {
        OTHERWISE, END_IF -> IF; END_REPEAT -> REPEAT; CASE, END_MENU -> MENU; END_EACH -> REPEAT_EACH; else -> null
    }
}

val BUILT_IN_VARIABLES = listOf("Shortcut Input", "Shared Subject", "Message Sender", "Message Chat", "Notification Title", "Notification App", "Previous Result", "Current Date", "Current Time", "Clipboard", "Repeat Index", "Repeat Item", "Menu Result")

fun variableNames(actions: List<Action>): List<String> =
    (BUILT_IN_VARIABLES + actions.filter { it.type == ActionType.SET_VARIABLE }.map { it.p("name").trim() })
        .filter { it.isNotEmpty() }.distinct()

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
    val inShareSheet: Boolean = false,
)

enum class TriggerKind(val title: String, val event: Boolean = true) {
    Time("Time of Day", event = false),
    WhatsAppCallLong("WhatsApp Call Runs Long", event = false),
    Charger("Charger"),
    BatteryLevel("Battery Level"),
    Dnd("Do Not Disturb"),
    WiFi("Wi-Fi"),
    WhatsAppMessage("WhatsApp Message"),
    App("App"),
    AppNotification("Notification"),
    Alarm("Alarm"),
    Bluetooth("Bluetooth"),
    Headphones("Headphones"),
    AirplaneMode("Airplane Mode"),
    BatterySaver("Battery Saver"),
    Location("Location"),
}

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
    val warnFirst: Boolean = false,
    val on: Boolean = true,
    val level: Int = 20,
    val text: String = "",
    val app: String = "",
    val timeMode: String = "time",
    val offset: Int = 0,
    val every: Int = 60,
    val date: String = "",
    val monthDay: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val radius: Int = 150,
    val place: String = "",
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
