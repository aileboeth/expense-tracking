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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.data.Kinds
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

private val WEEK = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@Composable
fun HomeScreen(padding: PaddingValues, openSettings: () -> Unit) {
    val ctx = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    val range = monthRange(month)
    val txs = repoQuery(month, initial = emptyList<Tx>()) { Repo.listRange(range.first, range.second) }
    val pending = repoQuery(initial = emptyList<Tx>()) { Repo.pending() }
    var editing by remember { mutableStateOf<Tx?>(null) }
    var adding by remember { mutableStateOf(false) }

    var permTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { permTick++; onPauseOrDispose { } }
    val setupMissing = remember(permTick) { !Perms.listener(ctx) || !Perms.overlay(ctx) }

    val expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
    val income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
    val days = txs.groupBy { localDate(it.time) }.toSortedMap(compareByDescending { it })

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            item { MonthBar(month, onChange = { month = it }) }
            item { SummaryCard(expense, income) }
            if (setupMissing) item {
                NoticeCard("自动记账还没设置好", "需要打开通知使用权和悬浮窗权限", "去设置", onClick = openSettings)
            }
            if (pending.isNotEmpty()) {
                item {
                    Text(
                        "待补金额 / 待确认（${pending.size}）",
                        style = MaterialTheme.typography.titleSmall, color = PendingAmber,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(pending, key = { "p${it.id}" }) { TxRow(it) { editing = it } }
            }
            if (txs.isEmpty()) item {
                Text(
                    "这个月还没有账。付款后会自动记上，也可以点右下角 + 手动记。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 32.dp),
                )
            }
            for ((day, list) in days) {
                item(key = "d$day") { DayHeader(day, list) }
                items(list, key = { it.id }) { TxRow(it) { editing = it } }
            }
        }
        FloatingActionButton(
            onClick = { adding = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, "记一笔") }
    }

    editing?.let { tx -> TxEditor(initial = tx, onDone = { editing = null }) }
    if (adding) TxEditor(initial = null, onDone = { adding = false })
}

@Composable
fun MonthBar(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton({ onChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上个月") }
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (month != YearMonth.now()) TextButton({ onChange(YearMonth.now()) }) { Text("本月") }
        IconButton({ onChange(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下个月") }
    }
}

@Composable
private fun SummaryCard(expense: Long, income: Long) {
    Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(16.dp)) {
            Amount("支出", expense, ExpenseRed, Modifier.weight(1f))
            Amount("收入", income, IncomeGreen, Modifier.weight(1f))
            Amount("结余", income - expense, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Amount(label: String, cents: Long, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val text = if (cents < 0) "-" + Money.format(-cents) else Money.format(cents)
        Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun NoticeCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick) { Text(action) }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, list: List<Tx>) {
    val ex = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
    val inc = list.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
    val label = when (day) {
        LocalDate.now() -> "今天"
        LocalDate.now().minusDays(1) -> "昨天"
        else -> "${day.monthValue}月${day.dayOfMonth}日"
    }
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$label  ${WEEK[day.dayOfWeek.value - 1]}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            val parts = buildList {
                if (ex > 0) add("支出 ${Money.format(ex)}")
                if (inc > 0) add("收入 ${Money.format(inc)}")
            }
            Text(parts.joinToString("  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(Modifier.padding(top = 6.dp))
    }
}

@Composable
fun TxRow(tx: Tx, onClick: () -> Unit) {
    val time = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(tx.time))
    val color = when {
        tx.amountCents == null -> PendingAmber
        tx.type == TxType.EXPENSE -> ExpenseRed
        else -> IncomeGreen
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).background(color.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(tx.category.take(1).ifEmpty { if (tx.type == TxType.EXPENSE) "支" else "收" }, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tx.category.ifEmpty { "未分类" } + if (tx.note.isNotBlank()) " · ${tx.note}" else "",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = listOf(time, tx.channel, tx.counterpart.ifBlank { Kinds.label(tx.kind) }).filter { it.isNotBlank() }.distinct()
            Text(
                sub.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (tx.amountCents == null) "补金额" else Money.signed(tx),
            color = color, fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(Modifier.height(0.dp))
}
