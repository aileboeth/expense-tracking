package com.aileboeth.expensetracking.ui

import android.app.DatePickerDialog
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Outlives screens, so a delete waiting for "undo" still happens when the user navigates away. */
object AppScope : CoroutineScope by MainScope()

/** Shared state for the quick actions on bill rows: hidden (deleted, undo pending) rows and the category sheet. */
class TxActions(val snackbar: SnackbarHostState) {
    var hidden by mutableStateOf(setOf<Long>())
    var categoryFor by mutableStateOf<Tx?>(null)

    private suspend fun ask(msg: String, action: String): Boolean {
        snackbar.currentSnackbarData?.dismiss() // the previous one commits now
        val r = withTimeoutOrNull(6000) { snackbar.showSnackbar(msg, action, duration = SnackbarDuration.Indefinite) }
        if (r == null) snackbar.currentSnackbarData?.dismiss()
        return r == SnackbarResult.ActionPerformed
    }

    /** Hides the row at once; really deletes it unless "undo" is tapped within a few seconds. */
    fun delete(ctx: android.content.Context, tx: Tx) {
        hidden = hidden + tx.id
        AppScope.launch {
            val undo = ask(ctx.getString(R.string.act_deleted, rowLabel(ctx, tx)), ctx.getString(R.string.act_undo))
            if (!undo) {
                withContext(Dispatchers.IO) { Repo.delete(tx.id) }
                Bubble.refresh()
            }
            hidden = hidden - tx.id
        }
    }

    /** "Same again": a copy of the entry dated now. */
    fun copy(ctx: android.content.Context, tx: Tx) {
        AppScope.launch {
            val id = withContext(Dispatchers.IO) {
                Repo.markActive()
                Repo.insert(tx.copy(id = 0, time = System.currentTimeMillis(), status = Status.OK, source = "manual", channel = "手动", kind = "", raw = "", ref = "", counterpart = ""))
            }
            if (ask(ctx.getString(R.string.act_copied, rowLabel(ctx, tx)), ctx.getString(R.string.act_undo))) {
                withContext(Dispatchers.IO) { Repo.delete(id) }
            }
        }
    }

    fun setCategory(ctx: android.content.Context, tx: Tx, category: String) {
        AppScope.launch {
            withContext(Dispatchers.IO) { Repo.update(tx.copy(category = category)); Repo.markActive() }
            Bubble.refresh()
            if (ask(ctx.getString(R.string.act_recat, com.aileboeth.expensetracking.data.CategoryStyle.of(category).emoji + " " + Labels.category(ctx, category)), ctx.getString(R.string.act_undo))) {
                withContext(Dispatchers.IO) { Repo.update(tx) }
            }
        }
    }

    fun setDate(ctx: android.content.Context, tx: Tx) {
        val zone = ZoneId.systemDefault()
        val dt = Instant.ofEpochMilli(tx.time).atZone(zone)
        DatePickerDialog(ctx, { _, y, m, d ->
            val moved = dt.withYear(y).withMonth(m + 1).withDayOfMonth(d).toInstant().toEpochMilli()
            AppScope.launch { withContext(Dispatchers.IO) { Repo.update(tx.copy(time = moved)) } }
        }, dt.year, dt.monthValue - 1, dt.dayOfMonth).show()
    }

    private fun rowLabel(ctx: android.content.Context, tx: Tx) =
        Labels.category(ctx, tx.category) + (tx.amountCents?.let { " " + Money.money(it) } ?: "")
}

@Composable
fun rememberTxActions(): TxActions = remember { TxActions(SnackbarHostState()) }

/**
 * A bill row you can swipe: left = delete (with undo), right = change category. Long press opens a menu.
 * The row is drawn on [rowColor] so the coloured action behind it only shows while swiping.
 */
@Composable
fun SwipeTxRow(tx: Tx, actions: TxActions, rowColor: Color = MaterialTheme.colorScheme.surface, onEdit: () -> Unit) {
    if (tx.id in actions.hidden) return
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val threshold = with(LocalDensity.current) { 84.dp.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        val x = offset.value
        // What is behind the row.
        if (x != 0f) {
            val left = x > 0
            val bg = if (left) Brand else ExpenseRed
            val past = abs(x) >= threshold
            Row(
                Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).background(bg.copy(alpha = if (past) 1f else 0.75f))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (left) Arrangement.Start else Arrangement.End,
            ) {
                val scale = if (past) 1.15f else 0.9f
                Icon(if (left) Icons.Filled.Label else Icons.Filled.DeleteOutline, null, tint = Color.White, modifier = Modifier.scale(scale))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (left) R.string.act_category else R.string.delete), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(14.dp))
                .background(rowColor)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d ->
                        val v = (offset.value + d).coerceIn(-width, width * 0.6f)
                        scope.launch { offset.snapTo(v) }
                        val now = if (v >= threshold) 1 else if (v <= -threshold) -1 else 0
                        if (now != armed) {
                            armed = now
                            haptic.performHapticFeedback(if (now != 0) HapticFeedbackType.GestureThresholdActivate else HapticFeedbackType.SegmentTick)
                        }
                    },
                    onDragStopped = {
                        val v = offset.value
                        armed = 0
                        when {
                            v <= -threshold -> {
                                offset.animateTo(-width, tween(180))
                                actions.delete(ctx, tx)
                                offset.snapTo(0f)
                            }
                            v >= threshold -> {
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                actions.categoryFor = tx
                            }
                            else -> offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    },
                ),
        ) {
            TxRow(tx, onClick = onEdit, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            })
            DropdownMenu(menu, { menu = false }, offset = androidx.compose.ui.unit.DpOffset(56.dp, 0.dp)) {
                DropdownMenuItem({ Text(stringResource(R.string.act_edit)) }, { menu = false; onEdit() }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                if (tx.amountCents != null) DropdownMenuItem({ Text(stringResource(R.string.act_copy)) }, { menu = false; actions.copy(ctx, tx) },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null) })
                DropdownMenuItem({ Text(stringResource(R.string.act_category)) }, { menu = false; actions.categoryFor = tx }, leadingIcon = { Icon(Icons.Filled.Label, null) })
                DropdownMenuItem({ Text(stringResource(R.string.act_date)) }, { menu = false; actions.setDate(ctx, tx) }, leadingIcon = { Icon(Icons.Filled.Event, null) })
                DropdownMenuItem({ Text(stringResource(R.string.delete), color = ExpenseRed) }, { menu = false; actions.delete(ctx, tx) },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null, tint = ExpenseRed) })
            }
        }
    }
}

/** The bottom sheet that changes a row's category with one tap. Put it once per screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxActionsHost(actions: TxActions) {
    val tx = actions.categoryFor ?: return
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Read up front (tiny query): the sheet measures its height once, so the list must be there from the start.
    val cats = remember(tx.type) { Repo.categories(tx.type) }
    ModalBottomSheet(
        onDismissRequest = { actions.categoryFor = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.act_pick_category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(tx.amountCents?.let { Money.signed(tx) }, tx.note.ifBlank { null }, Labels.channel(ctx, tx.channel).ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(1.dp).padding(top = 12.dp))
            FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), maxItemsInEachRow = 4) {
                for (c in cats) Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (c == tx.category) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            actions.categoryFor = null
                            if (c != tx.category) actions.setCategory(ctx, tx, c)
                        }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CategoryBadge(c, 46)
                    Text(catName(c), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
                repeat((4 - cats.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
