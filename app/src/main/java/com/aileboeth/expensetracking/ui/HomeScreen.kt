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

@Composable
fun HomeScreen(
    padding: PaddingValues,
    openSettings: () -> Unit,
    openSearch: () -> Unit,
    addRequested: Boolean,
    onAddHandled: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    val range = monthRange(month)
    val txs = repoQuery(month, initial = emptyList<Tx>()) { Repo.listRange(range.first, range.second) }
    val pending = repoQuery(initial = emptyList<Tx>()) { Repo.pending() }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }
    var budgetDialog by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf(prefs.budgetCents) }

    LaunchedEffect(addRequested) { if (addRequested) { adding = true; onAddHandled() } }

    var permTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
    val setupMissing = remember(permTick) { !Perms.listener(ctx) || !Perms.overlay(ctx) }

    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
    val daily = remember(txs, month) { dailyTotals(txs, month, TxType.EXPENSE) }
    val days = txs.groupBy { localDate(it.time) }.toSortedMap(compareByDescending { it })

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeaderCard(month, { month = it }, expense, income, budget, daily, openSearch) { budgetDialog = true }
            }
            if (setupMissing) item {
                NoticeCard(stringResource(R.string.home_setup_title), stringResource(R.string.home_setup_body), stringResource(R.string.go_set), onClick = openSettings)
            }
            if (pending.isNotEmpty()) item {
                SoftCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Box(Modifier.size(8.dp).background(PendingAmber, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_pending), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(pluralStringResource(R.plurals.n_entries, pending.size, pending.size), color = PendingAmber, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(4.dp))
                    pending.forEach { TxRow(it) { editing = it } }
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
            for ((day, list) in days) {
                item(key = "d$day") {
                    SoftCard(padding = 12.dp) {
                        DayHeader(day, list)
                        list.forEach { TxRow(it) { editing = it } }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.home_add)) },
            containerColor = Brand,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    editing?.let { tx -> TxEditor(initial = tx, onDone = { editing = null }) }
    if (adding) TxEditor(initial = null, onDone = { adding = false })
    if (budgetDialog) BudgetDialog(prefs) { budgetDialog = false; budget = prefs.budgetCents }
}

@Composable
private fun HeaderCard(
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    expense: Long,
    income: Long,
    budget: Long,
    daily: List<Long?>,
    openSearch: () -> Unit,
    openBudget: () -> Unit,
) {
    val white = Color.White
    val soft = Color.White.copy(alpha = 0.78f)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeaderBrush)) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onMonth(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev_month), tint = white) }
                Text(Labels.yearMonth(LocalContext.current, month), color = white, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton({ onMonth(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month), tint = white) }
                if (month != YearMonth.now()) TextButton({ onMonth(YearMonth.now()) }) { Text(stringResource(R.string.this_month), color = white) }
                Spacer(Modifier.weight(1f))
                IconButton(openSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search), tint = white) }
            }
            Column(Modifier.padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.home_month_spent), color = soft, style = MaterialTheme.typography.labelLarge)
                Text(Money.money(expense), color = white, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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

@Composable
fun TxRow(tx: Tx, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val attached = (LocalAttachCounts.current[tx.id] ?: 0) > 0
    val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(tx.time))
    val color = when {
        tx.amountCents == null -> PendingAmber
        tx.type == TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
        else -> IncomeGreen
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 9.dp),
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
