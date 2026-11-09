package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aileboeth.expensetracking.R

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
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw BillException(ctx.getString(R.string.att_read_failed))
                val bill = BillImporter.parse(name, bytes)
                if (bill.rows.isEmpty()) throw BillException(ctx.getString(R.string.imp_empty))
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
            error = ctx.getString(R.string.imp_read_failed, e.message ?: e.javaClass.simpleName)
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
                        val note = r.goods.takeIf { it.isNotBlank() && it != r.counterpart }.orEmpty()
                        // Your own rules and learned notes first, then the bill's own category words.
                        val category = (com.aileboeth.expensetracking.data.Categorizer.fromNote(r.type, note, r.counterpart)
                            ?: BillImporter.guessCategory(r, st.source.kind)).ifBlank { "其他" }
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
            Toast.makeText(ctx, ctx.getString(R.string.imp_done, added, filled), Toast.LENGTH_LONG).show()
            onBack()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(state?.source?.let { stringResource(R.string.imp_title_of, Labels.channel(ctx, it.channel)) } ?: stringResource(R.string.imp_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            val st = state
            when {
                error != null -> SoftCard(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.imp_failed), fontWeight = FontWeight.SemiBold, color = ExpenseRed)
                    Text(error!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        stringResource(R.string.imp_howto),
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
                                Text(stringResource(R.string.imp_result), fontWeight = FontWeight.SemiBold)
                                Text(
                                    stringResource(R.string.imp_summary, st.lines.size, by[Outcome.MATCHED]?.size ?: 0, by[Outcome.MISSING]?.size ?: 0,
                                        by[Outcome.FILL]?.size ?: 0, by[Outcome.ALREADY_IMPORTED]?.size ?: 0),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (st.skipped.isNotEmpty()) Text(
                                    stringResource(R.string.imp_skipped) + st.skipped.entries.joinToString(", ") { "${it.key} ${it.value}" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        section(this, R.string.imp_missing, by[Outcome.MISSING], st, chosen) { i ->
                            chosen = if (i in chosen) chosen - i else chosen + i
                        }
                        section(this, R.string.imp_fill, by[Outcome.FILL], st, chosen) { i ->
                            chosen = if (i in chosen) chosen - i else chosen + i
                        }
                        section(this, R.string.imp_matched, by[Outcome.MATCHED], st, null) {}
                    }
                    Button(
                        onClick = { doImport(st) },
                        enabled = chosen.isNotEmpty() && !working,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) { Text(if (chosen.isEmpty()) stringResource(R.string.imp_nothing) else pluralStringResource(R.plurals.imp_selected, chosen.size, chosen.size)) }
                }
            }
        }
    }
}

private fun section(
    scope: androidx.compose.foundation.lazy.LazyListScope,
    title: Int,
    indices: List<Int>?,
    st: ImportState,
    chosen: Set<Int>?,
    toggle: (Int) -> Unit,
) {
    if (indices.isNullOrEmpty()) return
    scope.item {
        SoftCard(padding = 10.dp) {
            Text(stringResource(title) + " · ${indices.size}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp))
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
                            listOf(r.counterpart, r.goods).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifEmpty { r.billCategory.ifEmpty { stringResource(R.string.imp_no_desc) } },
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
            if (chosen == null && indices.size > 30) Text(stringResource(R.string.imp_more, indices.size - 30), Modifier.padding(6.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
