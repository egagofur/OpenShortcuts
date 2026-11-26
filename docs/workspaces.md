# Workspaces

A workspace is a dashboard for an API you use. It lives in one `.osadapter` file (JSON) that describes the API's address, how to sign in, the tabs and widgets to show, and the actions you can run. Your access token is never in the file: you paste it once on the phone, where it stays.

## Start a workspace

In the **Workspaces** tab, tap **+**:

| Option | What you get |
|---|---|
| Build with AI | Opens ChatGPT or Gemini, or copies a prompt for any AI chat or coding agent. The AI interviews you, then writes the workspace; you paste it back with **Paste a workspace**. |
| Start blank | A name, an API address and a sign-in method. You add tabs and widgets yourself. |
| GitHub | Your open pull requests, reviews waiting for you and assigned issues. Needs a personal access token. |
| GitLab | Merge requests, reviews, issues, projects and your activity. Works with gitlab.com or a self-hosted GitLab. |
| Demo | Sample hours, tasks and activity made on the phone. Works offline and sends nothing. |
| Paste a workspace | Reads workspace JSON from the clipboard, such as an AI's reply. A pasted workspace never replaces one you already have. |
| Import a file | An `.osadapter` someone shared with you, such as a template for your company's tools. |

Tap **Edit** to add or remove widgets and tabs. Hold a row and drag it to change the order.

## File format

```json
{
  "format": "openshortcuts-adapter",
  "version": 2,
  "id": "tracker",
  "name": "Tracker",
  "subtitle": "Hours and tasks",
  "baseUrl": "https://api.example.com/v1",
  "authKind": "bearer",
  "tabs": [
    { "id": "overview", "label": "Overview", "widgets": [ ... ] }
  ],
  "globalActions": [ ... ]
}
```

- `authKind`: `bearer` (sends `Authorization: Bearer <token>`), `private_token` (sends `PRIVATE-TOKEN: <token>`) or `none`. `authHeader` and `authPrefix` change the header if your API differs.
- `allowedDomains`: other hosts the workspace may call. The token is only sent to `baseUrl`'s host and these.
- Paths such as `data.items[*].name` pick values out of JSON responses. `[*]` walks every item of a list.
- `{{field}}` in an endpoint or body is filled from the row you tapped or from an action's fields. `{{today}}` and `{{month}}` are built in.

## Widgets

Every widget has `id`, `type`, `title`, `tint` and usually an `endpoint`.

| Type | What it shows | Main fields |
|---|---|---|
| `stat_grid` | Two-column numbers, optionally against a target | `metrics[]`: `label`, `valuePath`, `targetPath`, `suffix` |
| `target_bar` | One large number with a bar toward its goal, plus slim rows for related totals | `metrics[]` with `targetPath` |
| `progress_ring` | A ring toward a goal | `currentPath`, `targetPath`, `unit` |
| `bar_chart` | Bars per day or week; counts items or sums a field | `itemsPath`, `metaPath` (date), `valuePath`, `bucket` (`day`/`week`), `range` (`rolling`/`thisWeek`), `count`, `target`, `unit` |
| `breakdown` | Totals per group, largest first, with an Other row | `itemsPath`, `groupPath`, `valuePath`, `unit`, `count` |
| `heatmap_chart` | A year of activity, one square per day | `itemsPath`, `metaPath` |
| `live_status` | Whether something is running, with a start or stop button. Running, it counts up from `startedAtPath` (or from when it started, if the API has no start time); `holdToStop: true` makes the stop button press-and-hold | `activePath`, `activeTitlePath`, `subtitlePath`, `startedAtPath`, `holdToStop`, `activeActionId`, `inactiveActionId` |
| `share_card` | Ship Card: a photo card of your recent commits to share, with a route line of your day and the AIs credited in `Co-Authored-By` trailers. Reads a profile at `endpoint`, then commits at `itemsEndpoint` with the profile's fields filled in (`{{login}}`) | `endpoint`, `titlePath` (your handle), `itemsEndpoint`, `itemsPath`, `metaPath` (commit time), `messagePath`, `groupPath` (repo), `valuePath` (commits per item, e.g. a push) |
| `action_grid` | Colored tiles that run actions or your shortcuts | `actionRefs[]` |
| `profile_card` | The signed-in user | `titlePath`, `subtitlePath`, `metaPath`, `badgePath` |
| `note_card` | A short note | `body` |
| `list` / `grouped_list` | Rows you can search, swipe and open | `itemsPath`, `titlePath`, `subtitlePath`, `badgePath`, `metaPath`, `valuePath`, `groupPath` or `groupBy: "week"`, `badgeTints`, `rowActions`, `detail` |

A row's `detail` can show the item's Markdown body, extra fields, comments, commits and file changes from their own endpoints.

## Actions

```json
{
  "id": "start_timer",
  "title": "Start Timer",
  "method": "POST",
  "endpoint": "/timer/start",
  "bodyTemplate": "{\"description\": \"{{description}}\"}",
  "params": [{ "key": "description", "label": "What are you working on?" }]
}
```

A param with `optionsEndpoint` shows a list to pick from (a project, a label). `confirmMessage` asks before running.

## From shortcuts and automations

Add **Run Workspace Action** to a shortcut, pick the action and fill in its fields once. Text fields can use shortcut variables. If a `live_status` widget starts or stops with that action, its state is checked first: starting something already running, or stopping nothing, is skipped. The result shows in the automation's notification.

## Privacy

- The token is stored on the phone, excluded from Android backups, and never written to exported files.
- Cached responses stay in storage that isn't backed up.
- Exported `.osadapter` files contain the layout only, so you can share a template with your team without sharing access.
