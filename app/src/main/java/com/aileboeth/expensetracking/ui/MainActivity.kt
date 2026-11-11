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
        setContent {
            AppTheme {
                val counts = repoQuery(initial = emptyMap<Long, Int>()) { com.aileboeth.expensetracking.data.Repo.attachmentCounts() }
                androidx.compose.runtime.CompositionLocalProvider(LocalAttachCounts provides counts) { AppRoot(requests) }
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
}

@Composable
private fun AppRoot(req: Requests) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    // Lives here so the settings list keeps its scroll position while a sub-page is open.
    val settingsList = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) { Updater.autoCheck(ctx) }
    LaunchedEffect(req.add.value, req.fill.value) { if (req.add.value != null || req.fill.value != null) { overlay = null; tab = 0 } }
    LaunchedEffect(req.search.value) { if (req.search.value) { overlay = Overlay.Search; req.search.value = false } }
    // The capsule's "✎ 改" opens the usual-amounts page; back from it lands on settings.
    LaunchedEffect(req.usual.value) {
        if (req.usual.value) { tab = 2; overlay = Overlay.UsualAmounts; req.usual.value = false }
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
        if (fresh.isNotEmpty() && celebrate.isEmpty()) celebrate = fresh
    }

    when (val o = overlay) {
        Overlay.Achievements -> AchievementsScreen(onBack = { overlay = null })
        Overlay.Wish -> WishJarScreen(onBack = { overlay = null })
        is Overlay.Recap -> RecapScreen(o.month, onBack = { overlay = null })
        Overlay.Search -> SearchScreen(onBack = { overlay = null })
        Overlay.BlacklistApps -> BlacklistAppsScreen(onBack = { overlay = null })
        Overlay.BlacklistWords -> BlacklistWordsScreen(onBack = { overlay = null })
        Overlay.Log -> RecognitionLogScreen(onBack = { overlay = null })
        Overlay.Categories -> CategoriesScreen(onBack = { overlay = null })
        Overlay.UsualAmounts -> UsualAmountsScreen(onBack = { overlay = null })
        Overlay.Rules -> CategoryRulesScreen(onBack = { overlay = null })
        Overlay.Stability -> StabilityScreen(onBack = { overlay = null })
        Overlay.PayApps -> PaymentAppsScreen(onBack = { overlay = null })
        is Overlay.Import -> ImportScreen(o.uri, onBack = { overlay = null })
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
                    openSearch = { overlay = Overlay.Search },
                    addRequested = req.add.value,
                    onAddHandled = { req.add.value = null },
                    fillRequested = req.fill.value,
                    onFillHandled = { req.fill.value = null },
                    openAchievements = { overlay = Overlay.Achievements },
                    openWish = { overlay = Overlay.Wish },
                    openRecap = { overlay = Overlay.Recap(it) },
                )
                1 -> StatsScreen(padding, openRecap = { overlay = Overlay.Recap(it) })
                else -> SettingsScreen(
                    padding,
                    onImport = { overlay = Overlay.Import(it) },
                    openBlacklist = { overlay = Overlay.BlacklistApps },
                    openWords = { overlay = Overlay.BlacklistWords },
                    openLog = { overlay = Overlay.Log },
                    openCategories = { overlay = Overlay.Categories },
                    openRules = { overlay = Overlay.Rules },
                    openStability = { overlay = Overlay.Stability },
                    openPayApps = { overlay = Overlay.PayApps },
                    openUsual = { overlay = Overlay.UsualAmounts },
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
