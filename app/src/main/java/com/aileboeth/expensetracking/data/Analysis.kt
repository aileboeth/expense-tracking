package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Month-end forecast: where this month's spending is heading. */
data class Forecast(
    val spent: Long,
    /** Expected total at the end of the month. */
    val projected: Long,
    val low: Long,
    val high: Long,
    /** Expected spending per remaining day. */
    val rate: Long,
    val daysPassed: Int,
    val daysInMonth: Int,
    val budget: Long,
    /** What can still be spent per day to stay inside the budget (null = no budget, negative = already over). */
    val safeDaily: Long?,
    /** Day of the month the budget is expected to run out (null = it lasts, or no budget). */
    val runOutDay: Int?,
    /** Big one-off expenses left out of the pace (they are not expected to repeat). */
    val oneOffs: Long,
    /** Cumulative spending per day (index 0 = day 1); null for days still to come. */
    val cumulative: List<Long?>,
)

/** Spending habits over a window of days. */
data class Habits(
    val days: Int,
    /** Average spending per weekday, Monday first. */
    val weekdayAvg: LongArray,
    /** Number of expenses per hour of the day. */
    val hourCount: IntArray,
    val hourSum: LongArray,
    /** Counts per amount bucket (bounds in [Analysis.BUCKETS]). */
    val bucketCount: IntArray,
    val bucketSum: LongArray,
    val count: Int,
    val total: Long,
    val medianAmount: Long,
    val perDay: Float,
    val weekdayDaily: Long,
    val weekendDaily: Long,
    val longestNoSpend: Int,
    val noSpendDays: Int,
)

/** One category over several months, with how this month compares to its usual level. */
data class CategoryTrend(
    val category: String,
    /** Monthly totals, oldest first; the last one is the month looked at. */
    val months: List<Long>,
    /** This month, scaled to a whole month when it is still running. */
    val paced: Long,
    /** Average of the earlier months that had spending (0 = no history). */
    val usual: Long,
) {
    /** paced / usual; 0 when there is no history to compare with. */
    val ratio: Float get() = if (usual > 0) paced.toFloat() / usual else 0f
}

data class MonthSum(val month: YearMonth, val expense: Long, val income: Long) {
    val balance: Long get() = income - expense
    /** Share of income kept, or null without income. */
    val savingRate: Float? get() = if (income > 0) (income - expense).toFloat() / income else null
}

data class YearView(
    val year: Int,
    /** Expense per day of the year (index 0 = Jan 1). */
    val daily: LongArray,
    val months: List<MonthSum>,
    val expense: Long,
    val income: Long,
    val activeDays: Int,
)

/** "照这样一年…" style projections. */
data class NoteHabit(val note: String, val times: Int, val sum: Long, val perYear: Long)

data class SmallSpend(val limit: Long, val count: Int, val sum: Long, val perYear: Long)

object Analysis {
    /** Upper bounds of the amount buckets in cents (the last bucket is open). */
    val BUCKETS = longArrayOf(1000, 3000, 5000, 10000, 20000, 50000)

    private fun expenses(txs: List<Tx>) = txs.filter { it.type == TxType.EXPENSE && it.status == Status.OK && (it.amountCents ?: 0) > 0 }

    private fun day(t: Tx, zone: ZoneId) = Instant.ofEpochMilli(t.time).atZone(zone).toLocalDate()

    fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /**
     * Forecast for [month]. [history] = expenses of the three months before, used to steady the pace early in
     * the month. Single expenses far above the usual size are counted once and not extrapolated.
     */
    fun forecast(
        txs: List<Tx>,
        history: List<Tx>,
        month: YearMonth,
        budget: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Forecast {
        val len = month.lengthOfMonth()
        val passed = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> len
        }
        val cur = expenses(txs).filter { YearMonth.from(day(it, zone)) == month }
        val spent = cur.sumOf { it.amountCents!! }
        val perDay = LongArray(len)
        for (t in cur) perDay[day(t, zone).dayOfMonth - 1] += t.amountCents!!
        var run = 0L
        val cumulative = List(len) { i -> if (i < passed) { run += perDay[i]; run } else null }

        val hist = expenses(history)
        val typical = median((cur + hist).map { it.amountCents!! })
        val oneOffLimit = maxOf(typical * 6, 20000L)
        val oneOffs = cur.filter { it.amountCents!! >= oneOffLimit }.sumOf { it.amountCents!! }
        val curRate = if (passed > 0) (spent - oneOffs).toDouble() / passed else 0.0
        val histDays = hist.map { YearMonth.from(day(it, zone)) }.distinct().sumOf { it.lengthOfMonth() }
        val histOneOffs = hist.filter { it.amountCents!! >= oneOffLimit }.sumOf { it.amountCents!! }
        val histRate = if (histDays > 0) (hist.sumOf { it.amountCents!! } - histOneOffs).toDouble() / histDays else null
        // Early in the month the history counts more; by the end only this month matters.
        val w = if (histRate == null) 1.0 else (passed.toDouble() / len).coerceIn(0.25, 1.0)
        val rate = if (histRate == null) curRate else w * curRate + (1 - w) * histRate
        val left = len - passed
        val projected = spent + (rate * left).roundToLong()
        val spread = (rate * left * (if (passed < 7) 0.35 else 0.2)).roundToLong()

        val safe = if (budget > 0) (if (left > 0) (budget - spent) / left else budget - spent) else null
        val runOut = if (budget > 0 && left > 0 && rate > 0 && spent < budget && projected > budget) {
            (passed + ceil((budget - spent) / rate).toInt()).coerceIn(passed + 1, len)
        } else if (budget > 0 && spent >= budget) passed.coerceAtLeast(1).let { d ->
            // the day the budget was actually used up
            cumulative.indexOfFirst { it != null && it >= budget }.takeIf { it >= 0 }?.plus(1) ?: d
        } else null
        return Forecast(
            spent = spent, projected = projected, low = projected - spread, high = projected + spread,
            rate = rate.roundToLong(), daysPassed = passed, daysInMonth = len, budget = budget,
            safeDaily = safe, runOutDay = runOut, oneOffs = oneOffs, cumulative = cumulative,
        )
    }

    /** Habits over the days [from]..[to] (inclusive). */
    fun habits(txs: List<Tx>, from: LocalDate, to: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Habits {
        val days = (to.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(1)
        val ex = expenses(txs).filter { val d = day(it, zone); !d.isBefore(from) && !d.isAfter(to) }
        val wdSum = LongArray(7)
        val wdDays = IntArray(7)
        for (i in 0 until days) wdDays[from.plusDays(i.toLong()).dayOfWeek.value - 1]++
        val hourCount = IntArray(24)
        val hourSum = LongArray(24)
        val bucketCount = IntArray(BUCKETS.size + 1)
        val bucketSum = LongArray(BUCKETS.size + 1)
        val spendDays = HashSet<Long>()
        for (t in ex) {
            val z = Instant.ofEpochMilli(t.time).atZone(zone)
            val c = t.amountCents!!
            wdSum[z.dayOfWeek.value - 1] += c
            hourCount[z.hour]++
            hourSum[z.hour] += c
            val b = BUCKETS.indexOfFirst { c < it }.let { if (it < 0) BUCKETS.size else it }
            bucketCount[b]++
            bucketSum[b] += c
            spendDays += z.toLocalDate().toEpochDay()
        }
        val weekdayAvg = LongArray(7) { if (wdDays[it] > 0) wdSum[it] / wdDays[it] else 0 }
        val weekdayDays = (0..4).sumOf { wdDays[it] }
        val weekendDays = wdDays[5] + wdDays[6]
        var longest = 0
        var cur = 0
        for (i in 0 until days) {
            if (from.toEpochDay() + i in spendDays) cur = 0 else { cur++; longest = maxOf(longest, cur) }
        }
        return Habits(
            days = days, weekdayAvg = weekdayAvg, hourCount = hourCount, hourSum = hourSum,
            bucketCount = bucketCount, bucketSum = bucketSum, count = ex.size, total = ex.sumOf { it.amountCents!! },
            medianAmount = median(ex.map { it.amountCents!! }), perDay = ex.size.toFloat() / days,
            weekdayDaily = if (weekdayDays > 0) (0..4).sumOf { wdSum[it] } / weekdayDays else 0,
            weekendDaily = if (weekendDays > 0) (wdSum[5] + wdSum[6]) / weekendDays else 0,
            longestNoSpend = longest, noSpendDays = days - spendDays.size,
        )
    }

    /**
     * Per category: totals of the [span] months ending with [month], and this month (paced to a full month
     * while it runs) against the average of the earlier months. Biggest this month first.
     */
    fun categoryTrends(
        txs: List<Tx>,
        month: YearMonth,
        span: Int = 6,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CategoryTrend> {
        val first = month.minusMonths((span - 1).toLong())
        val months = (0 until span).map { first.plusMonths(it.toLong()) }
        val ex = expenses(txs)
        val by = HashMap<String, LongArray>()
        for (t in ex) {
            val i = months.indexOf(YearMonth.from(day(t, zone)))
            if (i < 0) continue
            by.getOrPut(t.category.ifBlank { "其他" }) { LongArray(span) }[i] += t.amountCents!!
        }
        val factor = if (month == YearMonth.from(today)) month.lengthOfMonth().toDouble() / today.dayOfMonth else 1.0
        // Months before the first record don't count as "spent nothing".
        val firstUsed = months.indexOfFirst { m -> by.values.any { it[months.indexOf(m)] > 0 } }.coerceAtLeast(0)
        return by.map { (cat, arr) ->
            val earlier = (firstUsed until span - 1).map { arr[it] }
            val usual = if (earlier.isEmpty()) 0 else earlier.sum() / earlier.size
            CategoryTrend(cat, arr.toList(), (arr[span - 1] * factor).roundToLong(), usual)
        }.sortedWith(compareByDescending<CategoryTrend> { it.months.last() }.thenByDescending { it.months.sum() })
    }

    /** Categories clearly above (ratio >= 1.5) or below (<= 0.6) their usual level, by size of the gap. */
    fun anomalies(trends: List<CategoryTrend>, minGap: Long = 5000): List<CategoryTrend> =
        trends.filter { it.usual > 0 && kotlin.math.abs(it.paced - it.usual) >= minGap && (it.ratio >= 1.5f || it.ratio <= 0.6f) }
            .sortedByDescending { kotlin.math.abs(it.paced - it.usual) }

    fun year(txs: List<Tx>, year: Int, zone: ZoneId = ZoneId.systemDefault()): YearView {
        val start = LocalDate.of(year, 1, 1)
        val daily = LongArray(start.lengthOfYear())
        val exp = LongArray(12)
        val inc = LongArray(12)
        val active = HashSet<Int>()
        for (t in txs) {
            if (t.status != Status.OK) continue
            val c = t.amountCents ?: continue
            val d = day(t, zone)
            if (d.year != year) continue
            if (t.type == TxType.EXPENSE) {
                daily[d.dayOfYear - 1] += c
                exp[d.monthValue - 1] += c
            } else inc[d.monthValue - 1] += c
            active += d.dayOfYear
        }
        return YearView(
            year = year, daily = daily,
            months = (0 until 12).map { MonthSum(YearMonth.of(year, it + 1), exp[it], inc[it]) },
            expense = exp.sum(), income = inc.sum(), activeDays = active.size,
        )
    }

    /** Notes written at least [minTimes] times in [days] days, projected to a year. */
    fun noteHabits(txs: List<Tx>, days: Int, minTimes: Int = 3): List<NoteHabit> =
        expenses(txs).filter { it.note.isNotBlank() }.groupBy { it.note.trim() }
            .map { (n, l) -> val s = l.sumOf { it.amountCents!! }; NoteHabit(n, l.size, s, s * 365 / days.coerceAtLeast(1)) }
            .filter { it.times >= minTimes }
            .sortedByDescending { it.perYear }

    /** Expenses below [limit] in [days] days, projected to a year. */
    fun smallSpend(txs: List<Tx>, days: Int, limit: Long = 3000): SmallSpend {
        val small = expenses(txs).filter { it.amountCents!! < limit }
        val s = small.sumOf { it.amountCents!! }
        return SmallSpend(limit, small.size, s, s * 365 / days.coerceAtLeast(1))
    }

    /** Months still needed to fill [left] at [perMonth] (null when nothing is being kept). */
    fun monthsToGoal(left: Long, perMonth: Long): Int? = if (left <= 0) 0 else if (perMonth <= 0) null else ceil(left.toDouble() / perMonth).toInt()
}
