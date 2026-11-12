package com.aileboeth.expensetracking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Analysis
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.CategoryTrend
import com.aileboeth.expensetracking.data.Forecast
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Mood
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.data.Wish
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

const val TAB_FORECAST = 0
const val TAB_HABITS = 1
const val TAB_CATEGORIES = 2
const val TAB_YEAR = 3

private val TABS = listOf(R.string.an_tab_forecast, R.string.an_tab_habits, R.string.an_tab_categories, R.string.an_tab_year)

/** The last day a month's numbers run to: today for the running month. */
private fun endOf(month: YearMonth): LocalDate =
    if (month == YearMonth.now()) LocalDate.now() else month.atEndOfMonth()

private fun inMonth(t: Tx, m: YearMonth) = YearMonth.from(localDate(t.time)) == m

private fun spentIn(txs: List<Tx>, m: YearMonth, type: TxType = TxType.EXPENSE) =
    txs.filter { it.type == type && it.status == Status.OK && inMonth(it, m) }.sumOf { it.amountCents ?: 0 }

/** "详细分析": forecast, habits, category trends and the year, as swipeable tabs. */
@Composable
fun AnalysisScreen(
    startMonth: YearMonth,
    startTab: Int = TAB_FORECAST,
    onBack: () -> Unit,
    openRecap: (YearMonth) -> Unit = {},
    openWish: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    var month by remember { mutableStateOf(startMonth) }
    val pager = rememberPagerState(startTab) { TABS.size }
    val scope = rememberCoroutineScope()
    // Thirteen months up to the month looked at: enough for the forecast, a year of habits and the trends.
    val hist = repoQuery(month, initial = null as List<Tx>?) {
        Repo.listRange(monthRange(month.minusMonths(12)).first, monthRange(month).second)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.an_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton({ month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month)) }
                Text(Labels.monthShort(ctx, month), fontWeight = FontWeight.SemiBold)
                IconButton({ if (month < YearMonth.now()) month = month.plusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month),
                        tint = if (month < YearMonth.now()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)
                }
            }
            // pill tabs; the pages below can also be swiped
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
            ) {
                TABS.forEachIndexed { i, res ->
                    val on = pager.currentPage == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(50))
                            .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(res), softWrap = false, style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                val list = hist
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (list == null) return@LazyColumn
                    when (page) {
                        TAB_FORECAST -> forecastTab(month, list, openWish)
                        TAB_HABITS -> habitsTab(month, list)
                        TAB_CATEGORIES -> categoriesTab(month, list)
                        else -> yearTab(month, list, openRecap)
                    }
                }
            }
        }
    }
}

// ---- shared bits -------------------------------------------------------------------------------

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

/** Small labelled number in a 2-column grid. */
@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

@Composable
private fun FactGrid(vararg facts: Triple<String, String, Color>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.toList().chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (l, v, c) -> Fact(l, v, Modifier.weight(1f), c) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A line with a leading emoji, for the "smart" sentences. */
@Composable
private fun Line(emoji: String, text: String, sub: String? = null) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 17.sp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Muted(sub)
        }
    }
}

@Composable
private fun Empty(text: String) {
    SoftCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.CALM, emptyList(), size = 72.dp, light = false)
            Muted(text, Modifier.padding(top = 10.dp))
        }
    }
}

// ---- 1. forecast ---------------------------------------------------------------------------------

private fun LazyListScope.forecastTab(month: YearMonth, hist: List<Tx>, openWish: () -> Unit) {
    item(key = "fc") { ForecastBody(month, hist, openWish) }
}

@Composable
private fun ForecastBody(month: YearMonth, hist: List<Tx>, openWish: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var budget by remember { mutableStateOf(prefs.budgetCents) }
    var budgetDialog by remember { mutableStateOf(false) }
    val running = month == YearMonth.now()
    val fc = remember(hist, month, budget) {
        val prev = hist.filter { val m = YearMonth.from(localDate(it.time)); m >= month.minusMonths(3) && m < month }
        Analysis.forecast(hist.filter { inMonth(it, month) }, prev, month, budget)
    }
    var sel by remember(month) { mutableStateOf<Int?>(null) }
    val wish = repoQuery(initial = null as Wish?) {
        val all = Repo.wishes()
        all.firstOrNull { it.id == prefs.focusWish && it.done == 0L } ?: all.firstOrNull { it.done == 0L }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (fc.spent == 0L && running && fc.daysPassed <= 1 && hist.none { inMonth(it, month.minusMonths(1)) }) {
            Empty(stringResource(R.string.an_fc_empty))
            return@Column
        }
        // hero
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(20.dp)) {
            Column {
                Text(stringResource(if (running) R.string.an_fc_heading else R.string.an_fc_heading_past), color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge)
                RollingMoney(if (running) fc.projected else fc.spent, androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold), Color.White)
                Text(
                    if (running) stringResource(R.string.an_fc_range, Money.money(fc.low.coerceAtLeast(fc.spent)), Money.money(fc.high), Money.money(fc.spent), fc.daysPassed, fc.daysInMonth)
                    else stringResource(R.string.an_fc_past_sub, Money.money(fc.spent / fc.daysInMonth)),
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // budget
        SoftCard {
            SectionTitle(stringResource(R.string.an_fc_budget)) {
                TextButton({ budgetDialog = true }) { Text(stringResource(if (budget > 0) R.string.an_fc_budget_change else R.string.an_fc_budget_set)) }
            }
            if (budget <= 0) Muted(stringResource(R.string.an_fc_no_budget))
            else BudgetLines(fc, running)
        }

        // pace chart
        SoftCard {
            SectionTitle(stringResource(R.string.an_fc_pace))
            Spacer(Modifier.height(4.dp))
            PaceChart(fc.cumulative, fc.projected, budget, sel, { sel = it })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(Brand, stringResource(R.string.an_fc_actual))
                if (running) LegendDot(Brand.copy(alpha = 0.5f), stringResource(R.string.an_fc_projected))
                if (budget > 0) LegendDot(ExpenseRed.copy(alpha = 0.6f), stringResource(R.string.an_fc_budget))
            }
            if (running) Muted(
                stringResource(R.string.an_fc_rate, Money.money(fc.rate)) +
                    if (fc.oneOffs > 0) stringResource(R.string.an_fc_oneoffs, Money.money(fc.oneOffs)) else "",
                Modifier.padding(top = 6.dp),
            )
        }

        // next month + wish
        val months = (1..3).map { month.minusMonths(it.toLong()) }
        val past = months.map { spentIn(hist, it) }
        val thisOne = if (running) fc.projected else fc.spent
        val weighted = listOf(thisOne to 3, past[0] to 2, past[1] to 1).filter { it.first > 0 }
        val keep = (listOf(month) + months.take(2)).map { m ->
            val inc = spentIn(hist, m, TxType.INCOME)
            val exp = if (m == month && running) fc.projected else spentIn(hist, m)
            inc - exp
        }.filter { it != 0L }
        if (weighted.size >= 2 || wish != null) SoftCard {
            SectionTitle(stringResource(R.string.an_fc_ahead))
            if (weighted.size >= 2) {
                val guess = weighted.sumOf { it.first * it.second } / weighted.sumOf { it.second }
                Line("🔮", stringResource(R.string.an_fc_next, Labels.monthShort(ctx, month.plusMonths(1)), Money.money(guess)), stringResource(R.string.an_fc_next_sub))
            }
            wish?.let { w ->
                val avgKeep = if (keep.isEmpty()) 0L else keep.sum() / keep.size
                val left = w.target - w.saved
                val n = Analysis.monthsToGoal(left, avgKeep)
                Row(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = openWish)) {
                    Line(
                        w.emoji,
                        if (n == null) stringResource(R.string.an_fc_wish_none, w.name)
                        else pluralStringResource(R.plurals.an_fc_wish, n, w.name, n),
                        if (n == null) stringResource(R.string.an_fc_wish_none_sub)
                        else stringResource(R.string.an_fc_wish_sub, Money.money(avgKeep), Money.money(left)),
                    )
                }
            }
        }
    }
    if (budgetDialog) BudgetDialog(prefs) { budgetDialog = false; budget = prefs.budgetCents }
}

@Composable
private fun BudgetLines(fc: Forecast, running: Boolean) {
    val ctx = LocalContext.current
    val b = fc.budget
    val end = if (running) fc.projected else fc.spent
    val diff = b - end
    val ratio = (fc.spent.toFloat() / b).coerceIn(0f, 1f)
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // used part, with the expected end as a lighter extension
        Canvas(Modifier.weight(1f).height(12.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color(0x22888888), Offset.Zero, size, r)
            val pe = (end.toFloat() / b).coerceIn(0f, 1f)
            if (running) drawRoundRect((if (diff < 0) ExpenseRed else Brand).copy(alpha = 0.28f), Offset.Zero, Size(size.width * pe, size.height), r)
            drawRoundRect(if (fc.spent > b) ExpenseRed else Brand, Offset.Zero, Size(size.width * ratio, size.height), r)
        }
        Text("  ${fc.spent * 100 / b}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
    Muted(stringResource(R.string.an_fc_budget_of, Money.money(fc.spent), Money.money(b)))
    Spacer(Modifier.height(6.dp))
    if (diff >= 0) Line("🟢", stringResource(if (running) R.string.an_fc_keep else R.string.an_fc_kept, Money.money(diff)))
    else Line("🔴", stringResource(if (running) R.string.an_fc_over else R.string.an_fc_was_over, Money.money(-diff)))
    if (running) {
        val left = fc.daysInMonth - fc.daysPassed
        val safe = fc.safeDaily ?: 0
        if (safe > 0 && left > 0) Line("🗓️", stringResource(R.string.an_fc_safe, Money.money(safe)), pluralStringResource(R.plurals.an_fc_days_left, left, left))
        else if (fc.spent >= b) Line("🫣", stringResource(R.string.an_fc_used_up))
        fc.runOutDay?.takeIf { fc.spent < b }?.let { d ->
            Line("⏳", stringResource(R.string.an_fc_run_out, Labels.monthDay(ctx, YearMonth.now().atDay(d))), stringResource(R.string.an_fc_run_out_sub, Money.money(fc.rate)))
        }
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text("  $text", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- 2. habits -------------------------------------------------------------------------------------

private fun LazyListScope.habitsTab(month: YearMonth, hist: List<Tx>) {
    item(key = "hb") { HabitsBody(month, hist) }
}

private val WINDOWS = listOf(30, 90, 365)

@Composable
private fun HabitsBody(month: YearMonth, hist: List<Tx>) {
    val ctx = LocalContext.current
    var window by remember { mutableIntStateOf(90) }
    val end = endOf(month)
    val from = end.minusDays(window - 1L)
    val h = remember(hist, window, month) { Analysis.habits(hist, from, end) }
    var wdSel by remember(window, month) { mutableStateOf<Int?>(null) }
    var hrSel by remember(window, month) { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface).padding(3.dp),
        ) {
            for (w in WINDOWS) {
                val on = w == window
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) Brand else Color.Transparent)
                        .clickable { window = w }.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (w == 365) stringResource(R.string.an_hb_year) else stringResource(R.string.an_hb_days, w),
                        color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (h.count == 0) { Empty(stringResource(R.string.an_hb_empty)); return@Column }

        // rhythm
        SoftCard {
            SectionTitle(stringResource(R.string.an_hb_rhythm))
            Spacer(Modifier.height(8.dp))
            FactGrid(
                Triple(stringResource(R.string.an_hb_per_day), "%.1f".format(h.perDay), Color.Unspecified),
                Triple(stringResource(R.string.an_hb_total), pluralStringResource(R.plurals.n_entries, h.count, h.count), Color.Unspecified),
                Triple(stringResource(R.string.an_hb_no_spend), pluralStringResource(R.plurals.an_days, h.noSpendDays, h.noSpendDays), IncomeGreen),
                Triple(stringResource(R.string.an_hb_longest), pluralStringResource(R.plurals.an_days, h.longestNoSpend, h.longestNoSpend), IncomeGreen),
            )
        }

        // weekdays
        val top = h.weekdayAvg.indices.maxBy { h.weekdayAvg[it] }
        SoftCard {
            SectionTitle(stringResource(R.string.an_hb_weekdays))
            Muted(stringResource(R.string.an_hb_weekday_top, Labels.weekdayFull(ctx, top), Money.money(h.weekdayAvg[top])), Modifier.padding(top = 2.dp, bottom = 6.dp))
            BarsChart(
                h.weekdayAvg.toList(), (0..6).map { Labels.weekdayShort(ctx, it) }, Brand, top, wdSel, { wdSel = if (wdSel == it) null else it },
                valueText = { Money.money(h.weekdayAvg[it]) },
            )
            val we = h.weekendDaily
            val wd = h.weekdayDaily
            Line(
                "🏖️", stringResource(R.string.an_hb_weekend, Money.money(we), Money.money(wd)),
                when {
                    wd > 0 && we > wd -> stringResource(R.string.an_hb_weekend_more, ((we - wd) * 100 / wd).toInt())
                    we > 0 && wd > we -> stringResource(R.string.an_hb_weekday_more, ((wd - we) * 100 / we).toInt())
                    else -> null
                },
            )
        }

        // hours
        val peak = h.hourCount.indices.maxBy { h.hourCount[it] }
        SoftCard {
            SectionTitle(stringResource(R.string.an_hb_hours))
            Muted(stringResource(R.string.an_hb_hour_top, peak), Modifier.padding(top = 2.dp, bottom = 6.dp))
            BarsChart(
                h.hourCount.map { it.toLong() }, (0..23).map { "$it" }, Tint.Teal, peak, hrSel, { hrSel = if (hrSel == it) null else it },
                height = 130.dp, labelEvery = 3,
                valueText = { stringResource0(ctx, R.string.an_hb_hour_tip, it, h.hourCount[it], Money.money(h.hourSum[it])) },
            )
            val night = (listOf(22, 23) + (0..4)).sumOf { h.hourCount[it] }
            val meal = (11..13).sumOf { h.hourCount[it] }
            if (night > 0) Line("🦉", pluralStringResource(R.plurals.an_hb_night, night, night))
            if (meal > 0) Line("🍱", stringResource(R.string.an_hb_noon, meal * 100 / h.count))
        }

        // amounts
        SoftCard {
            SectionTitle(stringResource(R.string.an_hb_amounts))
            Muted(stringResource(R.string.an_hb_median, Money.money(h.medianAmount)), Modifier.padding(top = 2.dp, bottom = 8.dp))
            val maxN = (h.bucketCount.maxOrNull() ?: 1).coerceAtLeast(1)
            val bounds = Analysis.BUCKETS
            h.bucketCount.forEachIndexed { i, n ->
                val label = when (i) {
                    0 -> stringResource(R.string.an_hb_below, Money.sym + Money.short(bounds[0]))
                    bounds.size -> stringResource(R.string.an_hb_above, Money.sym + Money.short(bounds.last()))
                    else -> Money.sym + Money.short(bounds[i - 1]) + "–" + Money.short(bounds[i])
                }
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(14.dp)) {
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                        if (n > 0) Box(Modifier.fillMaxWidth(n.toFloat() / maxN).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Tint.Orange.copy(alpha = 0.35f + 0.65f * n / maxN)))
                    }
                    Text(
                        "  " + pluralStringResource(R.plurals.n_entries, n, n), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp), maxLines = 1,
                    )
                    Text(Money.sym + shortYuan(h.bucketSum[i]), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(58.dp), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            val small = h.bucketCount[0] + h.bucketCount[1]
            if (small > 0) Line("👛", stringResource(R.string.an_hb_small_share, small * 100 / h.count, Money.sym + Money.short(bounds[1])),
                stringResource(R.string.an_hb_small_sum, Money.money(h.bucketSum[0] + h.bucketSum[1])))
        }
    }
}

/** stringResource for use inside non-composable lambdas. */
private fun stringResource0(ctx: android.content.Context, id: Int, vararg args: Any): String = ctx.getString(id, *args)

// ---- 3. categories -------------------------------------------------------------------------------

private fun LazyListScope.categoriesTab(month: YearMonth, hist: List<Tx>) {
    item(key = "ct") { CategoriesBody(month, hist) }
}

@Composable
private fun CategoriesBody(month: YearMonth, hist: List<Tx>) {
    val ctx = LocalContext.current
    val trends = remember(hist, month) { Analysis.categoryTrends(hist, month) }
    val odd = remember(trends) { Analysis.anomalies(trends) }
    var open by remember(month) { mutableStateOf<String?>(null) }
    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (trends.isEmpty()) { Empty(stringResource(R.string.an_ct_empty)); return@Column }
        SoftCard {
            SectionTitle(stringResource(R.string.an_ct_alerts))
            if (month == YearMonth.now()) Muted(stringResource(R.string.an_ct_paced_note), Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(6.dp))
            when {
                trends.all { it.usual == 0L } -> Line("🌱", stringResource(R.string.an_ct_no_history))
                odd.isEmpty() -> Line("👍", stringResource(R.string.an_ct_all_normal))
                else -> odd.take(5).forEach { t ->
                    val up = t.paced > t.usual
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(t.category, 34)
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                if (up) stringResource(R.string.an_ct_up, catName(t.category), "%.1f".format(t.ratio))
                                else stringResource(R.string.an_ct_down, catName(t.category), ((1 - t.ratio) * 100).toInt()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Muted(stringResource(R.string.an_ct_vs, Money.money(t.paced), Money.money(t.usual)))
                        }
                        Text(
                            if (up) "▲" else "▼", color = if (up) ExpenseRed else IncomeGreen, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        SoftCard(padding = 12.dp) {
            SectionTitle(stringResource(R.string.an_ct_six), Modifier.padding(start = 4.dp, top = 4.dp)) {
                Muted(stringResource(R.string.an_ct_tap))
            }
            Spacer(Modifier.height(4.dp))
            trends.forEach { t ->
                val color = Color(CategoryStyle.of(t.category).color)
                Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { open = if (open == t.category) null else t.category }.animateContentSize().padding(4.dp)) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(t.category, 34)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(catName(t.category), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted(if (t.usual > 0) stringResource(R.string.an_ct_usual, Money.money(t.usual)) else stringResource(R.string.an_ct_new))
                        }
                        MiniBars(t.months, color, Modifier.size(width = 66.dp, height = 26.dp))
                        Text(Money.money(t.months.last()), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(84.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
                    }
                    if (open == t.category) {
                        var sel by remember { mutableStateOf<Int?>(5) }
                        BarsChart(t.months, months.map { Labels.monthShort(ctx, it) }, color, 5, sel, { sel = it }, height = 130.dp,
                            valueText = { Money.money(t.months[it]) }, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                    }
                }
            }
        }
    }
}

/** Six tiny bars; the last (this month) in full colour. */
@Composable
private fun MiniBars(values: List<Long>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val slot = size.width / values.size
        val bw = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = if (v > 0) (size.height * v / max).coerceAtLeast(3.dp.toPx()) else 2.dp.toPx()
            drawRoundRect(
                if (i == values.lastIndex) color else color.copy(alpha = if (v > 0) 0.35f else 0.15f),
                Offset(slot * i + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 3),
            )
        }
    }
}

// ---- 4. year -------------------------------------------------------------------------------------

private fun LazyListScope.yearTab(month: YearMonth, hist: List<Tx>, openRecap: (YearMonth) -> Unit) {
    item(key = "yr") { YearBody(month, hist, openRecap) }
}

@Composable
private fun YearBody(month: YearMonth, hist: List<Tx>, openRecap: (YearMonth) -> Unit) {
    val ctx = LocalContext.current
    var year by remember(month) { mutableIntStateOf(month.year) }
    val zone = ZoneId.systemDefault()
    val view = repoQuery(year, initial = null as com.aileboeth.expensetracking.data.YearView?) {
        val a = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val b = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        Analysis.year(Repo.listRange(a, b), year)
    }
    var day by remember(year) { mutableStateOf<LocalDate?>(null) }
    var mSel by remember(year) { mutableStateOf<Int?>(null) }
    val today = LocalDate.now()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val v = view ?: return@Column
        // summary
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 18.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Color.White) }
                    Text(stringResource(R.string.an_yr_title, year), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton({ if (year < today.year) year++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White.copy(alpha = if (year < today.year) 1f else 0.3f))
                    }
                }
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.an_yr_spent), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    RollingMoney(v.expense, androidx.compose.ui.text.TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold), Color.White)
                    // only worth saying when something was actually kept
                    val rate = if (v.income > 0 && v.income > v.expense) ((v.income - v.expense) * 100 / v.income).toInt() else null
                    Text(
                        stringResource(R.string.an_yr_line, Money.money(v.income), Money.money(v.income - v.expense)) +
                            (rate?.let { "  ·  " + stringResource(R.string.an_yr_rate, it) } ?: ""),
                        color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                    )
                    Text(pluralStringResource(R.plurals.an_yr_active, v.activeDays, v.activeDays), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (v.expense == 0L && v.income == 0L) { Empty(stringResource(R.string.an_yr_empty, year)); return@Column }

        // heatmap
        SoftCard {
            SectionTitle(stringResource(R.string.an_yr_heat))
            Spacer(Modifier.height(8.dp))
            YearHeatmap(year, v.daily, day, { day = if (day == it) null else it })
            val d = day
            Muted(
                if (d != null) stringResource(R.string.an_yr_day, Labels.monthDay(ctx, d) + " " + Labels.weekday(ctx, d), Money.money(v.daily[d.dayOfYear - 1]))
                else stringResource(R.string.an_yr_heat_hint),
                Modifier.padding(top = 6.dp),
            )
        }

        // months
        SoftCard {
            SectionTitle(stringResource(R.string.an_yr_months))
            Spacer(Modifier.height(6.dp))
            MonthPairsChart(v.months.map { Labels.monthNarrow(ctx, it.month.monthValue) }, v.months.map { it.expense }, v.months.map { it.income }, mSel, { mSel = if (mSel == it) null else it })
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(ExpenseRed, stringResource(R.string.expense))
                LegendDot(IncomeGreen, stringResource(R.string.income))
            }
            val s = mSel?.let { v.months[it] }
            if (s != null) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Labels.yearMonth(ctx, s.month), fontWeight = FontWeight.SemiBold)
                        Muted(stringResource(R.string.an_yr_month_line, Money.money(s.expense), Money.money(s.income), Money.money(s.balance)))
                    }
                    if (s.expense > 0 && s.month <= YearMonth.now()) TextButton({ openRecap(s.month) }) { Text(stringResource(R.string.an_yr_recap)) }
                }
            } else {
                val used = v.months.filter { it.expense > 0 }
                if (used.size >= 2) {
                    val most = used.maxBy { it.expense }
                    val least = used.filter { it.month < YearMonth.now() }.minByOrNull { it.expense }
                    Line("📈", stringResource(R.string.an_yr_most, Labels.monthShort(ctx, most.month), Money.money(most.expense)))
                    if (least != null && least != most) Line("🍃", stringResource(R.string.an_yr_least, Labels.monthShort(ctx, least.month), Money.money(least.expense)))
                }
                val kept = v.months.filter { it.income > 0 && it.balance > 0 }.size
                if (kept > 0) Line("💰", pluralStringResource(R.plurals.an_yr_kept_months, kept, kept))
            }
        }

        // fun projections (last 90 days up to the month looked at)
        val end = endOf(month)
        val window = hist.filter { val d = localDate(it.time); !d.isBefore(end.minusDays(89)) && !d.isAfter(end) }
        val notes = remember(window) { Analysis.noteHabits(window, 90) }
        val small = remember(window) { Analysis.smallSpend(window, 90) }
        SoftCard {
            SectionTitle(stringResource(R.string.an_fun_title))
            Muted(stringResource(R.string.an_fun_sub), Modifier.padding(top = 2.dp, bottom = 4.dp))
            if (year == today.year && v.expense > 0) {
                val full = v.expense * today.lengthOfYear() / today.dayOfYear
                Line("📅", stringResource(R.string.an_fun_year, Money.money(full)))
            }
            notes.take(3).forEach { n ->
                Line("📝", stringResource(R.string.an_fun_note, n.note, Money.money(n.perYear)), pluralStringResource(R.plurals.an_fun_note_sub, n.times, n.times, Money.money(n.sum)))
            }
            if (small.count > 0) Line(
                "👛", stringResource(R.string.an_fun_small, Money.sym + Money.short(small.limit), Money.money(small.perYear)),
                pluralStringResource(R.plurals.an_fun_small_sub, small.count, small.count, Money.money(small.sum)),
            )
            Line("🐷", stringResource(R.string.an_fun_save, Money.money(1000), Money.money(365_000)))
        }
    }
}
