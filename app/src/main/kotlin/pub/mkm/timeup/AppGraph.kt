package pub.mkm.timeup

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pub.mkm.timeup.alarm.AlarmGate
import pub.mkm.timeup.alarm.Notifier
import pub.mkm.timeup.alarm.Scheduler
import pub.mkm.timeup.data.Actions
import pub.mkm.timeup.data.Repository
import pub.mkm.timeup.db.Database
import pub.mkm.timeup.domain.DayView
import pub.mkm.timeup.domain.Days
import pub.mkm.timeup.domain.State
import pub.mkm.timeup.export.Exporter
import java.time.ZoneId

/** Hand-rolled dependency graph; one instance per process, owned by [TimeUpApp]. */
class AppGraph(val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)
    val database = Database.open(context)
    val repo = Repository(database)
    val notifier = Notifier(context)
    val alarmGate = AlarmGate(context)
    val scheduler = Scheduler(context, repo, notifier, alarmGate, scope)
    val actions = Actions(repo, scheduler)

    fun now(): Long = System.currentTimeMillis()
    fun zone(): ZoneId = ZoneId.systemDefault()

    fun dayView(state: State = repo.state.value, now: Long = now()): DayView =
        DayView(state, Days.window(now, zone()), now)

    fun start() {
        repo.load()
        notifier.ensureChannels()
        Exporter.cleanup(context)
        scheduler.resync()
    }
}
