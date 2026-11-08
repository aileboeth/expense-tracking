package com.aileboeth.expensetracking.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.aileboeth.expensetracking.capture.CaptureService
import com.aileboeth.expensetracking.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

val ExpenseRed = Color(0xFFE5533D)
val IncomeGreen = Color(0xFF2E9E6A)
val PendingAmber = Color(0xFFF2A007)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF7FD3AA))
        else -> lightColorScheme(primary = Color(0xFF2E7D5B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Runs [query] on a background thread and again after every database write. */
@Composable
fun <T> repoQuery(vararg keys: Any?, initial: T, query: () -> T): T {
    val version by Repo.version.collectAsState()
    val state = produceState(initial, version, *keys) { value = withContext(Dispatchers.IO) { query() } }
    return state.value
}

fun monthRange(ym: YearMonth): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start to end
}

fun localDate(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

object Perms {
    fun listener(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(ctx, CaptureService::class.java))

    fun overlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun battery(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    fun openListener(ctx: Context) = start(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openOverlay(ctx: Context) {
        if (!start(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))) {
            openAppDetails(ctx)
        }
    }

    @android.annotation.SuppressLint("BatteryLife")
    fun openBattery(ctx: Context) {
        if (!start(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))) {
            start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openAppDetails(ctx: Context) =
        start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))

    fun start(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) {
        false
    }
}
