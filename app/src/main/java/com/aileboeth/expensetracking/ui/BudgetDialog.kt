package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs

@Composable
fun BudgetDialog(prefs: Prefs, onDone: () -> Unit) {
    var text by remember { mutableStateOf(prefs.budgetCents.takeIf { it > 0 }?.let { Money.format(it) } ?: "") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("每月预算") },
        text = {
            Column {
                Text(
                    "首页会显示还剩多少、每天还能花多少；用到 80% 时悬浮球描黄边，超出描红边。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = false },
                    label = { Text("预算（元）") }, singleLine = true, isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton({
                val c = Money.parse(text)
                if (c == null) error = true else { prefs.budgetCents = c; onDone() }
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton({ prefs.budgetCents = 0; onDone() }) { Text("不设预算") }
        },
    )
}
