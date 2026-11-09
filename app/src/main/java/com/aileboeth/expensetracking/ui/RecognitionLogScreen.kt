package com.aileboeth.expensetracking.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.data.LogEntry
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.Parser
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private enum class LogFilter(val label: String) { ALL("全部"), RECORDED("记了"), SKIPPED("没记") }

/** What one log result means, for the coloured tag. */
private data class Outcome(val tag: String, val detail: String, val color: Color, val recorded: Boolean)

private fun outcome(result: String): Outcome = when {
    result.startsWith("已记账") -> Outcome("已记账", "", IncomeGreen, true)
    result.startsWith("待补金额") -> Outcome("待补", "记下了，等你补金额或确认", PendingAmber, true)
    result.startsWith("黑名单 App") -> Outcome("黑名单", "这个 App 在黑名单里", Tint.Grey, false)
    result.startsWith("黑名单关键词") -> Outcome("黑名单", result.removeSuffix("，不记"), Tint.Grey, false)
    result.startsWith("不记：") -> Outcome("不记", result.removePrefix("不记："), Tint.Grey, false)
    result.startsWith("没认出") -> Outcome("没认出", "像是付款通知，但格式没见过", Tint.Orange, false)
    result.startsWith("出错") -> Outcome("出错", result.removePrefix("出错："), ExpenseRed, false)
    else -> Outcome("其他", result, Tint.Grey, false)
}

@Composable
fun RecognitionLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    val logs = repoQuery(initial = null as List<LogEntry>?) { Repo.logs() }
    val names = remember(logs) { logs.orEmpty().map { it.pkg }.distinct().associateWith { appLabel(ctx, it) } }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.CHINA) }

    SubPage("识别记录", onBack) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(f.label) }) }
        }
        val shown = logs.orEmpty().filter {
            when (filter) {
                LogFilter.ALL -> true
                LogFilter.RECORDED -> outcome(it.result).recorded
                LogFilter.SKIPPED -> !outcome(it.result).recorded
            }
        }
        val days = shown.groupBy { localDate(it.time) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
            if (logs != null && shown.isEmpty()) item {
                SoftCard {
                    Text("还没有记录", fontWeight = FontWeight.SemiBold)
                    Text(
                        "微信、支付宝发来付款通知后，这里会写它记了没有、为什么。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for ((day, list) in days) {
                item(key = "h$day") {
                    Text(
                        dayLabel(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(list, key = { _, l -> l.id }) { i, l ->
                    LogRow(l, names[l.pkg] ?: l.pkg, time.format(Date(l.time)), i, list.size)
                }
            }
        }
    }
}

@Composable
private fun LogRow(l: LogEntry, app: String, time: String, index: Int, count: Int) {
    val o = outcome(l.result)
    val r = 20.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp, bottomEnd = if (index == count - 1) r else 0.dp,
    )
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            AppIcon(l.pkg, app, 36.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("  $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    Text(
                        o.tag, color = o.color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(o.color.copy(alpha = 0.13f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                Text(
                    listOf(l.title, l.text).filter { it.isNotBlank() }.joinToString("：").ifBlank { "（空通知）" },
                    style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                if (o.detail.isNotBlank()) Text(
                    o.detail, style = MaterialTheme.typography.bodySmall, color = o.color,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (index != count - 1) RowDivider()
    }
}

private fun dayLabel(d: LocalDate): String = when (d) {
    LocalDate.now() -> "今天"
    LocalDate.now().minusDays(1) -> "昨天"
    else -> "${d.monthValue}月${d.dayOfMonth}日"
}

private fun appLabel(ctx: Context, pkg: String): String = when (pkg) {
    Parser.WECHAT -> "微信"
    Parser.ALIPAY -> "支付宝"
    else -> runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
