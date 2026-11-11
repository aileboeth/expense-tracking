package com.aileboeth.expensetracking.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Wish
import com.aileboeth.expensetracking.data.WishLog
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.tan

private val WISH_EMOJI = listOf("🎧", "📱", "💻", "✈️", "🎮", "👟", "📷", "🎁", "🏠", "🚗", "🎸", "💍", "🐱", "📚", "🌴", "💰")
private val LIQUID = Brush.verticalGradient(listOf(Color(0xFFB6A8FF), Color(0xFF6A55F0)))
private val GLASS = Color(0xFF9C8BFF)

/** Draws a glass jar filled to [progress]; the surface waves with [phase] and leans with [tilt] (degrees). */
private fun DrawScope.jar(progress: Float, phase: Float, tilt: Float, bubbles: Float, outline: Color) {
    val w = size.width
    val h = size.height
    val neckH = h * 0.12f
    val lidH = h * 0.08f
    val body = RoundRect(w * 0.04f, lidH + neckH * 0.6f, w * 0.96f, h * 0.98f, CornerRadius(w * 0.22f))
    val bodyPath = Path().apply { addRoundRect(body) }
    // lid
    drawRoundRect(Color(0xFFFFC14D), Offset(w * 0.2f, 0f), Size(w * 0.6f, lidH), CornerRadius(lidH * 0.4f))
    drawRoundRect(outline.copy(alpha = 0.18f), Offset(w * 0.24f, lidH), Size(w * 0.52f, neckH), CornerRadius(4f))
    // glass
    drawPath(bodyPath, Color.White.copy(alpha = 0.55f))
    clipPath(bodyPath) {
        if (progress > 0f) {
            val top = body.top + (body.bottom - body.top) * (1f - progress)
            val lean = tan(Math.toRadians(tilt.toDouble())).toFloat()
            val amp = (h * 0.018f) * (if (progress >= 0.999f) 0.3f else 1f)
            val p = Path().apply {
                moveTo(0f, h)
                var x = 0f
                lineTo(0f, top + (x - w / 2) * lean)
                while (x <= w) {
                    val y = top + (x - w / 2) * lean + sin((x / w) * 2f * PI.toFloat() * 1.3f + phase) * amp
                    lineTo(x, y); x += w / 40f
                }
                lineTo(w, h); close()
            }
            drawPath(p, LIQUID)
            // a few bubbles rising
            for (i in 0 until 6) {
                val bx = w * (0.18f + 0.13f * i)
                val travel = (bubbles + i * 0.17f) % 1f
                val by = body.bottom - (body.bottom - top) * travel
                if (by > top + 6) drawCircle(Color.White.copy(alpha = 0.35f * (1f - travel)), w * (0.012f + 0.006f * (i % 3)), Offset(bx, by))
            }
        }
        // shine
        drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(w * 0.12f, body.top + h * 0.08f), Size(w * 0.07f, h * 0.45f), CornerRadius(w * 0.04f))
    }
    drawPath(bodyPath, outline, style = Stroke(width = w * 0.025f))
}

/** Small still jar for the home tile. */
@Composable
fun MiniJar(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) { jar(progress, 0f, 0f, 0f, GLASS) }
}

/** Big jar: waves, bubbles, and leans when the phone is tilted. */
@Composable
private fun LiveJar(progress: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    var rawTilt by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // The liquid stays level while the phone turns, so it leans the other way on screen.
                rawTilt = Math.toDegrees(atan2(e.values[0].toDouble(), e.values[1].toDouble())).toFloat().coerceIn(-28f, 28f)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    val tilt by animateFloatAsState(rawTilt, spring(dampingRatio = 0.35f, stiffness = 60f), label = "tilt")
    val fill by animateFloatAsState(progress, tween(1200), label = "fill")
    val t = rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, 2f * PI.toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase")
    val bub by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "bub")
    Canvas(modifier) { jar(fill, phase, tilt, bub, GLASS) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WishJarScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val haptic = LocalHapticFeedback.current
    val wishes = repoQuery(initial = emptyList<Wish>()) { Repo.wishes() }
    var selected by remember { mutableLongStateOf(prefs.focusWish) }
    val w = wishes.firstOrNull { it.id == selected } ?: wishes.firstOrNull { it.done == 0L } ?: wishes.firstOrNull()
    val log = repoQuery(w?.id, initial = emptyList<WishLog>()) { w?.let { Repo.wishLog(it.id) } ?: emptyList() }
    val lastMonth = YearMonth.now().minusMonths(1)
    val surplus = repoQuery(initial = 0L) {
        val r = monthRange(lastMonth)
        Repo.sum(TxType.INCOME, r.first, r.second) - Repo.sum(TxType.EXPENSE, r.first, r.second)
    }
    var editWish by remember { mutableStateOf<Wish?>(null) }
    var creating by remember { mutableStateOf(false) }
    var depositing by remember { mutableIntStateOf(0) } // 1 = put in, -1 = take out
    var confirmDelete by remember { mutableStateOf(false) }
    var party by remember { mutableStateOf<Long?>(null) }

    fun deposit(cents: Long, note: String) {
        val wish = w ?: return
        val wasDone = wish.done > 0
        AppScope.launch {
            withContext(Dispatchers.IO) { Repo.deposit(wish.id, cents, note); Repo.markActive() }
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!wasDone && wish.saved + cents >= wish.target) party = System.currentTimeMillis()
        }
    }

    Box(Modifier.fillMaxSize()) {
        SubPage(stringResource(R.string.wish_title), onBack) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)) {
                // wish picker
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (x in wishes) {
                            val on = x.id == w?.id
                            Text(
                                x.emoji + " " + x.name + if (x.done > 0) " ✓" else "",
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Brand else MaterialTheme.colorScheme.surface)
                                    .clickable { selected = x.id; prefs.focusWish = x.id }.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).clickable { creating = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.wish_new), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                if (w == null) item {
                    SoftCard(padding = 24.dp) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            LiveJar(0.08f, Modifier.size(width = 150.dp, height = 190.dp))
                            Text(stringResource(R.string.wish_empty_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 16.dp))
                            Text(stringResource(R.string.wish_empty_body), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                            Button({ creating = true }, colors = ButtonDefaults.buttonColors(containerColor = Brand), modifier = Modifier.padding(top = 16.dp)) {
                                Text(stringResource(R.string.wish_set_one))
                            }
                        }
                    }
                } else {
                    item {
                        SoftCard(padding = 18.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(w.emoji, fontSize = 26.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(w.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                IconButton({ editWish = w }) { Icon(Icons.Filled.Edit, stringResource(R.string.act_edit)) }
                                IconButton({ confirmDelete = true }) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.delete)) }
                            }
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                LiveJar(w.progress, Modifier.size(width = 170.dp, height = 215.dp))
                                Text(
                                    "${(w.progress * 100).toInt()}%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 50.dp).background(Color(0x332A1D7A), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                Money.money(w.saved) + " / " + Money.money(w.target),
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            )
                            Text(
                                if (w.done > 0) stringResource(R.string.wish_done_long) else stringResource(R.string.wish_left, Money.money(w.target - w.saved)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            )
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button({ depositing = 1 }, colors = ButtonDefaults.buttonColors(containerColor = Brand), modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.wish_put_in)) }
                                FilledTonalButton({ depositing = -1 }, enabled = w.saved > 0, modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(14.dp)) { Text(stringResource(R.string.wish_take_out)) }
                            }
                        }
                    }
                    if (surplus > 0 && prefs.surplusMonthDone != lastMonth.toString() && w.done == 0L) item {
                        SoftCard(modifier = Modifier.padding(top = 12.dp), padding = 14.dp) {
                            Text(stringResource(R.string.wish_surplus, Labels.monthShort(ctx, lastMonth), Money.money(surplus)), fontWeight = FontWeight.Medium)
                            Row(Modifier.padding(top = 6.dp)) {
                                Spacer(Modifier.weight(1f))
                                TextButton({ prefs.surplusMonthDone = lastMonth.toString(); Repo.markActive() }) { Text(stringResource(R.string.wish_no_thanks)) }
                                TextButton({
                                    prefs.surplusMonthDone = lastMonth.toString()
                                    deposit(surplus, ctx.getString(R.string.wish_surplus_note, Labels.monthShort(ctx, lastMonth)))
                                }) { Text(stringResource(R.string.wish_put_in)) }
                            }
                        }
                    }
                    if (log.isNotEmpty()) {
                        item { GroupLabel(stringResource(R.string.wish_history)) }
                        item {
                            SoftCard(padding = 12.dp) {
                                for (l in log.take(50)) Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(32.dp).background((if (l.amount >= 0) Brand else ExpenseRed).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                                        Text(if (l.amount >= 0) "🪙" else "↩", fontSize = 15.sp)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(l.note.ifBlank { stringResource(if (l.amount >= 0) R.string.wish_put_in else R.string.wish_take_out) }, fontWeight = FontWeight.Medium)
                                        Text(Labels.monthDay(ctx, Instant.ofEpochMilli(l.time).atZone(ZoneId.systemDefault()).toLocalDate()),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text((if (l.amount >= 0) "+" else "-") + Money.format(kotlin.math.abs(l.amount)), fontWeight = FontWeight.SemiBold,
                                        color = if (l.amount >= 0) Brand else ExpenseRed)
                                }
                            }
                        }
                    }
                    item {
                        Text(stringResource(R.string.wish_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
                    }
                }
            }
        }
        Confetti(party, Modifier.fillMaxSize(), origin = Offset(0.5f, 0.3f), count = 160)
    }

    if (creating || editWish != null) WishDialog(editWish, onClose = { creating = false; editWish = null }) { saved ->
        AppScope.launch {
            val id = withContext(Dispatchers.IO) { Repo.putWish(saved).also { Repo.markActive() } }
            selected = id; prefs.focusWish = id
        }
        creating = false; editWish = null
    }
    if (depositing != 0 && w != null) DepositDialog(depositing > 0, if (depositing < 0) w.saved else null, onClose = { depositing = 0 }) { cents, note ->
        deposit(if (depositing > 0) cents else -cents, note)
        depositing = 0
    }
    if (confirmDelete && w != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(stringResource(R.string.wish_delete_title, w.name)) },
        text = { Text(stringResource(R.string.wish_delete_body)) },
        confirmButton = {
            TextButton({ confirmDelete = false; AppScope.launch { withContext(Dispatchers.IO) { Repo.deleteWish(w.id) } } }) {
                Text(stringResource(R.string.delete), color = ExpenseRed)
            }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WishDialog(initial: Wish?, onClose: () -> Unit, onSave: (Wish) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: WISH_EMOJI.first()) }
    var target by remember { mutableStateOf(initial?.target?.let { Money.short(it) } ?: "") }
    val cents = Money.parse(target)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(if (initial == null) R.string.wish_new_title else R.string.wish_edit_title)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(16) }, label = { Text(stringResource(R.string.wish_name)) },
                    placeholder = { Text(stringResource(R.string.wish_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.wish_target)) }, prefix = { Text(Money.sym + " ") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (e in WISH_EMOJI) Box(
                        Modifier.size(38.dp).clip(CircleShape).background(if (e == emoji) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 20.sp) }
                }
            }
        },
        confirmButton = {
            TextButton({
                val c = cents ?: return@TextButton
                onSave((initial ?: Wish(name = "", emoji = "", target = 0)).copy(name = name.trim(), emoji = emoji, target = c,
                    done = if (initial != null && initial.saved < c) 0 else initial?.done ?: 0))
            }, enabled = name.isNotBlank() && cents != null) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DepositDialog(putIn: Boolean, max: Long?, onClose: () -> Unit, onSave: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val cents = Money.parse(amount)?.takeIf { max == null || it <= max }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(if (putIn) R.string.wish_put_in else R.string.wish_take_out)) },
        text = {
            Column {
                OutlinedTextField(amount, { amount = it }, prefix = { Text(Money.sym + " ") }, singleLine = true,
                    isError = amount.isNotBlank() && cents == null,
                    supportingText = max?.let { { Text(stringResource(R.string.max_amount, Money.money(it))) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (putIn) FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in listOf(1000L, 5000L, 10000L, 50000L)) Text(
                        Money.sym + Money.short(c),
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { amount = Money.short(c) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                OutlinedTextField(note, { note = it.take(20) }, placeholder = { Text(stringResource(R.string.wish_note_hint)) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ cents?.let { onSave(it, note.trim()) } }, enabled = cents != null) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClose) { Text(stringResource(R.string.cancel)) } },
    )
}
