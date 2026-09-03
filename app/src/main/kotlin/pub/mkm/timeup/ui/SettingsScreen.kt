package pub.mkm.timeup.ui

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import pub.mkm.timeup.graph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onExport: () -> Unit, onDeleteEverything: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val settings = graph.settings
    var vibrate by remember { mutableStateOf(settings.vibrate) }
    var autoStop by remember { mutableIntStateOf(settings.autoStopMinutes) }
    var soundUri by remember { mutableStateOf(settings.alarmSound) }
    var confirmDelete by remember { mutableStateOf(false) }

    val exactAlarms = rememberOnResume { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val notifications = rememberOnResume { graph.notifier.notificationsEnabled }
    val fullScreen = rememberOnResume { graph.notifier.canUseFullScreenIntent }
    val promoted = rememberOnResume { graph.notifier.canPostPromoted }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            settings.alarmSound = uri?.toString()
            soundUri = settings.alarmSound
        }
    }
    val soundTitle = remember(soundUri) {
        val uri = soundUri?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "Default alarm"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Header("Alarm")
            ListItem(
                modifier = Modifier.clickable {
                    ringtonePicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri?.toUri() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                        },
                    )
                },
                headlineContent = { Text("Alarm sound") },
                supportingContent = { Text(soundTitle) },
            )
            ListItem(
                headlineContent = { Text("Vibrate") },
                trailingContent = { Switch(checked = vibrate, onCheckedChange = { vibrate = it; settings.vibrate = it }) },
            )
            ListItem(
                headlineContent = { Text("Stop ringing after") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp)) {
                        val options = listOf(1, 2, 5)
                        options.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = autoStop == m,
                                onClick = { autoStop = m; settings.autoStopMinutes = m },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) { Text("$m min") }
                        }
                    }
                },
            )
            Text(
                "The alarm plays on the alarm stream, so silent mode does not mute it. Do Not Disturb lets alarms through by default; check your DND settings if it does not.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Header("Permissions")
            PermissionRow("Notifications", notifications, "Needed for the alarm, wrap-up and the live countdown") {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            PermissionRow("Alarms & reminders", exactAlarms, "Exact alarm at the moment the budget is spent") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
            }
            PermissionRow("Full-screen alarm", fullScreen, "Shows the alarm over the lock screen") {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()))
            }
            PermissionRow("Live Updates", promoted, "Status-bar chip and lock-screen countdown while a priority runs") {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Header("Your data")
            ListItem(
                modifier = Modifier.clickable(onClick = onExport),
                headlineContent = { Text("Export database") },
                supportingContent = { Text("The SQLite file the app runs on, via the share sheet") },
            )
            ListItem(
                modifier = Modifier.clickable { confirmDelete = true },
                headlineContent = { Text("Delete everything", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Removes all priorities and history on this device") },
            )
            Text(
                "Everything stays on this device. Android's backup may copy the database to your Google account, end-to-end encrypted with your screen lock. The app never uses the network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = { Text("All priorities and the whole event log on this device will be removed. Export first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteEverything(); onBack() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, description: String, onFix: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onFix),
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Row {
                Text(
                    if (granted) "On" else "Off",
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}
