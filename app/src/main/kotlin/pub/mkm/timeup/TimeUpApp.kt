package pub.mkm.timeup

import android.app.Application
import android.content.Context

class TimeUpApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.start()
    }
}

val Context.graph: AppGraph
    get() = (applicationContext as TimeUpApp).graph
