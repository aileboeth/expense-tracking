package com.aileboeth.expensetracking.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Kinds
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Add (initial = null) or edit one entry; also used to fill in a pending amount. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TxEditor(initial: Tx?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.amountCents?.let { Money.format(it) } ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var time by remember { mutableLongStateOf(initial?.time ?: System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    val categories = repoQuery(type, initial = emptyList<String>()) { Repo.categories(type) }

    fun save() {
        val cents = Money.parse(amount)
        if (cents == null) {
            error = "请填金额，例如 12.5"; return
        }
        val tx = (initial ?: Tx(type = type, amountCents = cents, time = time, channel = "手动")).copy(
            type = type, amountCents = cents, category = category.ifBlank { "其他" }, note = note.trim(), time = time, status = Status.OK,
        )
        scope.launch {
            withContext(Dispatchers.IO) { if (initial == null) Repo.insert(tx) else Repo.update(tx) }
            Bubble.refresh()
            onDone()
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = {
            Text(
                when {
                    initial == null -> "记一笔"
                    initial.amountCents == null -> "补上金额"
                    else -> "修改"
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (initial != null && initial.source == "auto") {
                    Text(
                        listOf(initial.channel, Kinds.label(initial.kind), initial.counterpart, initial.raw)
                            .filter { it.isNotBlank() }.distinct().joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TxType.EXPENSE,
                        onClick = { if (type != TxType.EXPENSE) { type = TxType.EXPENSE; category = "" } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("支出") }
                    SegmentedButton(
                        selected = type == TxType.INCOME,
                        onClick = { if (type != TxType.INCOME) { type = TxType.INCOME; category = "" } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("收入") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("分类", style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in categories) {
                        FilterChip(
                            selected = c == category,
                            onClick = { category = if (category == c) "" else c },
                            label = { Text(CategoryStyle.label(c)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("用途 / 备注") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(time)),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({
                        val cal = Calendar.getInstance().apply { timeInMillis = time }
                        DatePickerDialog(ctx, { _, y, m, d ->
                            TimePickerDialog(ctx, { _, h, min ->
                                cal.set(y, m, d, h, min, 0)
                                time = cal.timeInMillis
                            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                    }) { Text("改时间") }
                }
            }
        },
        confirmButton = { TextButton(::save) { Text("保存") } },
        dismissButton = {
            Row {
                if (initial != null) TextButton({
                    scope.launch {
                        withContext(Dispatchers.IO) { Repo.delete(initial.id) }
                        Bubble.refresh()
                        onDone()
                    }
                }) { Text("删除", color = ExpenseRed) }
                TextButton(onDone) { Text("取消") }
            }
        },
    )
}
