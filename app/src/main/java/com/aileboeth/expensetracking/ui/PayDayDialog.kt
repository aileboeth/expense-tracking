package com.aileboeth.expensetracking.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.PayCalendar
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.TxType
import com.aileboeth.expensetracking.widget.Widgets

/**
 * 发钱日: the day of the month money comes in. The ledger's months then run from that day to the day before
 * the next one; "按自然月" goes back to calendar months.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayDayDialog(prefs: Prefs, onDone: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var day by remember { mutableIntStateOf(prefs.payDay) }
    var adjust by remember { mutableStateOf(prefs.payAdjust) }
    var cats by remember { mutableStateOf(prefs.payCategories) }
    var ignored by remember { mutableStateOf(prefs.payIgnored) }
    // income categories (其他 never means pay); 生活费 is offered even when an older ledger lacks it
    val incomeCats = remember {
        Repo.categories(TxType.INCOME).filter { it != "其他" }.let { if ("生活费" in it) it else it + "生活费" }
    }

    fun apply(newDay: Int) {
        prefs.payDay = newDay
        prefs.payAdjust = adjust
        prefs.payCategories = cats
        prefs.payIgnored = ignored
        if ("生活费" in cats && newDay > 0 && "生活费" !in Repo.categories(TxType.INCOME)) Repo.addCategory(TxType.INCOME, "生活费")
        Repo.touch()
        Widgets.scheduleUpdate(ctx)
        onDone()
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(R.string.payday_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.payday_body), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                // 1..31 as a small calendar grid
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..31).chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (k in 0 until 7) {
                                val d = week.getOrNull(k)
                                if (d == null) { Spacer(Modifier.weight(1f)); continue }
                                val on = d == day
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                                        .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { day = d },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "$d", fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
                if (day > 0) {
                    // the running calendar month under the picked day, e.g. "11月 = 11.15 – 12.14"
                    val m = java.time.YearMonth.now()
                    val pc = PayCalendar(day)
                    val f = java.time.format.DateTimeFormatter.ofPattern(stringResource(R.string.fmt_cycle_day), AppLocale.locale(ctx))
                    val range = f.format(pc.start(m)) + " – " + f.format(pc.end(m).minusDays(1))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.payday_example, Labels.monthShort(ctx, m), range) +
                            if (day >= 29) "\n" + stringResource(R.string.payday_short_month, day) else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.payday_adjust), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(stringResource(R.string.payday_adjust_sub, PayCalendar.WINDOW.toInt()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(adjust, { adjust = it })
                }
                if (adjust) {
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (c in incomeCats) {
                            FilterChip(
                                selected = c in cats,
                                onClick = { cats = if (c in cats) cats - c else cats + c },
                                label = { Text(catLabel(c)) },
                            )
                        }
                    }
                    if (cats.isEmpty()) Text(stringResource(R.string.payday_no_cats), style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                    if (ignored.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.payday_ignored, ignored.size), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton({ ignored = emptySet() }) { Text(stringResource(R.string.payday_restore)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ apply(day) }, enabled = day > 0) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (prefs.payDay > 0) TextButton({ apply(0) }) { Text(stringResource(R.string.payday_natural)) }
                TextButton(onDone) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
