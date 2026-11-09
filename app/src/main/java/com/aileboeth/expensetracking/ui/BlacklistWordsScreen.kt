package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo

/** Keywords: an entry whose payer, merchant or text contains one of them is not recorded. Saved at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlacklistWordsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var words by remember { mutableStateOf(prefs.blacklistWords) }
    var input by remember { mutableStateOf("") }
    val counterparts = repoQuery(initial = emptyList<String>()) { Repo.recentCounterparts(12) }
    val blocked = repoQuery(initial = emptyMap<String, Int>()) {
        Repo.logs().mapNotNull { Regex("黑名单关键词「(.+)」").find(it.result)?.groupValues?.get(1) }.groupingBy { it }.eachCount()
    }

    fun save(new: Set<String>) { words = new; prefs.blacklistWords = new }
    fun add() {
        val w = input.trim()
        if (w.isNotEmpty()) save(words + w)
        input = ""
    }

    SubPage("黑名单关键词", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${words.size}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                            Text(" 个关键词", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                            val total = words.sumOf { blocked[it] ?: 0 }
                            if (total > 0) Text(
                                "已拦下 $total 笔", color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Text(
                            "付款方、商户或通知内容里含有这些词，就不记账。适合挡掉自己账户之间转来转去这类不算收支的钱。",
                            color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        input, { input = it }, placeholder = { Text("输入关键词，如：多点科技") },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(::add, enabled = input.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) { Text("添加") }
                }
            }
            item {
                Text("已添加", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
                SoftCard(padding = 14.dp) {
                    if (words.isEmpty()) {
                        Text("还没有。在上面输入，或者从下面最近的付款方里点一个。", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (w in words.sorted()) {
                                val n = blocked[w] ?: 0
                                InputChip(
                                    selected = true,
                                    onClick = { save(words - w) },
                                    label = { Text(if (n > 0) "$w · 拦下 $n" else w) },
                                    trailingIcon = { Icon(Icons.Filled.Close, "删掉", Modifier.size(16.dp)) },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                    border = null,
                                )
                            }
                        }
                    }
                }
            }
            val suggestions = counterparts.filter { c -> words.none { c.contains(it) } }
            if (suggestions.isNotEmpty()) item {
                Text("最近的付款方 / 商户", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
                SoftCard(padding = 14.dp) {
                    Text("点一下就加进黑名单", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in suggestions) AssistChip(
                            onClick = { save(words + c) },
                            label = { Text(c) },
                            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}
