package com.aileboeth.expensetracking.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.TxType
import com.aileboeth.expensetracking.ui.MainActivity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Home-screen card: this month's spending, today, budget left, and a "+ 记一笔" button. */
class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.render(context, manager, ids)
    }
}

object Widgets {
    const val ACTION_ADD = "com.aileboeth.expensetracking.ADD"

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
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java))
        if (ids.isNotEmpty()) render(ctx, mgr, ids)
    }

    fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(ctx)
        val zone = ZoneId.systemDefault()
        val month = YearMonth.now()
        val mStart = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val mEnd = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val dEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val monthSpent = Repo.sum(TxType.EXPENSE, mStart, mEnd)
        val todaySpent = Repo.sum(TxType.EXPENSE, dStart, dEnd)
        val budget = Prefs(ctx).budgetCents
        val pending = runCatching { Repo.pending().size }.getOrDefault(0)

        val res = ctx.applicationContext
        val detail = buildString {
            append(res.getString(R.string.widget_today, Money.money(todaySpent)))
            if (budget > 0) {
                val left = budget - monthSpent
                append(if (left >= 0) res.getString(R.string.widget_budget_left, Money.money(left)) else res.getString(R.string.widget_over_budget, Money.money(-left)))
            }
            if (pending > 0) append(res.getString(R.string.widget_pending, pending))
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val add = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).setAction(ACTION_ADD).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(ctx.packageName, R.layout.widget_summary).apply {
            setTextViewText(R.id.widget_title, res.getString(R.string.widget_month_spent, com.aileboeth.expensetracking.ui.Labels.monthShort(res, month)))
            setTextViewText(R.id.widget_month, Money.money(monthSpent))
            setTextViewText(R.id.widget_add, res.getString(R.string.widget_add))
            setTextViewText(R.id.widget_detail, detail)
            setOnClickPendingIntent(R.id.widget_root, open)
            setOnClickPendingIntent(R.id.widget_add, add)
        }
        mgr.updateAppWidget(ids, views)
    }
}
