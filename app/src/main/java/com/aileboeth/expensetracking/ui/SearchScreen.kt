package com.aileboeth.expensetracking.ui

import androidx.compose.ui.res.stringResource
import com.aileboeth.expensetracking.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.data.TxFilter
import com.aileboeth.expensetracking.parse.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private enum class Period(val label: Int) { MONTH(R.string.period_month), THREE(R.string.period_3m), YEAR(R.string.period_year), ALL(R.string.period_all) }

@Composable
fun SearchScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf<TxType?>(null) }
    var period by remember { mutableStateOf(Period.THREE) }
    var channel by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Tx?>(null) }

    val zone = ZoneId.systemDefault()
    val start = when (period) {
        Period.MONTH -> monthRange(YearMonth.now()).first
        Period.THREE -> monthRange(YearMonth.now().minusMonths(2)).first
        Period.YEAR -> LocalDate.now().withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
        Period.ALL -> null
    }
    val filter = TxFilter(
        query = query, type = type, channel = channel, category = category,
        minCents = Money.parse(min), maxCents = Money.parse(max), start = start,
    )
    val results = repoQuery(filter, initial = emptyList<Tx>()) { Repo.search(filter) }
    val channels = repoQuery(initial = emptyList<String>()) { Repo.channels() }
    val categories = repoQuery(type, initial = emptyList<String>()) {
        when (type) {
            null -> (Repo.categories(TxType.EXPENSE) + Repo.categories(TxType.INCOME)).distinct()
            else -> Repo.categories(type!!)
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) } },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            ChipRow {
                FilterChip(type == null, { type = null; category = null }, label = { Text(stringResource(R.string.period_all)) })
                FilterChip(type == TxType.EXPENSE, { type = TxType.EXPENSE; category = null }, label = { Text(stringResource(R.string.expense)) })
                FilterChip(type == TxType.INCOME, { type = TxType.INCOME; category = null }, label = { Text(stringResource(R.string.income)) })
                Spacer(Modifier.width(8.dp))
                Period.entries.forEach { p -> FilterChip(period == p, { period = p }, label = { Text(stringResource(p.label)) }) }
            }
            ChipRow {
                FilterChip(channel == null, { channel = null }, label = { Text(stringResource(R.string.search_all_channels)) })
                channels.forEach { c -> FilterChip(channel == c, { channel = if (channel == c) null else c }, label = { Text(channelName(c)) }) }
            }
            ChipRow {
                FilterChip(category == null, { category = null }, label = { Text(stringResource(R.string.search_all_categories)) })
                categories.forEach { c -> FilterChip(category == c, { category = if (category == c) null else c }, label = { Text(catLabel(c)) }) }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val soft = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                )
                Text(stringResource(R.string.amount_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp))
                OutlinedTextField(min, { min = it }, placeholder = { Text(stringResource(R.string.min_amount, Money.sym)) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = soft,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Text("  —  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(max, { max = it }, placeholder = { Text(stringResource(R.string.max_amount, Money.sym)) }, singleLine = true, shape = RoundedCornerShape(14.dp), colors = soft,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            val ex = results.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 }
            val inc = results.filter { it.type == TxType.INCOME }.sumOf { it.amountCents ?: 0 }
            Text(
                stringResource(R.string.search_summary, results.size, Money.money(ex), Money.money(inc)) + if (results.size >= 500) stringResource(R.string.search_capped) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            val days = results.groupBy { localDate(it.time) }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (results.isEmpty()) item {
                    Text(stringResource(R.string.search_empty), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                for ((day, list) in days) item(key = "d$day") {
                    SoftCard(padding = 12.dp) {
                        DayHeader(day, list)
                        list.forEach { tx -> TxRow(tx) { editing = tx } }
                    }
                }
            }
        }
    }
    editing?.let { tx -> TxEditor(initial = tx, onDone = { editing = null }) }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
