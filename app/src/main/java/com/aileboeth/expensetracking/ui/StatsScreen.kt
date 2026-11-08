package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(padding: PaddingValues) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    val range = monthRange(month)
    val txs = repoQuery(month, initial = emptyList<Tx>()) { Repo.listRange(range.first, range.second) }

    val picked = txs.filter { it.type == type }
    val total = picked.sumOf { it.amountCents ?: 0 }
    val groups = picked.groupBy { it.category.ifEmpty { "未分类" } }
        .map { (name, list) -> Triple(name, list.sumOf { it.amountCents ?: 0 }, list.size) }
        .sortedByDescending { it.second }
    val color = if (type == TxType.EXPENSE) ExpenseRed else IncomeGreen
    val daysInMonth = if (month == YearMonth.now()) java.time.LocalDate.now().dayOfMonth else month.lengthOfMonth()

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
    ) {
        item { MonthBar(month) { month = it } }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
            }
        }
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(if (type == TxType.EXPENSE) "本月支出" else "本月收入", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥" + Money.format(total), style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.SemiBold)
                Text(
                    "${picked.size} 笔 · 日均 ¥" + Money.format(if (daysInMonth > 0) total / daysInMonth else 0),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (groups.isEmpty()) item {
            Text("没有记录", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
        }
        for ((name, cents, count) in groups) {
            item(key = name) {
                val share = if (total > 0) cents.toFloat() / total else 0f
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, modifier = Modifier.weight(1f))
                        Text("¥${Money.format(cents)}", fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(
                        progress = { share },
                        color = color,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp),
                    )
                    Text(
                        "%.1f%% · %d 笔".format(share * 100, count),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
