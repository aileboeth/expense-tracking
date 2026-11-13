package com.aileboeth.expensetracking.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.capture.Diagnostics
import com.aileboeth.expensetracking.capture.ScreenLink
import com.aileboeth.expensetracking.capture.ScreenSamples
import com.aileboeth.expensetracking.capture.ScreenService
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.parse.Parser
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 0 = off, 1 = on and running, 2 = on in the system settings but not bound. */
fun fullAutoState(ctx: android.content.Context): Int = when {
    ScreenService.connected -> 1
    ScreenService.enabled(ctx) -> 2
    else -> 0
}

/** Settings → 自动记账 → 全自动: the accessibility service, its options and what it read lately. */
@Composable
fun FullAutoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    // The service connects a moment after it is switched on, and keeps reading: refresh while open.
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val state = remember(tick) { fullAutoState(ctx) }
    val events = remember(tick) { ScreenLink.events().take(12) }
    val lastWindow = remember(tick) { ScreenLink.lastWindow }
    var fetch by remember { mutableStateOf(prefs.autoFetch) }
    var samples by remember { mutableStateOf(prefs.screenSamples) }
    val sampleCount = remember(tick, samples) { ScreenSamples.count(ctx) }
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    /** "clear" / "off" while the delete-samples question is open. */
    var confirm by remember { mutableStateOf("") }
    if (confirm.isNotEmpty()) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = "" },
        title = { Text(stringResource(R.string.fa_samples_clear_title)) },
        text = { Text(stringResource(R.string.fa_samples_clear_body, sampleCount)) },
        confirmButton = {
            androidx.compose.material3.TextButton({
                if (confirm == "off") { samples = false; prefs.screenSamples = false }
                ScreenSamples.clear(ctx); confirm = ""; tick++
                Toast.makeText(ctx, ctx.getString(R.string.fa_samples_cleared), Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.fa_samples_clear_ok), color = ExpenseRed) }
        },
        dismissButton = { androidx.compose.material3.TextButton({ confirm = "" }) { Text(stringResource(R.string.cancel)) } },
    )

    SubPage(stringResource(R.string.fa_title), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(when (state) { 1 -> R.string.fa_state_on; 2 -> R.string.fa_state_stuck; else -> R.string.fa_state_off }),
                                        color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(R.string.fa_badge), color = Color.White, style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                                Text(
                                    stringResource(when (state) { 1 -> R.string.fa_state_on_sub; 2 -> R.string.fa_state_stuck_sub; else -> R.string.fa_state_off_sub }),
                                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            Box(Modifier.padding(start = 12.dp).size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Icon(if (state == 1) Icons.Filled.Check else Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }
                        Button(
                            { ScreenService.open(ctx) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Brand),
                            modifier = Modifier.padding(top = 14.dp),
                        ) {
                            Icon(Icons.Filled.PowerSettingsNew, null, modifier = Modifier.size(18.dp))
                            Text(stringResource(if (state == 0) R.string.fa_open else R.string.fa_open_again), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            if (state != 1) item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.fa_where), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.fa_restricted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { GroupLabel(stringResource(R.string.fa_what)) }
            item {
                SoftCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(R.string.fa_what_1, R.string.fa_what_2, R.string.fa_what_3).forEachIndexed { i, res ->
                            Row {
                                Text(
                                    "${i + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.size(22.dp).clip(CircleShape).background(Brand).padding(top = 2.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                                Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
            item { GroupLabel(stringResource(R.string.fa_options)) }
            item {
                SettingGroup(
                    {
                        SettingSwitch(Icons.Filled.TouchApp, Tint.Purple, stringResource(R.string.fa_fetch), stringResource(R.string.fa_fetch_sub), fetch) {
                            fetch = it; prefs.autoFetch = it; ScreenService.reconfigure()
                        }
                    },
                    {
                        SettingSwitch(Icons.Filled.Science, Tint.Teal, stringResource(R.string.fa_samples), stringResource(R.string.fa_samples_sub), samples) {
                            // Turning it off deletes the samples: ask first when there are some.
                            if (!it && sampleCount > 0) confirm = "off" else { samples = it; prefs.screenSamples = it }
                        }
                    },
                    if (samples || sampleCount > 0) {
                        {
                            SettingRow(
                                Icons.Filled.Share, Tint.Blue, stringResource(R.string.fa_samples_send),
                                if (sampleCount > 0) stringResource(R.string.fa_samples_send_sub, sampleCount) else stringResource(R.string.fa_samples_none),
                            ) {
                                if (sampleCount > 0) runCatching {
                                    Diagnostics.share(ctx, Diagnostics.report(ctx, withText = false) + "\n屏幕样本\n==========\n" + ScreenSamples.text(ctx))
                                }
                            }
                        }
                    } else null,
                    if (sampleCount > 0) {
                        {
                            SettingRow(Icons.Filled.DeleteSweep, Tint.Red, stringResource(R.string.fa_samples_clear), null, { }) {
                                confirm = "clear"
                            }
                        }
                    } else null,
                )
            }
            item { GroupLabel(stringResource(R.string.fa_recent)) }
            item {
                SoftCard {
                    if (events.isEmpty()) Text(stringResource(R.string.fa_recent_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    events.forEach { e ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(fmt.format(Date(e.time)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp))
                            Text(e.text, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    lastWindow?.let { (t, pkg) ->
                        val who = when (pkg) { Parser.WECHAT -> stringResource(R.string.ch_wechat); Parser.ALIPAY -> stringResource(R.string.ch_alipay); else -> pkg }
                        Text(
                            stringResource(R.string.fa_last_window, who, fmt.format(Date(t))),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.fa_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.fa_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
