package com.aileboeth.expensetracking.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.TxType
import com.aileboeth.expensetracking.update.Updater
import com.aileboeth.expensetracking.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SECONDS = listOf(3, 6, 10, 20)

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    onImport: (android.net.Uri) -> Unit,
    openBlacklist: () -> Unit = {},
    openWords: () -> Unit = {},
    openLog: () -> Unit = {},
    openUsual: Boolean = false,
    onUsualOpened: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val listener = remember(tick) { Perms.listener(ctx) }
    val overlay = remember(tick) { Perms.overlay(ctx) }
    val battery = remember(tick) { Perms.battery(ctx) }

    var bubbleOn by remember { mutableStateOf(prefs.bubbleEnabled) }
    var seconds by remember { mutableIntStateOf(prefs.bubbleSeconds) }
    var otherApps by remember { mutableStateOf(prefs.otherApps) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdate) }
    var dialog by remember { mutableStateOf("") }
    // Re-read after any dialog closes or the page comes back, so the values below are never stale.
    val blackApps = remember(dialog, tick) { prefs.blacklistApps.size }
    val blackWords = remember(dialog, tick) { prefs.blacklistWords.size }
    val budget = remember(dialog, tick) { prefs.budgetCents }
    val mirror = remember(dialog, tick) { prefs.mirrorPrefix }
    LaunchedEffect(openUsual) { if (openUsual) { dialog = "usual"; onUsualOpened() } }
    val (versionName, _) = remember { Updater.currentVersion(ctx) }
    val pickBill = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onImport(uri) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item { StatusCard(listOf(listener, overlay, battery)) }

        item { Section("自动记账") }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Notifications, Tint.Purple, "通知使用权", "读取微信、支付宝的付款通知（必需）", { StatusPill(listener) }) { Perms.openListener(ctx) } },
                { SettingRow(Icons.Filled.PictureInPictureAlt, Tint.Blue, "悬浮窗", "付款后在屏幕边上显示记账胶囊", { StatusPill(overlay) }) { Perms.openOverlay(ctx) } },
                { SettingRow(Icons.Filled.BatteryChargingFull, Tint.Green, "忽略电池优化", "减少后台被系统停掉", { StatusPill(battery) }) { Perms.openBattery(ctx) } },
                { SettingRow(Icons.Filled.Bolt, Tint.Orange, "后台耗电 / 自启动", "vivo：应用详情里允许「后台高耗电」和「自启动」") { Perms.openAppDetails(ctx) } },
                {
                    SettingRow(Icons.Filled.Sync, Tint.Teal, "重新连接通知监听", "发现漏记时点一下；App 更新后打开一次也会自动重连", { }) {
                        ListenerKeeper.force(ctx); Toast.makeText(ctx, "已重新连接", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        item { Hint("「通知使用权」开不了（提示受限设置）时：应用详情 → 右上角 ⋮ → 允许受限设置，再回来开。") }

        item { Section("悬浮胶囊") }
        item {
            SettingGroup(
                { SettingSwitch(Icons.Filled.TouchApp, Tint.Purple, "付款后显示", "贴在屏幕边上，不挡你正在用的 App", bubbleOn) { bubbleOn = it; prefs.bubbleEnabled = it } },
                {
                    Column {
                        SettingRow(Icons.Filled.Timer, Tint.Blue, "自动收起", "有金额的账多久后收起；没金额的会一直留着", { }) { }
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 64.dp, end = 12.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (s in SECONDS) FilterChip(
                                selected = seconds == s,
                                onClick = { seconds = s; prefs.bubbleSeconds = s },
                                label = { Text("$s 秒") },
                            )
                        }
                    }
                },
                { SettingRow(Icons.Filled.Payments, Tint.Green, "常用金额", "手表付款等没金额的账，点一下就能选") { dialog = "usual" } },
                {
                    SettingRow(Icons.Filled.PendingActions, Tint.Orange, "弹出待补金额的胶囊", "把还没填金额的账再弹出来") {
                        if (!Perms.overlay(ctx)) Toast.makeText(ctx, "先打开悬浮窗权限", Toast.LENGTH_SHORT).show()
                        else scope.launch {
                            val n = withContext(Dispatchers.IO) { Repo.pending().size }
                            if (n == 0) Toast.makeText(ctx, "没有待补的账", Toast.LENGTH_SHORT).show() else Bubble.showPending(ctx)
                        }
                    }
                },
            )
        }

        item { Section("识别") }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Block, Tint.Red, "黑名单 App", "这些 App 的通知不记账", { ValueChevron(if (blackApps == 0) "未设置" else "$blackApps 个") }, openBlacklist) },
                { SettingRow(Icons.AutoMirrored.Filled.Label, Tint.Pink, "黑名单关键词", "付款方、商户含这些词就不记", { ValueChevron(if (blackWords == 0) "未设置" else "$blackWords 个") }, openWords) },
                { SettingSwitch(Icons.Filled.Apps, Tint.Blue, "也识别其他 App", "默认只认微信、支付宝；打开后其他 App 的金额通知要你确认才记", otherApps) { otherApps = it; prefs.otherApps = it } },
                { SettingRow(Icons.AutoMirrored.Filled.ReceiptLong, Tint.Teal, "识别记录", "最近的付款通知：记了没有、为什么", onClick = openLog) },
            )
        }

        item { Section("账本") }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Savings, Tint.Green, "月预算", "首页会显示还能花多少", { ValueChevron(if (budget > 0) "¥" + Money.short(budget) else "未设置") }) { dialog = "budget" } },
                { SettingRow(Icons.Filled.UploadFile, Tint.Blue, "导入账单对账", "选微信/支付宝导出的 .xlsx / .csv，补上漏记的") { pickBill.launch(arrayOf("*/*")) } },
                { SettingRow(Icons.Filled.Category, Tint.Orange, "管理分类", "增加或删除支出、收入的分类") { dialog = "cats" } },
                {
                    SettingRow(Icons.Filled.Download, Tint.Teal, "导出 CSV", "导出到 下载/记账/，Excel、WPS 都能打开") {
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) { runCatching { Export.csv(ctx) } }
                            Toast.makeText(ctx, msg.fold({ "已导出到 $it" }, { "导出失败：${it.message}" }), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }

        item { Section("更新") }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.SystemUpdate, Tint.Purple, "检查更新", null, { ValueChevron("v$versionName") }) { Updater.check(ctx, manual = true) } },
                { SettingSwitch(Icons.Filled.Autorenew, Tint.Blue, "自动检查更新", "打开 App 时检查，最多 12 小时一次", autoUpdate) { autoUpdate = it; prefs.autoUpdate = it } },
                { SettingRow(Icons.Filled.Public, Tint.Grey, "下载加速前缀", mirror.ifBlank { "不使用，直连 GitHub" }) { dialog = "mirror" } },
            )
        }
        item {
            Text(
                "Expense Tracking $versionName\n账单只存在这台手机里，不会上传。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }

    when (dialog) {
        "cats" -> CategoriesDialog { dialog = "" }
        "mirror" -> MirrorDialog(prefs) { dialog = "" }
        "usual" -> UsualAmountsDialog(prefs) { dialog = "" }
        "budget" -> BudgetDialog(prefs) { dialog = ""; Widgets.scheduleUpdate(ctx) }
    }
}

/** Gradient card on top: is automatic recording ready? */
@Composable
private fun StatusCard(perms: List<Boolean>) {
    val missing = perms.count { !it }
    val ready = missing == 0
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (ready) "自动记账运行中" else "还差 $missing 项设置",
                    color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (ready) "微信、支付宝付款后会自动记上" else "打开下面标红的几项，付款才会自动记",
                    color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("通知", "悬浮窗", "电池").zip(perms).forEach { (name, ok) ->
                        Text(
                            (if (ok) "✓ " else "✕ ") + name, color = if (ok) Color.White else Color(0xFFFFD6CF),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = if (ok) 0.18f else 0.10f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (ready) Icons.Filled.Check else Icons.Filled.PriorityHigh, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun ValueChevron(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

// ---- dialogs --------------------------------------------------------------------------------

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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun UsualAmountsDialog(prefs: Prefs, onClose: () -> Unit) {
    var type by remember { mutableStateOf(TxType.EXPENSE) }
    var list by remember(type) { mutableStateOf(prefs.usualAmounts(type)) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("常用金额") },
        text = {
            Column {
                Text(
                    "没写金额的账（手表付款、转账到账）会把这些金额列出来，点一下就选好。" +
                        "一个都不设时，按你最近最常付的金额自动给出。点金额可以删掉。",
                    style = MaterialTheme.typography.bodySmall,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    SegmentedButton(type == TxType.EXPENSE, { type = TxType.EXPENSE }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("支出") }
                    SegmentedButton(type == TxType.INCOME, { type = TxType.INCOME }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("收入") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (list.isEmpty()) Text("还没设（现在用自动的）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    for (c in list) InputChip(
                        selected = false,
                        onClick = { list = list - c; prefs.setUsualAmounts(type, list) },
                        label = { Text("¥" + Money.short(c) + "  ×") },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        input, { input = it; error = false }, label = { Text("新金额") }, singleLine = true, isError = error,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton({
                        val c = Money.parse(input)
                        if (c == null) error = true else {
                            list = (list + c).distinct(); prefs.setUsualAmounts(type, list); input = ""
                        }
                    }) { Text("添加") }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("完成") } },
    )
}
