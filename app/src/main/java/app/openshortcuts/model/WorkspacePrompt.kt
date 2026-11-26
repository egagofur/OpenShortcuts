package app.openshortcuts.model

/**
 * Prompts that turn a general AI (a chat like ChatGPT or Gemini, or a coding agent) into an interviewer
 * that ends with a valid .osadapter file. The AI never needs the user's token: the user pastes that
 * into the app afterwards.
 */
object WorkspacePrompt {

    private val FORMAT = """
## The .osadapter format (JSON)

Top level:
{
  "format": "openshortcuts-adapter",          // required, exactly this
  "version": 2,                                // required
  "id": "short-kebab-id",                      // lowercase letters, digits, dashes
  "name": "Short name shown in the app",
  "subtitle": "One short line, e.g. Hours and tasks",
  "glyph": "layers",                           // icon, see list below
  "tint": "Blue",                              // Blue Green Indigo Orange Red Gray Teal Yellow Pink Purple
  "baseUrl": "https://api.example.com/v1",     // no trailing slash
  "allowedDomains": [],                        // other hosts it may call; usually empty
  "authKind": "bearer",                        // "bearer" | "private_token" | "none"
  "authHeader": "Authorization",               // header the token goes in
  "authPrefix": "Bearer ",                     // text before the token; "" for none
  "tokenHint": "Where to create the token, e.g. Settings > API tokens",
  "tabs": [ { "id": "overview", "label": "Overview", "endpoint": "/summary", "widgets": [ ... ] } ],
  "globalActions": [ ... ]
}
NEVER put a token, password or secret anywhere in the file. The user types the token into the app.

Paths pick values out of a JSON response: "data.total", "data.items", "items[*].name" ([*] walks every item of a list), "" means the response itself (when it is a list).
Placeholders: "{{field}}" in an endpoint or body is filled from the tapped row (e.g. "/tasks/{{id}}") or from an action's params. Built in: "{{today}}" (YYYY-MM-DD) and "{{month}}" (YYYY-MM).
Dates are read from ISO 8601 strings ("2026-11-26T09:00:00Z") or "YYYY-MM-DD".

Every widget: "id" (unique), "type", "title", "tint", optional "glyph", and usually "endpoint" (a GET path; widgets with the same endpoint share one request).

Widget types:
- "stat_grid": numbers in two columns. "metrics": [{"label","valuePath","targetPath"?,"suffix"?,"tint"}]
- "target_bar": one big number with a bar toward a goal; extra metrics become slim rows. "metrics": [{"label","valuePath","targetPath","suffix","tint"}]
- "progress_ring": "currentPath","targetPath","unit"
- "bar_chart": bars per day or week from a list of items. "itemsPath","metaPath" (the date field),"valuePath" (number to sum; omit to count items),"bucket": "day"|"week","range": "rolling"|"thisWeek","count" (bars),"target" (goal per bar),"unit"
- "breakdown": totals per group, largest first. "itemsPath","groupPath","valuePath" (omit to count),"unit","count" (groups shown)
- "heatmap_chart": a year of activity, one square per day. "itemsPath","metaPath"
- "live_status": shows if something is running, with a start/stop button and a timer counting up. "activePath" (true/false),"activeTitlePath","subtitlePath" (e.g. the project),"startedAtPath" (ISO start time, so the timer is right),"holdToStop": true (stop needs press-and-hold),"inactiveText","activeActionId","inactiveActionId"
- "share_card": a "Ship Card" studio that turns recent commits into a shareable photo card. "endpoint" (profile),"titlePath" (handle),"itemsEndpoint" (commits; "{{login}}" etc. are filled from the profile, "{{since_7d}}" is a week ago),"itemsPath","metaPath" (commit time),"messagePath","groupPath" (repo),"valuePath" (commits per item)
- "action_grid": tiles that run actions. "actionRefs": [{"title","glyph","tint","actionId"}]
- "profile_card": the signed-in user. "titlePath","subtitlePath","metaPath","badgePath"
- "note_card": "body" (short text)
- "list": rows. "itemsPath","titlePath","subtitlePath","badgePath","metaPath" (a date or short text),"valuePath"+"unit" (a number on the right),"searchable": true,"badgeTints": {"DONE":"Green"},"glyph": "none" to hide the row icon,"rowActions","detail"
- "grouped_list": like "list" plus "groupPath" (a field) or "groupBy": "week" (groups rows by the week of metaPath, with totals of valuePath)
  "detail" (when a row is tapped): {"endpoint"?: "/items/{{id}}","bodyPath" (Markdown),"urlPath" (link to open in the browser),"fields": [{"label","path"}],"commentsEndpoint"?,"commentAuthorPath"?,"commentBodyPath"?,"commentTimePath"?,"actions"?: [action...]}
  "rowActions": [{"label","glyph","tint","swipe": "right"|"left","method","endpoint","bodyTemplate"?}]

Actions (in "globalActions", referenced by id):
{"id","title","method": "POST"|"PUT"|"PATCH"|"DELETE","endpoint","bodyTemplate"?: "{\"name\": \"{{name}}\"}","params"?: [{"key","label","placeholder"?,"default"?,"optionsEndpoint"? (GET list to pick from),"optionsPath"?,"optionLabelPath"?,"optionValuePath"?}],"confirmMessage"? (asks before running),"tint","glyph"}
Values in bodyTemplate are escaped for JSON automatically; keep numbers unquoted ({{projectId}}) and text quoted ("{{description}}").
A shortcut step "Run Workspace Action" can call these actions from automations; a live_status widget tied to an action lets the app skip a start that is already running.

Icons (glyph): layers timer clock alarm calendar check bolt playFill pause stop person lock info bell link braces branch grid menu quote send search sliders sun moon pin home battery wifi phone flash text trash x plus.
""".trimIndent()

    private val EXAMPLE = """
## A small complete example
{
  "format": "openshortcuts-adapter", "version": 2, "id": "tracker", "name": "Tracker", "subtitle": "Hours and tasks",
  "glyph": "timer", "tint": "Blue", "baseUrl": "https://api.example.com/v1", "allowedDomains": [],
  "authKind": "bearer", "authHeader": "Authorization", "authPrefix": "Bearer ", "tokenHint": "Profile > API tokens",
  "tabs": [
    {"id": "overview", "label": "Overview", "endpoint": "/summary", "widgets": [
      {"id": "timer", "type": "live_status", "title": "Timer running", "tint": "Green", "endpoint": "/timer",
       "activePath": "data.running", "activeTitlePath": "data.title", "inactiveText": "No timer running",
       "activeActionId": "stop", "inactiveActionId": "start"},
      {"id": "week", "type": "target_bar", "title": "This week", "tint": "Blue", "endpoint": "/summary",
       "metrics": [{"label": "This week", "valuePath": "data.week_hours", "targetPath": "data.week_goal", "suffix": "h", "tint": "Blue"}]},
      {"id": "days", "type": "bar_chart", "title": "Hours per day", "tint": "Blue", "endpoint": "/entries",
       "itemsPath": "data", "metaPath": "started_at", "valuePath": "hours", "range": "thisWeek", "unit": "h", "target": 8}
    ]},
    {"id": "log", "label": "Log", "endpoint": "/entries", "widgets": [
      {"id": "entries", "type": "grouped_list", "title": "Entries", "tint": "Blue", "glyph": "none", "endpoint": "/entries",
       "itemsPath": "data", "titlePath": "title", "subtitlePath": "project.name", "badgePath": "status", "metaPath": "started_at",
       "groupBy": "week", "valuePath": "hours", "unit": "h", "badgeTints": {"APPROVED": "Green", "PENDING": "Orange"},
       "detail": {"bodyPath": "note", "fields": [{"label": "Project", "path": "project.name"}]}}
    ]}
  ],
  "globalActions": [
    {"id": "start", "title": "Start Timer", "method": "POST", "endpoint": "/timer/start", "tint": "Green", "glyph": "playFill",
     "bodyTemplate": "{\"projectId\": {{projectId}}, \"description\": \"{{description}}\"}",
     "params": [{"key": "projectId", "label": "Project", "optionsEndpoint": "/projects", "optionsPath": "data", "optionLabelPath": "name", "optionValuePath": "id"},
                {"key": "description", "label": "Task"}]},
    {"id": "stop", "title": "Stop Timer", "method": "POST", "endpoint": "/timer/stop", "tint": "Red", "glyph": "pause", "confirmMessage": "Stop the timer?"}
  ]
}
""".trimIndent()

    private val RULES = """
## Rules for the file you produce
- Only use paths that exist in the sample responses you have seen. If you are unsure about a path, ask.
- Prefer few, useful widgets per tab (2 to 5). Put the most important number first.
- Use "badgeTints" for statuses, "glyph": "none" for lists with long titles, "valuePath"+"unit" when rows have a number such as hours or amount.
- Use read-only GET endpoints for widgets. Only add actions (POST/PUT/PATCH/DELETE) the user explicitly asked for, and add "confirmMessage" to anything that deletes or changes shared data.
- Ids are unique, lowercase and short. JSON must be valid: double quotes, no comments, no trailing commas.
- Never include tokens, passwords, cookies or personal data from the samples (names, emails) in the file.
""".trimIndent()

    /** For a normal AI chat: the user can't run code, so the AI asks for docs and sample responses. */
    fun chat(): String = """
You are helping me build a "workspace" for OpenShortcuts, an Android app. A workspace is a small dashboard inside the app for an API I use: numbers, charts, lists and buttons, organized in tabs. It is described by one JSON file with the extension .osadapter. Your job is to interview me, then write that file.

How to interview me:
- Reply in the language I write in. Keep each message short and plain; I may not be a developer.
- Ask ONE question at a time and wait for my answer. Offer 2 to 4 example answers when that helps.
- Grill me until you are sure. Do not guess an endpoint, a field name or a date format; ask me, or ask me to paste an example.
- Never ask for my token, password or API key, and tell me not to paste them. I will add the token inside the app myself.
- Before writing the file, summarize the plan (tabs, widgets, actions) in a short list and ask me to confirm or change it.

Cover these, in order:
1. What service or tool is this for, and what do I want to see or do on my phone? (e.g. hours this week, open tickets, today's orders, start/stop a timer)
2. The API: its base address (like https://api.example.com/v1), and links to its documentation if I have them. If I don't know, help me find it (Developer settings, API docs, the browser's Network tab).
3. How it signs in: a Bearer token, a custom header (like PRIVATE-TOKEN), or none. Where in the service I can create a token (for "tokenHint").
4. For each thing I want to see: the exact GET endpoint, and ask me to paste a real example response (I can open it in the browser or copy it from the docs). Tell me to remove private data first. Read the JSON carefully to find the right paths, list fields and date fields.
5. Which numbers have a goal or target (weekly hours, budget), which lists need grouping (by status, by week), what a tapped row should show.
6. Any actions I want (start/stop, approve, create). For each: method, endpoint, body, and what I need to fill in each time. Ask whether it changes shared data and needs a confirmation.
7. How many tabs, their names, and what goes first.

When I confirm the plan, reply with the complete file in ONE ```json code block and nothing inside the block except the JSON. After the block, tell me in 3 short steps how to use it: copy the JSON, open OpenShortcuts > Workspaces > + > Paste a workspace, then add my token when the app asks. If I report an error or a widget that shows nothing, ask for the response again and fix the file.

$FORMAT

$EXAMPLE

$RULES

Start now with your first question.
""".trimIndent()

    /** For a coding agent that can read docs and call the API itself. */
    fun agent(): String = """
Build an OpenShortcuts workspace file (.osadapter) for me. OpenShortcuts is an Android app; a workspace is a dashboard inside it for an API I use (numbers, charts, lists, action buttons in tabs), described by one JSON file.

Work like this:
1. Interview me first, one question at a time, until you know: the service and what I want to see or do on my phone; the API base URL and docs; how it signs in; which screens, numbers, goals, lists and actions I need. Grill me: don't assume endpoints or fields. Reply in the language I use.
2. Investigate the API yourself: read its documentation, and if you can run commands, call the GET endpoints to see real responses. Read the token from an environment variable I set (for example OS_TOKEN); never ask me to paste it into the chat, never print it, and never write it to a file.
3. Only call read-only GET endpoints while exploring. Do not call anything that creates, changes or deletes data unless I say so for that exact request.
4. Map real response fields to widget paths. Prefer few, useful widgets per tab.
5. Show me the plan (tabs, widgets, actions) and wait for my OK.
6. Write the file to ./<id>.osadapter, validate that it is JSON, and check every path against a real response. Then tell me how to load it: send the file to my phone and open it with OpenShortcuts, or copy its contents and use Workspaces > + > Paste a workspace. I add the token in the app.

$FORMAT

$EXAMPLE

$RULES

Start by asking your first question.
""".trimIndent()
}
