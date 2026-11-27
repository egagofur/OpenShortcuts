package app.openshortcuts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.AuthKind
import app.openshortcuts.model.Tint
import app.openshortcuts.model.WorkspaceAdapter
import app.openshortcuts.model.WorkspaceTemplates
import app.openshortcuts.model.WorkspacePrompt

private enum class CreateStep { Pick, Blank, Ai }

/** Opens the marketplace. Provided by the app's navigation. */
val LocalOpenMarketplace = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/** Start a workspace: from the marketplace, with AI, blank for your own API, or from a pasted or .osadapter file. */
@Composable
fun NewWorkspaceDialog(onDismiss: () -> Unit, onImport: () -> Unit, onPaste: () -> Unit, onCreated: (WorkspaceAdapter) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(CreateStep.Pick) }
    fun create(a: WorkspaceAdapter) {
        AdapterStore.upsert(a)
        onCreated(a)
    }

    DuoDialog(onDismiss) {
        when (step) {
            CreateStep.Pick -> {
                DialogTitle("New Workspace", "A dashboard for an API you use. Your token stays on this phone.")
                val openMarket = LocalOpenMarketplace.current
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceRow("Browse the marketplace", false, "store", Tint.Green.color()) { onDismiss(); openMarket() }
                    ChoiceRow("Build with AI", false, "quote", Tint.Purple.color()) { step = CreateStep.Ai }
                    ChoiceRow("Start blank", false, "plus", Tint.Blue.color()) { step = CreateStep.Blank }
                    ChoiceRow("Paste a workspace", false, "copy", Tint.Gray.color(), onPaste)
                    ChoiceRow("Import an .osadapter file", false, "down", Tint.Gray.color(), onImport)
                }
            }
            CreateStep.Ai -> AiStep(onPaste = onPaste, onBack = { step = CreateStep.Pick })
            CreateStep.Blank -> {
                var name by remember { mutableStateOf("") }
                var url by remember { mutableStateOf("https://") }
                var auth by remember { mutableStateOf(AuthKind.BEARER) }
                var header by remember { mutableStateOf("") }
                var prefix by remember { mutableStateOf("") }
                var tried by remember { mutableStateOf(false) }
                val nameFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                val urlFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                val headerFocus = remember { androidx.compose.ui.focus.FocusRequester() }
                // Checked when you press Create; each problem shows on its own field.
                val nameError = if (tried && name.isBlank()) "Give the workspace a name." else null
                val urlError = if (tried && !WorkspaceTemplates.isValidBaseUrl(url)) "Use an address that starts with https:// and has a host." else null
                val headerError = if (tried && auth == AuthKind.CUSTOM && !header.trim().matches(Regex("[A-Za-z0-9-]+"))) "Enter a header name, like X-API-Key (letters, numbers and dashes)." else null
                DialogTitle("Blank Workspace", "Name it and point it at your API. You'll add tabs and widgets next.")
                FormField("Name", name, { name = it }, "Team tracker", error = nameError, focus = nameFocus)
                Spacer(Modifier.height(12.dp))
                FormField("API address", url, { url = it }, "https://api.example.com/v1", error = urlError, focus = urlFocus,
                    keyboard = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri))
                Spacer(Modifier.height(12.dp))
                Text("Sign-in", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceRow("Bearer token", auth == AuthKind.BEARER) { auth = AuthKind.BEARER }
                    ChoiceRow("PRIVATE-TOKEN header", auth == AuthKind.PRIVATE_TOKEN) { auth = AuthKind.PRIVATE_TOKEN }
                    ChoiceRow("Custom header", auth == AuthKind.CUSTOM) { auth = AuthKind.CUSTOM }
                    ChoiceRow("No sign-in", auth == AuthKind.NONE) { auth = AuthKind.NONE }
                }
                if (auth == AuthKind.CUSTOM) {
                    Spacer(Modifier.height(12.dp))
                    FormField("Header name", header, { header = it.take(60) }, "X-API-Key", error = headerError, focus = headerFocus,
                        hint = "Your token is sent in this header.")
                    Spacer(Modifier.height(12.dp))
                    FormField("Before the token (optional)", prefix, { prefix = it.take(30) }, "e.g. Token  or  Api-Key ",
                        hint = if (header.isNotBlank()) "Sent as: ${header.trim()}: ${prefix}••••" else null)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Create Workspace", Modifier.fillMaxWidth()) {
                    tried = true
                    val badName = name.isBlank()
                    val badUrl = !WorkspaceTemplates.isValidBaseUrl(url)
                    val badHeader = auth == AuthKind.CUSTOM && !header.trim().matches(Regex("[A-Za-z0-9-]+"))
                    // Take the person to the first thing to fix.
                    when {
                        badName -> runCatching { nameFocus.requestFocus() }
                        badUrl -> runCatching { urlFocus.requestFocus() }
                        badHeader -> runCatching { headerFocus.requestFocus() }
                        else -> create(WorkspaceTemplates.blank(name, url, auth, AdapterStore.adapters.value, header, prefix))
                    }
                }
                Spacer(Modifier.height(8.dp))
                QuietButton("Back", Modifier.fillMaxWidth()) { step = CreateStep.Pick }
            }
        }
    }
}

/**
 * An AI interviews you about your API and writes the workspace file. A chat gets a prompt that asks for
 * docs and sample responses; an agent gets one that lets it explore the API itself. Either way the
 * token stays out of the conversation, and the JSON comes back through "Paste a workspace".
 */
@Composable
private fun AiStep(onPaste: () -> Unit, onBack: () -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(text: String, label: String) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Workspace prompt", text))
        copied = label
    }
    fun open(url: String) {
        runCatching {
            ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
    DialogTitle("Build with AI", "An AI asks you about your API, one question at a time, then writes the workspace for you.")
    Text("1. Start the interview", style = Theme.t.label, color = c.ink)
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ChoiceRow("Open ChatGPT", false, "link", Tint.Green.color()) {
            val prompt = WorkspacePrompt.chat()
            copy(prompt, "ChatGPT")
            open("https://chatgpt.com/?q=" + java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20"))
        }
        ChoiceRow("Open Gemini", false, "link", Tint.Blue.color()) {
            copy(WorkspacePrompt.chat(), "Gemini")
            open("https://gemini.google.com/app")
        }
        ChoiceRow("Copy prompt for AI chat", false, "copy", Tint.Gray.color()) { copy(WorkspacePrompt.chat(), "chat") }
        ChoiceRow("Copy prompt for AI agent", false, "braces", Tint.Indigo.color()) { copy(WorkspacePrompt.agent(), "agent") }
    }
    copied?.let { which ->
        Spacer(Modifier.height(8.dp))
        Text(
            when (which) {
                "ChatGPT" -> "The prompt is also copied. If ChatGPT opens empty, paste it and send."
                "Gemini" -> "The prompt is copied. Paste it into Gemini and send."
                "agent" -> "Copied. Paste it into Claude Code, Codex, Cursor or another agent. It may read your API docs and test GET requests; give it the token through an environment variable, not the chat."
                else -> "Copied. Paste it into any AI chat and send."
            },
            style = Theme.t.small,
            color = c.ink2,
        )
    }
    Spacer(Modifier.height(14.dp))
    Text("2. Bring the workspace back", style = Theme.t.label, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text("When the AI sends the JSON, copy it, come back here and paste it. Never give the AI your token; you add it in the app.", style = Theme.t.small, color = c.ink2)
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Paste a workspace", Modifier.fillMaxWidth(), onClick = onPaste)
    Spacer(Modifier.height(8.dp))
    QuietButton("Back", Modifier.fillMaxWidth(), onClick = onBack)
}
