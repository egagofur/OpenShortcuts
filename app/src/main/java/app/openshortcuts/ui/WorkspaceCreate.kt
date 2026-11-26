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

private enum class CreateStep { Pick, Blank, GitLab, Ai }

/** Start a workspace: blank for your own API, a template (GitHub, GitLab, Demo), or an .osadapter file. */
@Composable
fun NewWorkspaceDialog(onDismiss: () -> Unit, onImport: () -> Unit, onPaste: () -> Unit, onCreated: (WorkspaceAdapter) -> Unit) {
    val c = Theme.c
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(CreateStep.Pick) }
    fun create(a: WorkspaceAdapter) {
        AdapterStore.upsert(a)
        onCreated(a)
    }
    fun fromTemplate(key: String, baseUrl: String? = null) {
        val t = WorkspaceTemplates.load(ctx, key) ?: return
        create(WorkspaceTemplates.instantiate(t, AdapterStore.adapters.value, baseUrl ?: t.baseUrl))
    }

    DuoDialog(onDismiss) {
        when (step) {
            CreateStep.Pick -> {
                DialogTitle("New Workspace", "A dashboard for an API you use. Your token stays on this phone.")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceRow("Build with AI", false, "quote", Tint.Purple.color()) { step = CreateStep.Ai }
                    ChoiceRow("Start blank, for your own API", false, "plus", Tint.Blue.color()) { step = CreateStep.Blank }
                    ChoiceRow("GitHub", false, "braces", Tint.Gray.color()) { fromTemplate("github") }
                    ChoiceRow("GitLab", false, "braces", Tint.Orange.color()) { step = CreateStep.GitLab }
                    ChoiceRow("Demo with sample data", false, "layers", Tint.Indigo.color()) { fromTemplate("demo") }
                    ChoiceRow("Paste a workspace", false, "copy", Tint.Gray.color(), onPaste)
                    ChoiceRow("Import an .osadapter file", false, "down", Tint.Gray.color(), onImport)
                }
            }
            CreateStep.Ai -> AiStep(onPaste = onPaste, onBack = { step = CreateStep.Pick })
            CreateStep.GitLab -> {
                var host by remember { mutableStateOf("https://gitlab.com") }
                val base = host.trim().trimEnd('/').removeSuffix("/api/v4") + "/api/v4"
                val ok = WorkspaceTemplates.isValidBaseUrl(base)
                DialogTitle("GitLab", "Use gitlab.com, or your company's own GitLab address.")
                Text("GitLab address", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Field(host, { host = it }, "https://gitlab.example.com", autofocus = false)
                if (!ok && host.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Start with https:// and include the host.", style = Theme.t.small, color = c.danger)
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Add GitLab", Modifier.fillMaxWidth()) { if (ok) fromTemplate("gitlab", base) }
                Spacer(Modifier.height(8.dp))
                QuietButton("Back", Modifier.fillMaxWidth()) { step = CreateStep.Pick }
            }
            CreateStep.Blank -> {
                var name by remember { mutableStateOf("") }
                var url by remember { mutableStateOf("https://") }
                var auth by remember { mutableStateOf(AuthKind.BEARER) }
                var tried by remember { mutableStateOf(false) }
                val urlOk = WorkspaceTemplates.isValidBaseUrl(url)
                DialogTitle("Blank Workspace", "Name it and point it at your API. You'll add tabs and widgets next.")
                Text("Name", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Field(name, { name = it }, "Team tracker", autofocus = false)
                Spacer(Modifier.height(12.dp))
                Text("API address", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Field(url, { url = it }, "https://api.example.com/v1", autofocus = false)
                if (tried && !urlOk) {
                    Spacer(Modifier.height(6.dp))
                    Text("Use an address that starts with https:// and has a host.", style = Theme.t.small, color = c.danger)
                }
                Spacer(Modifier.height(12.dp))
                Text("Sign-in", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChoiceRow("Bearer token", auth == AuthKind.BEARER) { auth = AuthKind.BEARER }
                    ChoiceRow("PRIVATE-TOKEN header", auth == AuthKind.PRIVATE_TOKEN) { auth = AuthKind.PRIVATE_TOKEN }
                    ChoiceRow("No sign-in", auth == AuthKind.NONE) { auth = AuthKind.NONE }
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Create Workspace", Modifier.fillMaxWidth()) {
                    tried = true
                    if (name.isNotBlank() && urlOk) create(WorkspaceTemplates.blank(name, url, auth, AdapterStore.adapters.value))
                }
                if (tried && name.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Give the workspace a name.", style = Theme.t.small, color = c.danger)
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
        ChoiceRow("Copy prompt for any AI chat", false, "copy", Tint.Gray.color()) { copy(WorkspacePrompt.chat(), "chat") }
        ChoiceRow("Copy prompt for an AI agent", false, "braces", Tint.Indigo.color()) { copy(WorkspacePrompt.agent(), "agent") }
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
