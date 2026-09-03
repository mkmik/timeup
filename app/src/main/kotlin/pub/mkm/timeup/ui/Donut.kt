package pub.mkm.timeup.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pub.mkm.timeup.domain.PriorityStatus

/**
 * The day donut: one segment per priority, length ∝ budget, filled ∝ spent.
 * Over-budget segments are fully filled with a darker inner edge.
 */
@Composable
fun Donut(
    statuses: List<PriorityStatus>,
    centerTop: String,
    centerBottom: String?,
    modifier: Modifier = Modifier,
) {
    val trackAlpha = if (MaterialTheme.colorScheme.background.luminanceIsDark()) 0.35f else 0.22f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 26.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val total = statuses.sumOf { it.priority.budgetMs }.coerceAtLeast(1L).toFloat()
            val gap = if (statuses.size > 1) 3f else 0f
            val available = 360f - gap * statuses.size
            var start = -90f
            for (st in statuses) {
                val sweep = available * st.priority.budgetMs / total
                val color = Color(st.priority.color)
                drawArc(color.copy(alpha = trackAlpha), start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                val frac = (st.spentMs.toFloat() / st.priority.budgetMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                if (frac > 0f) drawArc(color, start, sweep * frac, false, topLeft, arcSize, style = Stroke(stroke))
                if (st.overBudget) {
                    val inset = stroke * 0.4f
                    drawArc(
                        color.darken(),
                        start,
                        sweep,
                        false,
                        Offset(topLeft.x + inset, topLeft.y + inset),
                        Size(arcSize.width - 2 * inset, arcSize.height - 2 * inset),
                        style = Stroke(stroke * 0.2f),
                    )
                }
                start += sweep + gap
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text("still yours", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (centerBottom != null) {
                Text(
                    centerBottom,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

private fun Color.darken(): Color = Color(red * 0.55f, green * 0.55f, blue * 0.55f, alpha)
