# Launch kit

Copy for the GitHub repository and a Product Hunt launch. Keep the wording consistent everywhere: the same name, the same one-line description.

## GitHub repository

**About (description, max 350 characters)**

> The Shortcuts app Android was missing. Free, open-source automation inspired by iOS Shortcuts: build routines from simple sentences and run them by time, sunset, location, Bluetooth, app or WhatsApp. Ends WhatsApp calls when you fall asleep. No root, no account.

**Website:** the Product Hunt page or a GitHub Pages site, once it exists.

**Topics (GitHub allows 20):**

`android` `automation` `shortcuts` `ios-shortcuts` `tasker-alternative` `macrodroid-alternative` `jetpack-compose` `kotlin` `material-you` `productivity` `whatsapp` `whatsapp-automation` `routines` `geofencing` `open-source` `no-root` `android-app` `do-not-disturb` `battery-saver` `privacy`

Set them with the GitHub CLI:

```bash
gh repo edit egagofur/OpenShortcuts --description "The Shortcuts app Android was missing. Free, open-source automation inspired by iOS Shortcuts: build routines from simple sentences and run them by time, sunset, location, Bluetooth, app or WhatsApp. Ends WhatsApp calls when you fall asleep. No root, no account." --add-topic android,automation,shortcuts,ios-shortcuts,tasker-alternative,macrodroid-alternative,jetpack-compose,kotlin,productivity,whatsapp,whatsapp-automation,routines,geofencing,open-source,no-root,android-app,do-not-disturb,battery-saver,privacy
```

Upload `docs/images/hero.png` as the **social preview** (Settings → General → Social preview) so links on X, LinkedIn and WhatsApp show the mockup.

## Product Hunt

**Name:** OpenShortcuts

**Tagline (60 characters max):** The Shortcuts app Android was missing

**Topics:** Android, Productivity, Open Source, Developer Tools

**Description (260 characters max)**

> Build Android automations from simple sentences, like on iPhone. Run them with one tap or let them start on their own: at sunset, when you get home, when your earbuds connect. Ends WhatsApp calls if you fall asleep. Free, open source, no root, no account.

**Gallery, in order** (1270 × 760 works best; crop from `docs/images`):

1. `hero.png`: the promise in one picture
2. `shortcuts.png`: building a shortcut
3. `automations.png`: 15 triggers, map and sunset
4. `design.png`: landscape cover screen, welcome screen and tour
5. A 30-second screen recording: make “Sleep Mode”, set it for 23:00, fall asleep on a call

**Maker's first comment**

> Hi Product Hunt 👋 I kept falling asleep on WhatsApp calls and waking up to a hot phone with no battery. iPhone users fix that with Shortcuts; on Android the options were powerful but felt like programming.
>
> So I built OpenShortcuts: every action reads like a sentence (“Turn **On** Do Not Disturb”), and automations start on their own, from time, sunset, a place, Bluetooth, an app or a WhatsApp message. It ends the call through WhatsApp's own Hang up button, so it works with the phone locked, no root.
>
> It's free and open source, and nothing leaves your phone. I'd love to know which automation you'd build first, and what's missing.

## Launch-day checklist

- [ ] A signed release APK on GitHub Releases (the README links to it)
- [ ] README images rendered from the current build (`docs/mockups/render.sh`)
- [ ] Social preview image set
- [ ] Launch at 00:01 Pacific time, Tuesday to Thursday
- [ ] Reply to every comment in the first hours
- [ ] Share in r/androidapps, r/tasker, r/opensource, Hacker News (Show HN), and Indonesian Android groups, with the hero image
