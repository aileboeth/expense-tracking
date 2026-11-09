package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import com.aileboeth.expensetracking.R

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

private enum class LogFilter(val label: Int) { ALL(R.string.period_all), RECORDED(R.string.log_recorded), SKIPPED(R.string.log_skipped) }

/** What one log result means, for the coloured tag. */
/** [tag] / [detailRes] are string resources; [detail] is text taken from the stored result (keyword, reason). */
private data class Outcome(val tag: Int, val detailRes: Int?, val detail: String, val color: Color, val recorded: Boolean)

private fun outcome(result: String): Outcome = when {
    result.startsWith("已记账") -> Outcome(R.string.log_tag_recorded, null, "", IncomeGreen, true)
    result.startsWith("待补金额") -> Outcome(R.string.log_tag_pending, R.string.log_pending_detail, "", PendingAmber, true)
    result.startsWith("黑名单 App") -> Outcome(R.string.log_tag_blocked, R.string.log_blocked_app, "", Tint.Grey, false)
    result.startsWith("黑名单关键词") -> Outcome(R.string.log_tag_blocked, R.string.log_blocked_word, result.substringAfter("「").substringBefore("」"), Tint.Grey, false)
    result.startsWith("不记：") -> Outcome(R.string.log_tag_skipped, null, result.removePrefix("不记："), Tint.Grey, false)
    result.startsWith("没认出") -> Outcome(R.string.log_tag_unknown, R.string.log_unknown_detail, "", Tint.Orange, false)
    result.startsWith("出错") -> Outcome(R.string.log_tag_error, null, result.removePrefix("出错："), ExpenseRed, false)
    else -> Outcome(R.string.cat_other, null, result, Tint.Grey, false)
}

@Composable
fun RecognitionLogScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    val logs = repoQuery(initial = null as List<LogEntry>?) { Repo.logs() }
    val names = remember(logs) { logs.orEmpty().map { it.pkg }.distinct().associateWith { appLabel(ctx, it) } }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.CHINA) }

    SubPage(stringResource(R.string.set_log), onBack) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LogFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, label = { Text(stringResource(f.label)) }) }
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
                    Text(stringResource(R.string.log_empty), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.log_empty_body),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for ((day, list) in days) {
                item(key = "h$day") {
                    Text(
                        Labels.day(ctx, day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
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
                        stringResource(o.tag), color = o.color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(o.color.copy(alpha = 0.13f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
                Text(
                    listOf(l.title, l.text).filter { it.isNotBlank() }.joinToString("：").ifBlank { stringResource(R.string.log_blank) },
                    style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
                val detail = o.detailRes?.let { stringResource(it, o.detail) } ?: Reasons.show(o.detail)
                if (detail.isNotBlank()) Text(
                    detail, style = MaterialTheme.typography.bodySmall, color = o.color,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (index != count - 1) RowDivider()
    }
}

private fun appLabel(ctx: Context, pkg: String): String = when (pkg) {
    Parser.WECHAT -> ctx.getString(R.string.ch_wechat)
    Parser.ALIPAY -> ctx.getString(R.string.ch_alipay)
    else -> runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
