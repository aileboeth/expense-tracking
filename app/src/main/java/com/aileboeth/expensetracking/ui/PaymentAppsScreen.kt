package com.aileboeth.expensetracking.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.parse.Parser
import com.aileboeth.expensetracking.parse.PaymentApps
import java.util.Locale

private fun installed(ctx: Context, pkg: String) = try {
    ctx.packageManager.getApplicationInfo(pkg, 0); true
} catch (_: PackageManager.NameNotFoundException) {
    false
}

/** Payment apps that are installed and read (WeChat / Alipay count when installed). */
fun activePayApps(ctx: Context, prefs: Prefs): Int =
    PaymentApps.ALL.count { (it.pkg in Parser.KNOWN_APPS || it.pkg in prefs.payApps()) && installed(ctx, it.pkg) }

/** Which payment apps' notifications are read, ordered by what people use where this phone is. */
@Composable
fun PaymentAppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var on by remember { mutableStateOf(prefs.payApps()) }
    val loc = Locale.getDefault()
    val country = remember { PaymentApps.country(loc.language, loc.country) }
    val countryName = remember { if (country.isEmpty()) "" else Locale("", country).getDisplayCountry(AppLocale.locale(ctx)) }
    val apps = remember { PaymentApps.ranked(country).map { it to installed(ctx, it.pkg) } }
    val exact = apps.filter { it.first.pkg in Parser.KNOWN_APPS }
    val local = apps.filter { it.first.pkg !in Parser.KNOWN_APPS && country in it.first.regions }
    val world = apps.filter { it.first.pkg !in Parser.KNOWN_APPS && country !in it.first.regions && it.first.worldwide }
    val rest = apps.filter { it.first.pkg !in Parser.KNOWN_APPS && country !in it.first.regions && !it.first.worldwide }

    fun toggle(pkg: String, v: Boolean) {
        on = if (v) on + pkg else on - pkg
        prefs.setPayApps(on)
    }

    SubPage(stringResource(R.string.set_pay_apps), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeaderBrush).padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Column {
                        Text(
                            if (countryName.isEmpty()) stringResource(R.string.pay_title) else stringResource(R.string.pay_title_region, countryName),
                            color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        )
                        Text(stringResource(R.string.pay_body), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                GroupLabel(stringResource(R.string.pay_exact), stringResource(R.string.pay_exact_hint))
                PayGroup(exact, on, exactApps = true) { _, _ -> }
            }
            if (local.isNotEmpty()) item {
                GroupLabel(if (countryName.isEmpty()) stringResource(R.string.pay_local) else stringResource(R.string.pay_local_region, countryName))
                PayGroup(local, on) { p, v -> toggle(p, v) }
            }
            if (world.isNotEmpty()) item {
                GroupLabel(stringResource(R.string.pay_world))
                PayGroup(world, on) { p, v -> toggle(p, v) }
            }
            if (rest.isNotEmpty()) item {
                GroupLabel(stringResource(R.string.pay_other_regions))
                PayGroup(rest, on) { p, v -> toggle(p, v) }
            }
            item {
                Text(stringResource(R.string.pay_footer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun PayGroup(apps: List<Pair<PaymentApps.App, Boolean>>, on: Set<String>, exactApps: Boolean = false, onToggle: (String, Boolean) -> Unit) {
    SoftCard(padding = 0.dp) {
        apps.forEachIndexed { i, (app, isInstalled) ->
            val checked = exactApps || app.pkg in on
            Row(
                Modifier.fillMaxWidth().clickable(enabled = isInstalled && !exactApps) { onToggle(app.pkg, !checked) }
                    .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.pkg, app.name, 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(app.name, style = MaterialTheme.typography.bodyLarge,
                        color = if (isInstalled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            !isInstalled -> stringResource(R.string.pay_not_installed)
                            exactApps -> stringResource(R.string.pay_exact_sub)
                            checked -> stringResource(R.string.pay_on_sub)
                            else -> stringResource(R.string.pay_off_sub)
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (exactApps) StatusPill(isInstalled, stringResource(R.string.pay_always_on), stringResource(R.string.pay_not_installed_short))
                else Switch(checked && isInstalled, { onToggle(app.pkg, it) }, enabled = isInstalled)
            }
            if (i != apps.lastIndex) RowDivider()
        }
    }
}
