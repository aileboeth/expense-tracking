package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import com.aileboeth.expensetracking.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.TxType

private const val DAY = 24L * 3600 * 1000

/** The quick-pick amounts on the capsule's card for entries without an amount (watch payments, 转账到账). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsualAmountsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var list by remember(type) { mutableStateOf(prefs.usualAmounts(type)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    // Same rule as the capsule: the user's own list, else the most frequent amounts of the last 60 days.
    val auto = repoQuery(type, initial = emptyList<Long>()) {
        Repo.frequentAmounts(type, System.currentTimeMillis() - 60 * DAY, if (type == TxType.EXPENSE) 4 else 3)
    }
    val frequent = repoQuery(type, initial = emptyList<Long>()) {
        Repo.frequentAmounts(type, System.currentTimeMillis() - 90 * DAY, 10)
    }

    fun save(new: List<Long>) { list = new; prefs.setUsualAmounts(type, new) }
    fun add() {
        val c = Money.parse(input)
        if (c == null) { error = true; return }
        save((list + c).distinct()); input = ""; error = false
    }

    SubPage(stringResource(R.string.set_usual), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item { TypeToggle(type) { type = it; input = ""; error = false } }

            item {
                GroupLabel(stringResource(R.string.ua_preview), if (list.isEmpty()) stringResource(R.string.ua_auto) else stringResource(R.string.ua_yours))
                Preview(list.ifEmpty { auto })
            }

            item {
                GroupLabel(stringResource(R.string.ua_list), if (list.isEmpty()) "" else stringResource(R.string.n_items, list.size))
                SoftCard(padding = 14.dp) {
                    if (list.isEmpty()) {
                        Text(
                            stringResource(R.string.ua_empty),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ReorderGrid(list, key = { it }, columns = 3, cellHeight = 50.dp, onReorder = { save(it) }) { c, dragging ->
                            Row(
                                Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(50))
                                    .background(if (dragging) Brand else MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { save(list - c) }.padding(start = 14.dp, end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    Money.sym + Money.short(c), fontWeight = FontWeight.SemiBold, maxLines = 1,
                                    color = if (dragging) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(Icons.Filled.Close, stringResource(R.string.delete), Modifier.size(16.dp),
                                    tint = if (dragging) Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.drag_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            TextButton({ save(emptyList()) }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.ua_reset)) }
                        }
                    }
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            input, { input = it; error = false },
                            placeholder = { Text(stringResource(R.string.ua_input)) }, prefix = { Text(Money.sym + " ") },
                            singleLine = true, isError = error, shape = RoundedCornerShape(14.dp),
                            supportingText = if (error) { { Text(stringResource(R.string.ua_bad)) } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(::add, enabled = input.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.add)) }
                    }
                }
            }

            val suggest = frequent.filter { it !in list }
            if (suggest.isNotEmpty()) item {
                GroupLabel(stringResource(R.string.ua_frequent), stringResource(R.string.ua_90d))
                SoftCard(padding = 14.dp) {
                    Text(stringResource(R.string.ua_tap_add), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in suggest) AssistChip(
                            onClick = { save((list + c).distinct()) },
                            label = { Text(Money.sym + Money.short(c)) },
                            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/** Looks like the chip row on the capsule's bottom card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Preview(amounts: List<Long>) {
    val chip = Modifier.clip(RoundedCornerShape(50)).padding(0.dp)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(22.dp)).padding(16.dp),
    ) {
        Text(stringResource(R.string.card_how_much), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (c in amounts) PreviewChip(Money.sym + Money.short(c), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, chip)
            PreviewChip(stringResource(R.string.card_other_amount), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, chip)
            PreviewChip(stringResource(R.string.card_edit), Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, chip.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)))
        }
        if (amounts.isEmpty()) Text(
            stringResource(R.string.ua_no_history),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PreviewChip(text: String, bg: Color, fg: Color, modifier: Modifier) {
    Text(
        text, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
        modifier = modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
