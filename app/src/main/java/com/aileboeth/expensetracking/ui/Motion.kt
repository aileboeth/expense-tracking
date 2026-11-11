package com.aileboeth.expensetracking.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.aileboeth.expensetracking.data.Money
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Text whose characters roll like an odometer when it changes: up when the number grew, down when it shrank.
 * Characters are matched from the right so the digits line up.
 */
@Composable
fun RollingText(text: String, up: Boolean, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        val n = text.length
        text.forEachIndexed { i, ch ->
            val fromEnd = n - i
            key(fromEnd) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val d = (fromEnd * 28).coerceAtMost(220)
                        (slideInVertically(tween(320, d)) { h -> if (up) h else -h } + fadeIn(tween(220, d))) togetherWith
                            (slideOutVertically(tween(320, d)) { h -> if (up) -h else h } + fadeOut(tween(160, d))) using
                            SizeTransform(clip = true)
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color, maxLines = 1) }
            }
        }
    }
}

/** An amount that rolls to its new value. */
@Composable
fun RollingMoney(cents: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val last = remember { longArrayOf(cents) }
    val up = cents >= last[0]
    LaunchedEffect(cents) { last[0] = cents }
    RollingText(Money.money(cents), up, style, color, modifier)
}

private class Bit(val vx: Float, val vy: Float, val spin: Float, val color: Color, val round: Boolean, val w: Float, val h: Float)

private val CONFETTI = listOf(
    Color(0xFF8F7BFF), Color(0xFF5B45E0), Color(0xFFFFC14D), Color(0xFFFF7FA8), Color(0xFF5FD3A0), Color(0xFF7CC4FF), Color(0xFFFFFFFF),
)

/** A burst of confetti from [origin] (fractions of the box) whenever [trigger] changes. Draws nothing when done. */
@Composable
fun Confetti(trigger: Any?, modifier: Modifier = Modifier.fillMaxSize(), origin: Offset = Offset(0.5f, 0.35f), count: Int = 110) {
    if (trigger == null) return
    val bits = remember(trigger) {
        List(count) {
            val a = Random.nextFloat() * 2f * PI.toFloat()
            val speed = 0.35f + Random.nextFloat() * 0.75f
            Bit(
                vx = cos(a) * speed * 0.8f, vy = sin(a) * speed - 0.55f, spin = (Random.nextFloat() - 0.5f) * 900f,
                color = CONFETTI[Random.nextInt(CONFETTI.size)], round = Random.nextInt(3) == 0,
                w = 6f + Random.nextFloat() * 6f, h = 3f + Random.nextFloat() * 4f,
            )
        }
    }
    val t = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { t.animateTo(1f, tween(2400, easing = LinearEasing)) }
    if (t.value >= 1f) return
    Canvas(modifier) {
        val time = t.value * 2.4f
        val dp = 1.dp.toPx()
        val ox = size.width * origin.x
        val oy = size.height * origin.y
        val alpha = (1f - t.value * t.value).coerceIn(0f, 1f)
        for (b in bits) {
            val x = ox + b.vx * size.width * time
            val y = oy + b.vy * size.height * 0.6f * time + 0.55f * size.height * time * time
            if (y > size.height + 20) continue
            rotate(b.spin * time, Offset(x, y)) {
                if (b.round) drawCircle(b.color.copy(alpha = alpha), b.h * dp * 0.8f, Offset(x, y))
                else drawRect(b.color.copy(alpha = alpha), Offset(x - b.w * dp / 2, y - b.h * dp / 2), Size(b.w * dp, b.h * dp))
            }
        }
    }
}

/** A purple disc that pops in and draws a check mark; [onDone] after it has been seen. */
@Composable
fun SuccessCheck(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.3f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) }
            launch { kotlinx.coroutines.delay(90); draw.animateTo(1f, tween(260)) }
        }
        kotlinx.coroutines.delay(260)
        onDone()
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(96.dp)) {
            val r = size.minDimension / 2 * pop.value
            drawCircle(Brand, r)
            val p = Path().apply {
                moveTo(size.width * 0.29f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.66f)
                lineTo(size.width * 0.72f, size.height * 0.37f)
            }
            val m = PathMeasure().apply { setPath(p, false) }
            val part = Path()
            m.getSegment(0f, m.length * draw.value, part, true)
            drawPath(part, Color.White, style = Stroke(width = 7.dp.toPx() * pop.value, cap = StrokeCap.Round))
        }
    }
}
