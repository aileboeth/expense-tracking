package com.aileboeth.expensetracking.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.update.Updater
import com.aileboeth.expensetracking.widget.Widgets

/** What an outside entry point (widget, launcher shortcut, capsule) asked the app to open. */
class Requests {
    /** "+ 记一笔" from the widget or a launcher shortcut: the type to start with. */
    val add = mutableStateOf<com.aileboeth.expensetracking.parse.TxType?>(null)
    /** The capsule's ✎ 改 asks to edit the usual amounts. */
    val usual = mutableStateOf(false)
    val search = mutableStateOf(false)
    /** Open the editor on this entry (widget "fill in" without the overlay permission). */
    val fill = mutableStateOf<Long?>(null)
}

class MainActivity : ComponentActivity() {
    private val requests = Requests()

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLocale.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        Shortcuts.publish(this)
        Fx.load(this)
        setContent {
            AppTheme {
                val counts = repoQuery(initial = emptyMap<Long, Int>()) { com.aileboeth.expensetracking.data.Repo.attachmentCounts() }
                FxProvider {
                    androidx.compose.runtime.CompositionLocalProvider(LocalAttachCounts provides counts) { AppRoot(requests) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.aileboeth.expensetracking.bubble.Bubble.setAppInFront(true)
        ListenerKeeper.ensure(this, 1500)
        // Opening the app counts as today's check-in.
        Thread { runCatching { com.aileboeth.expensetracking.data.Repo.markActive() } }.start()
    }

    override fun onPause() {
        super.onPause()
        com.aileboeth.expensetracking.bubble.Bubble.setAppInFront(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Widgets.ACTION_ADD -> requests.add.value =
                if (intent.getStringExtra("type") == "INCOME") com.aileboeth.expensetracking.parse.TxType.INCOME else com.aileboeth.expensetracking.parse.TxType.EXPENSE
            com.aileboeth.expensetracking.bubble.Bubble.ACTION_USUAL_AMOUNTS -> requests.usual.value = true
            Shortcuts.ACTION_SEARCH -> requests.search.value = true
            Widgets.ACTION_FILL -> intent.getLongExtra("tx", 0L).takeIf { it > 0 }?.let { requests.fill.value = it }
        }
    }
}

private sealed interface Overlay {
    data object Search : Overlay
    data object BlacklistApps : Overlay
    data object BlacklistWords : Overlay
    data object Log : Overlay
    data object Categories : Overlay
    data object UsualAmounts : Overlay
    data object Rules : Overlay
    data object Stability : Overlay
    data object PayApps : Overlay
    data class Import(val uri: Uri) : Overlay
    data object Achievements : Overlay
    data object Wish : Overlay
    data class Recap(val month: java.time.YearMonth) : Overlay
    data class Analysis(val month: java.time.YearMonth, val tab: Int = 0) : Overlay
    data class Settings(val section: SettingsSection) : Overlay
}

@Composable
private fun AppRoot(req: Requests) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Pages opened on top of the tabs; back pops one (a settings category → its blacklist page → back to the category).
    var stack by remember { mutableStateOf(listOf<Overlay>()) }
    val overlay = stack.lastOrNull()
    fun open(o: Overlay) { stack = stack + o }
    fun back() { stack = stack.dropLast(1) }
    // Lives here so the settings list keeps its scroll position while a sub-page is open.
    val settingsList = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) { Updater.autoCheck(ctx) }
    LaunchedEffect(req.add.value, req.fill.value) { if (req.add.value != null || req.fill.value != null) { stack = emptyList(); tab = 0 } }
    LaunchedEffect(req.search.value) { if (req.search.value) { stack = listOf(Overlay.Search); req.search.value = false } }
    // The capsule's "✎ 改" opens the usual-amounts page; back from it lands on the capsule settings.
    LaunchedEffect(req.usual.value) {
        if (req.usual.value) { tab = 2; stack = listOf(Overlay.Settings(SettingsSection.CAPSULE), Overlay.UsualAmounts); req.usual.value = false }
    }

    // Badges: re-checked a moment after the data changes; new ones get a little party.
    val version by com.aileboeth.expensetracking.data.Repo.version.collectAsState()
    var celebrate by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(version) {
        kotlinx.coroutines.delay(900)
        val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.aileboeth.expensetracking.data.Achievements.check(ctx) }
            com.aileboeth.expensetracking.data.Repo.unseenBadges()
        }
        if (fresh.isNotEmpty() && celebrate.isEmpty()) {
            // With the pop-up switched off the badges are still collected, just quietly.
            if (Fx.flags.badgePopup) celebrate = fresh
            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.aileboeth.expensetracking.data.Repo.markBadgesSeen() }
        }
    }

    when (val o = overlay) {
        Overlay.Achievements -> AchievementsScreen(onBack = ::back)
        Overlay.Wish -> WishJarScreen(onBack = ::back)
        is Overlay.Recap -> RecapScreen(o.month, onBack = ::back)
        is Overlay.Analysis -> AnalysisScreen(o.month, o.tab, onBack = ::back, openRecap = { open(Overlay.Recap(it)) }, openWish = { open(Overlay.Wish) })
        Overlay.Search -> SearchScreen(onBack = ::back)
        Overlay.BlacklistApps -> BlacklistAppsScreen(onBack = ::back)
        Overlay.BlacklistWords -> BlacklistWordsScreen(onBack = ::back)
        Overlay.Log -> RecognitionLogScreen(onBack = ::back)
        Overlay.Categories -> CategoriesScreen(onBack = ::back)
        Overlay.UsualAmounts -> UsualAmountsScreen(onBack = ::back)
        Overlay.Rules -> CategoryRulesScreen(onBack = ::back)
        Overlay.Stability -> StabilityScreen(onBack = ::back)
        Overlay.PayApps -> PaymentAppsScreen(onBack = ::back)
        is Overlay.Import -> ImportScreen(o.uri, onBack = ::back)
        is Overlay.Settings -> SettingsSectionScreen(
            o.section, onBack = ::back,
            nav = SettingsNav(
                import = { open(Overlay.Import(it)) },
                blacklist = { open(Overlay.BlacklistApps) },
                words = { open(Overlay.BlacklistWords) },
                log = { open(Overlay.Log) },
                categories = { open(Overlay.Categories) },
                rules = { open(Overlay.Rules) },
                stability = { open(Overlay.Stability) },
                usual = { open(Overlay.UsualAmounts) },
                payApps = { open(Overlay.PayApps) },
                achievements = { open(Overlay.Achievements) },
                wish = { open(Overlay.Wish) },
            ),
        )
        null -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text(stringResource(R.string.nav_bills)) })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text(stringResource(R.string.nav_stats)) })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text(stringResource(R.string.nav_settings)) })
                }
            },
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(
                    padding,
                    openSettings = { tab = 2 },
                    openSearch = { open(Overlay.Search) },
                    addRequested = req.add.value,
                    onAddHandled = { req.add.value = null },
                    fillRequested = req.fill.value,
                    onFillHandled = { req.fill.value = null },
                    openAchievements = { open(Overlay.Achievements) },
                    openWish = { open(Overlay.Wish) },
                    openRecap = { open(Overlay.Recap(it)) },
                )
                1 -> StatsScreen(
                    padding,
                    openRecap = { open(Overlay.Recap(it)) },
                    openAnalysis = { m, t -> open(Overlay.Analysis(m, t)) },
                )
                else -> SettingsScreen(
                    padding,
                    openSection = { open(Overlay.Settings(it)) },
                    listState = settingsList,
                )
            }
        }
    }
    UpdateDialog()
    if (celebrate.isNotEmpty()) BadgeCelebration(celebrate) {
        celebrate = emptyList()
        Thread { com.aileboeth.expensetracking.data.Repo.markBadgesSeen() }.start()
    }
}

@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val state by Updater.state.collectAsState()
    when (val s = state) {
        is Updater.State.Available -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_available, s.info.version)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.update_current, s.current))
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.info.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton({ Updater.download(ctx, s.info) }) { Text(stringResource(R.string.update_download)) } },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text(stringResource(R.string.update_later)) } },
        )
        is Updater.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading, s.info.version)) },
            text = {
                Column {
                    if (s.total > 0) {
                        LinearProgressIndicator(progress = { s.done.toFloat() / s.total }, modifier = Modifier.padding(vertical = 8.dp))
                        Text("%.1f / %.1f MB".format(s.done / 1048576f, s.total / 1048576f))
                    } else {
                        LinearProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                        Text("%.1f MB".format(s.done / 1048576f))
                    }
                }
            },
            confirmButton = {},
        )
        is Updater.State.Ready -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_ready)) },
            text = { Text(stringResource(R.string.update_ready_body)) },
            confirmButton = { TextButton({ Updater.install(ctx, s.file) }) { Text(stringResource(R.string.update_install)) } },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text(stringResource(R.string.close)) } },
        )
        is Updater.State.UpToDate -> if (Updater.manual) AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_uptodate)) },
            text = { Text(stringResource(R.string.update_version, s.current)) },
            confirmButton = { TextButton({ Updater.dismiss() }) { Text(stringResource(R.string.ok)) } },
        )
        is Updater.State.Failed -> if (Updater.manual) AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(s.message + "\n\n" + stringResource(R.string.update_failed_hint)) },
            confirmButton = { TextButton({ Updater.dismiss() }) { Text(stringResource(R.string.ok)) } },
        )
        else -> {}
    }
}
