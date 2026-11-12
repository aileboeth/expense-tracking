package com.aileboeth.expensetracking.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.Mood
import com.aileboeth.expensetracking.data.MoodCalc
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Streak
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import com.aileboeth.expensetracking.ui.Labels
import com.aileboeth.expensetracking.ui.MainActivity
import com.aileboeth.expensetracking.ui.MascotPainter
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Re-draws when the widget is resized (older Android picks no size-specific layout by itself). */
abstract class SizedWidget(private val kind: Widgets.Kind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.render(context, manager, ids, kind)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle?) =
        Widgets.render(context, manager, intArrayOf(id), kind)
}

/** 4×2: this month, budget ring with the feather, last 7 days, usual amounts to tap, pending to fill. */
class SummaryWidget : SizedWidget(Widgets.Kind.SUMMARY)

/** 2×2: the feather, today's spending and the check-in streak. */
class TodayWidget : SizedWidget(Widgets.Kind.TODAY)

/** 4×1: one tap = one entry with a usual amount. */
class QuickWidget : SizedWidget(Widgets.Kind.QUICK)

/** Taps on widget buttons that work without opening the app. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        Repo.init(ctx)
        when (intent.action) {
            Widgets.ACTION_QUICK -> {
                val cents = intent.getLongExtra("cents", 0L).takeIf { it > 0 } ?: return
                val id = Repo.insert(Tx(
                    type = TxType.EXPENSE, amountCents = cents, category = Bubble.OTHER, channel = "手动", kind = "widget",
                    time = System.currentTimeMillis(), status = Status.OK, source = "manual",
                ))
                Repo.markActive()
                tick(ctx)
                // The capsule lets them pick a category right there; without it a toast says where to find it.
                val prefs = Prefs(ctx)
                if (prefs.bubbleEnabled && Bubble.canDraw(ctx)) Bubble.show(ctx, id)
                else Toast.makeText(ctx, ctx.getString(R.string.widget_quick_done, Money.money(cents)), Toast.LENGTH_SHORT).show()
            }
            Widgets.ACTION_FILL -> {
                val id = intent.getLongExtra("tx", 0L)
                if (id <= 0 || !Bubble.openFor(ctx, id)) {
                    ctx.startActivity(Intent(ctx, MainActivity::class.java).setAction(Widgets.ACTION_FILL).putExtra("tx", id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }
        }
    }

    private fun tick(ctx: Context) = runCatching {
        if (!Prefs(ctx).fxHaptics) return@runCatching
        ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
    }
}

object Widgets {
    const val ACTION_ADD = "com.aileboeth.expensetracking.ADD"
    const val ACTION_QUICK = "com.aileboeth.expensetracking.QUICK"
    const val ACTION_FILL = "com.aileboeth.expensetracking.FILL"

    enum class Kind(val provider: Class<*>, val layout: Int) {
        SUMMARY(SummaryWidget::class.java, R.layout.widget_summary),
        TODAY(TodayWidget::class.java, R.layout.widget_today),
        QUICK(QuickWidget::class.java, R.layout.widget_quick),
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var appCtx: Context? = null
    private val refresh = Runnable { appCtx?.let { runCatching { updateAll(it) } } }

    /** Coalesces bursts of database writes into one widget update. */
    fun scheduleUpdate(ctx: Context) {
        appCtx = ctx.applicationContext
        main.removeCallbacks(refresh)
        main.postDelayed(refresh, 800)
    }

    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (k in Kind.entries) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, k.provider))
            if (ids.isNotEmpty()) render(ctx, mgr, ids, k)
        }
    }

    /** Everything a widget shows, read once. */
    class Data(
        val month: YearMonth,
        val monthSpent: Long,
        val todaySpent: Long,
        val budget: Long,
        val pending: List<Tx>,
        val week: LongArray,
        val mood: Mood,
        val streak: Int,
        val usual: List<Long>,
        /** Fixed wording for the picker preview ("本月支出" instead of "11月支出"). */
        val preview: Boolean = false,
    ) {
        val background: Int
            get() = when {
                budget > 0 && monthSpent >= budget -> R.drawable.widget_bg_over
                mood == Mood.WORRIED -> R.drawable.widget_bg_warn
                mood == Mood.SLEEPY -> R.drawable.widget_bg_night
                else -> R.drawable.widget_bg
            }

        companion object {
            fun load(ctx: Context): Data {
                val zone = ZoneId.systemDefault()
                fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
                val today = LocalDate.now()
                val month = YearMonth.now()
                val prefs = Prefs(ctx)
                return Data(
                    month = month,
                    monthSpent = Repo.sum(TxType.EXPENSE, ms(month.atDay(1)), ms(month.plusMonths(1).atDay(1))),
                    todaySpent = Repo.sum(TxType.EXPENSE, ms(today), ms(today.plusDays(1))),
                    budget = prefs.budgetCents,
                    pending = runCatching { Repo.pending() }.getOrDefault(emptyList()),
                    week = LongArray(7) { i -> val d = today.minusDays((6 - i).toLong()); Repo.sum(TxType.EXPENSE, ms(d), ms(d.plusDays(1))) },
                    mood = MoodCalc.now(ctx),
                    streak = Streak.current(Repo.activeDays(), today.toEpochDay()),
                    usual = prefs.usualAmounts(TxType.EXPENSE).ifEmpty {
                        Repo.frequentAmounts(TxType.EXPENSE, System.currentTimeMillis() - 60L * 86_400_000, 4)
                    },
                )
            }

            /** Made-up numbers for the widget picker preview. */
            fun sample() = Data(
                month = YearMonth.now(), monthSpent = 123_580, todaySpent = 3_950, budget = 300_000, pending = emptyList(),
                week = longArrayOf(4_200, 1_600, 0, 12_800, 2_350, 5_900, 3_950), mood = Mood.HAPPY, streak = 12,
                usual = listOf(800, 1_500, 3_000, 5_000), preview = true,
            )
        }
    }

    /** Widget sizes (dp) a layout is drawn for; Android 12+ picks the biggest that fits, older ones get one by the options. */
    private val SIZES = mapOf(
        Kind.SUMMARY to listOf(180, 250, 320).flatMap { w -> listOf(110, 170, 210, 250, 300).map { h -> SizeDp(w, h) } },
        Kind.TODAY to listOf(SizeDp(100, 100), SizeDp(150, 150)),
        Kind.QUICK to listOf(SizeDp(200, 50), SizeDp(260, 50), SizeDp(320, 50), SizeDp(400, 50)),
    )

    data class SizeDp(val w: Int, val h: Int)

    /** Height (dp) of everything in the 4×2 widget except the bars: paddings, header, buttons, day labels. */
    private const val BARS_FIXED_DP = 162

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, kind: Kind) {
        Repo.init(ctx)
        val d = runCatching { Data.load(ctx) }.getOrNull() ?: return
        for (id in ids) runCatching {
            val views = if (android.os.Build.VERSION.SDK_INT >= 31) {
                RemoteViews(SIZES.getValue(kind).associate { s -> android.util.SizeF(s.w.toFloat(), s.h.toFloat()) to build(ctx, d, kind, s) })
            } else {
                // portrait: the width is the smallest, the height the largest of the reported range
                val o = mgr.getAppWidgetOptions(id)
                val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: SIZES.getValue(kind).last().w
                val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: SIZES.getValue(kind).last().h
                build(ctx, d, kind, SizeDp(w, h))
            }
            mgr.updateAppWidget(id, views)
        }
    }

    private fun build(ctx: Context, d: Data, kind: Kind, s: SizeDp): RemoteViews = when (kind) {
        Kind.SUMMARY -> summary(ctx, d, s)
        Kind.TODAY -> today(ctx, d)
        Kind.QUICK -> quick(ctx, d, s)
    }

    /** The views of one widget kind at one size (screenshots and picker previews on the PC). */
    fun preview(ctx: Context, kind: Kind, size: SizeDp = SIZES.getValue(kind).last(), data: Data? = null): RemoteViews {
        Repo.init(ctx)
        return build(ctx, data ?: Data.load(ctx), kind, size)
    }

    private fun open(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun add(ctx: Context) = PendingIntent.getActivity(
        ctx, 1, Intent(ctx, MainActivity::class.java).setAction(ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun quickTap(ctx: Context, slot: Int, cents: Long) = PendingIntent.getBroadcast(
        ctx, 10 + slot, Intent(ctx, WidgetActionReceiver::class.java).setAction(ACTION_QUICK).putExtra("cents", cents),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun fill(ctx: Context, tx: Long) = PendingIntent.getBroadcast(
        ctx, 30, Intent(ctx, WidgetActionReceiver::class.java).setAction(ACTION_FILL).putExtra("tx", tx),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun RemoteViews.quickButtons(ctx: Context, d: Data, slots: IntArray) {
        slots.forEachIndexed { i, id ->
            val c = d.usual.getOrNull(i)
            if (c == null) setViewVisibility(id, View.GONE) else {
                setViewVisibility(id, View.VISIBLE)
                setTextViewText(id, Money.sym + Money.short(c))
                setOnClickPendingIntent(id, quickTap(ctx, i, c))
            }
        }
    }

    private fun RemoteViews.pendingPill(ctx: Context, d: Data, id: Int, short: Boolean) {
        val p = d.pending.firstOrNull()
        if (p == null) { setViewVisibility(id, View.GONE); return }
        setViewVisibility(id, View.VISIBLE)
        setTextViewText(id, if (short) ctx.getString(R.string.widget_pending_short, d.pending.size)
            else ctx.resources.getQuantityString(R.plurals.widget_pending_tap, d.pending.size, d.pending.size))
        setOnClickPendingIntent(id, fill(ctx, p.id))
    }

    private val BAR = intArrayOf(R.id.widget_b1, R.id.widget_b2, R.id.widget_b3, R.id.widget_b4, R.id.widget_b5, R.id.widget_b6, R.id.widget_b7)
    private val BAR_LABEL = intArrayOf(R.id.widget_l1, R.id.widget_l2, R.id.widget_l3, R.id.widget_l4, R.id.widget_l5, R.id.widget_l6, R.id.widget_l7)

    // Bitmaps are shared by all sizes of one update (one copy goes through Binder).
    private var ringCache: Pair<Any, Bitmap>? = null
    private var mascotCache: Pair<Any, Bitmap>? = null

    private fun ringFor(ctx: Context, d: Data): Bitmap {
        val key = listOf(d.budget, d.monthSpent, d.mood, ctx.resources.displayMetrics.density)
        ringCache?.let { (k, b) -> if (k == key) return b }
        return ring((64 * ctx.resources.displayMetrics.density).toInt(), d).also { ringCache = key to it }
    }

    private fun mascotFor(ctx: Context, d: Data, dp: Int): Bitmap {
        val key = listOf(d.mood, dp, ctx.resources.displayMetrics.density)
        mascotCache?.let { (k, b) -> if (k == key) return b }
        return MascotPainter.bitmap((dp * ctx.resources.displayMetrics.density).toInt(), d.mood).also { mascotCache = key to it }
    }

    private fun summary(ctx: Context, d: Data, s: SizeDp) = RemoteViews(ctx.packageName, R.layout.widget_summary).apply {
        setInt(R.id.widget_root, "setBackgroundResource", d.background)
        val title = if (d.preview) ctx.getString(R.string.home_month_spent) else ctx.getString(R.string.widget_month_spent, Labels.monthShort(ctx, d.month))
        setTextViewText(R.id.widget_title, title + if (d.streak > 0) "  🔥" + d.streak else "")
        setTextViewText(R.id.widget_month, Money.money(d.monthSpent))
        setTextViewText(R.id.widget_detail, buildString {
            append(ctx.getString(R.string.widget_today, Money.money(d.todaySpent)))
            if (d.budget > 0) {
                val left = d.budget - d.monthSpent
                // resource strings lose their leading space, so it is added here
                append(" " + (if (left >= 0) ctx.getString(R.string.widget_budget_left, Money.money(left)) else ctx.getString(R.string.widget_over_budget, Money.money(-left))).trim())
            }
        })
        setViewVisibility(R.id.widget_detail, if (s.h >= 140) View.VISIBLE else View.GONE)
        if (s.h < 140) setViewPadding(R.id.widget_root, dp(ctx, 14), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
        setImageViewBitmap(R.id.widget_ring, ringFor(ctx, d))
        // A pending entry gets a slim pill (short wording when narrow); the bars stay if there is still room.
        pendingPill(ctx, d, R.id.widget_pending, short = s.w < 250)

        val showBars = s.h >= 165 + (if (d.pending.isEmpty()) 0 else 40)
        setViewVisibility(R.id.widget_fill, if (showBars) View.GONE else View.VISIBLE)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            setViewVisibility(R.id.widget_bars, View.GONE)
            setViewVisibility(R.id.widget_bars_box, if (showBars) View.VISIBLE else View.GONE)
            if (showBars) weekBars(ctx, d, s)
        } else {
            setViewVisibility(R.id.widget_bars_box, View.GONE)
            setViewVisibility(R.id.widget_bars, if (showBars) View.VISIBLE else View.GONE)
            if (showBars) {
                val dm = ctx.resources.displayMetrics.density
                setImageViewBitmap(R.id.widget_bars, bars(((s.w - 26) * dm).toInt(), ((s.h - 140).coerceIn(24, 90) * dm).toInt(), d.week))
            }
        }

        // As many amount buttons as fit; narrow widgets get a round "+" instead of "+ 记一笔".
        val wide = s.w >= 300
        val addW = if (wide) 96 else 40
        val chips = ((s.w - 26 - addW) / 54).coerceIn(1, 3)
        val slots = intArrayOf(R.id.widget_q1, R.id.widget_q2, R.id.widget_q3)
        // Too short for the pill: the pending entry takes the first button instead.
        val pendingInRow = s.h < 150 && d.pending.isNotEmpty()
        if (pendingInRow) setViewVisibility(R.id.widget_pending, View.GONE)
        val amountSlots = slots.take(chips).drop(if (pendingInRow) 1 else 0).toIntArray()
        quickButtons(ctx, d, amountSlots)
        if (pendingInRow) {
            setViewVisibility(R.id.widget_q1, View.VISIBLE)
            setTextViewText(R.id.widget_q1, ctx.getString(R.string.widget_pending_short, d.pending.size))
            setOnClickPendingIntent(R.id.widget_q1, fill(ctx, d.pending.first().id))
        }
        for (id in slots.drop(chips)) setViewVisibility(id, View.GONE)
        if (d.usual.isEmpty() || wide) {
            setTextViewText(R.id.widget_add, ctx.getString(R.string.widget_add))
            setViewPadding(R.id.widget_add, dp(ctx, 12), 0, dp(ctx, 12), 0)
            setTextViewTextSize(R.id.widget_add, android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        } else {
            setTextViewText(R.id.widget_add, "+")
            setViewPadding(R.id.widget_add, 0, 0, 0, 0)
            setTextViewTextSize(R.id.widget_add, android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
        }
        setOnClickPendingIntent(R.id.widget_root, open(ctx))
        setOnClickPendingIntent(R.id.widget_add, add(ctx))
    }

    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Android 12+: bar heights through layout weights, today's bar bright, weekday initials underneath. */
    @android.annotation.TargetApi(31)
    private fun RemoteViews.weekBars(ctx: Context, d: Data, s: SizeDp) {
        val max = (d.week.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val today = LocalDate.now()
        val loc = com.aileboeth.expensetracking.ui.AppLocale.locale(ctx)
        val barW = when { s.w >= 300 -> 16f; s.w >= 240 -> 13f; else -> 10f }
        // What is left for the bars once header, buttons, paddings and the day labels are placed.
        val room = (s.h - BARS_FIXED_DP - (if (d.pending.isEmpty()) 0 else 40)).coerceAtLeast(14).toFloat()
        for (i in 0 until 7) {
            val v = d.week[i]
            val bh = if (v == 0L) 5f else (room * (0.12f + 0.88f * v / max)).coerceAtLeast(barW * 0.8f)
            setViewLayoutHeight(BAR[i], bh, android.util.TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutWidth(BAR[i], barW, android.util.TypedValue.COMPLEX_UNIT_DIP)
            setInt(BAR[i], "setBackgroundResource", when {
                i == 6 -> R.drawable.widget_bar_today
                v == 0L -> R.drawable.widget_bar_zero
                else -> R.drawable.widget_bar
            })
            val day = today.minusDays((6 - i).toLong())
            // "今" in Chinese; languages without a one-letter word for today keep the weekday (bright = today)
            val todayWord = ctx.getString(R.string.widget_today_short)
            setTextViewText(BAR_LABEL[i], if (i == 6 && todayWord.isNotBlank()) todayWord
                else day.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, loc))
            setTextColor(BAR_LABEL[i], if (i == 6) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt())
        }
    }

    private fun today(ctx: Context, d: Data) = RemoteViews(ctx.packageName, R.layout.widget_today).apply {
        setInt(R.id.widget_root, "setBackgroundResource", d.background)
        setImageViewBitmap(R.id.widget_mascot, mascotFor(ctx, d, 60))
        setTextViewText(R.id.widget_label, ctx.getString(R.string.today))
        setTextViewText(R.id.widget_today, Money.money(d.todaySpent))
        // With a budget: what is left per day for the rest of the month.
        if (d.budget > 0) {
            val daysLeft = d.month.lengthOfMonth() - LocalDate.now().dayOfMonth + 1
            val left = d.budget - d.monthSpent + d.todaySpent
            setViewVisibility(R.id.widget_left, View.VISIBLE)
            setTextViewText(R.id.widget_left, if (left > 0) ctx.getString(R.string.widget_per_day, Money.money(left / daysLeft))
                else ctx.getString(R.string.widget_budget_gone))
        } else setViewVisibility(R.id.widget_left, View.GONE)
        setTextViewText(R.id.widget_streak, if (d.streak > 0) ctx.resources.getQuantityString(R.plurals.widget_streak, d.streak, d.streak)
            else ctx.getString(R.string.widget_streak_none))
        setOnClickPendingIntent(R.id.widget_root, open(ctx))
    }

    private fun quick(ctx: Context, d: Data, s: SizeDp) = RemoteViews(ctx.packageName, R.layout.widget_quick).apply {
        setInt(R.id.widget_root, "setBackgroundResource", d.background)
        setImageViewBitmap(R.id.widget_mascot, mascotFor(ctx, d, 40))
        setOnClickPendingIntent(R.id.widget_mascot, open(ctx))
        pendingPill(ctx, d, R.id.widget_pending, short = true)
        // As many amounts as fit next to the feather, the "+" and (if any) the pending pill.
        val room = s.w - 18 - 44 - 34 - (if (d.pending.isNotEmpty()) 84 else 0)
        val n = (room / 56).coerceIn(1, 4)
        val all = intArrayOf(R.id.widget_q1, R.id.widget_q2, R.id.widget_q3, R.id.widget_q4)
        quickButtons(ctx, d, all.take(n).toIntArray())
        for (id in all.drop(n)) setViewVisibility(id, View.GONE)
        setOnClickPendingIntent(R.id.widget_add, add(ctx))
        setOnClickPendingIntent(R.id.widget_root, open(ctx))
    }

    /** Budget ring (if a budget is set) around the feather. */
    private fun ring(size: Int, d: Data): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val stroke = size * 0.08f
        val r = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = 0x40FFFFFF
        c.drawOval(r, p)
        if (d.budget > 0) {
            val ratio = (d.monthSpent.toFloat() / d.budget).coerceIn(0f, 1f)
            p.color = when {
                ratio >= 1f -> 0xFFFFB4A8.toInt()
                ratio >= 0.8f -> 0xFFFFE08A.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            if (ratio > 0f) c.drawArc(r, -90f, 360f * ratio, false, p)
        }
        val m = (size * 0.68f).toInt()
        c.drawBitmap(MascotPainter.bitmap(m, d.mood), (size - m) / 2f, (size - m) / 2f + size * 0.02f, null)
        return b
    }

    /** Last 7 days as small rounded bars; today is the brightest. */
    private fun bars(w: Int, h: Int, week: LongArray): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val max = (week.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val gap = w / 7f
        val bw = (gap * 0.42f).coerceAtMost(h * 0.5f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        week.forEachIndexed { i, v ->
            val x = gap * i + (gap - bw) / 2
            p.color = if (i == 6) 0xF2FFFFFF.toInt() else if (v == 0L) 0x33FFFFFF else 0x73FFFFFF
            if (v == 0L) { c.drawCircle(x + bw / 2, h - bw * 0.3f, bw * 0.3f, p); return@forEachIndexed }
            val bh = (h * 0.12f + h * 0.88f * v / max).coerceAtLeast(bw)
            c.drawRoundRect(RectF(x, h - bh, x + bw, h.toFloat()), bw / 2, bw / 2, p)
        }
        return b
    }
}
