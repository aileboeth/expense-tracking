package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aileboeth.expensetracking.R
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Kinds
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

/** Kinds that only repeat the channel ("微信 · 微信付款"); others (手表/凭证, 免密, 账单导入) add information. */
private val PLAIN_KINDS = setOf("", "wechat_paid", "wechat_income", "wechat_income_no_amount", "alipay_expense", "alipay_income", "alipay_received", "alipay_received_no_amount")

/** Streak, badges, wish and recap state shown under the header. */
private data class FunState(
    val streak: Int = 0,
    val badges: Int = 0,
    val wish: com.aileboeth.expensetracking.data.Wish? = null,
    val todaySpent: Long = 0,
    val avgDaily: Long = 0,
    val recapMonth: YearMonth? = null,
)

private fun loadFun(ctx: android.content.Context): FunState {
    val prefs = Prefs(ctx)
    val today = LocalDate.now()
    val zone = java.time.ZoneId.systemDefault()
    val dStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val wishes = Repo.wishes()
    val prev = YearMonth.now().minusMonths(1)
    val recap = if (today.dayOfMonth <= 10 && prefs.recapBannerDone != prev.toString()) {
        val r = monthRange(prev)
        if (Repo.listRange(r.first, r.second).count { it.type == TxType.EXPENSE } >= 3) prev else null
    } else null
    return FunState(
        streak = com.aileboeth.expensetracking.data.Streak.current(Repo.activeDays(), today.toEpochDay()),
        badges = Repo.badges().size,
        wish = wishes.firstOrNull { it.id == prefs.focusWish && it.done == 0L } ?: wishes.firstOrNull { it.done == 0L } ?: wishes.firstOrNull(),
        todaySpent = Repo.sum(TxType.EXPENSE, dStart, dStart + 86_400_000L),
        avgDaily = Repo.sum(TxType.EXPENSE, dStart - 30L * 86_400_000L, dStart) / 30,
        recapMonth = recap,
    )
}

@Composable
fun HomeScreen(
    padding: PaddingValues,
    openSettings: () -> Unit,
    openSearch: () -> Unit,
    addRequested: TxType?,
    onAddHandled: () -> Unit,
    fillRequested: Long? = null,
    onFillHandled: () -> Unit = {},
    openAchievements: () -> Unit = {},
    openWish: () -> Unit = {},
    openRecap: (YearMonth) -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val range = monthRange(month)
    val txs = repoQuery(month, initial = emptyList<Tx>()) { Repo.listRange(range.first, range.second) }
    val pending = repoQuery(initial = emptyList<Tx>()) { Repo.pending() }
    val fun_ = repoQuery(initial = FunState()) { loadFun(ctx) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf<TxType?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf(prefs.budgetCents) }
    val actions = rememberTxActions()

    LaunchedEffect(addRequested) { if (addRequested != null) { adding = addRequested; onAddHandled() } }
    LaunchedEffect(fillRequested) {
        if (fillRequested != null) {
            editing = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Repo.get(fillRequested) }
            onFillHandled()
        }
    }

    var permTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
    val setupMissing = remember(permTick) { !Perms.listener(ctx) || !Perms.overlay(ctx) }

    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
    val daily = remember(txs, month) { dailyTotals(txs, month, TxType.EXPENSE) }
    val days = txs.groupBy { localDate(it.time) }.toSortedMap(compareByDescending { it })

    val mood = if (month == YearMonth.now()) com.aileboeth.expensetracking.data.MoodCalc.of(
        java.time.LocalTime.now().hour, budget, expense, LocalDate.now().dayOfMonth, month.lengthOfMonth(), fun_.todaySpent, fun_.avgDaily,
    ) else when {
        budget > 0 && expense > budget -> com.aileboeth.expensetracking.data.Mood.SAD
        budget > 0 -> com.aileboeth.expensetracking.data.Mood.HAPPY
        else -> com.aileboeth.expensetracking.data.Mood.CALM
    }
    val lines = mascotLines(ctx, month, expense, budget, fun_, pending.size)

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeaderCard(month, { month = it }, expense, income, budget, daily, mood, lines, openSearch) { budgetDialog = true }
            }
            item { FunRow(fun_, openAchievements, openWish) }
            fun_.recapMonth?.let { rm ->
                item {
                    RecapBanner(rm, onOpen = { prefs.recapBannerDone = rm.toString(); openRecap(rm) }) {
                        prefs.recapBannerDone = rm.toString(); Repo.markActive()
                    }
                }
            }
            if (setupMissing) item {
                NoticeCard(stringResource(R.string.home_setup_title), stringResource(R.string.home_setup_body), stringResource(R.string.go_set), onClick = openSettings)
            }
            val shownPending = pending.filter { it.id !in actions.hidden }
            if (shownPending.isNotEmpty()) item {
                SoftCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Box(Modifier.size(8.dp).background(PendingAmber, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_pending), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Text(pluralStringResource(R.plurals.n_entries, shownPending.size, shownPending.size), color = PendingAmber, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    shownPending.forEach { SwipeTxRow(it, actions) { editing = it } }
                }
            }
            if (txs.isEmpty()) item {
                SoftCard {
                    Text(stringResource(R.string.home_empty_title), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.home_empty_body),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for ((day, all) in days) {
                val list = all.filter { it.id !in actions.hidden }
                if (list.isNotEmpty()) item(key = "d$day") {
                    SoftCard(padding = 12.dp) {
                        DayHeader(day, list)
                        list.forEach { SwipeTxRow(it, actions) { editing = it } }
                    }
                }
            }
            if (txs.isNotEmpty()) item {
                Text(
                    stringResource(R.string.swipe_hint), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = TxType.EXPENSE },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.home_add)) },
            containerColor = Brand,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
        androidx.compose.material3.SnackbarHost(actions.snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))
    }

    TxActionsHost(actions)
    editing?.let { tx -> TxEditor(initial = tx, onDone = { editing = null }) }
    adding?.let { t -> TxEditor(initial = null, initialType = t, onDone = { adding = null }) }
    if (budgetDialog) BudgetDialog(prefs) { budgetDialog = false; budget = prefs.budgetCents }
}

/** What the feather says when tapped, most useful first. */
private fun mascotLines(ctx: android.content.Context, month: YearMonth, expense: Long, budget: Long, f: FunState, pending: Int): List<String> = buildList {
    val now = YearMonth.now()
    if (month != now) {
        add(ctx.getString(R.string.m_past, Labels.monthShort(ctx, month), Money.money(expense)))
        if (budget > 0) add(ctx.getString(if (expense <= budget) R.string.m_past_kept else R.string.m_past_over))
        return@buildList
    }
    if (pending > 0) add(ctx.resources.getQuantityString(R.plurals.m_pending, pending, pending))
    when {
        f.todaySpent == 0L -> add(ctx.getString(R.string.m_today_zero))
        f.avgDaily > 0 && f.todaySpent < f.avgDaily -> add(ctx.getString(R.string.m_today_less, Money.money(f.todaySpent)))
        f.avgDaily > 0 && f.todaySpent > f.avgDaily * 2 -> add(ctx.getString(R.string.m_today_lots, Money.money(f.todaySpent)))
        else -> add(ctx.getString(R.string.m_today, Money.money(f.todaySpent)))
    }
    if (budget > 0) {
        val left = budget - expense
        if (left >= 0) {
            val daysLeft = now.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
            add(ctx.getString(R.string.m_budget_left, Money.money(left), Money.money(left / daysLeft)))
        } else add(ctx.getString(R.string.m_budget_over, Money.money(-left)))
    }
    if (f.streak >= 2) add(ctx.resources.getQuantityString(R.plurals.m_streak, f.streak, f.streak))
    f.wish?.takeIf { it.done == 0L }?.let { add(ctx.getString(R.string.m_wish, it.emoji + it.name, Money.money(it.target - it.saved))) }
    val h = java.time.LocalTime.now().hour
    add(ctx.getString(when (h) { in 5..10 -> R.string.m_morning; in 11..13 -> R.string.m_noon; in 18..22 -> R.string.m_evening; in 23..23, in 0..4 -> R.string.m_night; else -> R.string.m_hi }))
}

@Composable
private fun FunRow(f: FunState, openAchievements: () -> Unit, openWish: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FunTile(Modifier.weight(1f), openAchievements) {
            Text(if (f.streak > 0) "🔥" else "🌱", fontSize = 22.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    if (f.streak > 0) pluralStringResource(R.plurals.streak_days, f.streak, f.streak) else stringResource(R.string.streak_start),
                    fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
                Text(stringResource(R.string.badges_count, f.badges, com.aileboeth.expensetracking.data.Achievements.ALL.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        FunTile(Modifier.weight(1f), openWish) {
            val w = f.wish
            MiniJar(w?.progress ?: 0f, Modifier.size(width = 24.dp, height = 30.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(w?.let { it.emoji + " " + it.name } ?: stringResource(R.string.wish_title), fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        w == null -> stringResource(R.string.wish_set_one)
                        w.done > 0 -> stringResource(R.string.wish_done_short)
                        else -> "${(w.progress * 100).toInt()}% · " + Money.money(w.saved)
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FunTile(modifier: Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.material3.Surface(
        modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun RecapBanner(month: YearMonth, onOpen: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFE3EE), Color(0xFFE9E4FF), Color(0xFFD9F1FF))))
            .clickable(onClick = onOpen).padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🎞️", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.recap_banner, Labels.monthShort(ctx, month)), fontWeight = FontWeight.SemiBold, color = Color(0xFF2A1D7A))
                Text(stringResource(R.string.recap_banner_sub), style = MaterialTheme.typography.bodySmall, color = Color(0xFF5B5480))
            }
            IconButton(onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color(0xFF5B5480)) }
        }
    }
}

@Composable
private fun HeaderCard(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    expense: Long,
    income: Long,
    budget: Long,
    daily: List<Long?>,
    mood: com.aileboeth.expensetracking.data.Mood,
    lines: List<String>,
    openSearch: () -> Unit,
    openBudget: () -> Unit,
) {
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.78f)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val drag = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val swipe = with(androidx.compose.ui.platform.LocalDensity.current) { 70.dp.toPx() }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeaderBrush)
            // Swipe sideways on the card to change the month.
            .pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val d = drag.value
                        val target = when {
                            d > swipe -> month.minusMonths(1)
                            d < -swipe && month < YearMonth.now() -> month.plusMonths(1)
                            else -> null
                        }
                        if (target != null) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                            onMonth(target)
                        } else if (d < -swipe) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject)
                        }
                        scope.launch { drag.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f)) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { change, d ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + d) }
                }
            },
    ) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month), tint = white) }
                Text(Labels.yearMonth(LocalContext.current, month), color = white, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton({ onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month), tint = white) }
                if (month != YearMonth.now()) TextButton({ onMonth(YearMonth.now()) }) { Text(stringResource(R.string.this_month), color = white) }
                Spacer(Modifier.weight(1f))
                IconButton(openSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search), tint = white) }
            }
            Column(Modifier.padding(horizontal = 12.dp).graphicsLayer {
                translationX = drag.value * 0.35f
                alpha = 1f - (kotlin.math.abs(drag.value) / (swipe * 4)).coerceIn(0f, 0.5f)
            }) {
              Row(verticalAlignment = Alignment.CenterVertically) {
               Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_month_spent), color = soft, style = MaterialTheme.typography.labelLarge)
                RollingMoney(expense, androidx.compose.ui.text.TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold), white)
               }
               Mascot(mood, lines, size = 62.dp)
              }
                Row(Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.home_income, Money.money(income)), color = soft, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(16.dp))
                    val bal = income - expense
                    Text(stringResource(R.string.home_balance, Money.money(bal)), color = soft, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                if (budget > 0) {
                    val ratio = expense.toFloat() / budget
                    LinearProgressIndicator(
                        progress = { ratio.coerceIn(0f, 1f) },
                        color = if (ratio >= 1f) Color(0xFFFFB4A8) else white,
                        trackColor = white.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                        modifier = Modifier.fillMaxWidth().height(6.dp).clickable(onClick = openBudget),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(budgetLine(LocalContext.current, month, expense, budget), color = soft, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable(onClick = openBudget))
                } else {
                    Text(stringResource(R.string.home_set_budget), color = white, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable(onClick = openBudget).padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Sparkline(daily.filterNotNull(), white)
            }
        }
    }
}

fun budgetLine(ctx: android.content.Context, month: YearMonth, expense: Long, budget: Long): String {
    val left = budget - expense
    if (left < 0) return ctx.getString(R.string.budget_over, Money.money(budget), Money.money(-left))
    val now = YearMonth.now()
    return if (month == now) {
        val daysLeft = month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
        ctx.getString(R.string.budget_left, Money.money(budget), Money.money(left), Money.money(left / daysLeft))
    } else {
        ctx.getString(R.string.budget_used, Money.money(budget), (expense * 100 / budget).toInt())
    }
}

@Composable
fun NoticeCard(title: String, body: String, action: String, onClick: () -> Unit) {
    SoftCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(ExpenseRed, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick) { Text(action) }
        }
    }
}

@Composable
fun DayHeader(day: LocalDate, list: List<Tx>) {
    val ex = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
    val inc = list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
    val ctx = LocalContext.current
    val label = Labels.day(ctx, day)
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text("  " + Labels.weekday(ctx, day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            val parts = buildList {
                if (ex > 0) add(ctx.getString(R.string.day_out, Money.format(ex)))
                if (inc > 0) add(ctx.getString(R.string.day_in, Money.format(inc)))
            }
            Text(parts.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun CategoryBadge(category: String, size: Int = 40) {
    val st = CategoryStyle.of(category)
    Box(
        Modifier.size(size.dp).background(Color(st.color).copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(st.emoji, fontSize = (size * 0.45f).sp) }
}

/** tx id -> number of receipts, provided once at the app root so each row can show a 📎. */
val LocalAttachCounts = androidx.compose.runtime.compositionLocalOf { emptyMap<Long, Int>() }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TxRow(tx: Tx, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val attached = (LocalAttachCounts.current[tx.id] ?: 0) > 0
    val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(tx.time))
    val color = when {
        tx.amountCents == null -> PendingAmber
        tx.type == TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        else -> IncomeGreen
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(tx.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (tx.category.isEmpty() && tx.kind.isNotEmpty()) Labels.kind(ctx, tx.kind) else Labels.category(ctx, tx.category)) + if (tx.note.isNotBlank()) " · ${tx.note}" else "",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (attached) Icon(
                    androidx.compose.material.icons.Icons.Filled.AttachFile, stringResource(R.string.has_receipt),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(15.dp),
                )
            }
            val titleIsKind = tx.category.isEmpty() && tx.kind.isNotEmpty()
            // "微信 · 微信付款" says the same thing twice: keep the kind only when it adds something (手表/凭证, 免密, 账单导入…).
            val kind = if (titleIsKind || tx.kind in PLAIN_KINDS) "" else Labels.kind(ctx, tx.kind)
            val sub = listOf(time, Labels.channel(ctx, tx.channel), tx.counterpart.ifBlank { kind })
                .filter { it.isNotBlank() }.distinct()
            Text(
                sub.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (tx.amountCents == null) stringResource(R.string.fill_amount) else Money.signed(tx),
            color = color, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        )
    }
}
