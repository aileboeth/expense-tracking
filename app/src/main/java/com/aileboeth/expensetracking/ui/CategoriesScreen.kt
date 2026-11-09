package com.aileboeth.expensetracking.ui

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
            n in cats -> { error = "已经有「$n」了"; return }
            n.length > 6 -> { error = "名字短一点，最多 6 个字"; return }
        }
        scope.launch(Dispatchers.IO) { Repo.addCategory(type, n) }
        input = ""; error = ""
    }

    SubPage("管理分类", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item { TypeToggle(type) { type = it; error = "" } }
            item {
                GroupLabel("新建分类")
                SoftCard(padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(input.trim().ifEmpty { "未分类" }, 44)
                        OutlinedTextField(
                            input, { input = it; error = "" },
                            placeholder = { Text(if (type == TxType.EXPENSE) "如：宠物、健身" else "如：理财、奖学金") },
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
                        FilledTonalButton(::add, enabled = input.isNotBlank()) { Text("添加") }
                    }
                    if (error.isNotEmpty()) Text(error, color = ExpenseRed, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 56.dp, top = 4.dp))
                }
            }
            item {
                GroupLabel(if (type == TxType.EXPENSE) "支出分类" else "收入分类", "${cats.size} 个")
                SoftCard(padding = 12.dp) {
                    cats.chunked(COLUMNS).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { c ->
                                CategoryTile(c, counts[c] ?: 0, deletable = c != FALLBACK, Modifier.weight(1f)) { confirmDelete = c }
                            }
                            repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Text(
                    "点分类可以删除，已记的账保留原来的分类。「其他」是没选分类时的默认项，不能删。",
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
            title = { Text("删除分类「$name」？") },
            text = { Text(if (n > 0) "已记的 $n 笔账不会变，只是以后选不到这个分类了。" else "这个分类还没有用过。") },
            confirmButton = {
                TextButton({
                    scope.launch(Dispatchers.IO) { Repo.deleteCategory(type, name) }
                    confirmDelete = null
                }) { Text("删除", color = ExpenseRed) }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryTile(name: String, count: Int, deletable: Boolean, modifier: Modifier, onDelete: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = deletable, onClick = onDelete).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            CategoryBadge(name, 48)
            if (deletable) Box(
                Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).size(18.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp)) }
        }
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(
            if (!deletable) "默认" else if (count > 0) "$count 笔" else "未使用",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
