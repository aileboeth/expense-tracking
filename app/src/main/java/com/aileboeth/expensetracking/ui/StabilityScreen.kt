package com.aileboeth.expensetracking.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.capture.Brands
import com.aileboeth.expensetracking.capture.CaptureService
import com.aileboeth.expensetracking.capture.Diagnostics
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.capture.SelfTest
import com.aileboeth.expensetracking.data.LogEntry
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Does it work on this phone?": live checks, a test notification, the maker's background settings, a report. */
@Composable
fun StabilityScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val granted = remember(tick) { ListenerKeeper.granted(ctx) }
    val connected = remember(tick) { CaptureService.connected }
    val overlay = remember(tick) { Perms.overlay(ctx) }
    val battery = remember(tick) { Perms.battery(ctx) }
    val lastPayment = repoQuery(tick, initial = null as LogEntry?) { Repo.logs().firstOrNull { it.pkg in Parser.KNOWN_APPS } }
    val guide = remember { Brands.current() }

    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SelfTest.Result?>(null) }
    var diagDialog by remember { mutableStateOf(false) }

    fun runTest() {
        testing = true; result = null
        scope.launch {
            result = SelfTest.run(ctx)
            delay(300)
            testing = false; tick++
        }
    }
    val askPost = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { runTest() }
    fun startTest() {
        if (Build.VERSION.SDK_INT >= 33 && !SelfTest.canPost(ctx)) askPost.launch(Manifest.permission.POST_NOTIFICATIONS) else runTest()
    }

    val ok = granted && connected && overlay && battery
    SubPage("自检和后台设置", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Column {
                        Text("${guide.brand} 手机", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${guide.system} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}",
                            color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (ok) "基本设置都好了。下面的后台设置也做一下，长时间熄屏也不容易漏记。"
                            else "还有标红的项没好，付款可能记不上。",
                            color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            item {
                GroupLabel("现在的状态")
                SettingGroup(
                    { SettingRow(Icons.Filled.Notifications, Tint.Purple, "通知使用权", "读付款通知，必需", { StatusPill(granted) }) { Perms.openListener(ctx) } },
                    {
                        SettingRow(Icons.Filled.Cable, Tint.Teal, "通知监听已连接", if (connected) "系统正在把通知交给记账" else "权限开着但没连上：点一下重新连接",
                            { StatusPill(connected, "已连接", "去连接") }) {
                            ListenerKeeper.force(ctx)
                            scope.launch { delay(2500); tick++ }
                        }
                    },
                    { SettingRow(Icons.Filled.PictureInPictureAlt, Tint.Blue, "悬浮窗", "付款后显示记账胶囊", { StatusPill(overlay) }) { Perms.openOverlay(ctx) } },
                    { SettingRow(Icons.Filled.BatteryChargingFull, Tint.Green, "忽略电池优化", "减少后台被停掉", { StatusPill(battery) }) { Perms.openBattery(ctx) } },
                    {
                        SettingRow(
                            Icons.Filled.Receipt, Tint.Orange, "最近一条付款通知",
                            lastPayment?.let { "${ago(it.time)} · ${if (it.pkg == Parser.WECHAT) "微信" else "支付宝"} · ${it.result}" } ?: "还没收到过",
                            { },
                        ) { }
                    },
                )
            }

            item {
                GroupLabel("自检")
                SoftCard(padding = 16.dp) {
                    Text("发一条测试通知，看记账能不能马上收到。几秒后通知会自己消失，不会记账。", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        ::startTest, enabled = !testing,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand), shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
                    ) {
                        if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("开始自检")
                    }
                    result?.let { r ->
                        val (good, msg) = when (r) {
                            is SelfTest.Result.Ok -> true to "收到了（用了 ${"%.1f".format(r.ms / 1000f)} 秒）。自动记账在这台手机上能正常工作。"
                            SelfTest.Result.NoListenerPermission -> false to "通知使用权没开。点上面的「通知使用权」去打开。"
                            SelfTest.Result.CannotPost -> false to "记账没有发通知的权限，测不了。在系统设置里允许记账发通知后再试（只用来自检）。"
                            SelfTest.Result.NotSeen -> false to "6 秒内没收到。点「通知监听已连接」重连一下再试；还不行就按下面的后台设置做一遍，或者把通知使用权关掉再打开。"
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(14.dp))
                                .background((if (good) IncomeGreen else ExpenseRed).copy(alpha = 0.1f)).padding(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(if (good) "✓" else "!", color = if (good) IncomeGreen else ExpenseRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }

            item {
                GroupLabel("${guide.brand} 的后台设置", "做一次就行")
                SoftCard(padding = 0.dp) {
                    guide.steps.forEachIndexed { i, step ->
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(step.where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp))
                                if (step.targets.isNotEmpty()) FilledTonalButton(
                                    { if (!Brands.open(ctx, step)) Toast.makeText(ctx, "打不开，请按上面的路径手动找", Toast.LENGTH_LONG).show() },
                                    modifier = Modifier.padding(top = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                ) { Text("去设置") }
                            }
                        }
                        if (i != guide.steps.lastIndex) RowDivider()
                    }
                }
                Text(
                    "不同系统版本的菜单名字可能略有不同；「去设置」打不开对应页面时，会打开记账的应用详情页。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            item {
                GroupLabel("还是漏记？")
                SettingGroup(
                    { SettingRow(Icons.Filled.Share, Tint.Purple, "导出诊断信息", "手机型号、系统、权限状态和最近的识别记录，发给开发者看") { diagDialog = true } },
                )
            }
        }
    }

    if (diagDialog) DiagnosticsDialog(onClose = { diagDialog = false })
}

@Composable
private fun DiagnosticsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var withText by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("导出诊断信息") },
        text = {
            Column {
                Text("包含：手机型号、系统版本、各项权限、最近 80 条识别记录（记了没有、为什么）。不包含你的账单金额和备注。", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("附带通知原文", style = MaterialTheme.typography.bodyLarge)
                        Text("没认出的格式要靠原文才能修；原文里可能有付款方名字", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(withText, { withText = it })
                }
            }
        },
        confirmButton = {
            TextButton({
                scope.launch {
                    val text = withContext(Dispatchers.IO) { Diagnostics.report(ctx, withText) }
                    Diagnostics.share(ctx, text)
                    onClose()
                }
            }) { Text("发送…") }
        },
        dismissButton = {
            TextButton({
                scope.launch {
                    val msg = withContext(Dispatchers.IO) { runCatching { Diagnostics.save(ctx, Diagnostics.report(ctx, withText)) } }
                    Toast.makeText(ctx, msg.fold({ "已保存到 $it" }, { "保存失败：${it.message}" }), Toast.LENGTH_LONG).show()
                    onClose()
                }
            }) { Text("保存到手机") }
        },
    )
}

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> "刚刚"
        m < 60 -> "$m 分钟前"
        m < 60 * 24 -> "${m / 60} 小时前"
        else -> "${m / 60 / 24} 天前"
    }
}
