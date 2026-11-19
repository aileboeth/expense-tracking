package com.aileboeth.expensetracking.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The ledger's "month": from one pay day to the day before the next. Each period is named after the calendar
 * month its pay day is set in (pay day 15 → "November" runs Nov 15 – Dec 14), so the rest of the app keeps
 * using [YearMonth] as the key. Without a pay day the periods are plain calendar months.
 *
 * With [pays] (incomes in a pay category) a period starts on the day the pay really arrived, when that is
 * within [WINDOW] days of the set day — early or late pay still opens the right period. Several in the
 * window: the biggest one wins. An income under half of the usual pay (the median of the other pays) is
 * not pay, so a small entry filed under 工资 by mistake does not move the month.
 */
class PayCalendar(payDay: Int = 0, pays: Collection<PayIn> = emptyList()) {
    /** 0 = not set (calendar months), else 1..31. */
    val payDay: Int = payDay.coerceIn(0, 31)
    private val pays: List<PayIn> = pays.toList().let { all ->
        all.filter { p ->
            val others = all.filter { it !== p }.map { it.cents }
            others.isEmpty() || p.cents * 2 >= Analysis.median(others)
        }.sortedBy { it.date }
    }
    private val starts = java.util.concurrent.ConcurrentHashMap<YearMonth, Pair<LocalDate, PayIn?>>()

    /** Periods are exactly the calendar months. */
    val natural: Boolean get() = payDay <= 1 && pays.isEmpty()

    /** The set pay day in [m]; months without that day use their last day. */
    fun nominal(m: YearMonth): LocalDate = m.atDay(payDay.coerceIn(1, m.lengthOfMonth()))

    private fun opening(m: YearMonth): Pair<LocalDate, PayIn?> = starts.getOrPut(m) {
        val n = nominal(m)
        val p = pays.filter { kotlin.math.abs(ChronoUnit.DAYS.between(n, it.date)) <= WINDOW }
            .sortedWith(compareByDescending<PayIn> { it.cents }
                .thenBy { kotlin.math.abs(ChronoUnit.DAYS.between(n, it.date)) }
                .thenBy { it.date })
            .firstOrNull()
        (p?.date ?: n) to p
    }

    /** First day of period [m]. */
    fun start(m: YearMonth): LocalDate = opening(m).first

    /** The income that moved the start of [m] off the set day; null when it starts on the set day. */
    fun openedBy(m: YearMonth): PayIn? = opening(m).second?.takeIf { it.date != nominal(m) }

    /** First day after period [m]. */
    fun end(m: YearMonth): LocalDate = start(m.plusMonths(1))

    fun days(m: YearMonth): Int = ChronoUnit.DAYS.between(start(m), end(m)).toInt()

    /** The period [d] falls in. */
    fun monthOf(d: LocalDate): YearMonth {
        val m = YearMonth.from(d)
        return when {
            d.isBefore(start(m)) -> m.minusMonths(1)
            !d.isBefore(end(m)) -> m.plusMonths(1)
            else -> m
        }
    }

    fun monthOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        monthOf(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate())

    /** The running period. */
    fun current(today: LocalDate = LocalDate.now()): YearMonth = monthOf(today)

    /** Day [d] counted from the start of period [m] (0 = its first day). */
    fun index(m: YearMonth, d: LocalDate): Int = ChronoUnit.DAYS.between(start(m), d).toInt()

    /** The [i]-th day of period [m] (0 = its first day). */
    fun date(m: YearMonth, i: Int): LocalDate = start(m).plusDays(i.toLong())

    /** Days of [m] gone by, today included: all of them for a past period, none for a future one. */
    fun passed(m: YearMonth, today: LocalDate = LocalDate.now()): Int {
        val c = current(today)
        return when {
            m == c -> index(m, today) + 1
            m.isAfter(c) -> 0
            else -> days(m)
        }
    }

    /** The last day the numbers of [m] run to: today for the running period. */
    fun lastDay(m: YearMonth, today: LocalDate = LocalDate.now()): LocalDate =
        if (m == current(today)) today else end(m).minusDays(1)

    /** [start, end) of period [m] in epoch milliseconds. */
    fun range(m: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        start(m).atStartOfDay(zone).toInstant().toEpochMilli() to end(m).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        /** How far from the set day an arrived pay may be and still open the period. */
        const val WINDOW = 7L
    }
}

/** A confirmed income in a pay category: the day it came in, how much, which entry. */
data class PayIn(val date: LocalDate, val cents: Long, val id: Long = 0, val category: String = "")

/** The app-wide calendar, rebuilt after every ledger write and settings change. */
object Cycles {
    @Volatile var cal: PayCalendar = PayCalendar()
        private set

    private var app: Context? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        reload()
    }

    /** Re-reads the pay day and the days pay came in. */
    fun reload() {
        val ctx = app ?: return
        val p = Prefs(ctx)
        val day = p.payDay
        cal = if (day == 0) PayCalendar() else PayCalendar(
            day,
            if (p.payAdjust) {
                val ignored = p.payIgnored
                runCatching { Repo.payIncomes(p.payCategories) }.getOrDefault(emptyList()).filter { it.id !in ignored }
            } else emptyList(),
        )
    }
}
