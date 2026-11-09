package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aileboeth.expensetracking.R

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wallet
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
    openCategories: () -> Unit = {},
    openRules: () -> Unit = {},
    openStability: () -> Unit = {},
    openUsual: () -> Unit = {},
    openPayApps: () -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
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
    val payApps = remember(dialog, tick) { activePayApps(ctx, prefs) }
    val currency = remember(dialog, tick) { prefs.currency }
    val language = remember(dialog, tick) { AppLocale.current(ctx) }
    val (versionName, _) = remember { Updater.currentVersion(ctx) }
    val brand = remember { com.aileboeth.expensetracking.capture.Brands.current() }
    val pickBill = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) onImport(uri) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item { StatusCard(listOf(listener, overlay, battery)) }

        item { Section(stringResource(R.string.set_sec_auto)) }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Notifications, Tint.Purple, stringResource(R.string.set_listener), stringResource(R.string.set_listener_sub), { StatusPill(listener) }) { Perms.openListener(ctx) } },
                { SettingRow(Icons.Filled.PictureInPictureAlt, Tint.Blue, stringResource(R.string.set_overlay), stringResource(R.string.set_overlay_sub), { StatusPill(overlay) }) { Perms.openOverlay(ctx) } },
                { SettingRow(Icons.Filled.BatteryChargingFull, Tint.Green, stringResource(R.string.set_battery), stringResource(R.string.set_battery_sub), { StatusPill(battery) }) { Perms.openBattery(ctx) } },
                { SettingRow(Icons.Filled.Bolt, Tint.Orange, stringResource(R.string.set_stability), stringResource(R.string.set_stability_sub, brand.brandName(ctx)), onClick = openStability) },
                {
                    SettingRow(Icons.Filled.Sync, Tint.Teal, stringResource(R.string.set_rebind), stringResource(R.string.set_rebind_sub), { }) {
                        ListenerKeeper.force(ctx); Toast.makeText(ctx, ctx.getString(R.string.set_rebind_done), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
        item { Hint(stringResource(R.string.set_restricted_hint)) }

        item { Section(stringResource(R.string.set_sec_capsule)) }
        item {
            SettingGroup(
                { SettingSwitch(Icons.Filled.TouchApp, Tint.Purple, stringResource(R.string.set_capsule_show), stringResource(R.string.set_capsule_show_sub), bubbleOn) { bubbleOn = it; prefs.bubbleEnabled = it } },
                {
                    Column {
                        SettingRow(Icons.Filled.Timer, Tint.Blue, stringResource(R.string.set_autohide), stringResource(R.string.set_autohide_sub), { }) { }
                        Row(
                            Modifier.padding(start = 64.dp, end = 14.dp, bottom = 12.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),
                        ) {
                            for (s in SECONDS) {
                                val on = seconds == s
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(50))
                                        .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable { seconds = s; prefs.bubbleSeconds = s }.padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.n_seconds, s), softWrap = false, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
                { SettingRow(Icons.Filled.Payments, Tint.Green, stringResource(R.string.set_usual), stringResource(R.string.set_usual_sub), onClick = openUsual) },
                {
                    SettingRow(Icons.Filled.PendingActions, Tint.Orange, stringResource(R.string.set_show_pending), stringResource(R.string.set_show_pending_sub)) {
                        if (!Perms.overlay(ctx)) Toast.makeText(ctx, ctx.getString(R.string.set_need_overlay), Toast.LENGTH_SHORT).show()
                        else scope.launch {
                            val n = withContext(Dispatchers.IO) { Repo.pending().size }
                            if (n == 0) Toast.makeText(ctx, ctx.getString(R.string.set_no_pending), Toast.LENGTH_SHORT).show() else Bubble.showPending(ctx)
                        }
                    }
                },
            )
        }

        item { Section(stringResource(R.string.set_sec_detect)) }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Wallet, Tint.Green, stringResource(R.string.set_pay_apps), stringResource(R.string.set_pay_apps_sub), { ValueChevron(stringResource(R.string.set_pay_apps_value, payApps)) }, openPayApps) },
                { SettingRow(Icons.Filled.Block, Tint.Red, stringResource(R.string.set_black_apps), stringResource(R.string.set_black_apps_sub), { ValueChevron(if (blackApps == 0) stringResource(R.string.not_set) else stringResource(R.string.n_items, blackApps)) }, openBlacklist) },
                { SettingRow(Icons.AutoMirrored.Filled.Label, Tint.Pink, stringResource(R.string.set_black_words), stringResource(R.string.set_black_words_sub), { ValueChevron(if (blackWords == 0) stringResource(R.string.not_set) else stringResource(R.string.n_items, blackWords)) }, openWords) },
                { SettingSwitch(Icons.Filled.Apps, Tint.Blue, stringResource(R.string.set_other_apps), stringResource(R.string.set_other_apps_sub), otherApps) { otherApps = it; prefs.otherApps = it } },
                { SettingRow(Icons.AutoMirrored.Filled.ReceiptLong, Tint.Teal, stringResource(R.string.set_log), stringResource(R.string.set_log_sub), onClick = openLog) },
            )
        }

        item { Section(stringResource(R.string.set_sec_ledger)) }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Savings, Tint.Green, stringResource(R.string.set_budget), stringResource(R.string.set_budget_sub), { ValueChevron(if (budget > 0) Money.sym + Money.short(budget) else stringResource(R.string.not_set)) }) { dialog = "budget" } },
                { SettingRow(Icons.Filled.CurrencyExchange, Tint.Pink, stringResource(R.string.set_currency), stringResource(R.string.set_currency_sub), { ValueChevron(currencyLabel(currency)) }) { dialog = "currency" } },
                { SettingRow(Icons.Filled.UploadFile, Tint.Blue, stringResource(R.string.set_import), stringResource(R.string.set_import_sub)) { pickBill.launch(arrayOf("*/*")) } },
                { SettingRow(Icons.Filled.Category, Tint.Orange, stringResource(R.string.set_categories), stringResource(R.string.set_categories_sub), onClick = openCategories) },
                { SettingRow(Icons.Filled.AutoFixHigh, Tint.Purple, stringResource(R.string.set_rules), stringResource(R.string.set_rules_sub), onClick = openRules) },
                {
                    SettingRow(Icons.Filled.Download, Tint.Teal, stringResource(R.string.set_export), stringResource(R.string.set_export_sub)) {
                        scope.launch {
                            val msg = withContext(Dispatchers.IO) { runCatching { Export.csv(ctx) } }
                            Toast.makeText(ctx, msg.fold({ ctx.getString(R.string.set_exported, it) }, { ctx.getString(R.string.set_export_failed, it.message ?: "") }), Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }

        item { Section(stringResource(R.string.set_sec_general)) }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.Translate, Tint.Blue, stringResource(R.string.set_language), null, { ValueChevron(AppLocale.label(ctx, language)) }) { dialog = "language" } },
            )
        }

        item { Section(stringResource(R.string.set_sec_update)) }
        item {
            SettingGroup(
                { SettingRow(Icons.Filled.SystemUpdate, Tint.Purple, stringResource(R.string.set_check_update), null, { ValueChevron("v$versionName") }) { Updater.check(ctx, manual = true) } },
                { SettingSwitch(Icons.Filled.Autorenew, Tint.Blue, stringResource(R.string.set_auto_update), stringResource(R.string.set_auto_update_sub), autoUpdate) { autoUpdate = it; prefs.autoUpdate = it } },
                { SettingRow(Icons.Filled.Public, Tint.Grey, stringResource(R.string.set_mirror), mirror.ifBlank { stringResource(R.string.set_mirror_none) }) { dialog = "mirror" } },
            )
        }
        item {
            Text(
                "Expense Tracking $versionName\n" + stringResource(R.string.set_footer),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }

    when (dialog) {
        "mirror" -> MirrorDialog(prefs) { dialog = "" }
        "budget" -> BudgetDialog(prefs) { dialog = ""; Widgets.scheduleUpdate(ctx) }
        "currency" -> CurrencyDialog(prefs) { dialog = ""; Widgets.scheduleUpdate(ctx) }
        "language" -> LanguageDialog { dialog = "" }
    }
}

/** Gradient card on top: is automatic recording ready? */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(perms: List<Boolean>) {
    val missing = perms.count { !it }
    val ready = missing == 0
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (ready) stringResource(R.string.set_status_ok) else pluralStringResource(R.plurals.set_status_missing, missing, missing),
                    color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (ready) stringResource(R.string.set_status_ok_sub) else stringResource(R.string.set_status_missing_sub),
                    color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(stringResource(R.string.set_pill_listener), stringResource(R.string.set_pill_overlay), stringResource(R.string.set_pill_battery)).zip(perms).forEach { (name, ok) ->
                        Text(
                            (if (ok) "✓ " else "✕ ") + name, color = if (ok) Color.White else Color(0xFFFFD6CF),
                            style = MaterialTheme.typography.labelMedium, softWrap = false,
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

@Composable
private fun MirrorDialog(prefs: Prefs, onClose: () -> Unit) {
    var value by remember { mutableStateOf(prefs.mirrorPrefix) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.set_mirror)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.set_mirror_body),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(value, { value = it }, singleLine = true, placeholder = { Text(stringResource(R.string.set_mirror_placeholder)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton({
                val v = value.trim()
                prefs.mirrorPrefix = if (v.isEmpty() || v.endsWith("/")) v else "$v/"
                onClose()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
