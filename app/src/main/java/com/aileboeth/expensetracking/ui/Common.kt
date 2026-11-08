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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

/** Brand purple, matching the launcher icon. */
val Brand = Color(0xFF5B45E0)
val HeaderBrush = Brush.linearGradient(listOf(Color(0xFF9C8BFF), Color(0xFF6A55F0), Color(0xFF3F2CC4)))

private val LightScheme = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E4FF), onPrimaryContainer = Color(0xFF1E1060),
    secondary = Color(0xFF7C6CF6), secondaryContainer = Color(0xFFECE8FF), onSecondaryContainer = Color(0xFF221569),
    background = Color(0xFFF6F5FB), onBackground = Color(0xFF1C1A24),
    surface = Color.White, onSurface = Color(0xFF1C1A24),
    surfaceVariant = Color(0xFFEFEDF6), onSurfaceVariant = Color(0xFF67647A),
    surfaceContainer = Color(0xFFF1EFF8), surfaceContainerLow = Color.White, surfaceContainerHigh = Color(0xFFF1EFF8),
    surfaceContainerHighest = Color(0xFFEAE7F4),
    outlineVariant = Color(0xFFE2DFEC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB6A8FF), onPrimary = Color(0xFF1E1060),
    primaryContainer = Color(0xFF3B2A9E), onPrimaryContainer = Color(0xFFE9E4FF),
    secondary = Color(0xFFC8BDFF), secondaryContainer = Color(0xFF332A63),
    background = Color(0xFF121019), onBackground = Color(0xFFE7E3F2),
    surface = Color(0xFF1C1926), onSurface = Color(0xFFE7E3F2),
    surfaceVariant = Color(0xFF2A2638), onSurfaceVariant = Color(0xFFB3AFC4),
    surfaceContainer = Color(0xFF221F2E), surfaceContainerLow = Color(0xFF1C1926), surfaceContainerHigh = Color(0xFF2A2638),
    surfaceContainerHighest = Color(0xFF332F42),
    outlineVariant = Color(0xFF3A3648),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}

/** The rounded white card used across the app. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Sum per day of the month (index 0 = day 1); days after today are null for the current month. */
fun dailyTotals(txs: List<com.aileboeth.expensetracking.data.Tx>, month: YearMonth, type: com.aileboeth.expensetracking.parse.TxType): List<Long?> {
    val days = month.lengthOfMonth()
    val sums = LongArray(days)
    for (t in txs) if (t.type == type) {
        val d = localDate(t.time)
        if (d.year == month.year && d.monthValue == month.monthValue) sums[d.dayOfMonth - 1] += t.amountCents ?: 0
    }
    val now = YearMonth.now()
    val lastDay = when {
        month == now -> java.time.LocalDate.now().dayOfMonth
        month.isAfter(now) -> 0
        else -> days
    }
    return List(days) { i -> if (i < lastDay) sums[i] else null }
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
