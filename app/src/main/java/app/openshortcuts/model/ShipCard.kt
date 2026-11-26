package app.openshortcuts.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One commit as the Ship Card sees it. */
data class ShipCommit(val at: Instant, val message: String = "", val repo: String = "", val count: Int = 1)

/** An AI that a commit credits as co-author ("Co-Authored-By: Claude <…>"). */
enum class AiAgent(val label: String, private val match: List<String>) {
    Claude("Claude", listOf("claude", "anthropic")),
    ChatGPT("ChatGPT", listOf("chatgpt", "openai", "codex", "gpt-")),
    Gemini("Gemini", listOf("gemini", "jules")),
    Grok("Grok", listOf("grok", "x.ai")),
    Copilot("Copilot", listOf("copilot")),
    Cursor("Cursor", listOf("cursor")),
    Devin("Devin", listOf("devin")),
    ;

    companion object {
        private val trailer = Regex("(?im)^\\s*co-authored-by:\\s*(.+)$")

        /** Every AI the commit message credits in a Co-Authored-By trailer. */
        fun creditedIn(message: String): Set<AiAgent> =
            trailer.findAll(message).flatMap { m ->
                val who = m.groupValues[1].lowercase()
                entries.filter { a -> a.match.any { who.contains(it) } }
            }.toSet()
    }
}

/** The time window a card covers. */
enum class ShipPeriod(val label: String) {
    Today("Today"), Week("This week"), Hackathon("Hackathon");
}

/** The numbers on a card, worked out from commits in its window. */
data class ShipStats(
    val commits: Int,
    val repos: Int,
    val activeDays: Int,
    /** First to last commit in the window. */
    val span: Duration,
    /** First to last commit within a day, averaged over active days: the honest "how long" for a week. */
    val daySpan: Duration,
    /** How many commits credit each AI, largest first. */
    val agents: List<Pair<AiAgent, Int>>,
    /** Share of commits that credit any AI, 0..1. */
    val aiShare: Float,
    /** Commit times in the window, oldest first: the card's "route". */
    val times: List<Instant>,
    val from: Instant,
    val to: Instant,
) {
    companion object {
        fun window(period: ShipPeriod, now: Instant, zone: ZoneId, hackathonHours: Int): Pair<Instant, Instant> {
            val today = LocalDate.ofInstant(now, zone)
            return when (period) {
                ShipPeriod.Today -> today.atStartOfDay(zone).toInstant() to now
                ShipPeriod.Week -> today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant() to now
                ShipPeriod.Hackathon -> now.minus(Duration.ofHours(hackathonHours.toLong())) to now
            }
        }

        fun from(commits: List<ShipCommit>, from: Instant, to: Instant, zone: ZoneId): ShipStats {
            val inWindow = commits.filter { !it.at.isBefore(from) && !it.at.isAfter(to) }.sortedBy { it.at }
            val total = inWindow.sumOf { it.count }
            val credited = inWindow.map { it to AiAgent.creditedIn(it.message) }
            val agents = AiAgent.entries
                .map { a -> a to credited.filter { a in it.second }.sumOf { it.first.count } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
            val withAi = credited.filter { it.second.isNotEmpty() }.sumOf { it.first.count }
            return ShipStats(
                commits = total,
                repos = inWindow.map { it.repo }.filter { it.isNotBlank() }.distinct().size,
                activeDays = inWindow.map { LocalDate.ofInstant(it.at, zone) }.distinct().size,
                span = if (inWindow.size < 2) Duration.ZERO else Duration.between(inWindow.first().at, inWindow.last().at),
                daySpan = inWindow.groupBy { LocalDate.ofInstant(it.at, zone) }.values
                    .map { d -> Duration.between(d.first().at, d.last().at) }
                    .let { spans -> if (spans.isEmpty()) Duration.ZERO else Duration.ofSeconds(spans.sumOf { it.seconds } / spans.size) },
                agents = agents,
                aiShare = if (total == 0) 0f else withAi.toFloat() / total,
                times = inWindow.map { it.at },
                from = from,
                to = to,
            )
        }

        /** "3h 24m", "45m", "2d 5h". */
        fun spanLabel(d: Duration): String {
            val m = d.toMinutes()
            return when {
                m >= 48 * 60 -> "${m / 1440}d ${(m % 1440) / 60}h"
                m >= 60 -> "${m / 60}h ${m % 60}m"
                else -> "${m}m"
            }
        }
    }
}

/** Which layout a persona draws with. */
enum class ShipLayout { Stack, Recap, Hackathon }

/**
 * A card's personality: its layout and its words. Every line can be edited; {tokens} are filled
 * from the stats ({commits} {span} {repos} {days} {ai} {agents} {handle} {night} {last} {rest} {hours} {date} {week}).
 */
enum class ShipPersona(
    val label: String,
    val layout: ShipLayout,
    val kicker: String,
    /** Ready-made headlines to pick from; the first is the default. */
    val headlines: List<String>,
    val sub: String,
    /** Label to value, each may hold tokens. */
    val rows: List<Pair<String, String>>,
) {
    Classic("Classic", ShipLayout.Stack, "", listOf("Shipped {date}", "Code. Commit. Repeat.", "Hari ini ship {commits} commit", "Another day, another deploy"),
        "@{handle}  with {agents}", listOf("COMMITS" to "{commits}", "FOCUS SPAN" to "{span}", "AI PAIR" to "{ai}")),
    VibeCoder("Vibe Coder", ShipLayout.Stack, "VIBE CODING SESSION",
        listOf("AI wrote {ai}. I brought the vibes.", "{agents} typed. I vibed.", "Ngoding pakai feeling", "Prompt engineer, technically."),
        "Team @{handle} + {agents}", listOf("AI PAIR" to "{ai}", "COMMITS" to "{commits}", "VIBE TIME" to "{span}")),
    ReluctantPro("Reluctant Pro", ShipLayout.Recap, "TECHNICALLY A PROGRAMMER",
        listOf("{commits} commits. Zero keyboard injuries.", "I don't code. I ship.", "Jago, tapi males ngetik.", "Technically, this counts."),
        "@{handle}", listOf("Commits" to "{commits}", "Repos" to "{repos}", "Hands on keys" to "{span}", "AI pair" to "{ai}")),
    LazyGenius("Lazy Genius", ShipLayout.Stack, "MINIMUM EFFORT. MAXIMUM SHIP.",
        listOf("Shipped in {span}. Rested the rest.", "Males ngoding, tetap ship.", "Work smart, nap hard.", "Minimum effort, maximum merge."),
        "@{handle}", listOf("COMMITS" to "{commits}", "EFFORT" to "{span}", "REST" to "{rest}")),
    NightOwl("Night Owl", ShipLayout.Recap, "NIGHT SHIFT",
        listOf("Last commit at {last}", "Ngoding pas orang tidur", "Bugs fear the dark.", "{night} commits after 10pm"),
        "@{handle}", listOf("Commits" to "{commits}", "Last commit" to "{last}", "Span" to "{span}", "Repos" to "{repos}")),
    Grindset("Grindset", ShipLayout.Recap, "GRINDSET",
        listOf("{days}-day streak. {commits} commits.", "No days off.", "Gas terus sampai deploy", "Consistency is a feature."),
        "@{handle}  ·  week {week}", listOf("Commits" to "{commits}", "Active days" to "{days}", "Repos" to "{repos}", "Span" to "{span}")),
    SeniorDev("Senior Dev", ShipLayout.Stack, "SENIOR DEV ENERGY",
        listOf("Few commits. Big impact.", "Review > rewrite.", "Senior itu yang hapus kode", "It works on prod."),
        "@{handle}", listOf("COMMITS" to "{commits}", "REPOS" to "{repos}", "SPAN" to "{span}")),
    Hackathon("Hackathon", ShipLayout.Hackathon, "HACKATHON",
        listOf("Hack Night", "Built it in {hours}h", "Nggak tidur demi demo", "Demo day survivor"),
        "Built in {hours}h", listOf("Commits" to "{commits}", "Repos" to "{repos}", "AI pair" to "{ai}")),
    ;

    val headline: String get() = headlines.first()

    companion object {
        private val token = Regex("\\{(\\w+)\\}")

        /**
         * Fills {tokens} in [text]; unknown tokens stay as written. [agents] overrides the detected AIs;
         * [perDay] shows {span} as an average day (for a week, first-to-last across days says little).
         */
        fun fill(text: String, s: ShipStats, handle: String, zone: ZoneId, agents: List<String>? = null, perDay: Boolean = false): String =
            token.replace(text) { m ->
                val local = s.to.atZone(zone)
                val names = agents ?: s.agents.map { it.first.label }
                when (m.groupValues[1]) {
                    "commits" -> s.commits.toString()
                    "span" -> if (perDay && s.activeDays > 1) ShipStats.spanLabel(s.daySpan) + "/day" else ShipStats.spanLabel(s.span)
                    "repos" -> s.repos.toString()
                    "days" -> s.activeDays.toString()
                    "ai" -> "${(s.aiShare * 100).toInt()}%"
                    "agents" -> names.joinToString(" + ").ifBlank { "AI" }
                    "handle" -> handle.ifBlank { "me" }
                    "night" -> s.times.count { val h = it.atZone(zone).hour; h >= 22 || h < 4 }.toString()
                    "last" -> s.times.lastOrNull()?.atZone(zone)?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "—"
                    "rest" -> ShipStats.spanLabel(Duration.between(s.from, s.to).minus(s.span).let { if (it.isNegative) Duration.ZERO else it })
                    "hours" -> Duration.between(s.from, s.to).toHours().coerceAtLeast(1).toString()
                    "date" -> java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH).format(local)
                    "week" -> local.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString()
                    else -> m.value
                }
            }
    }
}

/** AI names to pick from (searchable; anything else can be typed in). */
val SHIP_AI_NAMES = listOf(
    "Claude", "ChatGPT", "Codex", "Gemini", "Grok", "Copilot", "Cursor", "Windsurf", "Kiro", "Devin", "Jules",
    "Cline", "Aider", "Zed AI", "v0", "Bolt", "Lovable", "Replit Agent", "DeepSeek", "Qwen", "Mistral", "Llama", "Perplexity",
)

/** Languages and tools to pick from (searchable; anything else can be typed in). */
val SHIP_LANGUAGES = listOf(
    "Kotlin", "Java", "Swift", "Objective-C", "Dart", "Flutter", "TypeScript", "JavaScript", "React", "Next.js", "Vue", "Svelte",
    "Angular", "Node.js", "Deno", "Bun", "Python", "Django", "FastAPI", "Go", "Rust", "C", "C++", "C#", ".NET", "PHP", "Laravel",
    "Ruby", "Rails", "Elixir", "Erlang", "Scala", "Clojure", "Haskell", "OCaml", "F#", "Zig", "Nim", "Lua", "R", "Julia",
    "MATLAB", "SQL", "PostgreSQL", "MySQL", "MongoDB", "Redis", "GraphQL", "HTML", "CSS", "Tailwind", "Sass", "Shell", "Bash",
    "PowerShell", "Docker", "Kubernetes", "Terraform", "AWS", "GCP", "Azure", "Firebase", "Supabase", "Solidity", "WebAssembly",
    "Unity", "Unreal", "Godot", "Jetpack Compose", "SwiftUI", "React Native",
)
