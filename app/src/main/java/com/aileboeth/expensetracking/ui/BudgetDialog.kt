package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import com.aileboeth.expensetracking.R

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
        title = { Text(stringResource(R.string.budget_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.budget_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = false },
                    label = { Text(stringResource(R.string.budget_label, Money.sym)) }, singleLine = true, isError = error,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton({
                val c = Money.parse(text)
                if (c == null) error = true else { prefs.budgetCents = c; onDone() }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton({ prefs.budgetCents = 0; onDone() }) { Text(stringResource(R.string.budget_none)) }
        },
    )
}
