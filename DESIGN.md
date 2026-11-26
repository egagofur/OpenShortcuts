# OpenShortcuts Design System (`DESIGN.md`)

> Agent-ready design system specification for **OpenShortcuts** (Android / Jetpack Compose), structured using the **Loth Pattern** (Two-Layer Tokens, Component Contracts, and Enforceable UI Rules).  
> **Scope:** All existing screens (`HomeScreen`, `EditorScreen`, `AutomationScreen`, `GalleryScreen`, `SetupScreen`) and upcoming **Workspaces / `.osadapter` screens** (`WorkspaceScreen`).

---

## 1. Design Philosophy & Non-Negotiables

OpenShortcuts follows an **iPhone Duo / Apple Shortcuts-inspired tactile minimalism**:
1. **Sentence-first UI:** Actions, triggers, and Workspace filters read like natural sentences inside white/dark rounded cards with tappable blue **`TokenPill`** words—never raw forms, spreadsheets, or Material3 `OutlinedTextField` boxes.
2. **No Material Chrome:** Never use Material `TopAppBar`, `BottomNavigationBar`, `FloatingActionButton`, or default Material3 `ElevatedCard`/`FilledTonalButton`. Navigation lives in `NavHost` (iOS-style horizontal spring push + predictive back) and the side **`Rail`** (`76.dp`).
3. **Physical Motion & Haptics:** Every interactive surface responds with scale (`Modifier.pressable`), spring physics (`Motion.settle()`, `Motion.fling()`), and deliberate Android haptics (`CONFIRM`, `CLOCK_TICK`, `REJECT`), while strictly honoring `Theme.reduced` (`ANIMATOR_DURATION_SCALE == 0f`).
4. **Zero Raw Values in Feature Screens:** Feature and Workspace screens **must never** hardcode `Color(0x...)`, arbitrary corner radii, or custom animation curves. Always consume `Theme.c`, `Theme.t`, `Dimens`, `Motion`, and primitives from `Kit.kt`.

---

## 2. Layer 1 — Primitives (Raw Values)

> **Rule:** Never reference raw hex values or raw dp/sp numbers in feature/workspace screens. Primitives exist solely to bind Layer 2 Semantic Tokens in `Theme.kt`.

### 2.1 Color Primitives

| Group | Primitive Name | Hex / ARGB | Notes |
|---|---|---|---|
| **Neutral Light** | `gray-50` | `#F1F1F5` | Cool off-white canvas |
| | `white-99` | `#FDFDFE` | Card & raised surface |
| | `gray-200` | `#E4E4EA` | Subtle control fill |
| | `ink-950` | `#0C0C10` | Primary light-mode ink |
| | `ink-600` | `#66666D` | Secondary light-mode ink |
| | `ink-400` | `#9C9CA3` | Tertiary light-mode ink |
| **Neutral Dark** | `black-950` | `#0A0A0C` | OLED-friendly dark canvas |
| | `gray-900` | `#1C1C1F` | Dark card surface |
| | `gray-850` | `#26262A` | Dark raised surface (TabBar, GlassCircle) |
| | `gray-800` | `#2E2E33` | Dark control fill |
| | `white-95` | `#F3F3F6` | Primary dark-mode ink |
| | `gray-400` | `#9E9EA6` | Secondary dark-mode ink |
| | `gray-600` | `#6E6E76` | Tertiary dark-mode ink |
| **Brand & Status** | `blue-600` | `#0062E0` | Light accent |
| | `blue-50` | `#E6EFFD` | Light accent wash (`TokenPill` bg) |
| | `blue-400` | `#5AA2FF` | Dark accent |
| | `blue-950` | `#14284A` | Dark accent wash (`TokenPill` bg) |
| | `green-600` / `green-400` | `#1F9E3F` / `#3ACB5C` | Positive / charging / success |
| | `red-600` / `red-400` | `#D9322A` / `#FF5A50` | Danger / delete / error |
| **Alpha & Scrims** | `black-8%` / `white-10%` | `0x14000000` / `0x1AFFFFFF` | Hairline borders |
| | `black-32%` / `black-55%` | `0x52000000` / `0x8C000000` | Modal scrims |
| | `black-15%` / `transparent` | `0x26000000` / `0x00000000` | Light shadow / dark no-shadow |

### 2.2 `Tint` Category Palette (App-Wide Accent Chips & Tiles)
Used by `IconTile`, `ShortcutTile`, `Automation`, and **Adapter/Workspace badges** (`Tint.color()` in `Theme.kt`):

| `Tint` Enum | Hex | Typical Semantic Usage |
|---|---|---|
| `Tint.Blue` | `#2F7CF6` | Time, Bluetooth, Location, General |
| `Tint.Green` | `#28B14A` | Calls, WhatsApp, Approved / Passed |
| `Tint.Indigo` | `#5654D4` | Sleep, Focus, App triggers |
| `Tint.Orange` | `#F08A0A` | Clock, Battery, Warnings / Pending |
| `Tint.Red` | `#EC4338` | Notifications, Calendar, Failed / Urgent |
| `Tint.Gray` | `#8A8A90` | Neutral / System / Setup |
| `Tint.Teal` | `#239FC2` | Network, Wi-Fi, Sync |
| `Tint.Yellow` | `#F2AE00` | Morning, Battery Saver |
| `Tint.Pink` | `#EE3768` | Audio, Headphones, Media |
| `Tint.Purple` | `#9F4FD6` | Scripting, Pipeline / Run Shortcut |

### 2.3 Dimension & Typography Primitives (`Dimens`, `Type`)

| Token | Value | Usage |
|---|---|---|
| `Dimens.rail` | `76.dp` | Right-side status & action rail width |
| `Dimens.gutter` | `20.dp` | Standard horizontal content padding |
| `Dimens.cardRadius` | `26.dp` | Standard `Card` & `SwipeToDelete` corner radius |
| `Dimens.tile` | `28.dp` | Standard `IconTile` size (square with `size * 0.27f` radius) |
| `Dimens.glass` | `52.dp` | `GlassCircle` diameter on the `Rail` |
| `ShortcutTile` radius | `22.dp` | Shortcut grid tile (`height = 108.dp`) |
| `TabBar` radius | `30.dp` | Floating pill container (`padding = 5.dp`) |
| `TokenPill` radius | `9.dp` | Inline tappable parameter pill (`padding = 8.dp × 1.dp`) |
| `PrimaryButton` / `QuietButton` | `minHeight = 50.dp`, `radius = 25.dp` | Pill CTA buttons |
| `DuoDialog` radius | `30.dp` | Modal dialog (`padding = 20.dp`, `maxWidth = 380.dp` / `460.dp` in `isShort`) |

---

## 3. Layer 2 — Semantic Tokens (`Theme.c`, `Theme.t`, `Motion`)

> **Rule:** Theme switching happens exclusively in `ShortcutsTheme` by swapping `Light` and `Dark` `Palette` bindings. Components only read `Theme.c.*` and `Theme.t.*`.

### 3.1 `Palette` Semantic Roles (`Theme.c`)

| Semantic Token | Light Binding | Dark Binding | Role |
|---|---|---|---|
| `Theme.c.bg` | `gray-50` | `black-950` | Root screen background |
| `Theme.c.card` | `white-99` | `gray-900` | Sentence cards, list rows, dialogs |
| `Theme.c.raised` | `white-99` | `gray-850` | Floating `TabBar`, `GlassCircle`, elevated pills |
| `Theme.c.ink` | `ink-950` | `white-95` | Headings, primary labels, active tab text |
| `Theme.c.ink2` | `ink-600` | `gray-400` | Subtitles, descriptions, inactive tab text |
| `Theme.c.ink3` | `ink-400` | `gray-600` | Muted metadata, chevrons, close glyph |
| `Theme.c.fill` | `gray-200` | `gray-800` | `QuietButton`, active `TabBar` pill, `BatteryRing` track, `RemoveDot` |
| `Theme.c.hairline` | `black-8%` | `white-10%` | `1.dp` subtle borders on `GlassCircle` / dividers |
| `Theme.c.accent` | `blue-600` | `blue-400` | `PrimaryButton`, `TokenPill` text, active `RailHome` |
| `Theme.c.accentWash` | `blue-50` | `blue-950` | `TokenPill` background |
| `Theme.c.positive` | `green-600` | `green-400` | Charging ring, success feedback, swipe-approve |
| `Theme.c.danger` | `red-600` | `red-400` | `SwipeToDelete` reveal, low battery (`<= 20%`), destructive CTA |
| `Theme.c.scrim` | `black-32%` | `black-55%` | Sheet/drawer backdrop |
| `Theme.c.shadow` | `black-15%` | `transparent` | Elevation shadow (disabled in dark mode) |

### 3.2 Typography Scale (`Theme.t`)

| Token | Size / LineHeight | Weight | Tracking / Features | Usage |
|---|---|---|---|---|
| `Theme.t.large` | `30.sp / 34.sp` | `Bold` | `-0.02em` | Screen header title (`28.sp / 32.sp` when `isShort`) |
| `Theme.t.title` | `22.sp / 26.sp` | `SemiBold` | `-0.02em` | Dialog / Sheet / Section titles |
| `Theme.t.action` | `17.sp / 24.sp` | `Normal` | `-0.01em` | Sentence text inside action/trigger/workspace cards |
| `Theme.t.token` | `17.sp / 24.sp` | `Medium` | `-0.01em` | Text inside `TokenPill` |
| `Theme.t.body` | `16.sp / 23.sp` | `Normal` | `0` | Empty states, descriptions, long-form copy |
| `Theme.t.label` | `15.sp / 20.sp` | `Medium` | `0` | Card titles, `TabBar` labels, button labels (`17.sp` in `ButtonBox`) |
| `Theme.t.small` | `13.sp / 18.sp` | `Normal` | `0` | Subtitles, metadata, timestamps, badges (`11.sp` + `tnum` inside `BatteryRing`) |
| `Theme.t.clock` | `18.sp / 22.sp` | `SemiBold` | `-0.01em`, `tnum` | `RailClock` (tabular numerals prevent layout jitter) |

### 3.3 Motion & Haptic Tokens (`Motion` in `Theme.kt`, `Nav.kt`, `Kit.kt`)

| Token / Spec | Parameters | Usage |
|---|---|---|
| `Motion.easeOut` | `CubicBezierEasing(0.23f, 1f, 0.32f, 1f)` | Press down (`150ms`), fades, dialog scale/alpha, battery sweep (`650ms`) |
| `Motion.drawer` | `CubicBezierEasing(0.32f, 0.72f, 0f, 1f)` | Sheet / drawer transitions |
| `Motion.settle()` | `spring(dampingRatio = 1f, stiffness = 247f)` | Critical-damped release (`pressable` release, `TabBar` pill, `BatteryRing` level) |
| `Motion.fling()` | `spring(dampingRatio = 0.8f, stiffness = 438f)` | Snappy gesture snap-back |
| `Motion.press()` | `spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)` | Immediate tactile response |
| `NavState` push/pop | `spring(dampingRatio = 1f, stiffness = 300f)` | Screen transitions (`tween(200)` when `Theme.reduced`) |
| `pressable` scale | `0.97f` (default), `0.96f` (tabs), `0.95f` (`TokenPill`), `0.94f` (`GlassCircle`), `0.90f` (`RemoveDot`) | Applied via `graphicsLayer { scaleX; scaleY }`, disabled when `Theme.reduced` |
| Haptics (`Kit.kt`) | `view.tick()` (`CLOCK_TICK`), `view.confirm()` (`CONFIRM`), `LONG_PRESS`, `REJECT` | Threshold crossings, toggles, completion, failed run |

---

## 4. Component Contracts (`*.meta.json` Specification)

Every screen—including `WorkspaceScreen`—must compose exclusively from these primitives and obey their `whenNotToUse` and `limits`.

### 4.1 `Card` (`Kit.kt`)
```json
{
  "component": "Card",
  "replaces": ["androidx.compose.material3.Card", "ElevatedCard", "Surface"],
  "whenToUse": [
    "Grouping a sentence action, automation row, setup banner, or workspace item"
  ],
  "whenNotToUse": [
    "Compact grid shortcut tiles — use ShortcutTile (22.dp radius, tinted fill)",
    "Floating navigation bars — use TabBar or GlassCircle with Theme.c.raised"
  ],
  "limits": {
    "cornerRadius": "Dimens.cardRadius (26.dp)",
    "background": "Theme.c.card",
    "maxNestedCards": 0
  }
}
```

### 4.2 `TokenPill` (`Kit.kt`)
```json
{
  "component": "TokenPill",
  "aliases": ["blue word", "parameter chip", "inline filter"],
  "replaces": ["DropdownMenu", "OutlinedTextField", "FilterChip"],
  "whenToUse": [
    "Editable parameters inside a sentence card (actions, triggers, workspace filters)"
  ],
  "whenNotToUse": [
    "Primary screen-level CTAs — use PrimaryButton",
    "Static non-interactive status tags — use StatusBadge (read-only)"
  ],
  "limits": {
    "maxLines": 1,
    "pressScale": 0.95,
    "cornerRadius": "9.dp"
  }
}
```

### 4.3 `IconTile` (`Kit.kt`)
```json
{
  "component": "IconTile",
  "replaces": ["Raw Icon inside Box", "Material IconButton"],
  "whenToUse": [
    "Leading icon for actions, automations, setup banners, and workspace adapters"
  ],
  "whenNotToUse": [
    "Rail navigation/utility buttons — use GlassCircle"
  ],
  "limits": {
    "defaultSize": "28.dp (inline 20.dp, list row 34.dp)",
    "cornerRadius": "size * 0.27f",
    "glyphTint": "Color.White",
    "glyphScale": 0.66
  }
}
```

### 4.4 `PrimaryButton` & `QuietButton` (`Kit.kt`)
```json
{
  "component": "PrimaryButton / QuietButton",
  "replaces": ["androidx.compose.material3.Button", "TextButton", "FilledTonalButton"],
  "whenToUse": [
    "PrimaryButton: main commit action in a dialog, sheet, or workspace detail view",
    "QuietButton: secondary/cancel/dismiss action alongside PrimaryButton"
  ],
  "whenNotToUse": [
    "Inline sentence parameters — use TokenPill",
    "Rail actions — use GlassCircle"
  ],
  "limits": {
    "maxPrimaryPerView": 1,
    "minHeight": "50.dp",
    "cornerRadius": "25.dp",
    "maxLines": 2
  }
}
```

### 4.5 `TabBar` (`HomeScreen.kt`)
```json
{
  "component": "TabBar",
  "replaces": ["NavigationBar", "TabRow"],
  "whenToUse": [
    "Top-level home switching (Shortcuts | Automation | Workspaces)"
  ],
  "whenNotToUse": [
    "Sub-tabs inside a Workspace detail screen — use a compact segmented pill or TokenPill filter"
  ],
  "limits": {
    "maxTabs": 3,
    "segmentHeightPortrait": "48.dp",
    "segmentHeightCompact": "40.dp",
    "containerRadius": "30.dp",
    "shadowLightOnly": "18.dp"
  }
}
```

### 4.6 `Rail` & `GlassCircle` (`Kit.kt`, `RailStatus.kt`)
```json
{
  "component": "Rail",
  "replaces": ["TopAppBar", "FloatingActionButton"],
  "whenToUse": [
    "Persistent right-edge status (RailHome, RailClock, RailBattery) and max 2–3 primary screen actions"
  ],
  "limits": {
    "width": "Dimens.rail (76.dp)",
    "maxGlassCircles": 3,
    "glassSize": "Dimens.glass (52.dp)",
    "hideRailHomeWhen": "isShort (< 480.dp height)"
  }
}
```

### 4.7 `SwipeToDelete` / `SwipeAction` (`Gestures.kt`)
```json
{
  "component": "SwipeToDelete / SwipeAction",
  "replaces": ["Material SwipeToDismissBox"],
  "whenToUse": [
    "List rows supporting quick swipe-to-delete (Automation/Editor) or swipe-to-act (Workspace MR Approve / Run Shortcut)"
  ],
  "limits": {
    "armThreshold": "0.38f of row width",
    "flickVelocity": "700.dp/s",
    "hapticOnThreshold": "CLOCK_TICK",
    "hapticOnCommit": "CONFIRM",
    "accessibilityRequired": "CustomAccessibilityAction mirroring every swipe action"
  }
}
```

---

## 5. Layout, Safe Areas & Responsive Rules

1. **Root Split:** Every top-level screen is a `Row(Modifier.fillMaxSize().background(Theme.c.bg))`:
   - Left (`Modifier.weight(1f)`): Scrollable content (`LazyColumn` / `LazyVerticalGrid`) with `contentPadding(start = Dimens.gutter, end = 4.dp, bottom = bottomPad)`.
   - Right: `Rail(...)` (`76.dp` + `WindowInsets.topBar.only(WindowInsetsSides.End)`).
2. **Height Modes:**
   - **Standard Portrait (`screenHeightDp >= 480.dp`):** `TabBar` floats at `Alignment.BottomCenter` (`padding(start = 16.dp, bottom = 16.dp)`). Content `bottomPad = 112.dp` so the last card clears the floating `TabBar`.
   - **Short / Cover Screen / Landscape (`isShort`, `screenHeightDp < 480.dp`):** `TabBar` moves to `Alignment.TopEnd` (`padding(top = 10.dp, end = 4.dp)`), `Header` adds `end = 250.dp` to avoid colliding with `TabBar`, `RailHome` hides, and `bottomPad = 24.dp`.
3. **Grid Columns (`TileCells`):** Adaptive `maxOf(2, (availableSize + spacing) / (170.dp + spacing))` with `12.dp` horizontal & vertical spacing.

---

## 6. Workspace & Adapter UI Contract (`WorkspaceScreen`)

When an `.osadapter` is imported, `WorkspaceScreen` **must** feel indistinguishable from a native OpenShortcuts screen:

1. **Visibility Isolation:**
   - `TabBar` shows 2 tabs (`Shortcuts`, `Automation`) by default. The `Workspaces` tab appears **only** when `AdapterStore.adapters.isNotEmpty()`.
2. **Workspace Hub (`Workspaces` tab):**
   - Renders imported adapters as `Card` rows (`IconTile(34.dp)` + Adapter Name + Live Status Subtitle + `Tint` count pill + `ChevronRight`).
   - Rail `+` opens **Import `.osadapter`** (file picker / URL) with a **Domain & Endpoint Preview Sheet** (`DuoDialog`) before activation.
3. **Inside an Adapter Screen (`WorkspaceDetailScreen`):**
   - **Header:** Uses the exact `Header(title, sub)` pattern (`Theme.t.large` + `Theme.t.small`).
   - **Filter Bar:** Uses a single `Card` with sentence-style `TokenPill`s (e.g., `Show [Assigned MRs] in [All Projects]`), never a Material `SearchBar` or `TabRow`.
   - **Data Rows:** Each item is a `Card` (`26.dp` radius) inside `SwipeAction` (optional swipe-right positive action like *Approve*, swipe-left *Run Linked Shortcut*).
   - **Live Run Feedback:** Tapping an inline action or linked shortcut inside a Workspace row triggers the same breathing/spinner → pop + `view.confirm()` (or shake + `REJECT` on error) feedback as `ShortcutTile`.
   - **Detail Sheet:** Tapping a row pushes a detail screen via `NavState` (`NavHost`) with max **1 `PrimaryButton`** (e.g., *Merge* / *Submit Standup*) and optional `QuietButton` (*Open in Browser* / *Run Shortcut*).
4. **Forbidden Patterns in Workspace UI:**
   - No `WebView` for primary adapter screens.
   - No raw `Color(...)` literals outside `Theme.kt`.
   - No `TextField` visible by default at rest—editing opens `DuoDialog` or `Pickers.kt`.
   - No unlabeled icon buttons (`label` / `contentDescription` required on every `pressable` and `GlassCircle`).
