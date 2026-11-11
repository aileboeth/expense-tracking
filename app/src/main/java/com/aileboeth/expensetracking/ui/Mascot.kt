package com.aileboeth.expensetracking.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.aileboeth.expensetracking.data.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The app's little feather with a face. Plain android.graphics so the home-screen widget can draw
 * the very same one into a bitmap.
 */
object MascotPainter {
    private const val INK = 0xFF2A1D7A.toInt()
    private const val BLUSH = 0x66FF7FA8
    private const val DROP = 0xFF7CC4FF.toInt()
    private const val TONGUE = 0xFFFF8FAB.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    private val body = Path().apply {
        moveTo(50f, 6f)
        cubicTo(66f, 12f, 76f, 24f, 78f, 36f)
        lineTo(70.5f, 39f)
        cubicTo(76f, 54f, 70f, 73f, 54f, 86f)
        lineTo(47f, 86f)
        cubicTo(30f, 76f, 22f, 64f, 23.5f, 54f)
        lineTo(30.5f, 50.5f)
        cubicTo(22f, 40f, 30f, 16f, 50f, 6f)
        close()
    }

    /**
     * Draws the feather into a [size]×[size] box at the canvas origin.
     * [blink] 0 = eyes open … 1 = closed. [light] = white body (for purple backgrounds) or lavender (for light ones).
     */
    fun draw(c: Canvas, size: Float, mood: Mood, blink: Float = 0f, light: Boolean = true) {
        synchronized(this) {
            val bodyTop = if (light) 0xFFFFFFFF.toInt() else 0xFFB9AAFF.toInt()
            val bodyBottom = if (light) 0xFFDCD4FF.toInt() else 0xFF8C78F5.toInt()
            val quill = if (light) 0xFFC9BEFF.toInt() else 0xFF6A55F0.toInt()
            c.save()
            c.scale(size / 100f, size / 100f)
            c.rotate(-12f, 50f, 50f)

            // body
            fill.shader = LinearGradient(0f, 6f, 0f, 86f, bodyTop, bodyBottom, Shader.TileMode.CLAMP)
            c.drawPath(body, fill)
            fill.shader = null
            // quill + a hint of the shaft above the face
            stroke.color = quill; stroke.strokeWidth = 3.4f
            c.drawLine(50.5f, 84f, 48.5f, 97f, stroke)
            stroke.strokeWidth = 1.6f
            c.drawLine(50f, 12f, 50.6f, 33f, stroke)
            c.drawLine(51f, 70f, 51f, 84f, stroke)

            stroke.color = INK; stroke.strokeWidth = 2.4f
            fill.color = INK
            when (mood) {
                Mood.HAPPY -> {
                    arcEye(c, 41f, 51f, up = true); arcEye(c, 59f, 51f, up = true)
                    val m = Path().apply { moveTo(44f, 57.5f); quadTo(50f, 67f, 56f, 57.5f); close() }
                    c.drawPath(m, fill)
                    fill.color = TONGUE; c.drawOval(RectF(47.2f, 60.6f, 52.8f, 63.6f), fill); fill.color = INK
                    blush(c)
                }
                Mood.CALM -> {
                    dotEyes(c, blink)
                    c.drawPath(Path().apply { moveTo(45.5f, 58.5f); quadTo(50f, 62.5f, 54.5f, 58.5f) }, stroke)
                    blush(c)
                }
                Mood.WORRIED -> {
                    dotEyes(c, blink)
                    stroke.strokeWidth = 1.9f
                    c.drawLine(36.5f, 44.5f, 44f, 41.5f, stroke); c.drawLine(56f, 41.5f, 63.5f, 44.5f, stroke)
                    stroke.strokeWidth = 2.2f
                    c.drawPath(Path().apply { moveTo(44.5f, 60f); quadTo(47.2f, 57.4f, 50f, 60f); quadTo(52.8f, 62.6f, 55.5f, 60f) }, stroke)
                    drop(c, 70f, 33f, 3.2f)
                }
                Mood.SAD -> {
                    dotEyes(c, blink)
                    stroke.strokeWidth = 1.9f
                    c.drawLine(36.5f, 45.5f, 44f, 42f, stroke); c.drawLine(56f, 42f, 63.5f, 45.5f, stroke)
                    stroke.strokeWidth = 2.3f
                    c.drawPath(Path().apply { moveTo(45f, 62f); quadTo(50f, 56.5f, 55f, 62f) }, stroke)
                    drop(c, 38.5f, 58f, 2.8f)
                }
                Mood.SLEEPY -> {
                    arcEye(c, 41f, 49f, up = false); arcEye(c, 59f, 49f, up = false)
                    stroke.strokeWidth = 1.8f
                    c.drawCircle(50f, 59.5f, 1.9f, stroke)
                    blush(c)
                }
            }
            c.restore()

            if (mood == Mood.SLEEPY) {
                // z's float up next to it, not rotated
                c.save(); c.scale(size / 100f, size / 100f)
                text.color = if (light) 0xEEFFFFFF.toInt() else 0xFF6A55F0.toInt()
                text.textSize = 11f; c.drawText("z", 76f, 26f, text)
                text.textSize = 15f; c.drawText("Z", 84f, 13f, text)
                c.restore()
            }
        }
    }

    private fun dotEyes(c: Canvas, blink: Float) {
        val ry = (4.3f * (1f - blink)).coerceAtLeast(0.7f)
        for (x in floatArrayOf(41f, 59f)) {
            c.drawOval(RectF(x - 3.5f, 50f - ry, x + 3.5f, 50f + ry), fill)
            if (blink < 0.3f) {
                fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(x + 1.2f, 48.6f, 1.15f, fill); fill.color = INK
            }
        }
    }

    private fun arcEye(c: Canvas, x: Float, y: Float, up: Boolean) {
        c.drawPath(Path().apply { moveTo(x - 3.6f, y); quadTo(x, if (up) y - 5.5f else y + 4f, x + 3.6f, y) }, stroke)
    }

    private fun blush(c: Canvas) {
        fill.color = BLUSH
        c.drawCircle(34.5f, 56.5f, 3.4f, fill); c.drawCircle(65.5f, 56.5f, 3.4f, fill)
        fill.color = INK
    }

    private fun drop(c: Canvas, x: Float, y: Float, r: Float) {
        fill.color = DROP
        c.drawPath(Path().apply { moveTo(x, y - r * 2.2f); quadTo(x + r * 1.3f, y - r * 0.2f, x, y + r); quadTo(x - r * 1.3f, y - r * 0.2f, x, y - r * 2.2f); close() }, fill)
        fill.color = INK
    }

    fun bitmap(sizePx: Int, mood: Mood, light: Boolean = true): android.graphics.Bitmap =
        android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888).also {
            draw(Canvas(it), sizePx.toFloat(), mood, 0f, light)
        }
}

/** The feather, alive: it floats, blinks, and bounces + talks when tapped. */
@Composable
fun Mascot(
    mood: Mood,
    lines: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    light: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val float = rememberInfiniteTransition(label = "float")
    val bob by float.animateFloat(-1f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val sway by float.animateFloat(-1f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
    val blink = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    val spin = remember { Animatable(0f) }
    var say by remember { mutableStateOf<String?>(null) }
    var next by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2200L + Random.nextLong(2600))
            blink.animateTo(1f, tween(70)); blink.animateTo(0f, tween(110))
        }
    }
    LaunchedEffect(say) { if (say != null) { delay(3200); say = null } }

    Box(modifier.size(size)) {
        ComposeCanvas(
            Modifier.size(size)
                .graphicsLayer {
                    translationY = bob * 3.dp.toPx()
                    rotationZ = sway * 4f + spin.value
                    scaleX = pop.value; scaleY = pop.value
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    scope.launch {
                        pop.snapTo(0.82f)
                        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium))
                    }
                    if (Random.nextInt(5) == 0) scope.launch { spin.snapTo(0f); spin.animateTo(360f, tween(600)); spin.snapTo(0f) }
                    if (lines.isNotEmpty()) { say = lines[next % lines.size]; next++ }
                },
        ) {
            drawIntoCanvas { MascotPainter.draw(it.nativeCanvas, this.size.minDimension, mood, blink.value, light) }
        }
        val s = say
        if (s != null) Popup(alignment = Alignment.CenterEnd, offset = IntOffset(-with(androidx.compose.ui.platform.LocalDensity.current) { (size + 6.dp).roundToPx() }, 0)) {
            AnimatedVisibility(true, enter = fadeIn() + scaleIn(initialScale = 0.7f), exit = fadeOut() + scaleOut()) {
                Text(
                    s, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2A1D7A),
                    modifier = Modifier.widthIn(max = 220.dp).background(Color.White, RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                        .clickable { say = null }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** The mascot's ink colour, for text that sits next to it. */
val MascotInk = Color(0xFF2A1D7A)
