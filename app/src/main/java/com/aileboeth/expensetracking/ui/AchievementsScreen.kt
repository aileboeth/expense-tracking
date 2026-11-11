package com.aileboeth.expensetracking.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Achievements
import com.aileboeth.expensetracking.data.BadgeProgress
import com.aileboeth.expensetracking.data.Mood
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Streak
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val GOLD = Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFFFB86B)))
private val LAV = Brush.linearGradient(listOf(Color(0xFFE9E4FF), Color(0xFFC9BEFF)))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val progress = repoQuery(initial = emptyList<BadgeProgress>()) { Achievements.progress(ctx) }
    val days = repoQuery(initial = emptySet<Long>()) { Repo.activeDays() }
    val today = LocalDate.now().toEpochDay()
    val streak = Streak.current(days, today)
    val best = maxOf(Streak.best(days), streak)
    var open by remember { mutableStateOf<BadgeProgress?>(null) }
    val got = progress.count { it.unlocked }

    SubPage(stringResource(R.string.ach_title), onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp)) {
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeaderBrush).padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.ach_streak), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("🔥", fontSize = 30.sp)
                                Spacer(Modifier.width(6.dp))
                                RollingText(streak.toString(), true, androidx.compose.ui.text.TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold), Color.White)
                                Text(" " + pluralStringResource(R.plurals.days_word, streak),
                                    color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(
                                stringResource(R.string.ach_best_total, best, days.size),
                                color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Mascot(if (streak >= 3) Mood.HAPPY else Mood.CALM, listOf(
                            if (today in days) stringResource(R.string.ach_today_done) else stringResource(R.string.ach_today_todo),
                            stringResource(R.string.ach_keep_going),
                        ), size = 70.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    StreakGrid(days, today)
                }
                Text(
                    stringResource(R.string.ach_how), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            item { GroupLabel(stringResource(R.string.ach_badges), "$got / ${progress.size}") }
            item {
                SoftCard(padding = 10.dp) {
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 3) {
                        for (p in progress.sortedByDescending { it.unlockedAt != null }) BadgeTile(p, Modifier.weight(1f)) { open = p }
                        repeat((3 - progress.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    open?.let { p ->
        AlertDialog(
            onDismissRequest = { open = null },
            icon = { BadgeMedal(p.def.emoji, p.unlocked, 64) },
            title = { Text(stringResource(p.def.name), textAlign = TextAlign.Center) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(p.def.desc), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        p.unlockedAt?.let { stringResource(R.string.ach_unlocked_on, Labels.monthDay(ctx, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())) }
                            ?: stringResource(R.string.ach_progress, p.current.coerceAtMost(p.def.goal), p.def.goal),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = { TextButton({ open = null }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** The last 5 weeks, one dot per day; lit = checked in. Today has a ring. */
@Composable
private fun StreakGrid(days: Set<Long>, today: Long) {
    val first = today - 34
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (row in 0 until 5) Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (col in 0 until 7) {
                val d = first + row * 7 + col
                val on = d in days
                Box(
                    Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (on) Color.White else Color.White.copy(alpha = 0.16f))
                        .then(if (d == today) Modifier.border(2.dp, Color(0xFFFFC14D), RoundedCornerShape(5.dp)) else Modifier),
                )
            }
        }
    }
}

@Composable
fun BadgeMedal(emoji: String, unlocked: Boolean, size: Int) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (unlocked) GOLD else LAV)
            .then(if (unlocked) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.46f).sp, modifier = Modifier.alpha(if (unlocked) 1f else 0.35f))
    }
}

@Composable
private fun BadgeTile(p: BadgeProgress, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgeMedal(p.def.emoji, p.unlocked, 56)
        Text(
            stringResource(p.def.name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp),
            color = if (p.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!p.unlocked && p.def.goal > 1) {
            LinearProgressIndicator(
                progress = { (p.current.toFloat() / p.def.goal).coerceIn(0f, 1f) },
                strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                modifier = Modifier.padding(top = 6.dp).width(56.dp).height(4.dp),
            )
            Text("${p.current.coerceAtMost(p.def.goal)}/${p.def.goal}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!p.unlocked) {
            Text(stringResource(R.string.ach_locked), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Pops up over everything when badges were just unlocked: medals bounce in, confetti falls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeCelebration(ids: List<String>, onClose: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val defs = ids.mapNotNull { Achievements.def(it) }
    if (defs.isEmpty()) { LaunchedEffect(Unit) { onClose() }; return }
    LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }
    Dialog(onDismissRequest = onClose) {
        Box {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (defs.size == 1) stringResource(R.string.ach_new_one) else pluralStringResource(R.plurals.ach_new_many, defs.size, defs.size),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    defs.take(9).forEachIndexed { i, d ->
                        val s = remember { Animatable(0f) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(120L * i)
                            s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                        }
                        Column(
                            Modifier.width(if (defs.size == 1) 200.dp else 86.dp).padding(6.dp).scale(s.value),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BadgeMedal(d.emoji, true, if (defs.size == 1) 88 else 60)
                            Text(stringResource(d.name), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp), maxLines = 2, fontSize = if (defs.size == 1) 17.sp else 13.sp)
                            if (defs.size == 1) Text(stringResource(d.desc), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.Button(onClose, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Brand)) {
                    Text(stringResource(R.string.ach_yay))
                }
            }
            Confetti(ids, Modifier.matchParentSize(), origin = androidx.compose.ui.geometry.Offset(0.5f, 0.3f))
        }
    }
}
