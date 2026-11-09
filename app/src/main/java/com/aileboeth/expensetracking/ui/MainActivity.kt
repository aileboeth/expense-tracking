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
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.update.Updater
import com.aileboeth.expensetracking.widget.Widgets

class MainActivity : ComponentActivity() {
    /** Set when opened from the widget's "+ 记一笔". */
    private val addRequest = mutableStateOf(false)
    /** Set when the bubble's ✎ 改 asks to edit the usual amounts. */
    private val usualRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            AppTheme {
                val counts = repoQuery(initial = emptyMap<Long, Int>()) { com.aileboeth.expensetracking.data.Repo.attachmentCounts() }
                androidx.compose.runtime.CompositionLocalProvider(LocalAttachCounts provides counts) { AppRoot(addRequest, usualRequest) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.aileboeth.expensetracking.bubble.Bubble.setAppInFront(true)
        ListenerKeeper.ensure(this, 1500)
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
        if (intent?.action == Widgets.ACTION_ADD) addRequest.value = true
        if (intent?.action == com.aileboeth.expensetracking.bubble.Bubble.ACTION_USUAL_AMOUNTS) usualRequest.value = true
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
    data class Import(val uri: Uri) : Overlay
}

@Composable
private fun AppRoot(addRequest: MutableState<Boolean>, usualRequest: MutableState<Boolean>) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    // Lives here so the settings list keeps its scroll position while a sub-page is open.
    val settingsList = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(Unit) { Updater.autoCheck(ctx) }
    LaunchedEffect(addRequest.value) { if (addRequest.value) { overlay = null; tab = 0 } }
    // The capsule's "✎ 改" opens the usual-amounts page; back from it lands on settings.
    LaunchedEffect(usualRequest.value) {
        if (usualRequest.value) { tab = 2; overlay = Overlay.UsualAmounts; usualRequest.value = false }
    }

    when (val o = overlay) {
        Overlay.Search -> SearchScreen(onBack = { overlay = null })
        Overlay.BlacklistApps -> BlacklistAppsScreen(onBack = { overlay = null })
        Overlay.BlacklistWords -> BlacklistWordsScreen(onBack = { overlay = null })
        Overlay.Log -> RecognitionLogScreen(onBack = { overlay = null })
        Overlay.Categories -> CategoriesScreen(onBack = { overlay = null })
        Overlay.UsualAmounts -> UsualAmountsScreen(onBack = { overlay = null })
        Overlay.Rules -> CategoryRulesScreen(onBack = { overlay = null })
        Overlay.Stability -> StabilityScreen(onBack = { overlay = null })
        is Overlay.Import -> ImportScreen(o.uri, onBack = { overlay = null })
        null -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("账单") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Filled.DateRange, null) }, label = { Text("统计") })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
                }
            },
        ) { padding ->
            when (tab) {
                0 -> HomeScreen(
                    padding,
                    openSettings = { tab = 2 },
                    openSearch = { overlay = Overlay.Search },
                    addRequested = addRequest.value,
                    onAddHandled = { addRequest.value = false },
                )
                1 -> StatsScreen(padding)
                else -> SettingsScreen(
                    padding,
                    onImport = { overlay = Overlay.Import(it) },
                    openBlacklist = { overlay = Overlay.BlacklistApps },
                    openWords = { overlay = Overlay.BlacklistWords },
                    openLog = { overlay = Overlay.Log },
                    openCategories = { overlay = Overlay.Categories },
                    openRules = { overlay = Overlay.Rules },
                    openStability = { overlay = Overlay.Stability },
                    openUsual = { overlay = Overlay.UsualAmounts },
                    listState = settingsList,
                )
            }
        }
    }
    UpdateDialog()
}

@Composable
fun UpdateDialog() {
    val ctx = LocalContext.current
    val state by Updater.state.collectAsState()
    when (val s = state) {
        is Updater.State.Available -> AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("有新版本 ${s.info.version}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("当前 ${s.current}")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.info.notes, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { TextButton({ Updater.download(ctx, s.info) }) { Text("下载并安装") } },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text("以后") } },
        )
        is Updater.State.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载 ${s.info.version}") },
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
            title = { Text("下载好了") },
            text = { Text("如果没弹出安装界面：先允许「安装未知应用」，再点安装。") },
            confirmButton = { TextButton({ Updater.install(ctx, s.file) }) { Text("安装") } },
            dismissButton = { TextButton({ Updater.dismiss() }) { Text("关闭") } },
        )
        is Updater.State.UpToDate -> if (Updater.manual) AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("已是最新版") },
            text = { Text("当前版本 ${s.current}") },
            confirmButton = { TextButton({ Updater.dismiss() }) { Text("好") } },
        )
        is Updater.State.Failed -> if (Updater.manual) AlertDialog(
            onDismissRequest = { Updater.dismiss() },
            title = { Text("更新没成功") },
            text = { Text(s.message + "\n\n连不上 GitHub 时，可以在设置里填「下载加速前缀」。") },
            confirmButton = { TextButton({ Updater.dismiss() }) { Text("好") } },
        )
        else -> {}
    }
}
