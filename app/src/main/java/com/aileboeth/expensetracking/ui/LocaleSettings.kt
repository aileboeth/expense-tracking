package com.aileboeth.expensetracking.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import java.util.Currency

/** Currencies offered first: Chinese-speaking regions, then the big ones, then by region. */
private val CURRENCIES = listOf(
    "CNY", "HKD", "TWD", "MOP", "USD", "EUR", "GBP", "JPY", "KRW", "SGD", "AUD", "CAD", "NZD", "CHF",
    "INR", "THB", "MYR", "PHP", "IDR", "VND", "BRL", "MXN", "ARS", "RUB", "TRY", "PLN", "SEK", "NOK",
    "DKK", "CZK", "HUF", "ZAR", "AED", "SAR", "ILS",
)

/** "¥ CNY" style label for the settings row. */
fun currencyLabel(code: String): String = "${Money.symbolFor(code)} $code"

@Composable
fun CurrencyDialog(prefs: Prefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val current = remember { prefs.currency }
    val loc = AppLocale.locale(ctx)
    val list = remember { (listOf(Money.defaultCurrencyCode()) + CURRENCIES).distinct() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.set_currency)) },
        text = {
            Column {
                Text(stringResource(R.string.currency_body), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it }) { code ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                prefs.currency = code
                                Money.setCurrency(code)
                                onClose()
                            }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(code == current, null)
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(runCatching { Currency.getInstance(code).getDisplayName(loc) }.getOrDefault(code))
                                Text(currencyLabel(code), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun LanguageDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val current = remember { AppLocale.current(ctx) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.set_language)) },
        text = {
            Column {
                for (tag in AppLocale.OPTIONS) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            onClose()
                            if (tag != current) {
                                AppLocale.set(ctx, tag)
                                // Android 13+ restarts the screen by itself; older versions need a nudge.
                                if (Build.VERSION.SDK_INT < 33) ctx.findActivity()?.recreate()
                            }
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(tag == current, null)
                        Text(AppLocale.label(ctx, tag), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
