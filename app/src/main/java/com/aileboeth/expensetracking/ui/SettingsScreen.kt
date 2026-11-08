package com.aileboeth.expensetracking.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.widget.Widgets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.LogEntry
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.Parser
import com.aileboeth.expensetracking.parse.TxType
import com.aileboeth.expensetracking.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(padding: PaddingValues, onImport: (android.net.Uri) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val listener = remember(tick) { Perms.listener(ctx) }
    val overlay = remember(tick) { Perms.overlay(ctx) }
    val battery = remember(tick) { Perms.battery(ctx) }

    var bubbleOn by remember { mutableStateOf(prefs.bubbleEnabled) }
    var seconds by remember { mutableFloatStateOf(prefs.bubbleSeconds.toFloat()) }
    var otherApps by remember { mutableStateOf(prefs.otherApps) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdate) }
    var dialog by remember { mutableStateOf("") }
    val (versionName, _) = remember { Updater.currentVersion(ctx) }
    val pickBill = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onImport(uri) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
        item { Section("自动记账") }
        item { SoftCard(padding = 4.dp) {
            PermRow("通知使用权", "读取微信、支付宝的付款通知（必需）", listener) { Perms.openListener(ctx) }
            PermRow("悬浮窗", "付款后在屏幕边上显示小圆球", overlay) { Perms.openOverlay(ctx) }
            PermRow("忽略电池优化", "减少后台被系统停掉", battery) { Perms.openBattery(ctx) }
            Row2("后台耗电 / 自启动", "vivo：应用详情里允许「后台高耗电」和「自启动」，监听才稳定") { Perms.openAppDetails(ctx) }
            Hint("如果「通知使用权」开不了（提示受限设置）：应用详情 → 右上角 ⋮ → 允许受限设置，再回来开。")
        } }

        item { Section("悬浮球") }
        item { SoftCard(padding = 4.dp) {
            SwitchRow("付款后显示悬浮球", "小圆球，不挡你正在用的 App，点开能写用途", bubbleOn) {
                bubbleOn = it; prefs.bubbleEnabled = it
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("有金额的账：${seconds.toInt()} 秒后自动收起（没金额的会一直留着）", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = seconds, onValueChange = { seconds = it },
                    onValueChangeFinished = { prefs.bubbleSeconds = seconds.toInt() },
                    valueRange = 2f..30f,
                )
            }
            Row2("显示待补金额的悬浮球", "把还没填金额的账再弹出来") {
                if (!Perms.overlay(ctx)) Toast.makeText(ctx, "先打开悬浮窗权限", Toast.LENGTH_SHORT).show()
                else scope.launch {
                    val n = withContext(Dispatchers.IO) { Repo.pending().size }
                    if (n == 0) Toast.makeText(ctx, "没有待补的账", Toast.LENGTH_SHORT).show() else Bubble.showPending(ctx)
                }
            }
        } }

        item { Section("识别") }
        item { SoftCard(padding = 4.dp) {
            Row2("黑名单 App", "${prefs.blacklistApps.size} 个 · 这些 App 的通知不会记账") { dialog = "apps" }
            Row2("黑名单关键词", "${prefs.blacklistWords.size} 个 · 付款方/商户/内容含这些词就不记（如「多点科技」）") { dialog = "words" }
            SwitchRow("也识别其他 App 的收支通知", "默认只认微信、支付宝；打开后其他 App 的金额通知也会弹出来，要你确认才记", otherApps) {
                otherApps = it; prefs.otherApps = it
            }
            Row2("识别记录", "最近收到的付款类通知，以及记了还是没记、为什么") { dialog = "log" }
        } }

        item { Section("分类和数据") }
        item { SoftCard(padding = 4.dp) {
            Row2("月预算", if (prefs.budgetCents > 0) "每月 ¥${Money.format(prefs.budgetCents)}" else "未设置 · 首页会显示还能花多少") { dialog = "budget" }
            Row2("导入微信/支付宝账单对账", "选导出的 .xlsx / .csv：找出漏记的，补上手表付款的金额") { pickBill.launch(arrayOf("*/*")) }
            Row2("管理分类", "增加或删除支出、收入的分类") { dialog = "cats" }
            Row2("导出 CSV", "全部账单导出到 下载/记账/，可用 Excel / WPS 打开") {
                scope.launch {
                    val msg = withContext(Dispatchers.IO) { runCatching { Export.csv(ctx) } }
                    Toast.makeText(ctx, msg.fold({ "已导出到 $it" }, { "导出失败：${it.message}" }), Toast.LENGTH_LONG).show()
                }
            }
        } }

        item { Section("更新") }
        item { SoftCard(padding = 4.dp) {
            Row2("检查更新", "当前版本 $versionName") { Updater.check(ctx, manual = true) }
            SwitchRow("自动检查更新", "打开 App 时检查（最多 12 小时一次）", autoUpdate) {
                autoUpdate = it; prefs.autoUpdate = it
            }
            Row2("下载加速前缀", prefs.mirrorPrefix.ifBlank { "不使用（直连 GitHub）" }) { dialog = "mirror" }
            Hint("更新从 github.com/${Updater.REPO} 的最新 Release 下载。账单数据只存在手机里，不会上传。")
        } }
    }

    when (dialog) {
        "apps" -> BlacklistAppsDialog(prefs) { dialog = "" }
        "words" -> WordsDialog(prefs) { dialog = "" }
        "log" -> LogDialog { dialog = "" }
        "cats" -> CategoriesDialog { dialog = "" }
        "mirror" -> MirrorDialog(prefs) { dialog = "" }
        "budget" -> BudgetDialog(prefs) { dialog = ""; Widgets.scheduleUpdate(ctx) }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Row2(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (ok) "已开启" else "去开启",
            color = if (ok) IncomeGreen else ExpenseRed, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

// ---- dialogs --------------------------------------------------------------------------------

private data class AppItem(val pkg: String, val label: String)

@Composable
private fun BlacklistAppsDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var chosen by remember { mutableStateOf(prefs.blacklistApps) }
    var filter by remember { mutableStateOf("") }
    val apps = repoQuery(initial = emptyList<AppItem>()) {
        val pm = ctx.packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareByDescending<AppItem> { it.pkg in prefs.blacklistApps }.thenBy { it.label })
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("黑名单 App") },
        text = {
            Column {
                Text(
                    "勾选的 App，它们的通知一律不记账。" +
                        if (chosen.any { it in Parser.KNOWN_APPS }) "\n注意：你拉黑了微信或支付宝，它们的付款也不会自动记了。" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(filter, { filter = it }, label = { Text("搜索") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(apps.filter { filter.isBlank() || it.label.contains(filter, true) || it.pkg.contains(filter, true) }, key = { it.pkg }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                chosen = if (app.pkg in chosen) chosen - app.pkg else chosen + app.pkg
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(app.pkg in chosen, null)
                            Column {
                                Text(app.label)
                                Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ prefs.blacklistApps = chosen; onClose() }) { Text("保存") } },
        dismissButton = { TextButton(onClose) { Text("取消") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordsDialog(prefs: Prefs, onClose: () -> Unit) {
    var words by remember { mutableStateOf(prefs.blacklistWords) }
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("黑名单关键词") },
        text = {
            Column {
                Text("付款方、商户或通知内容里含有这些词，就不记账。点词可以删掉。", style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, label = { Text("新关键词") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton({
                        val w = input.trim()
                        if (w.isNotEmpty()) words = words + w
                        input = ""
                    }) { Text("添加") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (w in words.sorted()) InputChip(selected = false, onClick = { words = words - w }, label = { Text("$w  ×") })
                }
            }
        },
        confirmButton = { TextButton({ prefs.blacklistWords = words; onClose() }) { Text("保存") } },
        dismissButton = { TextButton(onClose) { Text("取消") } },
    )
}

@Composable
private fun LogDialog(onClose: () -> Unit) {
    val logs = repoQuery(initial = emptyList<LogEntry>()) { Repo.logs() }
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("识别记录") },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                if (logs.isEmpty()) item { Text("还没有。付一笔钱后再来看。") }
                items(logs, key = { it.id }) { l ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text("${fmt.format(Date(l.time))}  ${appName(l.pkg)}  ${l.result}", style = MaterialTheme.typography.labelMedium)
                        Text("${l.title}：${l.text}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("关闭") } },
    )
}

private fun appName(pkg: String) = when (pkg) {
    Parser.WECHAT -> "微信"
    Parser.ALIPAY -> "支付宝"
    else -> pkg
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var input by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val cats = repoQuery(type, initial = emptyList<String>()) { Repo.categories(type) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("管理分类") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                    SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, label = { Text("新分类") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton({
                        val n = input.trim(); input = ""
                        if (n.isNotEmpty()) scope.launch(Dispatchers.IO) { Repo.addCategory(type, n) }
                    }) { Text("添加") }
                }
                Text("点分类可以删除（已记的账不受影响）", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in cats) InputChip(selected = false, onClick = { confirmDelete = c }, label = { Text(c) })
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("完成") } },
    )
    confirmDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除分类「$name」？") },
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
private fun MirrorDialog(prefs: Prefs, onClose: () -> Unit) {
    var value by remember { mutableStateOf(prefs.mirrorPrefix) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("下载加速前缀") },
        text = {
            Column {
                Text(
                    "手机连 GitHub 很慢或连不上时，填一个 GitHub 下载加速地址，它会加在 github.com 链接前面，" +
                        "例如填 https://某加速站/ 。留空 = 直连。\n安装包会校验 SHA-256，系统也会校验签名，被改过的包装不上。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text("留空 = 直连") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton({
                val v = value.trim()
                prefs.mirrorPrefix = if (v.isEmpty() || v.endsWith("/")) v else "$v/"
                onClose()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClose) { Text("取消") } },
    )
}
