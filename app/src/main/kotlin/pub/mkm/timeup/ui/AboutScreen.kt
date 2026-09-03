package pub.mkm.timeup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pub.mkm.timeup.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("TimeUp ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text("A chess clock for your day. Give each priority a daily budget; only one runs at a time; a real alarm rings when a budget is spent; everything resets at local midnight.")
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Text("No account, no server, no analytics, no ads. The app does not have the INTERNET permission. Your data is an append-only event log in a SQLite file on this device; export it any time from the menu. Deleting the app deletes the data.")
            Text("Automation", style = MaterialTheme.typography.titleMedium)
            Text(
                "• Quick Settings tile: toggle (stop, or start the last priority).\n" +
                    "• Launcher shortcuts: long-press the icon for Toggle and per-priority Start.\n" +
                    "• Home screen widget: ring and list; tap a row to start or stop.\n" +
                    "• Intents for Tasker & co.: launch activity pub.mkm.timeup.control.ActionActivity with action pub.mkm.timeup.action.START / STOP / TOGGLE / LOG and extras \"priority\" (name) and \"minutes\".",
            )
            Text("Timekeeping", style = MaterialTheme.typography.titleMedium)
            Text("Nothing is counted. Remaining time is always computed from stored timestamps and the clock, so reboots and process death cannot lose time. A session that crosses midnight is split by overlap. A day is midnight to midnight in the phone's current time zone.")
            Text("Inspired by OwnTime for iPhone (StaLabs). Not affiliated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
