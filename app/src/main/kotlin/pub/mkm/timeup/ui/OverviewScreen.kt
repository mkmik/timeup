package pub.mkm.timeup.ui

import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import pub.mkm.timeup.domain.PriorityStatus
import pub.mkm.timeup.domain.formatHm
import pub.mkm.timeup.domain.formatHms

/** Re-evaluates [read] every time the screen resumes (permission toggles happen in system settings). */
@Composable
fun <T> rememberOnResume(read: () -> T): T {
    var key by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { key++ }
    return remember(key) { read() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    ui: OverviewUi,
    onToggle: (String) -> Unit,
    onLog: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
    onLogAny: () -> Unit,
    onExport: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val alarmsAllowed = rememberOnResume { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TimeUp") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("New priority") }, onClick = { menu = false; onNew() })
                        DropdownMenuItem(text = { Text("Log past time") }, onClick = { menu = false; onLogAny() }, enabled = ui.statuses.isNotEmpty())
                        DropdownMenuItem(text = { Text("Export database") }, onClick = { menu = false; onExport() })
                        DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("About") }, onClick = { menu = false; onAbout() })
                    }
                },
            )
        },
        floatingActionButton = {
            if (ui.statuses.isNotEmpty()) {
                FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Add, contentDescription = "New priority") }
            }
        },
    ) { padding ->
        if (ui.statuses.isEmpty()) {
            EmptyState(onNew, Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            item {
                Donut(
                    statuses = ui.statuses,
                    centerTop = formatHm(ui.hoursStillYoursMs),
                    centerBottom = ui.running?.priority?.name,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 16.dp).height(240.dp),
                )
            }
            if (!alarmsAllowed) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Alarms are off", fontWeight = FontWeight.Bold)
                            Text("Without the exact-alarm permission the budget alarm may ring minutes late.", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                            }) { Text("Allow alarms") }
                        }
                    }
                }
            }
            items(ui.statuses, key = { it.priority.id }) { st ->
                PriorityRow(st, onToggle = { onToggle(st.priority.id) }, onLog = { onLog(st.priority.id) }, onEdit = { onEdit(st.priority.id) })
            }
        }
    }
}

@Composable
private fun EmptyState(onNew: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("A chess clock for your day", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Give each priority a daily budget. Only one clock runs at a time; an alarm rings when a budget is spent; everything resets at midnight.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNew) { Text("Create your first priority") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityRow(st: PriorityStatus, onToggle: () -> Unit, onLog: () -> Unit, onEdit: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onLog()
                SwipeToDismissBoxValue.EndToStart -> onEdit()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    val remainingColor = if (st.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log")
                Spacer(Modifier.weight(1f))
                Text("Edit")
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.Edit, contentDescription = null)
            }
        },
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onToggle),
            colors = if (st.running) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ListItemDefaults.colors(),
            leadingContent = {
                Box(Modifier.size(20.dp).background(Color(st.priority.color), CircleShape))
            },
            headlineContent = { Text(st.priority.name, fontWeight = if (st.running) FontWeight.Bold else FontWeight.Normal) },
            supportingContent = {
                Text("${formatHm(st.priority.budgetMs)} budget" + (st.priority.wrapUpMinutes?.let { " · wrap-up ${it}m" } ?: ""))
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (st.running) formatHms(st.remainingMs) else formatHm(st.remainingMs),
                        style = MaterialTheme.typography.titleMedium,
                        color = remainingColor,
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (st.running) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (st.running) "Stop" else "Start",
                        )
                    }
                }
            },
        )
    }
}
