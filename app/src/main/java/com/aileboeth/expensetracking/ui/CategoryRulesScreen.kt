package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.data.Categorizer
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Rule
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** keyword -> category rules, plus what the app learned from your notes and its built-in words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryRulesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var keyword by remember { mutableStateOf("") }
    var target by remember(type) { mutableStateOf("") }
    val rules = repoQuery(type, initial = emptyList<Rule>()) { Repo.rules(type) }
    val cats = repoQuery(type, initial = emptyList<String>()) { Repo.categories(type) }
    val learned = repoQuery(type, initial = emptyList<Triple<String, String, Int>>()) { Repo.learnedNotes(type, 12) }

    fun add() {
        val k = keyword.trim()
        if (k.isEmpty() || target.isEmpty()) return
        scope.launch(Dispatchers.IO) { Repo.putRule(Rule(type = type, keyword = k, category = target)) }
        keyword = ""
    }

    SubPage("分类规则", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Column {
                        Text("按备注自动分类", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "写了备注、没选分类时，App 依次看：你的规则 → 你以前给这个备注选的分类 → 内置的常用词。都对不上才记到「其他」。",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item { TypeToggle(type, Modifier.padding(top = 16.dp)) { type = it } }

            item {
                GroupLabel("新建规则")
                SoftCard(padding = 14.dp) {
                    OutlinedTextField(
                        keyword, { keyword = it }, placeholder = { Text("备注或付款方里出现的词，如：瑞幸") },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("归到", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (c in cats.filter { it != "其他" }) FilterChip(target == c, { target = if (target == c) "" else c }, label = { Text(com.aileboeth.expensetracking.data.CategoryStyle.label(c)) })
                    }
                    FilledTonalButton(::add, enabled = keyword.isNotBlank() && target.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(if (keyword.isNotBlank() && target.isNotEmpty()) "添加：「${keyword.trim()}」→ $target" else "添加")
                    }
                }
            }

            item {
                GroupLabel("你的规则", if (rules.isEmpty()) "" else "${rules.size} 条")
                SoftCard(padding = 0.dp) {
                    if (rules.isEmpty()) Text(
                        "还没有。大多数时候不用设：给某个备注选过一次分类，App 下次就记住了。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    rules.forEachIndexed { i, r ->
                        MappingRow(r.keyword, r.category, null) { scope.launch(Dispatchers.IO) { Repo.deleteRule(r.id) } }
                        if (i != rules.lastIndex) RowDivider()
                    }
                }
            }

            if (learned.isNotEmpty()) item {
                GroupLabel("已经学会的", "按你以前的选择")
                SoftCard(padding = 0.dp) {
                    learned.forEachIndexed { i, (note, cat, n) ->
                        MappingRow(note, cat, "$n 次", null)
                        if (i != learned.lastIndex) RowDivider()
                    }
                }
                Text(
                    "想改的话，在账单里把这个备注的账改成别的分类，用得多的那个会胜出；或者在上面加一条规则（规则最优先）。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            item {
                GroupLabel("内置的常用词")
                SoftCard(padding = 14.dp) {
                    Categorizer.examples(type).filter { it.first in cats }.forEach { (cat, words) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryBadge(cat, 26)
                            Text("  $cat", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("  $words…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MappingRow(word: String, category: String, trailing: String?, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(104.dp)) {
            Text(
                word, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 10.dp).size(16.dp))
        CategoryBadge(category, 28)
        Text("  $category", modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
        if (onDelete != null) IconButton(onDelete) { Icon(Icons.Filled.Close, "删掉", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}
