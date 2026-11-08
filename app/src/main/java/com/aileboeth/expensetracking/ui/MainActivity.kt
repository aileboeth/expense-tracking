package com.aileboeth.expensetracking.ui

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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.update.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }
}

@Composable
private fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { Updater.autoCheck(ctx) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Filled.Home, null) }, label = { Text("账单") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Filled.Info, null) }, label = { Text("统计") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("设置") })
            }
        },
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(padding, openSettings = { tab = 2 })
            1 -> StatsScreen(padding)
            else -> SettingsScreen(padding)
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
