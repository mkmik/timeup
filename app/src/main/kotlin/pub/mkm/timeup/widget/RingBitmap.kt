package pub.mkm.timeup.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import pub.mkm.timeup.domain.PriorityStatus

/** Draws the day donut into a bitmap for surfaces that cannot use Compose Canvas (Glance widgets). */
object RingBitmap {
    fun draw(statuses: List<PriorityStatus>, sizePx: Int, strokePx: Float): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            strokeCap = Paint.Cap.BUTT
        }
        val rect = RectF(strokePx / 2, strokePx / 2, sizePx - strokePx / 2, sizePx - strokePx / 2)
        if (statuses.isEmpty()) {
            paint.color = Color.argb(60, 128, 128, 128)
            canvas.drawArc(rect, 0f, 360f, false, paint)
            return bmp
        }
        val total = statuses.sumOf { it.priority.budgetMs }.coerceAtLeast(1L).toFloat()
        val gap = if (statuses.size > 1) 4f else 0f
        val available = 360f - gap * statuses.size
        var start = -90f
        for (st in statuses) {
            val sweep = available * st.priority.budgetMs / total
            val c = st.priority.color
            paint.color = Color.argb(70, Color.red(c), Color.green(c), Color.blue(c))
            canvas.drawArc(rect, start, sweep, false, paint)
            val frac = (st.spentMs.toFloat() / st.priority.budgetMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            if (frac > 0f) {
                paint.color = c
                canvas.drawArc(rect, start, sweep * frac, false, paint)
            }
            if (st.overBudget) {
                val inner = RectF(rect).apply { inset(strokePx * 0.4f, strokePx * 0.4f) }
                paint.color = darken(c)
                paint.strokeWidth = strokePx * 0.2f
                canvas.drawArc(inner, start, sweep, false, paint)
                paint.strokeWidth = strokePx
            }
            start += sweep + gap
        }
        return bmp
    }

    private fun darken(c: Int): Int = Color.rgb((Color.red(c) * 0.55f).toInt(), (Color.green(c) * 0.55f).toInt(), (Color.blue(c) * 0.55f).toInt())
}
