package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aileboeth.expensetracking.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 其他 is where skipped categories go, so it cannot be deleted. */
private const val FALLBACK = "其他"
private const val COLUMNS = 4

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val cats = repoQuery(type, initial = emptyList<String>()) { Repo.categories(type) }
    val counts = repoQuery(type, initial = emptyMap<String, Int>()) { Repo.categoryCounts(type) }

    fun add() {
        val n = input.trim()
        when {
            n.isEmpty() -> return
            n in cats -> { error = ctx.getString(R.string.cat_exists, n); return }
            n.length > 12 -> { error = ctx.getString(R.string.cat_too_long); return }
        }
        scope.launch(Dispatchers.IO) { Repo.addCategory(type, n) }
        input = ""; error = ""
    }

    SubPage(stringResource(R.string.set_categories), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item { TypeToggle(type) { type = it; error = "" } }
            item {
                GroupLabel(stringResource(R.string.cat_new))
                SoftCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(input.trim().ifEmpty { "未分类" }, 44)
                        OutlinedTextField(
                            input, { input = it; error = "" },
                            placeholder = { Text(if (type == TxType.EXPENSE) stringResource(R.string.cat_new_hint_expense) else stringResource(R.string.cat_new_hint_income)) },
                            singleLine = true, shape = RoundedCornerShape(14.dp), isError = error.isNotEmpty(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { add() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        FilledTonalButton(::add, enabled = input.isNotBlank()) { Text(stringResource(R.string.add)) }
                    }
                    if (error.isNotEmpty()) Text(error, color = ExpenseRed, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 56.dp, top = 4.dp))
                }
            }
            item {
                GroupLabel(if (type == TxType.EXPENSE) stringResource(R.string.cat_expense_list) else stringResource(R.string.cat_income_list), stringResource(R.string.n_items, cats.size))
                SoftCard(padding = 12.dp) {
                    val t = type
                    ReorderGrid(cats, key = { it }, columns = COLUMNS, cellHeight = 106.dp, onReorder = { order ->
                        scope.launch(Dispatchers.IO) { Repo.reorderCategories(t, order) }
                    }) { c, dragging ->
                        CategoryTile(c, counts[c] ?: 0, deletable = c != FALLBACK, dragging, Modifier.fillMaxSize()) { confirmDelete = c }
                    }
                }
                Text(
                    stringResource(R.string.cat_footer) + " " + stringResource(R.string.drag_hint_cat),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }

    confirmDelete?.let { name ->
        val n = counts[name] ?: 0
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { CategoryBadge(name, 48) },
            title = { Text(stringResource(R.string.cat_delete_title, catName(name))) },
            text = { Text(if (n > 0) pluralStringResource(R.plurals.cat_delete_used, n, n) else stringResource(R.string.cat_delete_unused)) },
            confirmButton = {
                TextButton({
                    scope.launch(Dispatchers.IO) { Repo.deleteCategory(type, name) }
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete), color = ExpenseRed) }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun CategoryTile(name: String, count: Int, deletable: Boolean, dragging: Boolean, modifier: Modifier, onDelete: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(if (dragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(enabled = deletable, onClick = onDelete).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            CategoryBadge(name, 48)
            if (deletable) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp)) }
        }
        Text(catName(name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            if (!deletable) stringResource(R.string.cat_default) else if (count > 0) pluralStringResource(R.plurals.n_entries, count, count) else stringResource(R.string.cat_unused),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
