package com.aileboeth.expensetracking.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

private data class LaunchApp(val pkg: String, val label: String)

/** What the page needs, loaded off the main thread. */
private class AppsData(
    val apps: List<LaunchApp>,
    /** Non-WeChat/Alipay apps that recently posted money-looking notifications (from the log). */
    val recent: List<String>,
    /** How many notifications each blacklisted app had blocked (from the log, last 300 entries). */
    val blocked: Map<String, Int>,
)

private const val BLOCKED = "黑名单 App，不记"

/**
 * Full-screen picker for the app blacklist. Every switch is saved at once, so the count on the
 * settings page is right as soon as you go back.
 */
@Composable
fun BlacklistAppsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var chosen by remember { mutableStateOf(prefs.blacklistApps) }
    var otherApps by remember { mutableStateOf(prefs.otherApps) }
    var filter by remember { mutableStateOf("") }
    var confirmKnown by remember { mutableStateOf<LaunchApp?>(null) }
    // Order is fixed when the page opens, so a row does not jump away when you flip its switch.
    val initial = remember { prefs.blacklistApps }

    val data = repoQuery(initial = null as AppsData?) { loadApps(ctx, initial) }
    val labels = remember(data) { data?.apps?.associate { it.pkg to it.label }.orEmpty() }

    fun set(pkg: String, on: Boolean) {
        chosen = if (on) chosen + pkg else chosen - pkg
        prefs.blacklistApps = chosen
    }
    fun toggle(app: LaunchApp, on: Boolean) {
        if (on && app.pkg in Parser.KNOWN_APPS) confirmKnown = app else set(app.pkg, on)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("黑名单 App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                item {
                    HeaderCard(chosen.toList().sortedBy { labels[it] ?: it }, labels, data?.blocked.orEmpty()) { set(it, false) }
                }
                if (!otherApps) item {
                    InfoCard { otherApps = true; prefs.otherApps = true }
                }
                item {
                    OutlinedTextField(
                        value = filter, onValueChange = { filter = it },
                        placeholder = { Text("搜 App 名称或包名") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = { if (filter.isNotEmpty()) IconButton({ filter = "" }) { Icon(Icons.Filled.Clear, "清空") } },
                        singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                }

                if (data == null) {
                    item { Text("正在读取已安装的 App…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    return@LazyColumn
                }
                val q = filter.trim()
                val matches = { a: LaunchApp -> q.isEmpty() || a.label.contains(q, true) || a.pkg.contains(q, true) }

                val recent = if (q.isEmpty()) data.recent.mapNotNull { p -> data.apps.find { it.pkg == p } } else emptyList()
                if (recent.isNotEmpty()) {
                    item { GroupTitle("最近发过金额通知", "不想记的就拉黑") }
                    itemsIndexed(recent, key = { _, a -> "r:" + a.pkg }) { i, app ->
                        AppRow(app, i, recent.size, app.pkg in chosen, data.blocked[app.pkg] ?: 0) { toggle(app, it) }
                    }
                }

                val list = data.apps.filter(matches)
                item { GroupTitle(if (q.isEmpty()) "全部 App" else "搜索结果", "${list.size} 个") }
                if (list.isEmpty()) item {
                    Text("没有找到「$q」", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                itemsIndexed(list, key = { _, a -> a.pkg }) { i, app ->
                    AppRow(app, i, list.size, app.pkg in chosen, data.blocked[app.pkg] ?: 0) { toggle(app, it) }
                }
                item { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }

    confirmKnown?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmKnown = null },
            title = { Text("拉黑${app.label}？") },
            text = { Text("${app.label}的付款、收款就都不会自动记账了。只是不想记某个商户或付款方的话，用「黑名单关键词」更合适。") },
            confirmButton = { TextButton({ set(app.pkg, true); confirmKnown = null }) { Text("仍然拉黑", color = ExpenseRed) } },
            dismissButton = { TextButton({ confirmKnown = null }) { Text("取消") } },
        )
    }
}

private fun loadApps(ctx: Context, chosenAtOpen: Set<String>): AppsData {
    val pm = ctx.packageManager
    val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val collator = Collator.getInstance(Locale.CHINA)
    val apps = pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
        .map { LaunchApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedWith(
            compareByDescending<LaunchApp> { it.pkg in chosenAtOpen }
                .thenByDescending { it.pkg in Parser.KNOWN_APPS }
                .thenComparator { a, b -> collator.compare(a.label, b.label) },
        )
    val logs = Repo.logs()
    val installed = apps.mapTo(HashSet()) { it.pkg }
    val recent = logs.asSequence()
        .map { it.pkg }
        .filter { it !in Parser.KNOWN_APPS && it in installed }
        .distinct().take(6).toList()
    val blocked = logs.filter { it.result == BLOCKED }.groupingBy { it.pkg }.eachCount()
    return AppsData(apps, recent, blocked)
}

@Composable
private fun HeaderCard(chosen: List<String>, labels: Map<String, String>, blocked: Map<String, Int>, onRemove: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(vertical = 18.dp)) {
        Column {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                Text("${chosen.size}", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(" 个 App 已拉黑", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                Spacer(Modifier.weight(1f))
                val total = chosen.sumOf { blocked[it] ?: 0 }
                if (total > 0) Text(
                    "已拦下 $total 条通知", color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Text(
                "它们的通知一律不记账，开关一动就生效。",
                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))
            if (chosen.isEmpty()) {
                Text(
                    "还没有。在下面打开 App 右边的开关就行。",
                    color = Color.White, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.16f))
                        .fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                )
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(chosen, key = { it }) { pkg ->
                        Column(
                            Modifier.width(56.dp).clickable { onRemove(pkg) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                                    AppIcon(pkg, labels[pkg] ?: pkg, 40.dp)
                                }
                                Box(
                                    Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(18.dp)
                                        .clip(CircleShape).background(Color(0xFF2A1C8F)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Filled.Close, "移出黑名单", tint = Color.White, modifier = Modifier.size(12.dp)) }
                            }
                            Text(
                                labels[pkg] ?: pkg.substringAfterLast('.'), color = Color.White, fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(onEnable: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(
            "现在只识别微信、支付宝，其他 App 本来就不会记。打开「也识别其他 App」后，黑名单才对它们起作用。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
        )
        TextButton(onEnable) { Text("打开") }
    }
}

@Composable
private fun GroupTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One row of a rounded group: only the first and last rows round their outer corners. */
@Composable
private fun AppRow(app: LaunchApp, index: Int, count: Int, on: Boolean, blocked: Int, onChange: (Boolean) -> Unit) {
    val r = 20.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp, bottomEnd = if (index == count - 1) r else 0.dp,
    )
    val known = app.pkg in Parser.KNOWN_APPS
    Column(Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.pkg, app.label, 42.dp)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (sub, color) = when {
                    known && on -> "付款来源 · 已拉黑，不会自动记账" to ExpenseRed
                    known -> "付款来源 · 自动记账靠它" to MaterialTheme.colorScheme.primary
                    on && blocked > 0 -> "已拦下 $blocked 条通知" to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> app.pkg to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(on, onChange)
        }
        if (index != count - 1) HorizontalDivider(Modifier.padding(start = 70.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

private val iconCache = LruCache<String, ImageBitmap>(160)

/** The app's launcher icon, loaded in the background; a coloured letter until it is ready or if it cannot be read. */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = "$pkg@$px"
    val bmp by produceState(iconCache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap() }
                .getOrNull()?.also { iconCache.put(key, it) }
        }
    }
    val b = bmp
    if (b != null) {
        Image(b, label, Modifier.size(size))
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}
