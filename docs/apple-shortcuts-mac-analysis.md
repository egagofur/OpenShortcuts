# Apple Shortcuts on macOS 27.2: what it has, and what OpenShortcuts should take from it

Looked at on 2026-11-25, two ways:

- **The app itself**, browsed with plain clicks. Access was click-only, so the editor and the action library panel couldn't be opened.
- **The system string tables** that hold every action's English name:
  - `ActionKit.framework/…/Localizable.loctable`
  - `WorkflowKit.framework/…/Localizable.loctable`
  - `ActionKit.framework/…/Actions.loctable`

  These gave **411 named actions**, plus about 20 system-settings actions and 19 newer App Intents actions.

Automation trigger names are compiled into code, not stored in those tables. The trigger list below therefore comes from Apple's guide (see `feature-matrix.md`), not from this Mac.

## What the app looks like now

| Area | What's there | Notes for OpenShortcuts |
|---|---|---|
| **Describe a shortcut** | A panel next to the library: "What do you want your shortcut to do?" with rotating examples ("Summarize my day every morning", "Play my exercise playlist when I arrive at the gym"). You type a sentence and it builds the shortcut. | The biggest new idea. Nothing like it on Android. |
| Gallery | Curated rows: Shortcuts for Accessibility, Shortcuts for macOS, Fun & Games with Siri, **Apple Intelligence** (Get Started with Models, Morning Summary, Haiku, Action Items from Meeting Notes), Get Stuff Done (Start Pomodoro, Turn Text Into Audio, Stop Distractions, Batch Add Reminders), Quick Shortcuts | We have 4 starter shortcuts and no gallery |
| Automation | Now on the Mac too (empty here) | We have 7 triggers |
| Library | All Shortcuts, **Share Sheet**, **Quick Actions** (Finder/Services), folders | Share Sheet = run a shortcut on something shared to it. We don't have this yet |
| File menu | Import, Export, Duplicate, Rename, **Add to Dock** | We have import/export and Home Screen icons |
| View menu | Action Library, Action Info, Shortcut Details, Describe a Shortcut | — |
| Automation options | "Confirm Before Run", "Notify" | Our "Warn me a minute before" and the result notification |

## The action catalog by area

The "Has" column counts Apple actions that OpenShortcuts already has an equivalent for (29 actions today).

| Area | Apple | Has | Apple examples | Worth building on Android |
|---|---|---|---|---|
| Scripting & control flow | 19 | 8 | If, Repeat, Repeat with Each, Choose from Menu, **Choose from List**, Wait, **Wait to Return**, Stop and Output, Run Shortcut, **Comment** | Choose from List, Comment, Stop and Output, Wait to Return |
| Variables, lists, dictionaries | 25 | 2 | Set/Get/**Add to Variable**, **List, Get Item from List, Count, Dictionary**, Get Dictionary Value, Get Type | All of it: pure logic, easy |
| Numbers, dates, math | 20 | 0 | **Calculate, Random Number, Round, Format Number, Date, Adjust Date, Format Date, Time Between Dates**, Convert Measurement | All of it: pure logic, easy |
| Text | 33 + 4 | 3 | Text, Speak Text, Show Notification, **Replace Text, Split Text, Match Text, Change Case, Get Text from Input, Get URLs/Emails/Phone Numbers from Input**, Translate, Detect Language, Generate Hash, Base64 | Text tools and "Get … from Input" are easy and make message triggers much smarter |
| Web & network | 27 | 1 | Open URLs, **Get Contents of URL**, Get Contents of Web Page, Expand URL, RSS, **Get Network Details, Get Current IP**, Run Script over SSH | Get Contents of URL + JSON (needs the internet permission, so make it opt-in). Network details is easy |
| Files & documents | 33 | 0 | Save File, Get File, Create Folder, Make PDF, Make Archive, Get Text from PDF, Print, Quick Look | Save/Get File via the system picker, Print, Make PDF |
| Photos, video & images | 37 | 0 | **Take Photo, Take Screenshot, Get Latest Photos**, Resize/Crop/Convert Image, Make GIF, **Create QR Code, Scan QR or Barcode**, Remove Image Background, Set Wallpaper Photo | Take Photo, Get Latest Photos, QR create/scan, **Set Wallpaper** |
| Music, audio, podcasts | 26 | 1 | Play/Pause, **Play Sound, Record Audio**, Play Music, Skip Forward/Back, Seek, Recognize Music | Skip/Seek (media keys, trivial), Play Sound, Record Audio |
| Location, maps, weather | 19 | 0 | **Get Current Location**, Get Distance, Get Travel Time, Open in Maps, Get Directions, Get Current Weather | Current Location, Open in Maps. Weather needs internet |
| Calls, messages, email | 12 | 2 | **Call**, FaceTime, Send Message, **Send Message via WhatsApp**, **Send Email**, Share, AirDrop | **Call**, **Send SMS**, Send Email (compose), Share |
| Calendar, reminders, notes, contacts | 31 | 0 | **New Event, Get Upcoming Events, Find Contacts**, New Reminder, Start Timer, Create Note | Add Event / Get Upcoming Events (calendar provider), **Start Timer / Set Alarm** (clock app), Find Contacts |
| Device, apps, system | 36 + 20 | 7 | Open App, Lock Screen, Volume, Brightness, Torch, Focus, Vibrate, Battery, **Set Wallpaper**, **Get Current App**, Get Device Details, Hide App, Quit App, Sleep, Shut Down, **Get What's On Screen**; settings: Wi-Fi, Bluetooth, Airplane, Mobile Data, Low Power, Night Shift, Appearance, Orientation Lock, Hotspot | Get Device Details, Wallpaper, **Dark mode** (one-time adb grant), Orientation lock, **Home** (go to Home Screen via the lock-screen accessibility service) |
| Apple Intelligence | 1 | 0 | **Use Model** (On-Device, Cloud, ChatGPT) | Gemini Nano where available, or a model you bring yourself |
| Mac-only scripting | 5 | 0 | Run Shell Script, AppleScript, JavaScript for Automation, Watch Me Do | Not applicable |
| Third-party apps (old Workflow integrations) | 81 | — | Todoist, Trello, Bear, Things, Dropbox, Slack, Telegram… | Mostly skip. Most need each app's API |

**Overall:** OpenShortcuts covers roughly 24 of Apple's ~330 first-party actions. It is ahead in one place Apple isn't: WhatsApp call hang-up, auto-reply, and call-length and message triggers.

## The gap that matters most is in the middle, not the edges

Apple's depth doesn't come from exotic actions. It comes from **data actions**: lists, dictionaries, math, dates, text matching and "Get … from Input". Those let one shortcut combine others ("if the message contains 'urgent', call back"). They're about 80 actions, all possible on Android, and most are a few lines each, because they're pure logic with no permissions.

## Recommended next steps

1. ✅ **Data & text pack** (done; no new permissions)
   - Text: Replace, Split, Match (regex), Change Case, Contains
   - Get Numbers/URLs/Phone Numbers from Input
   - Calculate, Random Number, Round, Format Number
   - Date, Adjust Date, Format Date, Time Between Dates
   - List, Get Item from List, Count, Choose from List
   - Dictionary, Get/Set Dictionary Value
   - Add to Variable, Repeat with Each, Comment, Stop and Output
2. ✅ **Phone basics** (done): Call a number, Send SMS, Compose email, Share, Start Timer / Set Alarm, Add calendar event, Get upcoming events, Get current location, Open in Maps.
3. ✅ **Share sheet runs** (done): "Run with OpenShortcuts" in Android's share sheet, passing the shared text, link or file as Shortcut Input. This is Apple's Share Sheet and Quick Actions.
4. **Describe a shortcut:** type "When Mama messages me after 23:00, reply that I'm asleep" and get the automation and shortcut built. It can use Gemini Nano on phones that have it, or a model API you choose. This needs internet, so make it opt-in.
5. **Gallery:** a browsable set of ready-made shortcuts (Sleep Mode, auto-reply, Pomodoro, morning summary), each a `.openshortcut` file added with one tap.

Step 1 has the best value for the effort, and everything after it builds on it.
