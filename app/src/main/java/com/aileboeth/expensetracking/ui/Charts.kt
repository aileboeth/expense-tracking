package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.data.Money
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** "1234.56" style without decimals for large numbers, used on axes and tooltips. */
fun shortYuan(cents: Long): String {
    val y = cents / 100.0
    return when {
        y >= 10000 -> "%.1f万".format(y / 10000)
        y >= 100 -> y.roundToInt().toString()
        else -> Money.format(cents)
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, x: Float, y: Float, style: TextStyle, center: Boolean = true) {
    val layout = tm.measure(text, style)
    val left = if (center) x - layout.size.width / 2f else x
    drawText(layout, topLeft = Offset(left.coerceIn(0f, size.width - layout.size.width), y))
}

private fun smoothPath(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { i, p ->
        if (i == 0) moveTo(p.x, p.y) else {
            val prev = points[i - 1]
            val dx = (p.x - prev.x) / 2f
            cubicTo(prev.x + dx, prev.y, p.x - dx, p.y, p.x, p.y)
        }
    }
}

/**
 * Daily amounts of one month. [current] has one entry per day (null = day still to come);
 * [previous] (optional) is last month, drawn dashed for comparison. Tap or drag to pick a day.
 */
@Composable
fun DailyLineChart(
    current: List<Long?>,
    previous: List<Long>?,
    color: Color,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val small = TextStyle(fontSize = 10.sp, color = muted)
    val tip = TextStyle(fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    val n = current.size

    fun indexAt(x: Float, width: Float, padPx: Float): Int {
        val step = if (n > 1) (width - 2 * padPx) / (n - 1) else 1f
        return ((x - padPx) / step).roundToInt().coerceIn(0, n - 1)
    }

    Canvas(
        modifier.fillMaxWidth().height(190.dp)
            .pointerInput(n) { detectTapGestures { o -> onSelect(indexAt(o.x, size.width.toFloat(), 10.dp.toPx())) } }
            .pointerInput(n) {
                detectHorizontalDragGestures { change, _ -> onSelect(indexAt(change.position.x, size.width.toFloat(), 10.dp.toPx())) }
            },
    ) {
        if (n == 0) return@Canvas
        val pad = 10.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = 20.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - top - bottom
        val step = if (n > 1) w / (n - 1) else w
        val maxRaw = max(current.filterNotNull().maxOrNull() ?: 0L, previous?.maxOrNull() ?: 0L).coerceAtLeast(1000L)
        val maxV = maxRaw * 1.12f
        fun x(i: Int) = pad + i * step
        fun y(v: Long) = top + h - (v / maxV) * h

        // grid + top value
        for (k in 0..2) {
            val gy = top + h * k / 2f
            drawLine(gridColor, Offset(pad, gy), Offset(size.width - pad, gy), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        label(tm, "¥" + shortYuan(maxRaw), pad, 2.dp.toPx(), small, center = false)

        previous?.let { prev ->
            val pts = prev.take(n).mapIndexed { i, v -> Offset(x(i), y(v)) }
            if (pts.size > 1) drawPath(smoothPath(pts), muted.copy(alpha = 0.45f),
                style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }

        val pts = current.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
        if (pts.isNotEmpty()) {
            val line = smoothPath(pts)
            val fill = Path().apply {
                addPath(line)
                lineTo(pts.last().x, top + h); lineTo(pts.first().x, top + h); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f)), top, top + h))
            drawPath(line, color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        }

        // day labels
        val marks = listOf(0, 7, 14, 21, n - 1).distinct().filter { it < n }
        for (i in marks) label(tm, "${i + 1}日", x(i), size.height - bottom + 5.dp.toPx(), small)

        selected?.takeIf { it in 0 until n }?.let { i ->
            val v = current[i]
            drawLine(color.copy(alpha = 0.35f), Offset(x(i), top), Offset(x(i), top + h), 1.5.dp.toPx())
            if (v != null) {
                drawCircle(surface, 6.dp.toPx(), Offset(x(i), y(v)))
                drawCircle(color, 6.dp.toPx(), Offset(x(i), y(v)), style = Stroke(2.5.dp.toPx()))
            }
            val text = "${i + 1}日  ¥" + (v?.let { Money.format(it) } ?: "-")
            label(tm, text, x(i), 2.dp.toPx(), tip)
        }
    }
}

/** Monthly totals over several months: expense and income lines. */
@Composable
fun TrendChart(
    labels: List<String>,
    expense: List<Long>,
    income: List<Long>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val small = TextStyle(fontSize = 10.sp, color = muted)
    val n = labels.size

    Canvas(
        modifier.fillMaxWidth().height(200.dp).pointerInput(n) {
            detectTapGestures { o ->
                val pad = 18.dp.toPx()
                val step = if (n > 1) (size.width - 2 * pad) / (n - 1) else 1f
                onSelect(((o.x - pad) / step).roundToInt().coerceIn(0, n - 1))
            }
        },
    ) {
        if (n == 0) return@Canvas
        val pad = 18.dp.toPx()
        val top = 26.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - top - bottom
        val step = if (n > 1) w / (n - 1) else w
        val maxRaw = max(expense.maxOrNull() ?: 0L, income.maxOrNull() ?: 0L).coerceAtLeast(1000L)
        val maxV = maxRaw * 1.12f
        fun x(i: Int) = pad + i * step
        fun y(v: Long) = top + h - (v / maxV) * h

        for (k in 0..2) {
            val gy = top + h * k / 2f
            drawLine(gridColor, Offset(pad, gy), Offset(size.width - pad, gy), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        }
        selected?.takeIf { it in 0 until n }?.let { i ->
            drawRoundRect(muted.copy(alpha = 0.08f), Offset(x(i) - step / 2.6f, top - 4.dp.toPx()),
                Size(step / 1.3f, h + 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
        }
        for ((values, c) in listOf(income to IncomeGreen, expense to ExpenseRed)) {
            val pts = values.mapIndexed { i, v -> Offset(x(i), y(v)) }
            drawPath(smoothPath(pts), c, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round))
            pts.forEach { p ->
                drawCircle(surface, 3.6.dp.toPx(), p)
                drawCircle(c, 3.6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }
        }
        labels.forEachIndexed { i, l -> label(tm, l, x(i), size.height - bottom + 5.dp.toPx(), small) }
        selected?.takeIf { it in 0 until n }?.let { i ->
            val t = "支 ${shortYuan(expense[i])}  收 ${shortYuan(income[i])}"
            label(tm, t, x(i), 2.dp.toPx(), TextStyle(fontSize = 11.sp, color = muted, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Ring chart; the middle shows [title] and [value]. */
@Composable
fun DonutChart(slices: List<Pair<Long, Color>>, title: String, value: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val total = slices.sumOf { it.first }.toFloat()
            if (total <= 0f) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                return@Canvas
            }
            var start = -90f
            val gap = if (slices.size > 1) 1.6f else 0f
            for ((v, c) in slices) {
                val sweep = v / total * 360f
                if (sweep > gap) drawArc(c, start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/** One month as a calendar; darker cells = more money. */
@Composable
fun CalendarHeatmap(
    month: YearMonth,
    totals: Map<Int, Long>,
    color: Color,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val max = (totals.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val first = month.atDay(1).dayOfWeek.value - 1 // Monday = 0
    val days = month.lengthOfMonth()
    val today = LocalDate.now()
    val cells = List(first) { 0 } + (1..days).toList()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (d in listOf("一", "二", "三", "四", "五", "六", "日")) {
                Text(d, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        for (week in cells.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (k in 0 until 7) {
                    val day = week.getOrNull(k) ?: 0
                    if (day == 0) { Spacer(Modifier.weight(1f).aspectRatio(1f)); continue }
                    val v = totals[day] ?: 0L
                    val intensity = if (v > 0) (0.18f + 0.82f * sqrt(v.toFloat() / max)) else 0f
                    val bg = if (v > 0) color.copy(alpha = intensity) else MaterialTheme.colorScheme.surfaceVariant
                    val isToday = month.year == today.year && month.monthValue == today.monthValue && day == today.dayOfMonth
                    var m = Modifier.weight(1f).aspectRatio(1f).background(bg, RoundedCornerShape(8.dp))
                    if (selected == day) m = m.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp))
                    else if (isToday) m = m.border(1.5.dp, color, RoundedCornerShape(8.dp))
                    Box(m.clickable { onSelect(day) }, contentAlignment = Alignment.Center) {
                        Text(
                            "$day", fontSize = 11.sp,
                            color = if (intensity > 0.55f) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Small line for the header card. */
@Composable
fun Sparkline(values: List<Long?>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        val pts0 = values.mapIndexedNotNull { i, v -> v?.let { i to it } }
        if (pts0.size < 2) return@Canvas
        val maxV = (pts0.maxOf { it.second }).coerceAtLeast(1L) * 1.1f
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val pts = pts0.map { (i, v) -> Offset(i * step, size.height - (v / maxV) * size.height) }
        val line = smoothPath(pts)
        val fill = Path().apply { addPath(line); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.dp.toPx(), pts.last())
    }
}
