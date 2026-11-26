# Workspace Dashboard Builder Design Spec

**Date:** 2026-11-26  
**Scope:** OpenShortcuts Android App (`AdapterModel`, `AdapterEngine`, `AdapterStore`, `WorkspaceScreen`, `WorkspaceBuilder`, and local `.osadapter` v2 templates)  
**Design System:** [DESIGN.md](../../../DESIGN.md) + Emil Kowalski motion principles (`emil-design-eng`: snappy `easeOut`, interruptible springs, `30ms` capped stagger, `Theme.reduced` parity).

---

## 1. Goals & Isolation Guarantees
1. Transform `WorkspaceScreen` from a single-list view into a **multi-tab, widget-based Dashboard** with **Top Scrollable Pill Tabs + `HorizontalPager`**.
2. Provide a **rich 8-widget default catalog** styled strictly with `DESIGN.md` primitives (`Card`, `ShortcutTile`, `TokenPill`, `BatteryRing`-style arc, `SwipeAction`).
3. Support **In-App Dashboard Builder**: add/rename/reorder/delete tabs and widgets, **Save** locally to `adapters.json`, and **Export** clean `.osadapter` JSON via Android Share Sheet (`FileProvider`) **with zero tokens or secrets included**.
4. Preserve **100% isolation**: regular users without imported adapters never see `Workspaces`; company adapters (`local-adapters/*.osadapter`) remain gitignored.

---

## 2. `.osadapter` v2 Schema (Backwards-Compatible with v1)

### 2.1 `AdapterTab`
- `id: String`
- `label: String`
- `endpoint: String` (optional in v2 if widgets define their own endpoints; required in v1)
- `widgets: List<DashboardWidget>` (if empty/omitted in v1, auto-migrated in memory to a single `list` widget using the tab's v1 fields).

### 2.2 `DashboardWidget`
Common fields:
- `id: String`
- `type: WidgetType` (`stat_grid`, `progress_ring`, `target_bar`, `live_status`, `action_grid`, `sentence_bar`, `list`, `grouped_list`, `note_card`, `profile_card`, `bar_chart`, `heatmap_chart`)
- `title: String`
- `tint: Tint`
- `glyph: String`
- `endpoint: String?` (optional; shared cache per `(adapterId, endpoint)` per refresh cycle)

Type-specific `config` (stored cleanly in typed properties / JSON `config` object):
1. **`stat_grid`**: `metrics: List<StatMetric>` (`label`, `valuePath`, `suffix`, `targetPath?`, `tint`). Renders 2-column `Card` grid with `Theme.t.large` (`tnum`).
2. **`progress_ring`**: `currentPath`, `targetPath`, `unit` (e.g. `"h"`), `subtitlePath?`. Renders `Card` with animated arc (`650ms Motion.easeOut` sweep, `Theme.c.accent` or `Theme.c.positive` when ≥ 100%).
3. **`live_status`**: `activePath` (boolean), `activeTitlePath`, `inactiveText`, `activeActionId?`, `inactiveActionId?`. Renders pulsing status dot (respecting `Theme.reduced`) + one-tap action pill.
4. **`action_grid`**: `actions: List<DashboardActionRef>` (each can invoke an `AdapterAction` by `actionId` OR a native OpenShortcuts `Shortcut` by `shortcutName`/`shortcutId`). Renders 2-column `ShortcutTile`-style `108.dp` (`22.dp` radius) tiles with breathing/pop/shake feedback.
5. **`sentence_bar`**: `prefix`, `paramKey`, `choices: List<String>`, `targetEndpoint?`. Renders `Card` with tappable `TokenPill` to filter or set quick values.
6. **`list`**: `itemsPath`, `titlePath`, `subtitlePath`, `badgePath`, `metaPath`, `rowActions: List<AdapterRowAction>`, `primaryAction: AdapterAction?`. Renders swipeable `Card` rows (`0.38f` threshold, `CLOCK_TICK` + `CONFIRM`).
7. **`grouped_list`**: Same as `list` plus `groupPath` (renders section headers using `Theme.t.label` and `Theme.c.ink2`).
   Both list types also accept:
   - `valuePath` + `unit`: a number shown at the right of each row (e.g. hours). Grouped lists add up each group's values in its header.
   - `groupBy: "week"`: group rows by the week of `metaPath` ("This week", "Last week", "Week of 9 Nov") instead of `groupPath`.
   - `badgeTints`: badge value to tint, e.g. `{"APPROVED": "Green", "NEED APPROVAL": "Orange"}`. Badges in capitals read as sentence case ("Need approval").
   - `glyph: "none"`: hide the row icon to give long titles more room.
8. **`note_card`**: `body: String`. Renders quiet `Card` with `IconTile` + `Theme.t.body`.
9. **`target_bar`**: `metrics: List<StatMetric>`, each with a `targetPath`. The first metric is the headline: a large number, "of {target}", a progress bar and "{n} to go" (or "Goal reached"). Each further metric is one slim row with its own bar, so related totals (week and period) share one card.
10. **`bar_chart`**: `itemsPath`, `metaPath` (the date field, default `created_at`), and optionally:
    - `valuePath`: sum this number per bar (hours, amounts). Blank counts items.
    - `bucket`: `"day"` (default) or `"week"` (weeks start on Monday).
    - `range`: `"rolling"` (default, the last `count` days ending today) or `"thisWeek"` (Monday to Sunday; later days stay empty).
    - `count`: number of bars (default 7 days or 5 weeks).
    - `target`: goal per bar, drawn as a dashed line with a "Goal" legend.
    - `unit`: suffix for summed values, e.g. `"h"`.

    The headline is the total for day bars and the average of finished weeks for week bars. The bar still in progress is drawn lighter. Tapping a bar or its label shows that bar's value.

    Example, hours per day this week against an 8-hour goal:
    ```json
    { "type": "bar_chart", "title": "Hours per day", "endpoint": "/entries",
      "itemsPath": "data[*].entries[*]", "metaPath": "start", "valuePath": "hours",
      "range": "thisWeek", "unit": "h", "target": 8 }
    ```
11. **`breakdown`**: `itemsPath`, `groupPath` (e.g. `project.name`), `valuePath` + `unit` (blank counts items), `count` (groups shown, default 5; the rest fold into "Other"). Renders ranked rows, largest first, each with its value, share of the total and a bar scaled to the largest group.

A list shows its search box when `searchable` is set or it has more than 8 rows.

---

## 3. UI & Motion Architecture (`WorkspaceScreen.kt`)

### 3.1 Top Scrollable Pill Tabs + `HorizontalPager`
- Positioned directly below `Header(adapter.name, host)` inside the main `Column` (left of `Rail`).
- `LazyRow` of compact pill tabs (`36.dp` height, `18.dp` radius) with animated background/text color and `pressable(0.96f)`.
- Synced bidirectionally with `HorizontalPager(state = pagerState)`: tapping a tab calls `pagerState.animateScrollToPage(i, animationSpec = Motion.settle())`; swiping horizontally updates the active pill smoothly.

### 3.2 Widget Rendering & Emil-Style Motion
- Each tab page is a `LazyColumn` with `PaddingValues(start = Dimens.gutter, end = 4.dp, bottom = bottomPad)` and `12.dp` vertical spacing.
- **Endpoint Cache:** `AdapterEngine.fetchEndpoints(adapter, endpoints)` deduplicates `GET` requests across widgets in the active tab and runs them concurrently on `Dispatchers.IO`.
- **Staggered Entrance:** First 6 widgets animate in with `30ms` stagger (`alpha` + `10.dp` Y slide via `Motion.easeOut`), bypassed when `Theme.reduced` is `true`.

### 3.3 List Item Detail Page (`WorkspaceDetailScreen`)
- Tapping any item in a `list` or `grouped_list` widget pushes `Route.WorkspaceItem(adapterId, widgetId, itemJson)` via `NavHost` (spring push + predictive back + `Rail`).
- Supports optional `detail: DetailSpec` on `list` / `grouped_list` widgets:
  - `endpoint: String?` (optional live fetch when opened, e.g. `/time-entities/{{id}}` or `/projects/{{project_id}}/merge_requests/{{iid}}`)
  - `fields: List<DetailField>` (`label`, `path`) rendered in a key-value `Card` (`26.dp`)
  - `bodyPath: String?` for full-length description/notes
  - `urlPath: String?` for *Open in Browser* `QuietButton`
  - `actions: List<AdapterAction>` (max 1 `PrimaryButton`, remaining as `QuietButton`)
- **Smart Fallback:** If `detail` is omitted, `WorkspaceDetailScreen` renders `title`, `subtitle`, `badge`, `meta`, and top primitive fields from `item.raw` plus `rowActions`.

---

## 4. In-App Dashboard Builder (Edit, Save, Export)

1. **Enter Edit Mode:** Tap **Edit** (`GlassCircle("layers", "Edit dashboard")` or inside Settings `DuoDialog`).
2. **Edit Controls:**
   - **Tabs Bar in Edit Mode:** Tap `+ Tab` to add a tab, tap a tab's `×` or rename chip to rename/remove it.
   - **Per-Widget Controls:** Each widget shows compact `↑` / `↓` reorder buttons and `RemoveDot` (`×`), plus `+ Add Widget` card at the bottom of the tab.
   - **Add Widget Flow (`DuoDialog`):**
     1. Pick from the **8 Widget Templates** (with short description + icon).
     2. Configure title, endpoint, JSON path(s) / linked actions, `Tint`, and `Glyph` using `TokenPill`s and `DuoDialog` inputs.
3. **Save:** Immediately persists updated `WorkspaceAdapter` to `AdapterStore` (`adapters.json`).
4. **Export `.osadapter`:**
   - `AdapterStore.exportIntent(ctx, adapter)` writes `adapter.toJson().toString(2)` (which contains **zero** tokens or `adapter_secrets`) to `cacheDir/shared/<safe-id>.osadapter` and launches Android `ACTION_SEND` via `FileProvider`.

---

## 5. Verification Plan
- `./gradlew :app:assembleDebug` compiles with 0 errors.
- Unit test for `WorkspaceAdapter` v1 → v2 backwards-compatible parsing and v2 round-trip JSON serialization (`toJson()` → `fromJson()`) ensuring no secrets are serialized.
- `adb install -r` + push your own `.osadapter` files to `/sdcard/Download/` for on-device testing.
