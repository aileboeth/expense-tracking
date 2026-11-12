package com.aileboeth.expensetracking.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Mood
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.RecapCalc
import com.aileboeth.expensetracking.data.RecapData
import com.aileboeth.expensetracking.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

private val BGS = listOf(
    listOf(Color(0xFF9C8BFF), Color(0xFF5B45E0), Color(0xFF2E1E9A)),
    listOf(Color(0xFFFF8FB1), Color(0xFF9C6BFF)),
    listOf(Color(0xFF6CC6FF), Color(0xFF4E6BFF)),
    listOf(Color(0xFFFFB86B), Color(0xFFFF6F91)),
    listOf(Color(0xFF4FD1A5), Color(0xFF2E8FBF)),
    listOf(Color(0xFF6A55F0), Color(0xFF2A1D7A)),
)

/** "Your October": story-style cards to swipe or tap through; each can be saved or shared as a picture. */
@Composable
fun RecapScreen(month: YearMonth, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val data = repoQuery(month, initial = null as RecapData?) {
        val r = monthRange(month)
        val p = monthRange(month.minusMonths(1))
        RecapCalc.compute(month, Repo.listRange(r.first, r.second), Repo.listRange(p.first, p.second), Repo.activeDays())
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Prefs(ctx).let { it.recapsViewed = it.recapsViewed + 1 }; Repo.markActive() }
    }
    val pages = 6
    val pager = rememberPagerState { pages }
    val layers = List(pages) { rememberGraphicsLayer() }

    Box(Modifier.fillMaxSize().background(Color(0xFF1A1433))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            Box(
                Modifier.fillMaxSize()
                    // Taps on the left / right third go back / forward like stories. This sits inside the pager
                    // (not on top of it) so swiping still turns the pages.
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            when {
                                o.x < size.width / 3f -> scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                                o.x > size.width * 2 / 3f ->
                                    if (pager.currentPage == pages - 1) onBack() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            }
                        }
                    }
                    .drawWithContent { layers[page].record { this@drawWithContent.drawContent() }; drawLayer(layers[page]) }
                    .background(Brush.linearGradient(BGS[page % BGS.size])),
            ) {
                val d = data
                val now = pager.currentPage == page
                if (d == null) Empty(month) else when (page) {
                    0 -> Cover(d, now)
                    1 -> Total(d, now)
                    2 -> TopDay(d, now)
                    3 -> Where(d, now)
                    4 -> Habits(d, now)
                    else -> Ending(d, now)
                }
                Text("Expense Tracking", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 86.dp))
            }
        }
        Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until pages) Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f)),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.recap_title, Labels.monthShort(ctx, month)), color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp))
                IconButton(onBack) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White) }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 20.dp)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 6.dp),
        ) {
            fun capture(share: Boolean) {
                val layer = layers[pager.currentPage]
                scope.launch {
                    val bmp = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()
                    if (bmp == null) { Toast.makeText(ctx, ctx.getString(R.string.recap_save_failed), Toast.LENGTH_SHORT).show(); return@launch }
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val name = "recap-$month-${pager.currentPage + 1}.png"
                    if (share) shareImage(ctx, bmp, name) else {
                        val ok = withContext(Dispatchers.IO) { saveImage(ctx, bmp, name) }
                        Toast.makeText(ctx, ctx.getString(if (ok) R.string.recap_saved else R.string.recap_save_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            TextButton({ capture(false) }) {
                Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.recap_save), color = Color.White)
            }
            TextButton({ capture(true) }) {
                Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(" " + stringResource(R.string.recap_share), color = Color.White)
            }
        }
    }
}

private fun saveImage(ctx: Context, bmp: Bitmap, name: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ExpenseTracking")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val r = ctx.contentResolver
    val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
    r.update(uri, values, null, null)
    true
}.getOrDefault(false)

private fun shareImage(ctx: Context, bmp: Bitmap, name: String) {
    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    val f = File(dir, name)
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---- cards ------------------------------------------------------------------------------------

/** A column of lines that slide in one after another while the card is on screen. */
@Composable
private fun Card(now: Boolean, vararg parts: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp).padding(top = 90.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        parts.forEachIndexed { i, part ->
            AnimatedVisibility(
                now,
                enter = fadeIn(tween(500, 150 * i)) + slideInVertically(tween(500, 150 * i, FastOutSlowInEasing)) { it / 2 },
            ) { Column { part() } }
        }
    }
}

private val small = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp)
private val big = TextStyle(color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Bold)
private val mid = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

@Composable
private fun CountUp(cents: Long, now: Boolean, style: TextStyle) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(now) { if (now) { a.snapTo(0f); a.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) } }
    Text(Money.money((cents * a.value).toLong()), style = style, maxLines = 1)
}

@Composable
private fun Empty(month: YearMonth) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Mascot(Mood.SLEEPY, emptyList(), size = 110.dp)
        Text(stringResource(R.string.recap_empty, Labels.monthShort(ctx, month)), style = mid, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun Cover(d: RecapData, now: Boolean) {
    val ctx = LocalContext.current
    Card(now,
        { Mascot(Mood.HAPPY, listOf(stringResource(R.string.recap_hi)), size = 120.dp) },
        { Text(stringResource(R.string.recap_cover, Labels.yearMonth(ctx, d.month)), style = big.copy(fontSize = 38.sp), modifier = Modifier.padding(top = 24.dp)) },
        { Text(stringResource(R.string.recap_cover_sub, d.count, d.activeDays), style = small, modifier = Modifier.padding(top = 10.dp)) },
        { Text(stringResource(R.string.recap_tap_hint), style = small.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)), modifier = Modifier.padding(top = 36.dp)) },
    )
}

@Composable
private fun Total(d: RecapData, now: Boolean) {
    Card(now,
        { Text(stringResource(R.string.recap_spent), style = small) },
        { CountUp(d.expense, now, big) },
        {
            val diff = d.expense - d.prevExpense
            Text(
                when {
                    d.prevExpense == 0L -> stringResource(R.string.recap_first)
                    diff <= 0 -> stringResource(R.string.recap_less, Money.money(-diff), (-diff * 100 / d.prevExpense).toInt())
                    else -> stringResource(R.string.recap_more, Money.money(diff), (diff * 100 / d.prevExpense).toInt())
                },
                style = mid.copy(fontSize = 20.sp), modifier = Modifier.padding(top = 14.dp),
            )
        },
        { Text(stringResource(R.string.recap_avg, Money.money(d.dailyAvg)), style = small, modifier = Modifier.padding(top = 10.dp)) },
        { if (d.income > 0) Text(stringResource(R.string.recap_income, Money.money(d.income)), style = small, modifier = Modifier.padding(top = 4.dp)) },
    )
}

@Composable
private fun TopDay(d: RecapData, now: Boolean) {
    val ctx = LocalContext.current
    val day = d.topDay ?: return
    Card(now,
        { Text(stringResource(R.string.recap_top_day), style = small) },
        { Text(Labels.monthDay(ctx, day) + " " + Labels.weekday(ctx, day), style = mid.copy(fontSize = 30.sp), modifier = Modifier.padding(top = 6.dp)) },
        { CountUp(d.topDayAmount, now, big) },
        {
            d.topDayTx?.let { t ->
                Text(
                    stringResource(R.string.recap_top_day_on, CategoryStyle.of(t.category).emoji + " " + Labels.category(ctx, t.category) + if (t.note.isNotBlank()) " · " + t.note else "",
                        Money.money(t.amountCents ?: 0)),
                    style = small, modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
    )
}

@Composable
private fun Where(d: RecapData, now: Boolean) {
    val ctx = LocalContext.current
    val parts = mutableListOf<@Composable ColumnScope.() -> Unit>(
        { Text(stringResource(R.string.recap_where), style = mid, modifier = Modifier.padding(bottom = 18.dp)) },
    )
    for ((cat, cents, share) in d.categories) parts += {
        val grow = remember { Animatable(0f) }
        LaunchedEffect(now) { if (now) { grow.snapTo(0f); grow.animateTo(share, tween(900, 200, FastOutSlowInEasing)) } }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text(CategoryStyle.of(cat).emoji, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(Labels.category(ctx, cat), style = small.copy(color = Color.White, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text(Money.money(cents) + "  ${(share * 100).toInt()}%", style = small)
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth(grow.value.coerceAtLeast(0.02f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
                }
            }
        }
    }
    Card(now, *parts.toTypedArray())
}

@Composable
private fun Habits(d: RecapData, now: Boolean) {
    val ctx = LocalContext.current
    val parts = mutableListOf<@Composable ColumnScope.() -> Unit>(
        { Text(stringResource(R.string.recap_habits), style = mid, modifier = Modifier.padding(bottom = 14.dp)) },
    )
    if (d.topNote != null) parts += { Fact("📝", stringResource(R.string.recap_top_note, d.topNote, d.topNoteCount)) }
    parts += { Fact("🍃", stringResource(R.string.recap_no_spend, d.noSpendDays)) }
    d.biggest?.let { b ->
        parts += {
            Fact("💸", stringResource(R.string.recap_biggest, Money.money(b.amountCents ?: 0),
                Labels.category(ctx, b.category) + if (b.note.isNotBlank()) " · " + b.note else ""))
        }
    }
    if (d.lateNight > 0) parts += { Fact("🦉", stringResource(R.string.recap_night, d.lateNight)) }
    Card(now, *parts.toTypedArray())
}

@Composable
private fun Fact(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = small.copy(color = Color.White, fontSize = 18.sp))
    }
}

@Composable
private fun Ending(d: RecapData, now: Boolean) {
    val ctx = LocalContext.current
    val budget = remember { Prefs(ctx).budgetCents }
    val (mood, line) = when {
        budget > 0 && d.expense > budget -> Mood.SAD to R.string.recap_end_over
        d.prevExpense == 0L -> Mood.HAPPY to R.string.recap_end_first
        d.expense <= d.prevExpense -> Mood.HAPPY to R.string.recap_end_less
        else -> Mood.CALM to R.string.recap_end_more
    }
    Card(now,
        { Mascot(mood, listOf(stringResource(line)), size = 130.dp) },
        { Text(stringResource(line), style = mid, modifier = Modifier.padding(top = 24.dp)) },
        { Text(stringResource(R.string.recap_end_sub, Labels.monthShort(ctx, d.month.plusMonths(1))), style = small, modifier = Modifier.padding(top = 10.dp)) },
    )
}
