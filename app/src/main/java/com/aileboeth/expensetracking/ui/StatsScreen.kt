package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.aileboeth.expensetracking.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Today
import androidx.compose.ui.draw.clip
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.InsightCalc
import com.aileboeth.expensetracking.data.Insights
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun MonthBar(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton({ onChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month)) }
        Text(Labels.yearMonth(LocalContext.current, month), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        IconButton({ onChange(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month)) }
        Spacer(Modifier.weight(1f))
        if (month != YearMonth.now()) TextButton({ onChange(YearMonth.now()) }) { Text(stringResource(R.string.this_month)) }
    }
}

private const val NEW = "new"

/** "▲12%" / "▼5%" / new, shown with [changeText]. */
private fun change(cur: Long, prev: Long): String = when {
    prev == 0L && cur == 0L -> ""
    prev == 0L -> NEW
    else -> {
        val pct = (cur - prev) * 100 / prev
        if (pct >= 0) "▲$pct%" else "▼${-pct}%"
    }
}

@Composable
private fun changeText(cur: Long, prev: Long): String = change(cur, prev).let { if (it == NEW) stringResource(R.string.stat_new) else it }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(padding: PaddingValues) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var selectedDay by remember(month) { mutableStateOf<Int?>(null) }
    var span by remember { mutableIntStateOf(6) }
    var trendSel by remember(span, month) { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Tx?>(null) }

    val range = monthRange(month)
    val prevMonth = month.minusMonths(1)
    val prevRange = monthRange(prevMonth)
    val txs = repoQuery(month, initial = emptyList<Tx>()) { Repo.listRange(range.first, range.second) }
    val prevTxs = repoQuery(month, initial = emptyList<Tx>()) { Repo.listRange(prevRange.first, prevRange.second) }
    val trendFirst = month.minusMonths((span - 1).toLong())
    val trendTxs = repoQuery(month, span, initial = emptyList<Tx>()) { Repo.listRange(monthRange(trendFirst).first, range.second) }

    val color = if (type == TxType.EXPENSE) ExpenseRed else IncomeGreen
    val picked = txs.filter { it.type == type }
    val total = picked.sumOf { it.amountCents ?: 0 }
    val cur = remember(txs, type, month) { dailyTotals(txs, month, type) }
    val prev = remember(prevTxs, type, month) { dailyTotals(prevTxs, prevMonth, type).map { it ?: 0L } }
    val elapsed = cur.count { it != null }
    val prevSame = prev.take(elapsed).sum()

    val byCategory = picked.groupBy { it.category.ifEmpty { "未分类" } }
    val groups = byCategory
        .map { (name, list) -> Triple(name, list.sumOf { it.amountCents ?: 0 }, list.size) }
        .sortedByDescending { it.second }
    var expanded by remember(month, type) { mutableStateOf<String?>(null) }
    val insights = remember(txs, prevTxs, month) { InsightCalc.compute(txs, prevTxs, month) }
    val prevGroups = prevTxs.filter { it.type == type }.groupBy { it.category.ifEmpty { "未分类" } }
        .mapValues { (_, l) -> l.sumOf { it.amountCents ?: 0 } }
    val heat = picked.groupBy { localDate(it.time).dayOfMonth }.mapValues { (_, l) -> l.sumOf { it.amountCents ?: 0 } }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonthBar(month) { month = it } }
        insights?.let { ins -> item { InsightCard(month, ins) { editing = it } } }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.expense)) }
                SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.income)) }
            }
        }

        // 1. daily line
        item {
            SoftCard {
                SectionTitle(stringResource(R.string.stat_daily))
                Text(Money.money(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
                val diff = total - prevSame
                Text(
                    when {
                        elapsed == 0 -> stringResource(R.string.stat_not_started)
                        prevSame == 0L -> stringResource(R.string.stat_no_prev)
                        diff >= 0 -> stringResource(if (type == TxType.EXPENSE) R.string.stat_more_spent else R.string.stat_more_earned, Money.money(diff), changeText(total, prevSame))
                        else -> stringResource(if (type == TxType.EXPENSE) R.string.stat_less_spent else R.string.stat_less_earned, Money.money(-diff), changeText(total, prevSame))
                    } + if (elapsed > 0) " · " + stringResource(R.string.stat_daily_avg, Money.money(total / elapsed)) else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                DailyLineChart(cur, prev, color, selectedDay?.minus(1), { selectedDay = it + 1 })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(color, stringResource(R.string.stat_this_month))
                    Spacer(Modifier.width(16.dp))
                    Legend(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), stringResource(R.string.stat_last_month))
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.stat_tap_day), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 2. calendar
        item {
            SoftCard {
                SectionTitle(stringResource(if (type == TxType.EXPENSE) R.string.stat_cal_expense else R.string.stat_cal_income))
                Spacer(Modifier.height(8.dp))
                CalendarHeatmap(month, heat, color, selectedDay) { selectedDay = if (selectedDay == it) null else it }
            }
        }

        // 3. the picked day
        selectedDay?.let { day ->
            val list = txs.filter { localDate(it.time).dayOfMonth == day }
            item {
                SoftCard(padding = 12.dp) {
                    val d = month.atDay(day)
                    val ex = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
                    val inc = list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Labels.monthDay(LocalContext.current, d), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.day_out, Money.format(ex)) + "  " + stringResource(R.string.day_in, Money.format(inc)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (list.isEmpty()) Text(stringResource(R.string.stat_day_empty), Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { TxRow(it) { editing = it } }
                }
            }
        }

        // 4. categories
        item {
            SoftCard {
                SectionTitle(stringResource(R.string.stat_categories)) {
                    if (groups.isNotEmpty()) Text(stringResource(R.string.stat_tap_category), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.stat_no_records), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        DonutChart(
                            groups.map { it.second to Color(CategoryStyle.of(it.first).color) },
                            stringResource(if (type == TxType.EXPENSE) R.string.stat_total_expense else R.string.stat_total_income),
                            Money.sym + shortYuan(total),
                        )
                    }
                    groups.forEach { (name, cents, count) ->
                        val share = if (total > 0) cents.toFloat() / total else 0f
                        val ch = change(cents, prevGroups[name] ?: 0L)
                        val up = ch.startsWith("▲") || ch == NEW
                        val chColor = when {
                            ch.isEmpty() -> Color.Unspecified
                            (type == TxType.EXPENSE) == up -> ExpenseRed
                            else -> IncomeGreen
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).clickable { expanded = if (expanded == name) null else name }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryBadge(name, 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(catName(name), fontWeight = FontWeight.Medium)
                                    Text("  %.1f%% · ".format(share * 100) + pluralStringResource(R.plurals.n_entries, count, count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.weight(1f))
                                    Text(Money.money(cents), fontWeight = FontWeight.SemiBold)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LinearProgressIndicator(
                                        progress = { share },
                                        color = Color(CategoryStyle.of(name).color),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeCap = StrokeCap.Round,
                                        gapSize = 0.dp,
                                        drawStopIndicator = {},
                                        modifier = Modifier.weight(1f).height(6.dp),
                                    )
                                    if (ch.isNotEmpty()) Text("  " + stringResource(R.string.stat_vs_last, changeText(cents, prevGroups[name] ?: 0L)), style = MaterialTheme.typography.labelSmall, color = chColor)
                                }
                            }
                        }
                        if (expanded == name) NoteBreakdown(byCategory[name].orEmpty(), cents, Color(CategoryStyle.of(name).color))
                    }
                }
            }
        }

        // 5. months trend
        item {
            val months = (0 until span).map { trendFirst.plusMonths(it.toLong()) }
            val exp = months.map { m -> trendTxs.filter { it.type == TxType.EXPENSE && sameMonth(it, m) }.sumOf { it.amountCents ?: 0 } }
            val inc = months.map { m -> trendTxs.filter { it.type == TxType.INCOME && sameMonth(it, m) }.sumOf { it.amountCents ?: 0 } }
            SoftCard {
                SectionTitle(stringResource(R.string.stat_last_n_months, span)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(span == 6, { span = 6 }, label = { Text(stringResource(R.string.stat_n_months, 6)) })
                        FilterChip(span == 12, { span = 12 }, label = { Text(stringResource(R.string.stat_n_months, 12)) })
                    }
                }
                TrendChart(months.map { Labels.monthShort(LocalContext.current, it) }, exp, inc, trendSel, { trendSel = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(ExpenseRed, stringResource(R.string.expense))
                    Spacer(Modifier.width(16.dp))
                    Legend(IncomeGreen, stringResource(R.string.income))
                }
                Spacer(Modifier.height(8.dp))
                val active = months.indices.filter { exp[it] > 0 || inc[it] > 0 }
                if (active.isNotEmpty()) {
                    val avgE = active.sumOf { exp[it] } / active.size
                    val avgI = active.sumOf { inc[it] } / active.size
                    val most = active.maxBy { exp[it] }
                    Text(
                        stringResource(R.string.stat_trend_summary, Money.money(avgE), Money.money(avgI), Labels.monthShort(LocalContext.current, months[most]), Money.money(exp[most])),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    editing?.let { tx -> TxEditor(initial = tx, onDone = { editing = null }) }
}

/** Under an expanded category: how much went to each note (午饭 / 奶茶 / …). */
@Composable
private fun NoteBreakdown(list: List<Tx>, total: Long, color: Color) {
    val rows = remember(list) { InsightCalc.byNote(list) }
    Column(
        Modifier.fillMaxWidth().padding(start = 46.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val shown = rows.take(8)
        shown.forEach { (note, n, sum) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color, CircleShape))
                Text(
                    "  " + note.ifEmpty { stringResource(R.string.stat_no_note) }, style = MaterialTheme.typography.bodyMedium,
                    color = if (note.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, modifier = Modifier.weight(1f),
                )
                Text(pluralStringResource(R.plurals.n_entries, n, n) + "  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.money(sum), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "  " + if (total > 0) "${sum * 100 / total}%" else "", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp),
                )
            }
        }
        if (rows.size > shown.size) Text(
            stringResource(R.string.stat_more_notes, rows.size - shown.size), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
        )
        if (rows.size == 1 && rows[0].first.isEmpty()) Text(
            stringResource(R.string.stat_note_tip), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 本月洞察: a few sentences about this month's spending. Tap the biggest expense to open it. */
@Composable
private fun InsightCard(month: YearMonth, ins: Insights, onOpen: (Tx) -> Unit) {
    SoftCard(padding = 4.dp) {
        Text(
            if (month == YearMonth.now()) stringResource(R.string.ins_title_now) else stringResource(R.string.ins_title_month, Labels.monthShort(LocalContext.current, month)),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp),
        )
        val lines = buildList<@Composable () -> Unit> {
            if (ins.prevSame > 0) {
                val diff = ins.total - ins.prevSame
                val pct = diff * 100 / ins.prevSame
                add {
                    InsightLine(
                        if (diff >= 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                        if (diff >= 0) ExpenseRed else IncomeGreen,
                        stringResource(if (diff >= 0) R.string.ins_more else R.string.ins_less, Money.money(kotlin.math.abs(diff))),
                        stringResource(R.string.ins_prev, ins.days, Money.money(ins.prevSame), (if (pct >= 0) "▲" else "▼") + kotlin.math.abs(pct) + "%"),
                    )
                }
            }
            add {
                InsightLine(
                    Icons.Filled.Today, Tint.Blue, stringResource(R.string.stat_daily_avg, Money.money(ins.dailyAvg)),
                    stringResource(if (month == YearMonth.now()) R.string.ins_days_so_far else R.string.ins_days_month, ins.days, Money.money(ins.total)),
                )
            }
            ins.topGrowth?.let { (cat, now, then) ->
                add {
                    InsightLine(
                        Icons.Filled.LocalFireDepartment, Tint.Orange, stringResource(R.string.ins_growth, catName(cat)),
                        if (then == 0L) stringResource(R.string.ins_growth_new, Money.money(now)) else stringResource(R.string.ins_growth_more, Money.money(now - then), Money.money(then)),
                    )
                }
            }
            ins.biggest?.let { t ->
                val d = localDate(t.time)
                add {
                    InsightLine(
                        Icons.Filled.Payments, Tint.Purple, stringResource(R.string.ins_biggest, Money.money(t.amountCents ?: 0)),
                        listOf(catName(t.category), t.note, Labels.monthDay(LocalContext.current, d)).filter { it.isNotBlank() }.joinToString(" · "),
                    ) { onOpen(t) }
                }
            }
            ins.topNote?.let { (note, n, sum) ->
                add { InsightLine(Icons.Filled.EditNote, Tint.Teal, stringResource(R.string.ins_top_note, note), stringResource(R.string.ins_top_note_sub, n, Money.money(sum))) }
            }
        }
        lines.forEachIndexed { i, line ->
            line()
            if (i != lines.lastIndex) RowDivider()
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun InsightLine(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, sub: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, tint, 34)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun sameMonth(t: Tx, m: YearMonth): Boolean {
    val d: LocalDate = localDate(t.time)
    return d.year == m.year && d.monthValue == m.monthValue
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
