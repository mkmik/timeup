package pub.mkm.timeup.control

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/** No UI: runs the command from the launching intent, shows a toast, and finishes. Used by shortcuts and automation apps. */
class ActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Commands.execute(this, intent?.action, intent?.extras)
        Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        finish()
    }
}
