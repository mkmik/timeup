package pub.mkm.timeup.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import pub.mkm.timeup.R
import pub.mkm.timeup.domain.DayView
import pub.mkm.timeup.domain.formatHm
import pub.mkm.timeup.graph
import pub.mkm.timeup.ui.MainActivity

/** Home screen widget: the day ring, and on wide sizes the priority list with tap-to-start/stop. */
class TimeUpWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme { Content() }
        }
    }

    @Composable
    private fun Content() {
        val context = LocalContext.current
        val view = context.graph.dayView()
        val size = LocalSize.current
        val wide = size.width >= WIDE.width
        val density = context.resources.displayMetrics.density
        val ringDp = 96
        val ring = RingBitmap.draw(view.statuses, (ringDp * density).toInt(), 12 * density)

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = GlanceModifier.size(ringDp.dp), contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(ring), contentDescription = "Today's budgets", modifier = GlanceModifier.fillMaxSize())
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatHm(view.hoursStillYoursMs),
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    )
                    view.runningStatus?.let {
                        Text(
                            text = it.priority.name,
                            maxLines = 1,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                        )
                    }
                }
            }
            if (wide) {
                Spacer(GlanceModifier.width(8.dp))
                Column(modifier = GlanceModifier.fillMaxHeight().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    if (view.statuses.isEmpty()) {
                        Text("No priorities yet", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
                    }
                    for (st in view.statuses.take(5)) {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .clickable(actionRunCallback<ToggleAction>(actionParametersOf(PriorityIdKey to st.priority.id))),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                provider = ImageProvider(R.drawable.ic_stat),
                                contentDescription = null,
                                modifier = GlanceModifier.size(14.dp),
                                colorFilter = ColorFilter.tint(ColorProvider(Color(st.priority.color))),
                            )
                            Spacer(GlanceModifier.width(6.dp))
                            Text(
                                text = st.priority.name,
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = if (st.running) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                            Text(
                                text = (if (st.running) "▶ " else "") + formatHm(st.remainingMs),
                                style = TextStyle(
                                    color = if (st.overBudget) GlanceTheme.colors.error else GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
    }
}

val PriorityIdKey = ActionParameters.Key<String>("priorityId")

/** Tap on a widget row: start it, or stop it if it is the running one. */
class ToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[PriorityIdKey] ?: return
        val g = context.graph
        if (g.repo.state.value.openSession?.priorityId == id) g.actions.stop() else g.actions.start(id)
        TimeUpWidget().updateAll(context)
    }
}

class TimeUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimeUpWidget()
}
