package app.openshortcuts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.openshortcuts.model.DashboardActionRef
import app.openshortcuts.model.DashboardWidget
import app.openshortcuts.model.StatMetric
import app.openshortcuts.model.Tint
import app.openshortcuts.model.WidgetType
import java.util.UUID

@Composable
fun AddTabDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String) -> Unit,
) {
    val c = Theme.c
    var label by remember { mutableStateOf("") }
    DuoDialog(onDismiss = onDismiss) {
        DialogTitle("New Dashboard Tab", "Group related widgets into a swipeable tab.")
        Text("Tab Name", style = Theme.t.small, color = c.ink2)
        Spacer(Modifier.height(4.dp))
        BuilderInput(label, { label = it }, "e.g. Overview, Sprint, Approvals")
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Add Tab", Modifier.fillMaxWidth()) {
            val clean = label.trim().ifEmpty { "Tab" }
            onAdd(clean)
        }
        Spacer(Modifier.height(8.dp))
        QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
    }
}

@Composable
fun AddWidgetDialog(
    defaultEndpoint: String,
    onDismiss: () -> Unit,
    onAdd: (DashboardWidget) -> Unit,
) {
    val c = Theme.c
    var selectedType by remember { mutableStateOf<WidgetType?>(null) }
    var title by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf(defaultEndpoint) }
    var pathA by remember { mutableStateOf("") }
    var pathB by remember { mutableStateOf("") }
    var selectedTint by remember { mutableStateOf(Tint.Blue) }

    DuoDialog(onDismiss = onDismiss) {
        val type = selectedType
        if (type == null) {
            DialogTitle("Add Widget", "Choose a native OpenShortcuts block.")
            WidgetType.entries.forEach { wt ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .pressable(label = wt.label) {
                            selectedType = wt
                            title = wt.label
                        },
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile("layers", Tint.Blue.color(), size = 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(wt.label, style = Theme.t.label, color = c.ink)
                            Text(wt.summary, style = Theme.t.small, color = c.ink2)
                        }
                        Glyph("chevronRight", c.ink3, size = 16.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Cancel", Modifier.fillMaxWidth(), onClick = onDismiss)
        } else {
            DialogTitle(type.label, type.summary)
            Text("Title", style = Theme.t.small, color = c.ink2)
            Spacer(Modifier.height(4.dp))
            BuilderInput(title, { title = it }, type.label)
            Spacer(Modifier.height(10.dp))

            if (type != WidgetType.NOTE_CARD && type != WidgetType.ACTION_GRID) {
                Text("API Endpoint (GET)", style = Theme.t.small, color = c.ink2)
                Spacer(Modifier.height(4.dp))
                BuilderInput(endpoint, { endpoint = it }, "/endpoint")
                Spacer(Modifier.height(10.dp))
            }

            when (type) {
                WidgetType.LIST, WidgetType.GROUPED_LIST, WidgetType.BREAKDOWN -> {
                    Text("Items JSON Path (empty = root array)", style = Theme.t.small, color = c.ink2)
                    Spacer(Modifier.height(4.dp))
                    BuilderInput(pathA, { pathA = it }, "e.g. data or data[*].items[*]")
                    Spacer(Modifier.height(10.dp))
                    Text(if (type == WidgetType.LIST) "Title Field Path" else "Group By Path", style = Theme.t.small, color = c.ink2)
                    Spacer(Modifier.height(4.dp))
                    BuilderInput(pathB, { pathB = it }, if (type == WidgetType.LIST) "title" else "e.g. status")
                }
                WidgetType.PROGRESS_RING, WidgetType.TARGET_BAR -> {
                    Text("Current Value Path", style = Theme.t.small, color = c.ink2)
                    Spacer(Modifier.height(4.dp))
                    BuilderInput(pathA, { pathA = it }, "e.g. data.hours_this_week")
                    Spacer(Modifier.height(10.dp))
                    Text("Target Value Path", style = Theme.t.small, color = c.ink2)
                    Spacer(Modifier.height(4.dp))
                    BuilderInput(pathB, { pathB = it }, "e.g. data.this_week_normal_hours")
                }
                WidgetType.STAT_GRID -> {
                    Text("KPI Value Path", style = Theme.t.small, color = c.ink2)
                    Spacer(Modifier.height(4.dp))
                    BuilderInput(pathA, { pathA = it }, "e.g. data.count")
                }
                WidgetType.NOTE_CARD -> {
                    Text("Note Text", style = Theme.t.small, color = c.ink2)
                    Spacer(Modifier.height(4.dp))
                    BuilderInput(pathA, { pathA = it }, "Reminder or checklist...")
                }
                else -> {}
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Accent", style = Theme.t.small, color = c.ink2)
                TokenPill(selectedTint.name) {
                    val idx = Tint.entries.indexOf(selectedTint)
                    selectedTint = Tint.entries[(idx + 1) % Tint.entries.size]
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Add to Dashboard", Modifier.fillMaxWidth(), color = selectedTint.color()) {
                val id = UUID.randomUUID().toString().take(8)
                val widget = when (type) {
                    WidgetType.STAT_GRID -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        metrics = listOf(StatMetric(title.trim().ifEmpty { "Metric" }, pathA.trim().ifEmpty { "data" }, tint = selectedTint)),
                    )
                    WidgetType.PROGRESS_RING -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        currentPath = pathA.trim(), targetPath = pathB.trim(),
                    )
                    WidgetType.TARGET_BAR -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        metrics = listOf(StatMetric(title.trim().ifEmpty { "Progress" }, pathA.trim().ifEmpty { "data" }, targetPath = pathB.trim(), tint = selectedTint)),
                    )
                    WidgetType.LIVE_STATUS -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        activePath = pathA.trim().ifEmpty { "data.is_start_timer" },
                    )
                    WidgetType.SHARE_CARD -> DashboardWidget(id = id, type = type, title = title.trim().ifEmpty { "Ship Card" }, tint = selectedTint, glyph = "send")
                    WidgetType.ACTION_GRID -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint,
                        actionRefs = listOf(DashboardActionRef(title.trim().ifEmpty { "Run" }, "bolt", selectedTint)),
                    )
                    WidgetType.SENTENCE_BAR -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint,
                        choices = listOf("All", "Open", "Done"),
                    )
                    WidgetType.GROUPED_LIST -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        itemsPath = pathA.trim(), groupPath = pathB.trim().ifEmpty { "status" },
                    )
                    WidgetType.NOTE_CARD -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint,
                        body = pathA.trim().ifEmpty { "Tap Edit to customize this note." },
                    )
                    WidgetType.PROFILE_CARD -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim().ifEmpty { "/user" },
                    )
                    WidgetType.BAR_CHART -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim().ifEmpty { "/events?per_page=50" },
                    )
                    WidgetType.BREAKDOWN -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        itemsPath = pathA.trim(), groupPath = pathB.trim(),
                    )
                    WidgetType.HEATMAP_CHART -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim().ifEmpty { "/events?per_page=100" },
                    )
                    WidgetType.LIST -> DashboardWidget(
                        id = id, type = type, title = title.trim(), tint = selectedTint, endpoint = endpoint.trim(),
                        itemsPath = pathA.trim(), titlePath = pathB.trim().ifEmpty { "title" },
                    )
                }
                onAdd(widget)
            }
            Spacer(Modifier.height(8.dp))
            QuietButton("Back", Modifier.fillMaxWidth()) { selectedType = null }
        }
    }
}

@Composable
private fun BuilderInput(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val c = Theme.c
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.fill)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Theme.t.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = Theme.t.body, color = c.ink3)
                }
                inner()
            },
        )
    }
}
