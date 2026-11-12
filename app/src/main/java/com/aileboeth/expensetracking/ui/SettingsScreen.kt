package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.aileboeth.expensetracking.R

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.update.Updater
import com.aileboeth.expensetracking.widget.Widgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SECONDS = listOf(3, 6, 10, 20)

/** The categories on the top settings page; each opens its own page. */
enum class SettingsSection(val title: Int) {
    AUTO(R.string.set_sec_auto),
    CAPSULE(R.string.set_sec_capsule),
    DETECT(R.string.set_sec_detect),
    LEDGER(R.string.set_sec_ledger),
    FUN(R.string.set_sec_fun),
    GENERAL(R.string.set_sec_general),
}

/** Where the rows of a settings category lead. */
class SettingsNav(
    val import: (android.net.Uri) -> Unit = {},
    val blacklist: () -> Unit = {},
    val words: () -> Unit = {},
    val log: () -> Unit = {},
    val categories: () -> Unit = {},
    val rules: () -> Unit = {},
    val stability: () -> Unit = {},
    val usual: () -> Unit = {},
    val payApps: () -> Unit = {},
    val achievements: () -> Unit = {},
    val wish: () -> Unit = {},
)

/** Re-read on resume and whenever a dialog closes, so the values shown are never stale. */
@Composable
private fun rememberTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    return tick
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    openSection: (SettingsSection) -> Unit = {},
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val tick = rememberTick()
    val perms = remember(tick) { listOf(Perms.listener(ctx), Perms.overlay(ctx), Perms.battery(ctx)) }
    val missing = perms.count { !it }
    val budget = remember(tick) { prefs.budgetCents }
    val payApps = remember(tick) { activePayApps(ctx, prefs) }
    val bubbleOn = remember(tick) { prefs.bubbleEnabled }
    val fx by Fx.state
    val (versionName, _) = remember { Updater.currentVersion(ctx) }

    LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)) {
        item { StatusCard(perms) { openSection(SettingsSection.AUTO) } }
        item { GroupLabel(stringResource(R.string.set_categories_label)) }
        item {
            val on = listOf(fx.mascot, fx.haptics, fx.motion, fx.funRow, fx.recapBanner, fx.badgePopup).count { it }
            SettingGroup(
                {
                    SettingRow(Icons.Filled.Notifications, Tint.Purple, stringResource(R.string.set_sec_auto), stringResource(R.string.set_sec_auto_sub),
                        { StatusPill(missing == 0, stringResource(R.string.set_ready), pluralStringResource(R.plurals.set_missing_short, missing, missing)) },
                    ) { openSection(SettingsSection.AUTO) }
                },
                {
                    SettingRow(Icons.Filled.TouchApp, Tint.Blue, stringResource(R.string.set_sec_capsule), stringResource(R.string.set_sec_capsule_sub),
                        { ValueChevron(stringResource(if (bubbleOn) R.string.state_on else R.string.state_off)) },
                    ) { openSection(SettingsSection.CAPSULE) }
                },
                {
                    SettingRow(Icons.Filled.ImageSearch, Tint.Teal, stringResource(R.string.set_sec_detect), stringResource(R.string.set_sec_detect_sub),
                    ) { openSection(SettingsSection.DETECT) }
                },
                {
                    SettingRow(Icons.Filled.MenuBook, Tint.Green, stringResource(R.string.set_sec_ledger), stringResource(R.string.set_sec_ledger_sub),
                        { ValueChevron(if (budget > 0) stringResource(R.string.set_budget_value, Money.sym + Money.short(budget)) else "") },
                    ) { openSection(SettingsSection.LEDGER) }
                },
                {
                    SettingRow(Icons.Filled.AutoAwesome, Tint.Pink, stringResource(R.string.set_sec_fun), stringResource(R.string.set_sec_fun_sub),
                        { ValueChevron(stringResource(R.string.set_fun_value, on, 6)) },
                    ) { openSection(SettingsSection.FUN) }
                },
                {
                    SettingRow(Icons.Filled.Settings, Tint.Grey, stringResource(R.string.set_sec_general), stringResource(R.string.set_sec_general_sub),
                        { ValueChevron("v$versionName") },
                    ) { openSection(SettingsSection.GENERAL) }
                },
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
}

/** One settings category on its own page. */
@Composable
fun SettingsSectionScreen(section: SettingsSection, onBack: () -> Unit, nav: SettingsNav = SettingsNav()) {
    SubPage(stringResource(section.title), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            when (section) {
                SettingsSection.AUTO -> item { AutoSection(nav) }
                SettingsSection.CAPSULE -> item { CapsuleSection(nav) }
                SettingsSection.DETECT -> item { DetectSection(nav) }
                SettingsSection.LEDGER -> item { LedgerSection(nav) }
                SettingsSection.FUN -> item { FunSection(nav) }
                SettingsSection.GENERAL -> item { GeneralSection() }
            }
        }
    }
}

@Composable
private fun AutoSection(nav: SettingsNav) {
    val ctx = LocalContext.current
    val tick = rememberTick()
    val listener = remember(tick) { Perms.listener(ctx) }
    val overlay = remember(tick) { Perms.overlay(ctx) }
    val battery = remember(tick) { Perms.battery(ctx) }
    val brand = remember { com.aileboeth.expensetracking.capture.Brands.current() }
    Column {
        StatusCard(listOf(listener, overlay, battery), Modifier.padding(top = 8.dp))
        GroupLabel(stringResource(R.string.set_group_perms))
        SettingGroup(
            { SettingRow(Icons.Filled.Notifications, Tint.Purple, stringResource(R.string.set_listener), stringResource(R.string.set_listener_sub), { StatusPill(listener) }) { Perms.openListener(ctx) } },
            { SettingRow(Icons.Filled.PictureInPictureAlt, Tint.Blue, stringResource(R.string.set_overlay), stringResource(R.string.set_overlay_sub), { StatusPill(overlay) }) { Perms.openOverlay(ctx) } },
            { SettingRow(Icons.Filled.BatteryChargingFull, Tint.Green, stringResource(R.string.set_battery), stringResource(R.string.set_battery_sub), { StatusPill(battery) }) { Perms.openBattery(ctx) } },
        )
        GroupLabel(stringResource(R.string.set_group_keep))
        SettingGroup(
            { SettingRow(Icons.Filled.Bolt, Tint.Orange, stringResource(R.string.set_stability), stringResource(R.string.set_stability_sub, brand.brandName(ctx)), onClick = nav.stability) },
            {
                SettingRow(Icons.Filled.Sync, Tint.Teal, stringResource(R.string.set_rebind), stringResource(R.string.set_rebind_sub), { }) {
                    ListenerKeeper.force(ctx); Toast.makeText(ctx, ctx.getString(R.string.set_rebind_done), Toast.LENGTH_SHORT).show()
                }
            },
        )
        Hint(stringResource(R.string.set_restricted_hint))
    }
}

@Composable
private fun CapsuleSection(nav: SettingsNav) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var bubbleOn by remember { mutableStateOf(prefs.bubbleEnabled) }
    var seconds by remember { mutableIntStateOf(prefs.bubbleSeconds) }
    Column {
        Hint(stringResource(R.string.set_capsule_intro))
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
            { SettingRow(Icons.Filled.Payments, Tint.Green, stringResource(R.string.set_usual), stringResource(R.string.set_usual_sub), onClick = nav.usual) },
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
}

@Composable
private fun DetectSection(nav: SettingsNav) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val tick = rememberTick()
    var otherApps by remember { mutableStateOf(prefs.otherApps) }
    val blackApps = remember(tick) { prefs.blacklistApps.size }
    val blackWords = remember(tick) { prefs.blacklistWords.size }
    val payApps = remember(tick) { activePayApps(ctx, prefs) }
    Column {
        GroupLabel(stringResource(R.string.set_group_sources))
        SettingGroup(
            { SettingRow(Icons.Filled.Wallet, Tint.Green, stringResource(R.string.set_pay_apps), stringResource(R.string.set_pay_apps_sub), { ValueChevron(stringResource(R.string.set_pay_apps_value, payApps)) }, nav.payApps) },
            { SettingSwitch(Icons.Filled.Apps, Tint.Blue, stringResource(R.string.set_other_apps), stringResource(R.string.set_other_apps_sub), otherApps) { otherApps = it; prefs.otherApps = it } },
        )
        GroupLabel(stringResource(R.string.set_group_filters))
        SettingGroup(
            { SettingRow(Icons.Filled.Block, Tint.Red, stringResource(R.string.set_black_apps), stringResource(R.string.set_black_apps_sub), { ValueChevron(if (blackApps == 0) stringResource(R.string.not_set) else stringResource(R.string.n_items, blackApps)) }, nav.blacklist) },
            { SettingRow(Icons.AutoMirrored.Filled.Label, Tint.Pink, stringResource(R.string.set_black_words), stringResource(R.string.set_black_words_sub), { ValueChevron(if (blackWords == 0) stringResource(R.string.not_set) else stringResource(R.string.n_items, blackWords)) }, nav.words) },
        )
        GroupLabel(stringResource(R.string.set_group_check))
        SettingGroup(
            { SettingRow(Icons.AutoMirrored.Filled.ReceiptLong, Tint.Teal, stringResource(R.string.set_log), stringResource(R.string.set_log_sub), onClick = nav.log) },
        )
    }
}

@Composable
private fun LedgerSection(nav: SettingsNav) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf("") }
    val budget = remember(dialog) { prefs.budgetCents }
    val currency = remember(dialog) { prefs.currency }
    val pickBill = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) nav.import(uri) }
    Column {
        GroupLabel(stringResource(R.string.set_group_money))
        SettingGroup(
            { SettingRow(Icons.Filled.Savings, Tint.Green, stringResource(R.string.set_budget), stringResource(R.string.set_budget_sub), { ValueChevron(if (budget > 0) Money.sym + Money.short(budget) else stringResource(R.string.not_set)) }) { dialog = "budget" } },
            { SettingRow(Icons.Filled.CurrencyExchange, Tint.Pink, stringResource(R.string.set_currency), stringResource(R.string.set_currency_sub), { ValueChevron(currencyLabel(currency)) }) { dialog = "currency" } },
        )
        GroupLabel(stringResource(R.string.set_group_sorting))
        SettingGroup(
            { SettingRow(Icons.Filled.Category, Tint.Orange, stringResource(R.string.set_categories), stringResource(R.string.set_categories_sub), onClick = nav.categories) },
            { SettingRow(Icons.Filled.AutoFixHigh, Tint.Purple, stringResource(R.string.set_rules), stringResource(R.string.set_rules_sub), onClick = nav.rules) },
        )
        GroupLabel(stringResource(R.string.set_group_data))
        SettingGroup(
            { SettingRow(Icons.Filled.UploadFile, Tint.Blue, stringResource(R.string.set_import), stringResource(R.string.set_import_sub)) { pickBill.launch(arrayOf("*/*")) } },
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
    when (dialog) {
        "budget" -> BudgetDialog(prefs) { dialog = ""; Widgets.scheduleUpdate(ctx) }
        "currency" -> CurrencyDialog(prefs) { dialog = ""; Widgets.scheduleUpdate(ctx) }
    }
}

@Composable
private fun FunSection(nav: SettingsNav) {
    val ctx = LocalContext.current
    val fx by Fx.state
    fun set(change: (FxFlags) -> FxFlags) { Fx.update(ctx, change); Widgets.scheduleUpdate(ctx) }
    Column {
        GroupLabel(stringResource(R.string.set_group_home))
        SettingGroup(
            { SettingSwitch(Icons.Filled.AutoAwesome, Tint.Purple, stringResource(R.string.fx_mascot), stringResource(R.string.fx_mascot_sub), fx.mascot) { v -> set { it.copy(mascot = v) } } },
            { SettingSwitch(Icons.Filled.Dashboard, Tint.Orange, stringResource(R.string.fx_fun_row), stringResource(R.string.fx_fun_row_sub), fx.funRow) { v -> set { it.copy(funRow = v) } } },
            { SettingSwitch(Icons.Filled.Movie, Tint.Pink, stringResource(R.string.fx_recap), stringResource(R.string.fx_recap_sub), fx.recapBanner) { v -> set { it.copy(recapBanner = v) } } },
        )
        GroupLabel(stringResource(R.string.set_group_feel))
        SettingGroup(
            { SettingSwitch(Icons.Filled.Vibration, Tint.Blue, stringResource(R.string.fx_haptics), stringResource(R.string.fx_haptics_sub), fx.haptics) { v -> set { it.copy(haptics = v) } } },
            { SettingSwitch(Icons.Filled.Animation, Tint.Teal, stringResource(R.string.fx_motion), stringResource(R.string.fx_motion_sub), fx.motion) { v -> set { it.copy(motion = v) } } },
            { SettingSwitch(Icons.Filled.Celebration, Tint.Green, stringResource(R.string.fx_badges), stringResource(R.string.fx_badges_sub), fx.badgePopup) { v -> set { it.copy(badgePopup = v) } } },
        )
        GroupLabel(stringResource(R.string.set_group_collect))
        SettingGroup(
            { SettingRow(Icons.Filled.EmojiEvents, Tint.Orange, stringResource(R.string.fx_open_badges), stringResource(R.string.fx_open_badges_sub), onClick = nav.achievements) },
            { SettingRow(Icons.Filled.Savings, Tint.Purple, stringResource(R.string.wish_title), stringResource(R.string.fx_open_wish_sub), onClick = nav.wish) },
        )
    }
}

@Composable
private fun GeneralSection() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var dialog by remember { mutableStateOf("") }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdate) }
    val mirror = remember(dialog) { prefs.mirrorPrefix }
    val language = remember(dialog) { AppLocale.current(ctx) }
    val (versionName, _) = remember { Updater.currentVersion(ctx) }
    Column {
        GroupLabel(stringResource(R.string.set_group_display))
        SettingGroup(
            { SettingRow(Icons.Filled.Translate, Tint.Blue, stringResource(R.string.set_language), null, { ValueChevron(AppLocale.label(ctx, language)) }) { dialog = "language" } },
        )
        GroupLabel(stringResource(R.string.set_sec_update))
        SettingGroup(
            { SettingRow(Icons.Filled.SystemUpdate, Tint.Purple, stringResource(R.string.set_check_update), null, { ValueChevron("v$versionName") }) { Updater.check(ctx, manual = true) } },
            { SettingSwitch(Icons.Filled.Autorenew, Tint.Blue, stringResource(R.string.set_auto_update), stringResource(R.string.set_auto_update_sub), autoUpdate) { autoUpdate = it; prefs.autoUpdate = it } },
            { SettingRow(Icons.Filled.Public, Tint.Grey, stringResource(R.string.set_mirror), mirror.ifBlank { stringResource(R.string.set_mirror_none) }) { dialog = "mirror" } },
        )
        Text(
            "Expense Tracking $versionName\n" + stringResource(R.string.set_footer),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
    }
    when (dialog) {
        "mirror" -> MirrorDialog(prefs) { dialog = "" }
        "language" -> LanguageDialog { dialog = "" }
    }
}

/** Gradient card on top: is automatic recording ready? */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusCard(perms: List<Boolean>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val missing = perms.count { !it }
    val ready = missing == 0
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
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
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
    }
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
