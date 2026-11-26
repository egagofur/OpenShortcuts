# Landing page plan

A one-page site for OpenShortcuts that looks like the app itself: soft gray page, white rounded cards, blue words you can tap. Built with Svelte, hosted free on GitHub Pages.

## Goal

Someone lands from Product Hunt, Reddit or a search for "iOS Shortcuts for Android". In ten seconds they should know:

1. What it is: Shortcuts for Android, free and open source.
2. Why it's different: it reads like sentences, runs on its own, and works while the phone is locked.
3. What to do next: download the APK, or star it on GitHub.

## References (from Inspo)

| Site | What we take |
|---|---|
| apple.com | Centered hero: short headline, one line under it, a filled pill button next to an outlined one, and the product big underneath. Calm light background. |
| signal.org | Two phones overlapping at a slight angle, showing real screens. A "feature stack" below: one idea per section. |
| glideapps.com | Lots of quiet white space, gray supporting text, and a large but not huge headline in sentence case. |

What we don't take: Signal's full-color background (our app is gray and white) and Glide's text-only hero (our screens are the proof).

## Look and feel: the app's own tokens

The colors come straight from `ui/Theme.kt`, so the site and the app match. Dark mode follows the visitor's system setting, like the app.

| Token | Light | Dark | Used for |
|---|---|---|---|
| `--bg` | `#F1F1F5` | `#0A0A0C` | Page |
| `--card` | `#FDFDFE` | `#1C1C1F` | Cards, phone surroundings |
| `--ink` / `--ink2` / `--ink3` | `#0C0C10` / `#66666D` / `#9C9CA3` | `#F3F3F6` / `#9E9EA6` / `#6E6E76` | Text, from strongest to faintest |
| `--fill` | `#E4E4EA` | `#2E2E33` | Chips, inactive controls |
| `--accent` / `--accent-wash` | `#0062E0` / `#E6EFFD` | `#5AA2FF` / `#14284A` | Buttons, blue words in sentences |
| Tints | Blue, Green, Indigo, Orange, Red… | same | Icon tiles for triggers and actions, as in the app |

- **Type:** the system font (SF Pro on Apple devices, Roboto on Android, Inter as fallback). Headlines are semibold with tight letter spacing, in sentence case. Sizes: 56px headline on desktop, 36px on phones; 17px body text.
- **Shapes:** 26px card corners, 22px icon tiles, pill buttons. A soft shadow in light mode and none in dark mode, as in the app.
- **Motion:** the app's ease-out curve, `cubic-bezier(0.23, 1, 0.32, 1)`. Sections fade and rise 12px once as they scroll into view. Buttons shrink slightly (0.97) when pressed. Everything is still when the visitor has asked for reduced motion.

## Page structure

The page is a feature stack: one idea per section, separated by about 120px on desktop and 72px on phones. Content sits in one centered column, 1120px at most.

1. **Top bar.** The app icon and "OpenShortcuts" on the left. On the right: Features, Triggers, Privacy, a GitHub link with its star count, and a small "Download" pill. It turns frosted as you scroll.
2. **Hero (all visible without scrolling).**
   - Headline: "The Shortcuts app Android was missing."
   - Line under it: "Build routines from simple sentences. Run them with one tap, or let them run on their own."
   - Buttons: **Download APK** (filled) and **View on GitHub** (outlined).
   - Small print: "Free · Open source · No root · Android 10+".
   - Visual: two phones overlapping at about 8°. One shows the Sleep Mode editor and the other the automation list, using the existing `docs/screenshots/v2` images.
3. **The story, as a live sentence.** One large white card that reads like an automation in the app: "When a **WhatsApp call** runs over **45 min**, between **23:00** and **05:00**, run **Sleep Mode**." Tapping a blue word cycles to another real example, such as "When I **arrive home**…" or "At **sunset**…". Under it: "It started with one problem: falling asleep on a call and waking up to a hot phone with no battery."
4. **Reads like a sentence.** A screenshot on one side and short copy on the other: tap a blue word to change it, with no flowcharts and no scripting language.
5. **Runs on its own.** A grid of 15 trigger tiles in the app's icon style, in four groups: Time & place, Apps & messages, Connections, Power & focus. Each tile shows a glyph and a name. Next to it: the map screenshot, captioned "Arrive at or leave a place you pick on a map."
6. **Works while your phone is locked.** Three cards:
   - End a WhatsApp call: presses Hang up on WhatsApp's notification.
   - Reply on WhatsApp: from the notification.
   - Send WhatsApp: with Smart Lock, or it waits and sends when you unlock.
7. **60+ actions.** A wrapping row of chips grouped like the catalog (Calls & WhatsApp, Device, Clock & calendar, Scripting, Text & data), plus the catalog screenshot.
8. **Designed like a phone's own app.** A wide landscape screenshot with the rail, and a line about the iPhone Duo inspiration, motion and dark mode.
9. **Private by design.** Three small cards: No account · Runs on the phone · Open source. One line about permissions: "Asked for one at a time, only when a shortcut needs it."
10. **Questions.** Four expandable rows:
    - Does it need root? No.
    - Does it work with the screen off? Yes, through WhatsApp's own notification buttons.
    - Why so many permissions? Each one is asked for only when a shortcut uses it, with an explanation first.
    - Xiaomi, Oppo or Vivo? Turn on Auto launch; the app shows you where.
11. **Closing call to action.** "Make your first shortcut tonight." with Download APK and Star on GitHub.
12. **Footer.** "Not affiliated with Apple or WhatsApp", and links to GitHub, Releases and Issues. The repo has no license file yet; the footer names one once you pick it.

## Signature detail

The live sentence card in section 3. It is the app's core idea (sentences with blue tokens) shown as something you can tap, not a picture of it. Everything else stays quiet so this one moment stands out.

## Tech

- **SvelteKit + Svelte 5** with `adapter-static`, in a new `site/` folder at the repo root, separate from the Android build.
- Plain CSS with the tokens above as custom properties in one `tokens.css`, and no Tailwind, so the values stay identical to the app's.
- Components: `Nav`, `Hero`, `PhonePair`, `SentenceCard`, `Section`, `TriggerGrid`, `LockedCards`, `ActionChips`, `PrivacyCards`, `Faq`, `Footer`.
- Images: copy the `docs/screenshots/v2` PNGs into `site/static`, converted to WebP and sized for the page. No screens with personal contact names.
- SEO: title, description and Open Graph image (reusing `docs/images/hero.png`), plus `SoftwareApplication` JSON-LD for Google.
- Hosting: a GitHub Actions workflow builds `site/` and publishes it to GitHub Pages at `openshortcuts.egagofur.studio`.
- Checks: renders at 375px, 768px and 1440px in light and dark; keyboard focus is visible; Lighthouse is above 95 for performance and accessibility.

## Not decided yet

- The **Download APK** button points at GitHub Releases, which has no release yet. Until there is one, it can point to the build steps in the README.
- A custom domain, if you want one, instead of `github.io`.
