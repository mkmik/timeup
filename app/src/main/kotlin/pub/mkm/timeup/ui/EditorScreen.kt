package pub.mkm.timeup.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pub.mkm.timeup.domain.Priority

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    existing: Priority?,
    isFirst: Boolean,
    onSave: (name: String, color: Int, budgetSeconds: Long, wrapUpMinutes: Int?) -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var color by rememberSaveable { mutableIntStateOf(existing?.color ?: Palette.colors[5]) }
    var hours by rememberSaveable { mutableIntStateOf(existing?.let { (it.budgetSeconds / 3600).toInt() } ?: 1) }
    var minutes by rememberSaveable { mutableIntStateOf(existing?.let { ((it.budgetSeconds % 3600) / 60).toInt() } ?: 0) }
    var wrapUpOn by rememberSaveable { mutableStateOf(existing?.wrapUpMinutes != null) }
    var wrapUpMinutes by rememberSaveable { mutableIntStateOf(existing?.wrapUpMinutes ?: 5) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }

    val alarmsAllowed = rememberOnResume { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val budgetSeconds = hours * 3600L + minutes * 60L
    val valid = name.isNotBlank() && budgetSeconds in 60L..86_400L

    // First priority: ask for notifications, then for exact alarms (PRD §7.6), then leave.
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (!context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
        }
        onBack()
    }

    fun save() {
        onSave(name.trim(), color, budgetSeconds, if (wrapUpOn) wrapUpMinutes else null)
        if (isFirst) notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New priority" else "Edit priority") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { TextButton(onClick = ::save, enabled = valid) { Text("Save") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                SectionTitle("Colour")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (c in Palette.colors) {
                        val selected = c == color
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(c), CircleShape)
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = contrastOn(Color(c)))
                        }
                    }
                }
            }

            Column {
                SectionTitle("Daily budget")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stepper(label = "hours", value = hours, onChange = { hours = it }, range = 0..24, step = 1)
                    Stepper(label = "minutes", value = minutes, onChange = { minutes = it }, range = 0..55, step = 5)
                }
                if (!valid && name.isNotBlank()) {
                    Text("Budget must be between 1 minute and 24 hours", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Wrap-up notification")
                        Text("A notification a few minutes before the budget runs out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wrapUpOn, onCheckedChange = { wrapUpOn = it })
                }
                if (wrapUpOn) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (m in listOf(0, 5, 10, 15)) {
                            FilterChip(selected = wrapUpMinutes == m, onClick = { wrapUpMinutes = m }, label = { Text(if (m == 0) "at 0" else "${m}m") })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Stepper(label = "minutes before", value = wrapUpMinutes, onChange = { wrapUpMinutes = it }, range = 0..120, step = 1)
                }
            }

            if (!alarmsAllowed) {
                Column {
                    Text("Alarms are off", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Text(
                        "The budget alarm may ring minutes late. Turn on \"Alarms & reminders\" for TimeUp, or use a wrap-up at 0 minutes as a fallback.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    }) { Text("Open alarm settings") }
                }
            }

            Button(onClick = ::save, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text("Save") }

            if (existing != null) {
                OutlinedButton(onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth()) { Text("Archive priority") }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Archive ${existing?.name}?") },
            text = { Text("It disappears from today's list. Its history stays in the event log.") },
            confirmButton = { TextButton(onClick = { confirmArchive = false; onArchive() }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) {
            Icon(Icons.Default.Remove, contentDescription = "Less")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledIconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) {
            Icon(Icons.Default.Add, contentDescription = "More")
        }
    }
}
