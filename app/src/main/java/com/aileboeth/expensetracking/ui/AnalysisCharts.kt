package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.data.Money
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

private fun DrawScope.text(tm: TextMeasurer, s: String, x: Float, y: Float, style: TextStyle, center: Boolean = true) {
    val l = tm.measure(s, style)
    val left = if (center) x - l.size.width / 2f else x
    drawText(l, topLeft = Offset(left.coerceIn(0f, (size.width - l.size.width).coerceAtLeast(0f)), y))
}

/**
 * Cumulative spending of one month: the real line, a dashed projection to the month end, and (with a budget)
 * the budget as a horizontal line plus the even pace towards it. Tap or drag to read a day.
 */
@Composable
fun PaceChart(
    cumulative: List<Long?>,
    projected: Long,
    budget: Long,
    selected: Int?,
    onSelect: (Int) -> Unit,
    color: Color = Brand,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val small = TextStyle(fontSize = 10.sp, color = muted)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val n = cumulative.size
    fun idx(x: Float, w: Float, pad: Float) = (((x - pad) / ((w - 2 * pad) / (n - 1).coerceAtLeast(1)))).roundToInt().coerceIn(0, n - 1)
    Canvas(
        modifier.fillMaxWidth().height(200.dp)
            .pointerInput(n) { detectTapGestures { onSelect(idx(it.x, size.width.toFloat(), 12.dp.toPx())) } }
            .pointerInput(n) { detectHorizontalDragGestures { c, _ -> onSelect(idx(c.position.x, size.width.toFloat(), 12.dp.toPx())) } },
    ) {
        if (n == 0) return@Canvas
        val pad = 12.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = 20.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - top - bottom
        val last = cumulative.indexOfLast { it != null }
        val maxV = max(max(projected, budget), cumulative.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1000L) * 1.1f
        fun x(i: Int) = pad + w * i / (n - 1).coerceAtLeast(1)
        fun y(v: Long) = top + h - v / maxV * h
        for (k in 0..2) drawLine(grid, Offset(pad, top + h * k / 2f), Offset(size.width - pad, top + h * k / 2f), 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        if (budget > 0) {
            val by = y(budget)
            drawLine(ExpenseRed.copy(alpha = 0.55f), Offset(pad, by), Offset(size.width - pad, by), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
            drawLine(muted.copy(alpha = 0.35f), Offset(x(0), y(0)), Offset(x(n - 1), by), 1.2.dp.toPx())
            text(tm, ctx.getString(com.aileboeth.expensetracking.R.string.an_budget_line, Money.sym + shortYuan(budget)), size.width - pad, by - 14.dp.toPx(),
                small.copy(color = ExpenseRed), center = false)
        }
        if (last >= 0) {
            val pts = (0..last).map { Offset(x(it), y(cumulative[it]!!)) }
            val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            val fill = Path().apply { addPath(line); lineTo(pts.last().x, top + h); lineTo(pts.first().x, top + h); close() }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), color.copy(alpha = 0f)), top, top + h))
            drawPath(line, color, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
            if (last < n - 1) {
                val end = Offset(x(n - 1), y(projected))
                drawLine(color.copy(alpha = 0.6f), pts.last(), end, 2.dp.toPx(), cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                drawCircle(color.copy(alpha = 0.6f), 4.dp.toPx(), end, style = Stroke(2.dp.toPx()))
                drawCircle(color, 5.dp.toPx(), pts.last())
            }
        }
        for (i in listOf(0, 9, 19, n - 1).distinct()) text(tm, ctx.getString(com.aileboeth.expensetracking.R.string.day_n, i + 1), x(i), size.height - bottom + 5.dp.toPx(), small)
        text(tm, Money.sym + shortYuan((maxV / 1.1f).toLong()), pad, 2.dp.toPx(), small, center = false)
        selected?.takeIf { it in 0 until n }?.let { i ->
            drawLine(color.copy(alpha = 0.3f), Offset(x(i), top), Offset(x(i), top + h), 1.5.dp.toPx())
            val v = cumulative[i]
            val shown = v ?: if (last >= 0) {
                // a future day: read the projection
                val from = cumulative[last]!!
                from + (projected - from) * (i - last) / (n - 1 - last).coerceAtLeast(1)
            } else 0L
            drawCircle(surface, 5.dp.toPx(), Offset(x(i), y(shown)))
            drawCircle(color, 5.dp.toPx(), Offset(x(i), y(shown)), style = Stroke(2.dp.toPx()))
            val label = ctx.getString(com.aileboeth.expensetracking.R.string.day_n, i + 1) + "  " +
                (if (v == null) "≈" else "") + Money.money(shown)
            text(tm, label, x(i), 2.dp.toPx(), TextStyle(fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Plain vertical bars with labels under some of them; [highlight] is drawn in full colour, tap selects. */
@Composable
fun BarsChart(
    values: List<Long>,
    labels: List<String>,
    color: Color,
    highlight: Int?,
    selected: Int?,
    onSelect: (Int) -> Unit,
    height: Dp = 150.dp,
    labelEvery: Int = 1,
    valueText: (Int) -> String = { Money.sym + shortYuan(values[it]) },
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = MaterialTheme.colorScheme.surfaceVariant
    val small = TextStyle(fontSize = 10.sp, color = muted)
    val n = values.size
    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(n) { detectTapGestures { onSelect((it.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)) } },
    ) {
        if (n == 0) return@Canvas
        val top = 18.dp.toPx()
        val bottom = 18.dp.toPx()
        val h = size.height - top - bottom
        val slot = size.width / n
        val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
        val maxV = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            drawRoundRect(track, Offset(cx - bw / 2, top), Size(bw, h), CornerRadius(bw / 3))
            if (v > 0) {
                val bh = (h * v / maxV).coerceAtLeast(bw / 2)
                val c = when (i) {
                    selected -> color
                    highlight -> color
                    else -> color.copy(alpha = 0.45f)
                }
                drawRoundRect(c, Offset(cx - bw / 2, top + h - bh), Size(bw, bh), CornerRadius(bw / 3))
            }
            if (i % labelEvery == 0 || i == selected) text(tm, labels[i], cx, size.height - bottom + 4.dp.toPx(),
                if (i == selected || i == highlight) small.copy(color = color, fontWeight = FontWeight.SemiBold) else small)
        }
        val tip = selected ?: highlight
        tip?.takeIf { it in 0 until n }?.let { i ->
            text(tm, valueText(i), slot * i + slot / 2, 0f, TextStyle(fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold))
        }
    }
}

/** Expense and income of each month side by side, with the balance as a small mark. */
@Composable
fun MonthPairsChart(
    labels: List<String>,
    expense: List<Long>,
    income: List<Long>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val small = TextStyle(fontSize = 10.sp, color = muted)
    val n = labels.size
    Canvas(
        modifier.fillMaxWidth().height(180.dp)
            .pointerInput(n) { detectTapGestures { onSelect((it.x / (size.width.toFloat() / n)).toInt().coerceIn(0, n - 1)) } },
    ) {
        if (n == 0) return@Canvas
        val top = 20.dp.toPx()
        val bottom = 18.dp.toPx()
        val h = size.height - top - bottom
        val slot = size.width / n
        val bw = (slot * 0.3f).coerceAtMost(12.dp.toPx())
        val maxV = max(expense.maxOrNull() ?: 0L, income.maxOrNull() ?: 0L).coerceAtLeast(1000L) * 1.05f
        drawLine(grid, Offset(0f, top + h), Offset(size.width, top + h), 1f)
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            if (i == selected) drawRoundRect(muted.copy(alpha = 0.08f), Offset(slot * i + 2, top - 4.dp.toPx()), Size(slot - 4, h + 6.dp.toPx()), CornerRadius(8.dp.toPx()))
            val eh = h * expense[i] / maxV
            val ih = h * income[i] / maxV
            if (expense[i] > 0) drawRoundRect(ExpenseRed.copy(alpha = if (selected == null || selected == i) 1f else 0.5f),
                Offset(cx - bw - 1.dp.toPx(), top + h - eh), Size(bw, eh), CornerRadius(bw / 2.5f))
            if (income[i] > 0) drawRoundRect(IncomeGreen.copy(alpha = if (selected == null || selected == i) 1f else 0.5f),
                Offset(cx + 1.dp.toPx(), top + h - ih), Size(bw, ih), CornerRadius(bw / 2.5f))
            if (n <= 12 || i % 2 == 0) text(tm, labels[i], cx, size.height - bottom + 4.dp.toPx(), small)
        }
        text(tm, Money.sym + shortYuan((maxV / 1.05f).toLong()), 0f, 0f, small, center = false)
    }
}

/**
 * A year as little squares, one per day (weeks as columns, Monday on top); darker = more spent.
 * Tap a square to read the day.
 */
@Composable
fun YearHeatmap(
    year: Int,
    daily: LongArray,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    color: Color = Brand,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val small = TextStyle(fontSize = 9.sp, color = muted)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val jan1 = LocalDate.of(year, 1, 1)
    val lead = jan1.dayOfWeek.value - 1
    val weeks = (lead + daily.size + 6) / 7
    val nonZero = daily.filter { it > 0 }.sorted()
    // Scale against the 95th percentile so one big day doesn't wash out the rest.
    val cap = (nonZero.getOrNull((nonZero.size * 0.95).toInt().coerceAtMost(nonZero.size - 1)) ?: 1L).coerceAtLeast(1L)
    val today = LocalDate.now()
    Canvas(
        // height follows the width: 7 rows of square cells plus the month labels on top
        modifier.fillMaxWidth().aspectRatio(weeks / 9.6f).pointerInput(year) {
            detectTapGestures { o ->
                val top = 14.dp.toPx()
                val cell = (size.width / weeks.toFloat())
                val col = (o.x / cell).toInt()
                val row = ((o.y - top) / cell).toInt()
                if (row in 0..6) {
                    val i = col * 7 + row - lead
                    if (i in daily.indices) onSelect(jan1.plusDays(i.toLong()))
                }
            }
        },
    ) {
        val top = 14.dp.toPx()
        val cell = size.width / weeks
        val gap = cell * 0.18f
        for (i in daily.indices) {
            val pos = i + lead
            val col = pos / 7
            val row = pos % 7
            val d = jan1.plusDays(i.toLong())
            val v = daily[i]
            val c = when {
                d.isAfter(today) -> empty.copy(alpha = 0.4f)
                v <= 0 -> empty
                else -> color.copy(alpha = 0.2f + 0.8f * sqrt((v.toFloat() / cap).coerceAtMost(1f)))
            }
            val o = Offset(col * cell + gap / 2, top + row * cell + gap / 2)
            drawRoundRect(c, o, Size(cell - gap, cell - gap), CornerRadius(cell * 0.22f))
            if (d == selected) drawRoundRect(onSurface, o, Size(cell - gap, cell - gap), CornerRadius(cell * 0.22f), style = Stroke(1.5.dp.toPx()))
        }
        // month initials above the column where each month starts
        for (m in 1..12) {
            val pos = LocalDate.of(year, m, 1).dayOfYear - 1 + lead
            text(tm, Labels.monthNarrow(ctx, m), (pos / 7) * cell, 0f, small, center = false)
        }
    }
}
