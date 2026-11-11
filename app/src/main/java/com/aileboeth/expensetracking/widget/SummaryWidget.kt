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

/** 4×2: this month, budget ring with the feather, last 7 days, usual amounts to tap, pending to fill. */
class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.render(context, manager, ids, Widgets.Kind.SUMMARY)
}

/** 2×2: the feather, today's spending and the check-in streak. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.render(context, manager, ids, Widgets.Kind.TODAY)
}

/** 4×1: one tap = one entry with a usual amount. */
class QuickWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.render(context, manager, ids, Widgets.Kind.QUICK)
}

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
    private class Data(ctx: Context) {
        val month: YearMonth = YearMonth.now()
        val monthSpent: Long
        val todaySpent: Long
        val budget: Long = Prefs(ctx).budgetCents
        val pending: List<Tx>
        val week: LongArray
        val mood: Mood
        val streak: Int
        val usual: List<Long>

        init {
            val zone = ZoneId.systemDefault()
            fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val today = LocalDate.now()
            monthSpent = Repo.sum(TxType.EXPENSE, ms(month.atDay(1)), ms(month.plusMonths(1).atDay(1)))
            todaySpent = Repo.sum(TxType.EXPENSE, ms(today), ms(today.plusDays(1)))
            pending = runCatching { Repo.pending() }.getOrDefault(emptyList())
            week = LongArray(7) { i -> val d = today.minusDays((6 - i).toLong()); Repo.sum(TxType.EXPENSE, ms(d), ms(d.plusDays(1))) }
            mood = MoodCalc.now(ctx)
            streak = Streak.current(Repo.activeDays(), today.toEpochDay())
            usual = Prefs(ctx).usualAmounts(TxType.EXPENSE).ifEmpty {
                Repo.frequentAmounts(TxType.EXPENSE, System.currentTimeMillis() - 60L * 86_400_000, 4)
            }
        }

        val background: Int
            get() = when {
                budget > 0 && monthSpent >= budget -> R.drawable.widget_bg_over
                mood == Mood.WORRIED -> R.drawable.widget_bg_warn
                mood == Mood.SLEEPY -> R.drawable.widget_bg_night
                else -> R.drawable.widget_bg
            }
    }

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, kind: Kind) {
        Repo.init(ctx)
        val d = runCatching { Data(ctx) }.getOrNull() ?: return
        val views = when (kind) {
            Kind.SUMMARY -> summary(ctx, d)
            Kind.TODAY -> today(ctx, d)
            Kind.QUICK -> quick(ctx, d)
        }
        mgr.updateAppWidget(ids, views)
    }

    /** The views of one widget kind (for screenshots on the PC). */
    fun preview(ctx: Context, kind: Kind): RemoteViews {
        Repo.init(ctx)
        val d = Data(ctx)
        return when (kind) {
            Kind.SUMMARY -> summary(ctx, d)
            Kind.TODAY -> today(ctx, d)
            Kind.QUICK -> quick(ctx, d)
        }
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

    private fun summary(ctx: Context, d: Data) = RemoteViews(ctx.packageName, R.layout.widget_summary).apply {
        setInt(R.id.widget_root, "setBackgroundResource", d.background)
        setTextViewText(R.id.widget_title, ctx.getString(R.string.widget_month_spent, Labels.monthShort(ctx, d.month)) +
            if (d.streak > 0) "  🔥" + d.streak else "")
        setTextViewText(R.id.widget_month, Money.money(d.monthSpent))
        setTextViewText(R.id.widget_detail, buildString {
            append(ctx.getString(R.string.widget_today, Money.money(d.todaySpent)))
            if (d.budget > 0) {
                val left = d.budget - d.monthSpent
                append(if (left >= 0) ctx.getString(R.string.widget_budget_left, Money.money(left)) else ctx.getString(R.string.widget_over_budget, Money.money(-left)))
            }
        })
        val dm = ctx.resources.displayMetrics.density
        setImageViewBitmap(R.id.widget_ring, ring((64 * dm).toInt(), d))
        setImageViewBitmap(R.id.widget_bars, bars((280 * dm).toInt(), (30 * dm).toInt(), d.week))
        pendingPill(ctx, d, R.id.widget_pending, short = false)
        setViewVisibility(R.id.widget_bars, if (d.pending.isEmpty()) View.VISIBLE else View.GONE)
        quickButtons(ctx, d, intArrayOf(R.id.widget_q1, R.id.widget_q2, R.id.widget_q3))
        setTextViewText(R.id.widget_add, ctx.getString(R.string.widget_add))
        setOnClickPendingIntent(R.id.widget_root, open(ctx))
        setOnClickPendingIntent(R.id.widget_add, add(ctx))
    }

    private fun today(ctx: Context, d: Data) = RemoteViews(ctx.packageName, R.layout.widget_today).apply {
        setInt(R.id.widget_root, "setBackgroundResource", d.background)
        val dm = ctx.resources.displayMetrics.density
        setImageViewBitmap(R.id.widget_mascot, MascotPainter.bitmap((60 * dm).toInt(), d.mood))
        setTextViewText(R.id.widget_label, ctx.getString(R.string.today))
        setTextViewText(R.id.widget_today, Money.money(d.todaySpent))
        setTextViewText(R.id.widget_streak, if (d.streak > 0) ctx.resources.getQuantityString(R.plurals.widget_streak, d.streak, d.streak)
            else ctx.getString(R.string.widget_streak_none))
        setOnClickPendingIntent(R.id.widget_root, open(ctx))
    }

    private fun quick(ctx: Context, d: Data) = RemoteViews(ctx.packageName, R.layout.widget_quick).apply {
        setInt(R.id.widget_root, "setBackgroundResource", d.background)
        val dm = ctx.resources.displayMetrics.density
        setImageViewBitmap(R.id.widget_mascot, MascotPainter.bitmap((40 * dm).toInt(), d.mood))
        setOnClickPendingIntent(R.id.widget_mascot, open(ctx))
        pendingPill(ctx, d, R.id.widget_pending, short = true)
        // With a pending entry showing there is room for three amounts.
        quickButtons(ctx, d, if (d.pending.isEmpty()) intArrayOf(R.id.widget_q1, R.id.widget_q2, R.id.widget_q3, R.id.widget_q4)
            else intArrayOf(R.id.widget_q1, R.id.widget_q2, R.id.widget_q3))
        if (d.pending.isNotEmpty()) setViewVisibility(R.id.widget_q4, View.GONE)
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
        val bw = gap * 0.42f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        week.forEachIndexed { i, v ->
            val bh = if (v == 0L) bw * 0.5f else (h * 0.15f + h * 0.85f * v / max)
            val x = gap * i + (gap - bw) / 2
            p.color = if (i == 6) 0xF2FFFFFF.toInt() else 0x66FFFFFF
            c.drawRoundRect(RectF(x, h - bh, x + bw, h.toFloat()), bw / 2, bw / 2, p)
        }
        return b
    }
}
