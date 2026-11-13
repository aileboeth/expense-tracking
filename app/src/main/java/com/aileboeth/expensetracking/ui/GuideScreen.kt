package com.aileboeth.expensetracking.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.capture.ScreenService
import com.aileboeth.expensetracking.data.Mood
import kotlinx.coroutines.launch

private const val PAGES = 5

/** First-run guide: what the app does, the capsule, the permissions, full auto, and a few tips. */
@Composable
fun GuideScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES - 1
    // White status-bar icons over the purple top while the guide is open.
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val before = ctl?.isAppearanceLightStatusBars
        ctl?.isAppearanceLightStatusBars = false
        onDispose { if (before != null) ctl.isAppearanceLightStatusBars = before }
    }
    BackHandler { if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onDone() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                when (page) {
                    0 -> GuidePage(
                        art = { Mascot(Mood.HAPPY, listOf(stringResource(R.string.guide_1_a)), size = 120.dp) },
                        title = stringResource(R.string.guide_1_title), body = stringResource(R.string.guide_1_body),
                    ) {
                        Bullets(
                            Icons.Filled.Notifications to stringResource(R.string.guide_1_a),
                            Icons.Filled.AutoAwesome to stringResource(R.string.guide_1_b),
                            Icons.Filled.BarChart to stringResource(R.string.guide_1_c),
                        )
                    }
                    1 -> GuidePage(art = { CapsuleDemo() }, title = stringResource(R.string.guide_2_title), body = stringResource(R.string.guide_2_body)) {
                        Bullets(
                            Icons.Filled.Check to stringResource(R.string.guide_2_a),
                            Icons.Filled.PictureInPictureAlt to stringResource(R.string.guide_2_b),
                            Icons.AutoMirrored.Filled.CallSplit to stringResource(R.string.guide_2_c),
                        )
                    }
                    2 -> PermissionsPage()
                    3 -> FullAutoPage()
                    else -> GuidePage(
                        art = { Mascot(Mood.CALM, emptyList(), size = 110.dp) },
                        title = stringResource(R.string.guide_5_title), body = null,
                    ) {
                        Bullets(
                            Icons.Filled.SwipeLeft to stringResource(R.string.guide_5_a),
                            Icons.Filled.SwipeRight to stringResource(R.string.guide_5_b),
                            Icons.Filled.BarChart to stringResource(R.string.guide_5_c),
                            Icons.Filled.Widgets to stringResource(R.string.guide_5_d),
                            Icons.Filled.UploadFile to stringResource(R.string.guide_5_e),
                        )
                    }
                }
            }
            // dots + buttons
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton({ if (pager.currentPage == 0) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(stringResource(if (pager.currentPage == 0) R.string.guide_skip else R.string.guide_back), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(PAGES) { i ->
                        val on = i == pager.currentPage
                        val w by animateDpAsState(if (on) 20.dp else 7.dp, label = "dot")
                        val c by animateColorAsState(if (on) Brand else MaterialTheme.colorScheme.outlineVariant, label = "dotc")
                        Box(Modifier.padding(horizontal = 3.dp).height(7.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                Button(
                    { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Brand),
                ) {
                    Text(stringResource(if (last) R.string.guide_start else R.string.guide_next))
                    if (!last) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Gradient top with the picture, then title, text and the page's own content. */
@Composable
private fun GuidePage(art: @Composable () -> Unit, title: String, body: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(
            Modifier.fillMaxWidth().height(270.dp)
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(HeaderBrush)
                .statusBarsPadding(),
            contentAlignment = Alignment.Center,
        ) { art() }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(18.dp))
            content()
        }
    }
}

@Composable
private fun Bullets(vararg items: Pair<ImageVector, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingIcon(icon, Brand, size = 34)
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** A fake payment notification and the two capsules it can give. */
@Composable
private fun CapsuleDemo() {
    Box(Modifier.fillMaxSize().padding(top = 24.dp)) {
        Row(
            Modifier.align(Alignment.TopCenter).padding(horizontal = 28.dp).fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2DC100)), contentAlignment = Alignment.Center) {
                Text("¥", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(stringResource(R.string.guide_demo_notice), color = Color(0xFF1D1A2B), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(stringResource(R.string.guide_demo_notice_text), color = Color(0xFF6C6880), fontSize = 13.sp)
            }
        }
        // The capsules hug the right edge like on the phone.
        Column(Modifier.align(Alignment.CenterEnd).padding(top = 70.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.End) {
            DemoCapsule(Brush.horizontalGradient(listOf(Color(0xFF8F7BFF), Color(0xFF5B45E0))), Color.White, "¥30")
            DemoCapsule(Brush.horizontalGradient(listOf(Color(0xFFF4F1FF), Color(0xFFDCD4FF))), Color(0xFF3F2CC4), stringResource(R.string.guide_demo_pending))
        }
    }
}

@Composable
private fun DemoCapsule(brush: Brush, ink: Color, text: String) {
    Row(
        Modifier.shadow(4.dp, RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp))
            .clip(RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp)).background(brush)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.mipmap.ic_launcher_mono), null, tint = ink, modifier = Modifier.size(26.dp).scale(1.7f))
        Text(text, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun PermissionsPage() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val listener = remember(tick) { Perms.listener(ctx) }
    val overlay = remember(tick) { Perms.overlay(ctx) }
    val battery = remember(tick) { Perms.battery(ctx) }
    val done = listOf(listener, overlay, battery).count { it }
    GuidePage(
        art = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$done / 3", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Icons.Filled.Notifications to listener, Icons.Filled.PictureInPictureAlt to overlay, Icons.Filled.BatteryChargingFull to battery).forEach { (icon, ok) ->
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = if (ok) 0.30f else 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (ok) Icons.Filled.Check else icon, null, tint = Color.White) }
                    }
                }
            }
        },
        title = stringResource(R.string.guide_3_title), body = stringResource(R.string.guide_3_body),
    ) {
        SettingGroup(
            { SettingRow(Icons.Filled.Notifications, Tint.Purple, stringResource(R.string.guide_3_listener), stringResource(R.string.guide_3_listener_sub), { StatusPill(listener) }) { Perms.openListener(ctx) } },
            { SettingRow(Icons.Filled.PictureInPictureAlt, Tint.Blue, stringResource(R.string.guide_3_overlay), stringResource(R.string.guide_3_overlay_sub), { StatusPill(overlay) }) { Perms.openOverlay(ctx) } },
            { SettingRow(Icons.Filled.BatteryChargingFull, Tint.Green, stringResource(R.string.guide_3_battery), stringResource(R.string.guide_3_battery_sub), { StatusPill(battery) }) { Perms.openBattery(ctx) } },
        )
        Text(stringResource(R.string.set_restricted_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp))
        Text(stringResource(R.string.guide_3_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp))
    }
}

@Composable
private fun FullAutoPage() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val state = remember(tick) { fullAutoState(ctx) }
    GuidePage(
        art = {
            Box(Modifier.size(110.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(if (state == 1) Icons.Filled.Check else Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(58.dp))
            }
        },
        title = stringResource(R.string.guide_4_title), body = stringResource(R.string.guide_4_body),
    ) {
        SettingGroup(
            {
                SettingRow(Icons.Filled.AutoAwesome, Tint.Pink, stringResource(R.string.set_full_auto), stringResource(R.string.set_full_auto_sub),
                    { StatusPill(state == 1) }) { ScreenService.open(ctx) }
            },
        )
        Text(stringResource(R.string.fa_where), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp))
        Text(stringResource(R.string.guide_4_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp))
    }
}

/** Whether the guide should open by itself (first start). */
fun guidePending(ctx: android.content.Context) = !com.aileboeth.expensetracking.data.Prefs(ctx).guideDone

/** Marks it seen. */
fun guideDone(ctx: android.content.Context) { com.aileboeth.expensetracking.data.Prefs(ctx).guideDone = true }
