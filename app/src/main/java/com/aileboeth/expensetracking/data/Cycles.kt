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
 * With [payDates] (days money in a pay category came in) a period starts on the day the pay really arrived,
 * when that is within [WINDOW] days of the set day — early or late pay still opens the right period.
 */
class PayCalendar(payDay: Int = 0, payDates: Collection<LocalDate> = emptyList()) {
    /** 0 = not set (calendar months), else 1..31. */
    val payDay: Int = payDay.coerceIn(0, 31)
    private val dates: List<LocalDate> = payDates.distinct().sorted()
    private val starts = java.util.concurrent.ConcurrentHashMap<YearMonth, LocalDate>()

    /** Periods are exactly the calendar months. */
    val natural: Boolean get() = payDay <= 1 && dates.isEmpty()

    /** The set pay day in [m]; months without that day use their last day. */
    fun nominal(m: YearMonth): LocalDate = m.atDay(payDay.coerceIn(1, m.lengthOfMonth()))

    /** First day of period [m]. */
    fun start(m: YearMonth): LocalDate = starts.getOrPut(m) {
        val n = nominal(m)
        dates.firstOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(n, it)) <= WINDOW } ?: n
    }

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
            if (p.payAdjust) runCatching { Repo.incomeDays(p.payCategories) }.getOrDefault(emptyList()) else emptyList(),
        )
    }
}
