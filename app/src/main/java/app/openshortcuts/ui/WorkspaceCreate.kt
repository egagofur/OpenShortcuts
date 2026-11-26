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

private enum class CreateStep { Pick, Blank, GitLab }

/** Start a workspace: blank for your own API, a template (GitHub, GitLab, Demo), or an .osadapter file. */
@Composable
fun NewWorkspaceDialog(onDismiss: () -> Unit, onImport: () -> Unit, onCreated: (WorkspaceAdapter) -> Unit) {
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
                    ChoiceRow("Start blank, for your own API", false, "plus", Tint.Blue.color()) { step = CreateStep.Blank }
                    ChoiceRow("GitHub", false, "braces", Tint.Gray.color()) { fromTemplate("github") }
                    ChoiceRow("GitLab", false, "braces", Tint.Orange.color()) { step = CreateStep.GitLab }
                    ChoiceRow("Demo with sample data", false, "layers", Tint.Indigo.color()) { fromTemplate("demo") }
                    ChoiceRow("Import an .osadapter file", false, "down", Tint.Gray.color(), onImport)
                }
            }
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
