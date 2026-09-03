package pub.mkm.timeup.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import pub.mkm.timeup.domain.Priority
import pub.mkm.timeup.graph
import pub.mkm.timeup.ui.TimeUpTheme
import pub.mkm.timeup.ui.contrastOn

/** Full-screen "Budget spent" screen, launched over the lock screen by the alarm notification. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)

        val id = intent.getStringExtra(EXTRA_PRIORITY_ID)
        val priority = id?.let { graph.repo.state.value.priorities[it] }
        if (priority == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            // Close when the service stops ringing (auto-stop, or answered from the notification).
            AlarmRingService.ringing.drop(if (AlarmRingService.ringing.value) 0 else 1).collect { if (!it) finish() }
        }

        setContent {
            TimeUpTheme {
                AlarmScreen(
                    priority = priority,
                    onStop = { answer(AlarmRingService.ACTION_STOP_SESSION, priority.id) },
                    onKeepGoing = { answer(AlarmRingService.ACTION_KEEP_GOING, priority.id) },
                )
            }
        }
    }

    private fun answer(action: String, priorityId: String) {
        startService(AlarmRingService.intent(this, action, priorityId))
        finish()
    }

    companion object {
        const val EXTRA_PRIORITY_ID = "priority_id"

        fun intent(context: Context, priorityId: String): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_PRIORITY_ID, priorityId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun AlarmScreen(priority: Priority, onStop: () -> Unit, onKeepGoing: () -> Unit) {
    val bg = Color(priority.color)
    val fg = contrastOn(bg)
    Column(
        modifier = Modifier.fillMaxSize().background(bg).safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Budget spent", style = MaterialTheme.typography.titleLarge, color = fg.copy(alpha = 0.85f))
        Spacer(Modifier.height(12.dp))
        Text(priority.name, style = MaterialTheme.typography.displayMedium, color = fg, textAlign = TextAlign.Center)
        Spacer(Modifier.height(64.dp))
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = fg, contentColor = bg),
        ) { Text("Stop ${priority.name}", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onKeepGoing,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = fg),
        ) { Text("Keep going") }
    }
}
