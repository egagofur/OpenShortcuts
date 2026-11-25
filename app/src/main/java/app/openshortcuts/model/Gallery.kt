package app.openshortcuts.model

object Gallery {
    data class Item(
        val section: String,
        val title: String,
        val description: String,
        val tint: Tint,
        val glyph: String,
        val build: () -> Pair<Shortcut, Automation?>,
    )

    private fun a(type: ActionType, vararg params: Pair<String, String>) =
        Action(type = type, params = type.params.associate { it.key to it.default } + params)

    private fun shortcut(name: String, tint: Tint, glyph: String, vararg actions: Action, share: Boolean = false) =
        Shortcut(name = name, tint = tint, glyph = glyph, actions = actions.toList(), inShareSheet = share)

    private fun only(s: Shortcut) = s to null

    private const val ASLEEP = "Lagi tidur, nanti pagi aku balas ya 🙏"

    val sections = listOf("Sleep & Calls", "Messages", "Focus & Battery", "Everyday Tools")

    val items: List<Item> = listOf(
        Item("Sleep & Calls", "Sleep Mode", "Pause what's playing, end a WhatsApp call, turn on Do Not Disturb, dim the screen, say good night and lock.",
            Tint.Indigo, "moon") {
            only(shortcut("Sleep Mode", Tint.Indigo, "moon",
                a(ActionType.MEDIA, "op" to "Pause"),
                a(ActionType.END_WHATSAPP_CALL),
                a(ActionType.DND, "state" to "On"),
                a(ActionType.BRIGHTNESS, "level" to "10"),
                a(ActionType.SPEAK, "input" to "Selamat tidur"),
                a(ActionType.LOCK),
            ))
        },
        Item("Sleep & Calls", "Hang Up Late Calls", "When a WhatsApp call goes past 45 minutes between 23:00 and 05:00, end it, turn on Do Not Disturb and lock the phone.",
            Tint.Green, "phoneOff") {
            val s = shortcut("Hang Up Late Calls", Tint.Green, "phoneOff",
                a(ActionType.END_WHATSAPP_CALL),
                a(ActionType.DND, "state" to "On"),
                a(ActionType.LOCK),
            )
            s to Automation(kind = TriggerKind.WhatsAppCallLong, callMinutes = 45, windowStart = 23 * 60, windowEnd = 5 * 60,
                shortcutId = s.id, enabled = false)
        },
        Item("Sleep & Calls", "Asleep Auto-Reply", "Between 22:00 and 06:00, answer WhatsApp messages with “$ASLEEP”.",
            Tint.Purple, "quote") {
            val reply = a(ActionType.REPLY_WHATSAPP, "chat" to "{Message Chat}", "body" to ASLEEP)
            val s = shortcut("Asleep Auto-Reply", Tint.Purple, "quote",
                a(ActionType.IF, "subject" to "Hour", "op" to "is above", "value" to "21"),
                reply,
                a(ActionType.OTHERWISE),
                a(ActionType.IF, "subject" to "Hour", "op" to "is below", "value" to "6"),
                reply.copy(id = java.util.UUID.randomUUID().toString()),
                a(ActionType.END_IF),
                a(ActionType.END_IF),
            )
            s to Automation(kind = TriggerKind.WhatsAppMessage, text = "", shortcutId = s.id, enabled = false, warnFirst = false)
        },
        Item("Sleep & Calls", "Good Morning", "Turn Do Not Disturb off, ringer back on, brighten the screen and read today's events.",
            Tint.Yellow, "sun") {
            only(shortcut("Good Morning", Tint.Yellow, "sun",
                a(ActionType.DND, "state" to "Off"),
                a(ActionType.RINGER, "mode" to "Normal"),
                a(ActionType.BRIGHTNESS, "level" to "60"),
                a(ActionType.UPCOMING_EVENTS, "hours" to "12"),
                a(ActionType.IF, "subject" to "Previous Result", "op" to "has no value"),
                a(ActionType.SPEAK, "input" to "Selamat pagi. Hari ini kosong."),
                a(ActionType.OTHERWISE),
                a(ActionType.SPEAK, "input" to "Selamat pagi. Hari ini: {Previous Result}"),
                a(ActionType.END_IF),
            ))
        },
        Item("Messages", "Urgent Alert", "When a WhatsApp message says “urgent” or “penting”, vibrate and show who sent it, even at night.",
            Tint.Red, "bell") {
            val s = shortcut("Urgent Alert", Tint.Red, "bell",
                a(ActionType.MATCH_TEXT, "pattern" to "(?i)urgent|penting|darurat", "input" to "@prev"),
                a(ActionType.IF, "subject" to "Previous Result", "op" to "has any value"),
                a(ActionType.VIBRATE),
                a(ActionType.NOTIFY, "body" to "{Message Sender}: {Shortcut Input}"),
                a(ActionType.END_IF),
            )
            s to Automation(kind = TriggerKind.WhatsAppMessage, shortcutId = s.id, enabled = false, warnFirst = false)
        },
        Item("Messages", "Running Late", "Ask how many minutes, then share “Maaf, aku telat … menit” to any chat.",
            Tint.Orange, "timer") {
            only(shortcut("Running Late", Tint.Orange, "timer",
                a(ActionType.ASK, "kind" to "Number", "prompt" to "Telat berapa menit?"),
                a(ActionType.TEXT, "body" to "Maaf, aku telat {Previous Result} menit 🙏"),
                a(ActionType.SHARE, "input" to "@prev"),
            ))
        },
        Item("Messages", "Share My Location", "Get where you are and share a Google Maps link.",
            Tint.Blue, "pin") {
            only(shortcut("Share My Location", Tint.Blue, "pin",
                a(ActionType.LOCATION),
                a(ActionType.REPLACE_TEXT, "find" to " ", "with" to "", "input" to "@prev"),
                a(ActionType.TEXT, "body" to "Aku di sini: https://maps.google.com/?q={Previous Result}"),
                a(ActionType.SHARE, "input" to "@prev"),
            ))
        },
        Item("Focus & Battery", "Pomodoro", "Do Not Disturb on and a 25-minute focus timer.",
            Tint.Red, "timer") {
            only(shortcut("Pomodoro", Tint.Red, "timer",
                a(ActionType.DND, "state" to "On"),
                a(ActionType.TIMER, "minutes" to "25", "label" to "Focus"),
                a(ActionType.NOTIFY, "body" to "Focus for 25 minutes. Do Not Disturb is on."),
            ))
        },
        Item("Focus & Battery", "Low Battery Saver", "When battery falls below 20%, dim the screen, pause media and remind you to charge.",
            Tint.Orange, "battery") {
            val s = shortcut("Low Battery Saver", Tint.Orange, "battery",
                a(ActionType.BRIGHTNESS, "level" to "20"),
                a(ActionType.MEDIA, "op" to "Pause"),
                a(ActionType.NOTIFY, "body" to "Battery at {Shortcut Input}%. Time to charge."),
            )
            s to Automation(kind = TriggerKind.BatteryLevel, on = false, level = 20, shortcutId = s.id, enabled = false, warnFirst = false)
        },
        Item("Focus & Battery", "Unplugged Reminder", "When the charger is unplugged at night, check you meant to.",
            Tint.Green, "bolt") {
            val s = shortcut("Unplugged Reminder", Tint.Green, "bolt",
                a(ActionType.IF, "subject" to "Hour", "op" to "is above", "value" to "21"),
                a(ActionType.BATTERY),
                a(ActionType.VIBRATE),
                a(ActionType.NOTIFY, "body" to "Charger unplugged at {Previous Result}% battery. Meant to?"),
                a(ActionType.END_IF),
            )
            s to Automation(kind = TriggerKind.Charger, on = false, shortcutId = s.id, enabled = false, warnFirst = false)
        },
        Item("Everyday Tools", "Copy Links", "From the share sheet: copy every link in the text you shared.",
            Tint.Blue, "link") {
            only(shortcut("Copy Links", Tint.Blue, "link",
                a(ActionType.EXTRACT, "what" to "URLs", "input" to "@prev"),
                a(ActionType.COPY, "input" to "@prev"),
                a(ActionType.COUNT, "what" to "items", "input" to "@prev"),
                a(ActionType.SHOW, "input" to "Copied {Previous Result} link(s)"),
                share = true,
            ))
        },
        Item("Everyday Tools", "Days Until", "Ask for a date and show how many days are left.",
            Tint.Teal, "calendar") {
            only(shortcut("Days Until", Tint.Teal, "calendar",
                a(ActionType.ASK, "kind" to "Text", "prompt" to "Which date? (e.g. 2026-12-31)"),
                a(ActionType.SET_VARIABLE, "name" to "Target", "value" to "@prev"),
                a(ActionType.DATE, "value" to "today"),
                a(ActionType.TIME_BETWEEN, "input" to "@prev", "other" to "{Target}", "unit" to "days"),
                a(ActionType.SHOW, "input" to "{Previous Result} days until {Target}"),
            ))
        },
    )
}
