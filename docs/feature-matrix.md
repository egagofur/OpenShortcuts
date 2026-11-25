# Feature matrix: Apple Shortcuts vs OpenShortcuts

Compared with Apple's Shortcuts User Guide for iPhone (iOS 27, read 2026-11-25) and the current OpenShortcuts code.

**Legend**

| Mark | Meaning |
|---|---|
| ✅ | Done |
| 🟡 | Partly done |
| ❌ | Not built yet, possible on Android |
| ⛔ | Not possible on Android without root, or iPhone-only hardware |

The **Android route** column says how a missing feature would be built. "Listener host" means the notification-listener service OpenShortcuts already runs: Android keeps it alive, so it can watch system events without adding a separate always-on service.

## 1. Building shortcuts

| Apple feature | OpenShortcuts | Notes / Android route |
|---|---|---|
| Sentence-style actions with editable tokens | ✅ | Blue pills open a picker, slider or text field |
| Magic variables (any earlier action's output) | 🟡 | "Previous result" as input, plus named variables; not every earlier output yet |
| Special variables: Clipboard, Current Date, Shortcut Input, Ask Each Time | 🟡 | {Shortcut Input}, {Current Date}, {Current Time}, {Clipboard} (while the app is open), {Repeat Index}, {Menu Result}; no Ask Each Time |
| Set Variable / Add to Variable | ✅ | Set, Get and Add to Variable (builds a list); {Name} works in any text |
| If / Otherwise / End If | ✅ | Can test Battery Level, Hour, Charging, Previous Result, WhatsApp Call |
| Repeat / Repeat with Each, Repeat Item / Index | ✅ | Repeat N times and Repeat with Each, with {Repeat Index} and {Repeat Item} |
| Choose from Menu | ✅ | Options are cards; duplicate one to add more |
| Lists, Find and Filter actions | 🟡 | List, Get Item from List, Count, Choose from List (a list is one item per line); no Find/Filter yet |
| Dictionaries, Get Dictionary Value, Parsing JSON | ✅ | Dictionary, Get/Set Dictionary Value with dot paths (user.name, items.0.title) |
| Ask for Input | ✅ | Text or number. Needs the app open; automations stop with a clear message |
| Show Alert | ❌ | |
| Show Notification | ✅ | |
| Show Result / Show Content | ✅ | Dialog in the app, notification when in the background |
| Wait | ✅ | |
| Stop This Shortcut | ✅ | |
| Run Shortcut (nested) | ✅ | Up to 4 levels deep |
| Web APIs: Get Contents of URL | ❌ | The app has the internet permission now (for Describe a shortcut), so this is possible; keep it opt-in |
| Run JavaScript on Webpage | ❌ | Possible inside a WebView |
| URL schemes, x-callback-url | ❌ | Android deep links (`openshortcuts://run?name=…`) |
| Undo / redo | ✅ | |
| Reorder actions | 🟡 | Move Up / Move Down menu; no drag and drop |
| Duplicate / delete action | ✅ | Deleting an If removes its Otherwise and End If too |
| Rename, colour, icon | ✅ | 10 colours, 16 icons |
| Search the action catalogue | ✅ | Also matches Indonesian keywords |
| Use Model (Apple Intelligence / ChatGPT) | 🟡 | *Describe a shortcut* builds shortcuts with Claude using your own key. No Use Model action inside shortcuts yet |

## 2. Ways to run a shortcut

| Apple feature | OpenShortcuts | Notes / Android route |
|---|---|---|
| From the app | ✅ | Tap the tile; long-press to edit |
| Automations | ✅ | See section 3 |
| Add to Home Screen | ✅ | Details → Home Screen; icon in the shortcut's colour and glyph |
| Home Screen widget | ❌ | App widget (Glance) |
| Control Center | ✅ | Quick Settings tile runs one chosen shortcut (Details → Quick Settings); works from the lock screen after unlock |
| Search screen | ✅ | First 4 shortcuts appear on the app icon's long-press menu, and in search on launchers that support it |
| Siri | ❌ | Google Assistant App Actions; limited for third-party apps |
| From another app (share sheet, Shortcut Input) | ✅ | “Run Shortcut” in Android's share sheet and in the menu for selected text; the text becomes {Shortcut Input}, the subject {Shared Subject}. Turn on “Show in Share Sheet” per shortcut |
| Run from a URL | ❌ | Deep link |
| Action button | ⛔ | No such button on most phones |
| Back Tap | ⛔ | Pixel's Quick Tap can only open an app, not run a shortcut |
| Apple Watch | ⛔ | Wear OS would be a separate app |
| Apple Pencil Pro | ⛔ | |

## 3. Automation triggers

| Apple trigger | OpenShortcuts | Notes / Android route |
|---|---|---|
| Time of Day | ✅ | Daily, weekdays, weekends or chosen days; exact alarms; survives reboot |
| Alarm | ❌ | Android only says when the *next* alarm changes, not when it rings. Partial at best |
| Sleep | 🟡 | Covered by the quiet-hours window on the WhatsApp trigger; no real sleep detection |
| Arrive / Leave | ❌ | Geofence with `LocationManager.addProximityAlert`, background location permission |
| CarPlay | ❌ | Android Auto / car mode (`UiModeManager`) |
| Email | ❌ | Listener host, filtered to mail apps |
| Message | 🟡 | WhatsApp messages, filtered by contact or group name; the text becomes the shortcut's input. SMS not yet |
| Transaction (Wallet) | ⛔ | |
| Wi-Fi | 🟡 | Connects / disconnects, any network. Picking a network by name needs location permission (not yet) |
| Bluetooth | ❌ | Connect/disconnect broadcasts can still be received when the app isn't running |
| Apple Watch Workout | ⛔ | |
| NFC | ❌ | NFC tag intent filter with an OpenShortcuts tag URI |
| App (opened / closed) | ❌ | Usage access or an accessibility service |
| Airplane Mode | ❌ | Listener host |
| Do Not Disturb / Focus | ✅ | Turns on / turns off |
| Low Power Mode | ❌ | Battery saver broadcast in the listener host |
| Battery Level | ✅ | Rises above / falls below a percentage; the level becomes the shortcut's input |
| Charger | ✅ | Connected / disconnected |
| Sound Recognition | ⛔ | System feature, not exposed to apps |
| — (not in Apple) WhatsApp call runs long | ✅ | Call length plus quiet hours; OpenShortcuts only |

**Run options**

| Apple option | OpenShortcuts | Notes |
|---|---|---|
| Ask before running (notification you tap to run) | 🟡 | Works the other way round: a one-minute warning with Snooze / Not now / Run now, and the shortcut runs unless you stop it |
| Run without asking | ✅ | Turn off "Warn me a minute before" |
| Turn an automation on or off | ✅ | Switch in the list and in the editor |
| Filter the list by on / off | ❌ | |

## 4. Actions

Apple has hundreds of actions. This table compares by area and names what OpenShortcuts has.

| Area | Apple examples | OpenShortcuts today | Gap / Android route |
|---|---|---|---|
| Calls | Call, FaceTime | ✅ Call, End WhatsApp call, End phone call | ❌ Start a WhatsApp call |
| Focus / sound | Set Focus, ringer, volume | ✅ Do Not Disturb, ringer mode, volume | ❌ Per-app DND rules |
| Display | Brightness, appearance, Night Shift | ✅ Brightness | ❌ Dark mode (needs `WRITE_SECURE_SETTINGS` via a one-time adb grant), auto-rotate |
| Media | Play/Pause, Skip, Play Music | ✅ Play, Pause, Next, Previous | ❌ Pick a playlist or app |
| Device | Flashlight, battery, Wi-Fi, Bluetooth, cellular, Airplane | ✅ Flashlight, vibrate, battery level | ⛔ Turning Wi-Fi, Bluetooth or Airplane on/off directly is blocked since Android 10; can only open the toggle panel |
| Lock / screen | Lock Screen | ✅ Lock Screen | Minimal accessibility service with no event or screen access; fingerprint unlock keeps working |
| Apps | Open App, app intents | ✅ Open app | ❌ Close app (not possible), App Actions |
| Web | Open URLs, Get Contents of URL, Search Web | ✅ Open URL | ❌ HTTP requests (needs internet permission) |
| Text | Text, Speak, Translate, Dictate, Copy | ✅ Text, Speak Text, Copy to Clipboard | ❌ Speak Text (built-in TextToSpeech, easy) |
| Sharing / messages | Share, Copy to Clipboard, Send Message, Mail | ✅ Reply on WhatsApp, Send WhatsApp Message, Send SMS (tap or automatic), Send Email (compose), Share, Copy | Email can't be sent without you tapping Send |
| Calendar / Reminders / Clock | Add event, Get upcoming events, Start Timer, Alarm | ✅ Add Calendar Event, Get Upcoming Events, Start Timer, Set Alarm | ❌ Reminders (no system reminders app on Android) |
| Location / Maps | Get current location, directions, weather | ✅ Get Current Location, Open in Maps | ❌ Weather and travel time (need internet) |
| Documents / files | Save File, Get File, PDF, QR code | ❌ | Storage Access Framework |
| Photos / camera | Take Photo, Get Latest Photos | ❌ | MediaStore |
| Health | Log health sample | ❌ | Health Connect |
| Scripting | Wait, If, Repeat, Menu, Variables, Run Shortcut, Stop | ✅ Wait, If, Repeat, Repeat with Each, Menu, List, Ask, Variables, Dictionaries, Run Shortcut, Stop and Output | Find/Filter still missing |
| Apple Intelligence | Use Model, Get Current Weather | ❌ | Gemini Nano where available |

## 5. Sharing, Gallery and sync

| Apple feature | OpenShortcuts | Notes / Android route |
|---|---|---|
| Share as iCloud link | 🟡 | Share sends a `.openshortcut` file plus a readable recipe; no web link |
| Export / import a file ("Anyone", "People Who Know Me") | ✅ | `.openshortcut` file. Import from the share sheet, "Open with", or + → Import; you review the actions and warnings before adding |
| Import questions (asked when someone adds a shared shortcut) | ❌ | |
| Gallery of ready-made shortcuts | 🟡 | 4 starter shortcuts and 2 starter automations; no browsable gallery |
| Sync across devices | ❌ | Would need an account or Google Drive; the app is offline-only today |
| Automations synced but disabled on other devices | ⛔ | No sync yet |
| Privacy per shortcut ("Allow Running When Locked") | ❌ | |
| Permission explanations | ✅ | Setup screen with a reason for every permission |
| Light / dark mode, text size | ✅ | |

## Score

| Section | ✅ Done | 🟡 Partly done | ❌ Possible on Android | ⛔ Not possible |
|---|---|---|---|---|
| 1. Building | 16 | 6 | 3 | 0 |
| 2. Ways to run | 6 | 0 | 3 | 4 |
| 3. Triggers (Apple's 20) | 4 | 3 | 10 | 3 |
| 4. Action areas | 5 of 17 fully | 7 | 5 | 0 |
| 5. Sharing and sync | 3 | 2 | 3 | 1 |

OpenShortcuts covers the core editor and the night-time use case. The biggest gaps are ways to run a shortcut, event triggers, and variables and loops.

## Suggested next steps

Ordered by value for the effort:

1. ~~**Ways to run:** Home Screen icon, Quick Settings tile, long-press app shortcuts.~~ Done. Widget is still open.
2. ~~**Triggers through the listener host:** Charger, Battery Level, Do Not Disturb, Wi-Fi, WhatsApp message.~~ Done. Still open: Bluetooth, Wi-Fi by network name, SMS.
3. ~~**Scripting:** variables, Repeat, Choose from Menu, Ask for Input.~~ Done. Still open: lists, Repeat with Each, dictionaries.
4. ~~**Export / import file.**~~ Done.
5. ~~**Lock Screen and Speak Text actions.**~~ Done.

## Data & text pack (added after the macOS analysis)

Replace, Split, Combine, Match and Change Case of text; Get Numbers / URLs / Phone Numbers / Emails from Input; List, Get Item, Count, Choose from List, Repeat with Each, Add to Variable; Dictionary, Get / Set Dictionary Value; Calculate, Round, Random Number; Date, Adjust Date, Format Date, Time Between Dates; Comment, Stop and Output. If can now test contains, does not contain, begins with, ends with, matches (regex), has any value and has no value. See `apple-shortcuts-mac-analysis.md` for what's next.
