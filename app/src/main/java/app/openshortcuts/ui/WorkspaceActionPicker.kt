package app.openshortcuts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openshortcuts.engine.AdapterEngine
import app.openshortcuts.model.AdapterParam
import app.openshortcuts.model.AdapterStore
import app.openshortcuts.model.WorkspaceAdapter
import app.openshortcuts.model.WorkspaceCall
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** "Timesheet: Clock In" for the action token in a shortcut's sentence. */
fun workspaceCallLabel(raw: String): String? {
    val call = WorkspaceCall.decode(raw) ?: return null
    val adapter = AdapterStore.get(call.adapterId) ?: return null
    val action = adapter.actionById(call.actionId) ?: return null
    return "${adapter.name}: ${action.title}"
}

/**
 * Pick a workspace action for a shortcut, then fill in the fields it needs. Text fields accept
 * shortcut variables; list fields (a project) are loaded from the workspace once and remembered by name.
 */
@Composable
fun WorkspaceActionPicker(current: String, variables: List<String>, onDone: (String) -> Unit) {
    val c = Theme.c
    val adapters by AdapterStore.adapters.collectAsStateWithLifecycle()
    val existing = remember(current) { WorkspaceCall.decode(current) }
    var picked by remember {
        mutableStateOf(existing?.let { e -> AdapterStore.get(e.adapterId)?.let { a -> a.actionById(e.actionId)?.let { a to it } } })
    }
    val choice = picked
    if (choice == null) {
        val options = adapters.flatMap { a -> a.globalActions.map { a to it } }
        if (options.isEmpty()) {
            Text("Add a workspace with actions first, in the Workspaces tab.", style = Theme.t.body, color = c.ink2)
            return
        }
        LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options, key = { (a, act) -> a.id + "/" + act.id }) { (a, act) ->
                ChoiceRow("${a.name}: ${act.title}", existing?.adapterId == a.id && existing.actionId == act.id, act.glyph, act.tint.color()) {
                    if (act.params.isEmpty()) onDone(WorkspaceCall(a.id, act.id).encode()) else picked = a to act
                }
            }
        }
        return
    }
    val (adapter, action) = choice
    val same = existing?.adapterId == adapter.id && existing.actionId == action.id
    var values by remember(action.id) {
        mutableStateOf(action.params.associate { p -> p.key to ((if (same) existing!!.params[p.key] else null) ?: p.default) })
    }
    var labels by remember(action.id) { mutableStateOf(if (same) existing!!.labels else emptyMap()) }
    var listFor by remember { mutableStateOf<AdapterParam?>(null) }

    val open = listFor
    if (open != null) {
        OptionList(adapter, open) { value, label ->
            values = values + (open.key to value)
            labels = labels + (open.key to label)
            listFor = null
        }
        return
    }

    Text("${adapter.name}: ${action.title}", style = Theme.t.label, color = c.ink)
    Spacer(Modifier.height(12.dp))
    action.params.forEach { p ->
        Text(p.label, style = Theme.t.small, color = c.ink2)
        Spacer(Modifier.height(4.dp))
        if (!p.optionsEndpoint.isNullOrBlank()) {
            ChoiceRow(labels[p.key] ?: values[p.key]?.takeIf { it.isNotBlank() } ?: p.placeholder.ifBlank { "Choose" }, false) { listFor = p }
        } else {
            Field(values[p.key].orEmpty(), { v -> values = values + (p.key to v) }, p.placeholder, autofocus = false)
            Spacer(Modifier.height(8.dp))
            VariableChips(variables) { values = values + (p.key to values[p.key].orEmpty() + it) }
        }
        Spacer(Modifier.height(12.dp))
    }
    val missing = action.params.firstOrNull { !it.optionsEndpoint.isNullOrBlank() && values[it.key].isNullOrBlank() }
    PrimaryButton(if (missing != null) "Choose ${missing.label}" else "Done", Modifier.fillMaxWidth()) {
        if (missing != null) listFor = missing
        else onDone(WorkspaceCall(adapter.id, action.id, values, labels).encode())
    }
    Spacer(Modifier.height(8.dp))
    QuietButton("Pick another action", Modifier.fillMaxWidth()) { picked = null }
}

@Composable
private fun OptionList(adapter: WorkspaceAdapter, p: AdapterParam, onPick: (String, String) -> Unit) {
    val c = Theme.c
    var options by remember(p.key) { mutableStateOf<List<Pair<String, String>>?>(null) }
    var error by remember(p.key) { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(p.key) {
        val ep = p.optionsEndpoint ?: return@LaunchedEffect
        val res = AdapterEngine.fetchEndpoints(adapter, setOf(ep))[ep]
        error = res?.exceptionOrNull()?.message
        options = AdapterEngine.extractList(res?.getOrNull(), p.optionsPath).mapNotNull { obj ->
            val v = AdapterEngine.extractString(obj, p.optionValuePath)
            val l = AdapterEngine.extractString(obj, p.optionLabelPath)
            if (v.isBlank() || l.isBlank()) null else v to l
        }
    }
    Text("Choose ${p.label}", style = Theme.t.label, color = c.ink)
    Spacer(Modifier.height(10.dp))
    val list = options
    when {
        list == null -> Text("Loading from ${adapter.name}…", style = Theme.t.body, color = c.ink2)
        list.isEmpty() -> Text(error ?: "${adapter.name} returned nothing to choose from.", style = Theme.t.body, color = c.ink2)
        else -> Column {
            Field(query, { query = it }, "Search", autofocus = false)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list.filter { query.isBlank() || it.second.contains(query, ignoreCase = true) }, key = { it.first }) { (v, l) ->
                    ChoiceRow(l, false) { onPick(v, l) }
                }
            }
        }
    }
}
