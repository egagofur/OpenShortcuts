package app.openshortcuts.engine

import android.content.Context
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.BadRequestException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import app.openshortcuts.model.ActionType
import app.openshortcuts.model.Automation
import app.openshortcuts.model.BUILT_IN_VARIABLES
import app.openshortcuts.model.Library
import app.openshortcuts.model.ParamKind
import app.openshortcuts.model.Portable
import app.openshortcuts.model.Tint
import app.openshortcuts.model.TriggerKind
import app.openshortcuts.ui.Glyphs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * "Describe a shortcut": a sentence in, a reviewed shortcut (and optional automation) out.
 * Uses Claude with your own API key. Nothing is sent anywhere until you add a key and tap Create.
 */
object Describe {
    const val MODEL = "claude-opus-5-5"

    data class Draft(val imported: Portable.Imported, val automation: Automation?, val note: String)

    // --- API key: app-private and excluded from backups ---------------------------------

    private fun keyFile(ctx: Context) = File(ctx.noBackupFilesDir, "claude_api_key")
    fun key(ctx: Context): String? = keyFile(ctx).takeIf { it.exists() }?.readText()?.trim()?.ifEmpty { null }
    fun saveKey(ctx: Context, key: String) = keyFile(ctx).writeText(key.trim())
    fun clearKey(ctx: Context) { keyFile(ctx).delete(); client = null }

    @Volatile private var client: Pair<String, AnthropicClient>? = null
    private fun client(key: String): AnthropicClient =
        client?.takeIf { it.first == key }?.second
            ?: AnthropicOkHttpClient.builder().apiKey(key).build().also { client = key to it }

    // --- Prompt -----------------------------------------------------------------------------

    /** Built from the live catalog, so new actions are described without editing this file. */
    fun systemPrompt(): String = buildString {
        appendLine("You turn a person's description into one OpenShortcuts shortcut for Android, plus an optional automation that runs it.")
        appendLine("Use only the actions below. Write names and any text the person will read in the language they wrote in.")
        appendLine("Return params as key/value pairs using the keys listed; leave out keys you want at their default. Values are always strings.")
        appendLine("Text values may include variables written as {Name}. Built-in variables: ${BUILT_IN_VARIABLES.joinToString()}. Set Variable creates more.")
        appendLine("A param of kind INPUT takes \"@prev\" (the previous action's result) or literal text.")
        appendLine("Blocks must be closed: IF … (OTHERWISE …) END_IF; REPEAT … END_REPEAT; REPEAT_EACH … END_EACH; MENU, then one CASE per option (param label), then END_MENU.")
        appendLine("Lists are one item per line. Dates are written like \"now\", \"tomorrow 09:00\", \"23:00\" or \"2026-12-31 08:00\".")
        appendLine("Prefer the fewest actions that do the job. Don't invent phone numbers, names or links the person didn't give; leave those params empty instead and say so in note.")
        appendLine("Use RUN_SHORTCUT only with a name from the person's existing shortcuts.")
        appendLine()
        appendLine("ACTIONS (TYPE: title — sentence — params — notes):")
        ActionType.entries.forEach { t ->
            val params = t.params.joinToString("; ") { p ->
                buildString {
                    append(p.key).append(" (").append(p.kind.name.lowercase())
                    if (p.kind == ParamKind.CHOICE) append(": ").append(p.choices.joinToString("|"))
                    if (p.kind == ParamKind.NUMBER) append(" ${p.min}-${p.max}")
                    if (p.default.isNotEmpty()) append(", default \"").append(p.default).append('"')
                    append(')')
                }
            }.ifEmpty { "none" }
            append("- ").append(t.name).append(": ").append(t.title).append(" — \"").append(t.template).append("\" — ").append(params)
            if (t.output != null) append(" — outputs ").append(t.output)
            if (t.summary.isNotEmpty()) append(" — ").append(t.summary)
            appendLine()
        }
        appendLine()
        appendLine("AUTOMATION (set kind \"none\" if the person didn't ask for it to run by itself):")
        appendLine("- Time: time \"HH:mm\"; days = Calendar day numbers 1=Sunday … 7=Saturday, empty = every day.")
        appendLine("- WhatsAppCallLong: a WhatsApp call lasts callMinutes, only between windowStart and windowEnd (\"HH:mm\"; equal = any time).")
        appendLine("- WhatsAppMessage: a WhatsApp message arrives; text = part of the sender or group name (empty = anyone). The message text is {Shortcut Input}; {Message Sender} and {Message Chat} are set.")
        appendLine("- Charger: on = connected, off = disconnected. BatteryLevel: on = rises above level, off = falls below level; the level is {Shortcut Input}.")
        appendLine("- Dnd: on = Do Not Disturb turns on. WiFi: on = connects.")
        appendLine("- App: on = the app opens, off = it closes; app = its Android package name (e.g. com.instagram.android). The app's name is {Shortcut Input}.")
        appendLine("- AppNotification: a notification arrives; app = package name or \"\" for any app; text = words it must contain (\"\" = any). Its text is {Shortcut Input}; {Notification Title} and {Notification App} are set.")
        appendLine("- Alarm: on = an alarm goes off, off = an alarm is stopped. Bluetooth: on = connects; text = part of the device name (\"\" = any). Headphones: on = connected.")
        appendLine("- AirplaneMode: on = turns on. BatterySaver: on = turns on.")
        appendLine("Fill every automation field; ones that don't apply take any value (\"00:00\", 0, false, \"\"). warnFirst = false: automations run on their own. Set it true only when the user explicitly asks to be asked or warned first.")
        appendLine()
        appendLine("note: one or two plain sentences telling the person what you built and anything they still need to fill in or allow.")
    }

    private fun obj(props: Map<String, Any>, required: List<String> = props.keys.toList()) = mapOf(
        "type" to "object", "additionalProperties" to false, "required" to required, "properties" to props,
    )

    private fun schema(): BetaJsonOutputFormat.Schema {
        val action = obj(mapOf(
            "type" to mapOf("type" to "string", "enum" to ActionType.entries.map { it.name }),
            "params" to mapOf("type" to "array", "items" to obj(mapOf(
                "key" to mapOf("type" to "string"), "value" to mapOf("type" to "string"),
            ))),
        ))
        val automation = obj(mapOf(
            "kind" to mapOf("type" to "string", "enum" to listOf("none") + TriggerKind.entries.map { it.name }),
            "time" to mapOf("type" to "string"),
            "days" to mapOf("type" to "array", "items" to mapOf("type" to "integer")),
            "callMinutes" to mapOf("type" to "integer"),
            "windowStart" to mapOf("type" to "string"),
            "windowEnd" to mapOf("type" to "string"),
            "on" to mapOf("type" to "boolean"),
            "level" to mapOf("type" to "integer"),
            "text" to mapOf("type" to "string"),
            "app" to mapOf("type" to "string"),
            "warnFirst" to mapOf("type" to "boolean"),
        ))
        val root = obj(mapOf(
            "name" to mapOf("type" to "string"),
            "color" to mapOf("type" to "string", "enum" to Tint.entries.map { it.name }),
            "icon" to mapOf("type" to "string", "enum" to Glyphs.names),
            "actions" to mapOf("type" to "array", "items" to action),
            "automation" to automation,
            "note" to mapOf("type" to "string"),
        ))
        return BetaJsonOutputFormat.Schema.builder().apply { root.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) } }.build()
    }

    // --- Call ---------------------------------------------------------------------------------

    suspend fun describe(ctx: Context, request: String, lib: Library): Draft = withContext(Dispatchers.IO) {
        val key = key(ctx) ?: error("Add your Claude API key first.")
        if (request.isBlank()) error("Describe what the shortcut should do.")
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            // The catalog never changes between requests, so it's cached; your shortcut names go in the message.
            .systemOfBetaTextBlockParams(listOf(
                BetaTextBlockParam.builder().text(systemPrompt())
                    .cacheControl(BetaCacheControlEphemeral.builder().build()).build(),
            ))
            .outputConfig(BetaOutputConfig.builder()
                .effort(BetaOutputConfig.Effort.MEDIUM)
                .format(BetaJsonOutputFormat.builder().schema(schema()).build())
                .build())
            // If a safety check declines, let the API retry on a suitable model instead of failing.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .addUserMessage(buildString {
                appendLine("My existing shortcuts: ${lib.shortcuts.joinToString { it.name }.ifEmpty { "none" }}")
                appendLine()
                append(request.trim())
            })
            .build()
        val response = try {
            client(key).beta().messages().create(params)
        } catch (e: UnauthorizedException) {
            error("That API key wasn't accepted. Check it at console.anthropic.com.")
        } catch (e: PermissionDeniedException) {
            error("This API key can't use $MODEL.")
        } catch (e: RateLimitException) {
            error("Too many requests right now. Try again in a minute.")
        } catch (e: BadRequestException) {
            error("Claude couldn't take that request: ${e.message}")
        } catch (e: AnthropicServiceException) {
            error("Claude had a problem (${e.statusCode()}). Try again.")
        } catch (e: AnthropicIoException) {
            error("Couldn't reach Claude. Check your internet connection.")
        }
        when (response.stopReason().orElse(null)) {
            BetaStopReason.REFUSAL -> error("Claude declined to build that shortcut.")
            BetaStopReason.MAX_TOKENS -> error("That description made a shortcut too long to finish. Try a smaller one.")
            else -> Unit
        }
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        parse(text, lib)
    }

    /** Model JSON -> the same validated path as an imported .openshortcut file. */
    fun parse(text: String, lib: Library): Draft {
        val o = runCatching { JSONObject(text) }.getOrElse { error("Claude's answer wasn't a shortcut. Try again.") }
        val actions = JSONArray()
        o.optJSONArray("actions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                val p = JSONObject()
                a.optJSONArray("params")?.let { ps -> for (j in 0 until ps.length()) ps.getJSONObject(j).let { p.put(it.getString("key"), it.getString("value")) } }
                actions.put(JSONObject().put("type", a.getString("type")).put("params", p))
            }
        }
        val file = JSONObject().put("format", "openshortcuts").put("version", 1).put("shortcut", JSONObject()
            .put("name", o.optString("name")).put("tint", o.optString("color")).put("glyph", o.optString("icon")).put("actions", actions))
        val imported = Portable.decode(file.toString(), lib)
        if (imported.shortcut.actions.isEmpty()) error("Claude couldn't map that to any action. Try describing it differently.")
        val auto = o.optJSONObject("automation")?.let { a ->
            val kind = runCatching { TriggerKind.valueOf(a.optString("kind")) }.getOrNull() ?: return@let null
            fun minutes(s: String, fallback: Int) = runCatching { Data.parseDate(s).let { it.hour * 60 + it.minute } }.getOrDefault(fallback)
            Automation(
                kind = kind,
                timeOfDay = minutes(a.optString("time"), 22 * 60),
                days = a.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.getInt(it) }.filter { it in 1..7 }.toSet() }.orEmpty(),
                callMinutes = a.optInt("callMinutes", 30).coerceIn(1, 600),
                windowStart = minutes(a.optString("windowStart"), 0),
                windowEnd = minutes(a.optString("windowEnd"), 0),
                on = a.optBoolean("on", true),
                level = a.optInt("level", 20).coerceIn(1, 99),
                text = a.optString("text").take(60),
                app = a.optString("app").take(120),
                warnFirst = a.optBoolean("warnFirst", false),
                shortcutId = imported.shortcut.id,
                // Starts off: you turn it on after checking it.
                enabled = false,
            )
        }
        return Draft(imported, auto, o.optString("note").take(400))
    }
}
