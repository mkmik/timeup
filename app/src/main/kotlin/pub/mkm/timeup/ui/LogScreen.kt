package pub.mkm.timeup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pub.mkm.timeup.domain.Priority
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "Log past time": minutes already spent on a priority, ending at a chosen time (default now). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    priorities: List<Priority>,
    preselectedId: String?,
    onLog: (priorityId: String, minutes: Int, endAt: Long) -> Unit,
    onBack: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(preselectedId ?: priorities.firstOrNull()?.id) }
    var minutesText by rememberSaveable { mutableStateOf("30") }
    var endTime by rememberSaveable { mutableStateOf<String?>(null) } // "HH:mm" or null = now
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val minutes = minutesText.trim().toIntOrNull() ?: 0
    val valid = selected != null && minutes in 1..1440

    fun endAtMillis(): Long {
        val zone = ZoneId.systemDefault()
        val t = endTime ?: return System.currentTimeMillis()
        val now = LocalDateTime.now(zone)
        var candidate = now.toLocalDate().atTime(LocalTime.parse(t))
        if (candidate.isAfter(now)) candidate = candidate.minusDays(1)
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log past time") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column {
                SectionTitle("Priority")
                for (p in priorities) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selected = p.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == p.id, onClick = { selected = p.id })
                        Box(Modifier.size(14.dp).background(Color(p.color), CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(p.name)
                    }
                }
            }

            Column {
                SectionTitle("Minutes")
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(4) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in listOf(5, 15, 30, 60)) {
                        FilterChip(selected = minutes == m, onClick = { minutesText = m.toString() }, label = { Text("${m}m") })
                    }
                }
            }

            Column {
                SectionTitle("Ended at")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = endTime == null, onClick = { endTime = null }, label = { Text("Now") })
                    OutlinedButton(onClick = { showPicker = true }) { Text(endTime ?: "Pick a time…") }
                }
                Text("Counts toward the day it overlaps; a span across midnight is split.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Button(
                onClick = { onLog(selected!!, minutes, endAtMillis()) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log $minutes minutes") }
        }
    }

    if (showPicker) {
        val initial = endTime?.let(LocalTime::parse) ?: LocalTime.now()
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Ended at") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    endTime = LocalTime.of(state.hour, state.minute).format(DateTimeFormatter.ofPattern("HH:mm"))
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        )
    }
}
