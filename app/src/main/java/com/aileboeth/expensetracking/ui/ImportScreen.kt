package com.aileboeth.expensetracking.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.bill.BillException
import com.aileboeth.expensetracking.bill.BillImporter
import com.aileboeth.expensetracking.bill.BillImporter.Outcome
import com.aileboeth.expensetracking.bill.BillSource
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ImportState(
    val source: BillSource,
    val lines: List<BillImporter.Line>,
    val skipped: Map<String, Int>,
)

@Composable
fun ImportScreen(uri: Uri, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        try {
            val st = withContext(Dispatchers.IO) {
                val name = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                } ?: ""
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BillException("读不到这个文件")
                val bill = BillImporter.parse(name, bytes)
                if (bill.rows.isEmpty()) throw BillException("账单里没有可导入的收支")
                val from = bill.rows.minOf { it.time } - 24 * 3600_000L
                val to = bill.rows.maxOf { it.time } + 24 * 3600_000L
                val existing = Repo.listAll(from, to).map { BillImporter.Existing(it.id, it.type, it.amountCents, it.channel, it.time, it.ref) }
                val refs = Repo.refs(bill.rows.map { it.orderId }.filter { it.isNotEmpty() })
                ImportState(bill.source, BillImporter.reconcile(bill.source, bill.rows, existing, refs), bill.skipped)
            }
            state = st
            chosen = st.lines.indices.filter { st.lines[it].outcome == Outcome.MISSING || st.lines[it].outcome == Outcome.FILL }.toSet()
        } catch (e: BillException) {
            error = e.message
        } catch (e: Exception) {
            error = "读取失败：${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun doImport(st: ImportState) {
        working = true
        scope.launch {
            val (added, filled) = withContext(Dispatchers.IO) {
                var a = 0
                var f = 0
                Repo.batch {
                    for (i in chosen) {
                        val line = st.lines[i]
                        val r = line.row
                        val category = BillImporter.guessCategory(r, st.source.kind)
                        val note = r.goods.takeIf { it.isNotBlank() && it != r.counterpart }.orEmpty()
                        when (line.outcome) {
                            Outcome.MISSING -> {
                                insert(Tx(
                                    type = r.type, amountCents = r.amountCents, category = category, note = note,
                                    channel = st.source.channel, counterpart = r.counterpart, time = r.time,
                                    status = Status.OK, source = "import", kind = st.source.kind,
                                    raw = listOf(r.billCategory, r.status).filter { it.isNotBlank() }.joinToString(" · "),
                                    ref = r.orderId,
                                )); a++
                            }
                            Outcome.FILL -> {
                                val old = Repo.get(line.existingId ?: continue) ?: continue
                                update(old.copy(
                                    amountCents = r.amountCents, status = Status.OK,
                                    counterpart = old.counterpart.ifBlank { r.counterpart },
                                    note = old.note.ifBlank { note },
                                    category = old.category.ifBlank { category },
                                    ref = r.orderId,
                                )); f++
                            }
                            else -> {}
                        }
                    }
                }
                a to f
            }
            Bubble.refresh()
            Toast.makeText(ctx, "导入了 $added 笔，补上金额 $filled 笔", Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text(state?.source?.label?.let { "导入$it" } ?: "导入账单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            val st = state
            when {
                error != null -> SoftCard(Modifier.padding(16.dp)) {
                    Text("没能导入", fontWeight = FontWeight.SemiBold, color = ExpenseRed)
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "微信：我 → 服务 → 钱包 → 账单 → 右上角 ⋯ → 下载账单 → 用于个人对账（选 Excel）。\n" +
                            "支付宝：我的 → 账单 → 右上角 ⋯ → 开具交易流水证明 → 用于个人对账。\n" +
                            "收到邮件后先解压（密码在微信/支付宝的消息里），再导入里面的 .xlsx / .csv。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                st == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    val by = st.lines.indices.groupBy { st.lines[it].outcome }
                    LazyColumn(
                        Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            SoftCard {
                                Text("对账结果", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "账单里 ${st.lines.size} 笔：已经记过 ${by[Outcome.MATCHED]?.size ?: 0} 笔，" +
                                        "漏记 ${by[Outcome.MISSING]?.size ?: 0} 笔，能补上金额 ${by[Outcome.FILL]?.size ?: 0} 笔，" +
                                        "以前导入过 ${by[Outcome.ALREADY_IMPORTED]?.size ?: 0} 笔。",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (st.skipped.isNotEmpty()) Text(
                                    "没导入：" + st.skipped.entries.joinToString("，") { "${it.key} ${it.value} 笔" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        section(this, "漏记的（勾选的会加进账本）", by[Outcome.MISSING], st, chosen) { i ->
                            chosen = if (i in chosen) chosen - i else chosen + i
                        }
                        section(this, "能补上金额的（手表付款、没写金额的到账）", by[Outcome.FILL], st, chosen) { i ->
                            chosen = if (i in chosen) chosen - i else chosen + i
                        }
                        section(this, "已经记过的", by[Outcome.MATCHED], st, null) {}
                    }
                    Button(
                        onClick = { doImport(st) },
                        enabled = chosen.isNotEmpty() && !working,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(if (chosen.isEmpty()) "没有要导入的" else "导入选中的 ${chosen.size} 笔") }
                }
            }
        }
    }
}

private fun section(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    title: String,
    indices: List<Int>?,
    st: ImportState,
    chosen: Set<Int>?,
    toggle: (Int) -> Unit,
) {
    if (indices.isNullOrEmpty()) return
    scope.item {
        SoftCard(padding = 10.dp) {
            Text("$title · ${indices.size}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp))
            val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            indices.take(if (chosen == null) 30 else 500).forEach { i ->
                val r = st.lines[i].row
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = chosen != null) { toggle(i) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chosen != null) Checkbox(i in chosen, { toggle(i) }) else Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOf(r.counterpart, r.goods).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifEmpty { r.billCategory.ifEmpty { "（无说明）" } },
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            fmt.format(Date(r.time)) + if (r.billCategory.isNotBlank()) " · ${r.billCategory}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (r.type == TxType.EXPENSE) "-" else "+") + Money.format(r.amountCents),
                        color = if (r.type == TxType.EXPENSE) MaterialTheme.colorScheme.onSurface else IncomeGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (chosen == null && indices.size > 30) Text("……还有 ${indices.size - 30} 笔", Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
