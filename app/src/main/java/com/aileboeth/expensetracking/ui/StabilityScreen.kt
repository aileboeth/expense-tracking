package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aileboeth.expensetracking.R

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
    SubPage(stringResource(R.string.set_stability), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Column {
                        Text(if (guide.brandRes == R.string.brand_other) guide.brandName(ctx).replaceFirstChar { it.uppercase() } else stringResource(R.string.st_phone, guide.brandName(ctx)), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${guide.system} · Android ${Build.VERSION.RELEASE} · ${Build.MODEL}",
                            color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (ok) stringResource(R.string.st_ok) else stringResource(R.string.st_missing),
                            color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }

            item {
                GroupLabel(stringResource(R.string.st_status))
                SettingGroup(
                    { SettingRow(Icons.Filled.Notifications, Tint.Purple, stringResource(R.string.set_listener), stringResource(R.string.set_listener_sub), { StatusPill(granted) }) { Perms.openListener(ctx) } },
                    {
                        SettingRow(Icons.Filled.Cable, Tint.Teal, stringResource(R.string.st_connected), if (connected) stringResource(R.string.st_connected_yes) else stringResource(R.string.st_connected_no),
                            { StatusPill(connected, stringResource(R.string.st_pill_connected), stringResource(R.string.st_pill_connect)) }) {
                            ListenerKeeper.force(ctx)
                            scope.launch { delay(2500); tick++ }
                        }
                    },
                    { SettingRow(Icons.Filled.PictureInPictureAlt, Tint.Blue, stringResource(R.string.set_overlay), stringResource(R.string.set_overlay_sub), { StatusPill(overlay) }) { Perms.openOverlay(ctx) } },
                    { SettingRow(Icons.Filled.BatteryChargingFull, Tint.Green, stringResource(R.string.set_battery), stringResource(R.string.set_battery_sub), { StatusPill(battery) }) { Perms.openBattery(ctx) } },
                    {
                        SettingRow(
                            Icons.Filled.Receipt, Tint.Orange, stringResource(R.string.st_last_payment),
                            lastPayment?.let { ago(ctx, it.time) + " · " + (if (it.pkg == Parser.WECHAT) stringResource(R.string.ch_wechat) else stringResource(R.string.ch_alipay)) } ?: stringResource(R.string.st_never),
                            { },
                        ) { }
                    },
                )
            }

            item {
                GroupLabel(stringResource(R.string.st_selftest))
                SoftCard(padding = 16.dp) {
                    Text(stringResource(R.string.st_selftest_body), style = MaterialTheme.typography.bodyMedium)
                    Button(
                        ::startTest, enabled = !testing,
                        colors = ButtonDefaults.buttonColors(containerColor = Brand), shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(48.dp),
                    ) {
                        if (testing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.st_start))
                    }
                    result?.let { r ->
                        val (good, msg) = when (r) {
                            is SelfTest.Result.Ok -> true to stringResource(R.string.st_result_ok, "%.1f".format(r.ms / 1000f))
                            SelfTest.Result.NoListenerPermission -> false to stringResource(R.string.st_result_no_listener)
                            SelfTest.Result.CannotPost -> false to stringResource(R.string.st_result_cannot_post)
                            SelfTest.Result.NotSeen -> false to stringResource(R.string.st_result_not_seen)
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
                GroupLabel(stringResource(R.string.st_guide, guide.brandName(ctx)), stringResource(R.string.st_guide_once))
                SoftCard(padding = 0.dp) {
                    guide.steps.forEachIndexed { i, step ->
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Text("${i + 1}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(stringResource(step.title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(stringResource(step.where), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp))
                                if (step.targets.isNotEmpty()) FilledTonalButton(
                                    { if (!Brands.open(ctx, step)) Toast.makeText(ctx, ctx.getString(R.string.st_cannot_open), Toast.LENGTH_LONG).show() },
                                    modifier = Modifier.padding(top = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                ) { Text(stringResource(R.string.go_set)) }
                            }
                        }
                        if (i != guide.steps.lastIndex) RowDivider()
                    }
                }
                Text(
                    stringResource(R.string.st_guide_footer),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            item {
                GroupLabel(stringResource(R.string.st_still_missing))
                SettingGroup(
                    { SettingRow(Icons.Filled.Share, Tint.Purple, stringResource(R.string.st_diag), stringResource(R.string.st_diag_sub)) { diagDialog = true } },
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
        title = { Text(stringResource(R.string.st_diag)) },
        text = {
            Column {
                Text(stringResource(R.string.st_diag_body), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.st_diag_text), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.st_diag_text_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            }) { Text(stringResource(R.string.st_diag_send)) }
        },
        dismissButton = {
            TextButton({
                scope.launch {
                    val msg = withContext(Dispatchers.IO) { runCatching { Diagnostics.save(ctx, Diagnostics.report(ctx, withText)) } }
                    Toast.makeText(ctx, msg.fold({ ctx.getString(R.string.st_diag_saved, it) }, { ctx.getString(R.string.st_diag_save_failed, it.message ?: "") }), Toast.LENGTH_LONG).show()
                    onClose()
                }
            }) { Text(stringResource(R.string.st_diag_save)) }
        },
    )
}

private fun ago(ctx: android.content.Context, t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> ctx.getString(R.string.ago_now)
        m < 60 -> ctx.getString(R.string.ago_min, m.toInt())
        m < 60 * 24 -> ctx.getString(R.string.ago_hour, (m / 60).toInt())
        else -> ctx.resources.getQuantityString(R.plurals.ago_day, (m / 60 / 24).toInt(), (m / 60 / 24).toInt())
    }
}
