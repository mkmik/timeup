package pub.mkm.timeup.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pub.mkm.timeup.graph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TimeUpTheme { TimeUpNavHost() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recompute and reschedule whenever the app comes to the foreground (PRD §7.5).
        lifecycleScope.launch(Dispatchers.Default) { graph.scheduler.resync() }
    }
}
