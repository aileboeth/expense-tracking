package com.aileboeth.expensetracking.data

import android.content.Context
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.parse.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A savings goal ("心愿罐"): [saved] is the sum of its log, money is only counted, never moved. */
data class Wish(
    val id: Long = 0,
    val name: String,
    val emoji: String,
    val target: Long,
    val created: Long = System.currentTimeMillis(),
    /** When it reached the target (0 = not yet). */
    val done: Long = 0,
    val saved: Long = 0,
) {
    val progress: Float get() = if (target <= 0) 0f else (saved.toFloat() / target).coerceIn(0f, 1f)
}

data class WishLog(val id: Long, val amount: Long, val time: Long, val note: String)

/** Raw numbers the badges are computed from. */
data class BadgeStats(
    val entries: Long,
    val withNote: Long,
    val receipts: Long,
    val filled: Long,
    val widget: Long,
    val wishesDone: Long,
    val firstEntry: Long,
    /** Hours of day (0..23) that have at least one entry. */
    val hours: Set<Int>,
    /** Epoch days with at least one expense. */
    val expenseDays: Set<Long>,
)

/** Check-in streaks over epoch days. */
object Streak {
    /** Days in a row up to today; if today has no check-in yet, the run ending yesterday still counts. */
    fun current(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    fun best(days: Set<Long>): Int {
        var best = 0
        var run = 0
        var prev = Long.MIN_VALUE
        for (d in days.sorted()) {
            run = if (d == prev + 1) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Past days the user checked in but spent nothing. */
    fun noSpendDays(active: Set<Long>, expenseDays: Set<Long>, today: Long): List<Long> =
        active.filter { it < today && it !in expenseDays }.sorted()
}

data class BadgeDef(val id: String, val emoji: String, val name: Int, val desc: Int, val goal: Long = 1)

data class BadgeProgress(val def: BadgeDef, val current: Long, val unlockedAt: Long?) {
    val unlocked get() = unlockedAt != null
}

object Achievements {
    val ALL = listOf(
        BadgeDef("first", "🌱", R.string.bd_first, R.string.bd_first_d),
        BadgeDef("streak3", "🔥", R.string.bd_streak3, R.string.bd_streak3_d, 3),
        BadgeDef("streak7", "⚡", R.string.bd_streak7, R.string.bd_streak7_d, 7),
        BadgeDef("streak30", "🏆", R.string.bd_streak30, R.string.bd_streak30_d, 30),
        BadgeDef("streak100", "👑", R.string.bd_streak100, R.string.bd_streak100_d, 100),
        BadgeDef("count100", "💯", R.string.bd_count100, R.string.bd_count100_d, 100),
        BadgeDef("count1000", "📚", R.string.bd_count1000, R.string.bd_count1000_d, 1000),
        BadgeDef("budget", "🎯", R.string.bd_budget, R.string.bd_budget_d),
        BadgeDef("nospend", "🍃", R.string.bd_nospend, R.string.bd_nospend_d),
        BadgeDef("nospend5", "🌿", R.string.bd_nospend5, R.string.bd_nospend5_d, 5),
        BadgeDef("notes50", "✍️", R.string.bd_notes50, R.string.bd_notes50_d, 50),
        BadgeDef("filled10", "🧩", R.string.bd_filled10, R.string.bd_filled10_d, 10),
        BadgeDef("receipt", "📎", R.string.bd_receipt, R.string.bd_receipt_d),
        BadgeDef("night", "🦉", R.string.bd_night, R.string.bd_night_d),
        BadgeDef("early", "🐦", R.string.bd_early, R.string.bd_early_d),
        BadgeDef("custom_cat", "🎨", R.string.bd_custom_cat, R.string.bd_custom_cat_d),
        BadgeDef("widget", "📲", R.string.bd_widget, R.string.bd_widget_d),
        BadgeDef("wish", "🫙", R.string.bd_wish, R.string.bd_wish_d),
        BadgeDef("recap", "🎞️", R.string.bd_recap, R.string.bd_recap_d),
    )

    fun def(id: String) = ALL.firstOrNull { it.id == id }

    /** Current value per badge (blocking: run off the main thread). */
    fun progress(ctx: Context): List<BadgeProgress> {
        val prefs = Prefs(ctx)
        val unlocked = Repo.badges()
        val s = Repo.badgeStats()
        val active = Repo.activeDays()
        val today = LocalDate.now().toEpochDay()
        val best = maxOf(Streak.best(active), Streak.current(active, today)).toLong()
        val noSpend = Streak.noSpendDays(active, s.expenseDays, today)
        val noSpendBestMonth = noSpend.groupBy { YearMonth.from(LocalDate.ofEpochDay(it)) }.values.maxOfOrNull { it.size }?.toLong() ?: 0L
        val budgetKept = budgetMonthsKept(prefs.budgetCents, Repo.monthlyExpense(), active, YearMonth.now())
        val custom = (Repo.categories(TxType.EXPENSE) + Repo.categories(TxType.INCOME))
            .count { it !in Repo.DEFAULT_EXPENSE && it !in Repo.DEFAULT_INCOME }.toLong()
        val values = mapOf(
            "first" to s.entries,
            "streak3" to best, "streak7" to best, "streak30" to best, "streak100" to best,
            "count100" to s.entries, "count1000" to s.entries,
            "budget" to budgetKept,
            "nospend" to noSpend.size.toLong(), "nospend5" to noSpendBestMonth,
            "notes50" to s.withNote, "filled10" to s.filled, "receipt" to s.receipts,
            "night" to (if (s.hours.any { it in 0..4 }) 1L else 0L),
            "early" to (if (s.hours.any { it in 5..6 }) 1L else 0L),
            "custom_cat" to custom, "widget" to s.widget, "wish" to s.wishesDone,
            "recap" to prefs.recapsViewed.toLong(),
        )
        return ALL.map { BadgeProgress(it, values[it.id] ?: 0L, unlocked[it.id]) }
    }

    /** Unlocks every badge whose goal is reached; returns the new ones. */
    fun check(ctx: Context): List<String> {
        val fresh = progress(ctx).filter { !it.unlocked && it.current >= it.def.goal }.map { it.def.id }
        Repo.unlockBadges(fresh)
        return fresh
    }

    /** Completed months (before [now]) the user really used the app in (15+ check-in days) and stayed within the budget. */
    fun budgetMonthsKept(budget: Long, monthly: Map<String, Long>, active: Set<Long>, now: YearMonth): Long {
        if (budget <= 0) return 0
        val activeByMonth = active.groupingBy { YearMonth.from(LocalDate.ofEpochDay(it)) }.eachCount()
        return activeByMonth.count { (ym, n) -> ym.isBefore(now) && n >= 15 && (monthly[ym.toString()] ?: 0L) in 1..budget }.toLong()
    }
}

/** The feather's face. */
enum class Mood { HAPPY, CALM, WORRIED, SAD, SLEEPY }

object MoodCalc {
    /**
     * With a budget: compares what is spent with how far the month is. Without one: today against the usual day.
     * Late at night it just sleeps (unless the budget is blown).
     */
    fun of(hour: Int, budget: Long, monthSpent: Long, dayOfMonth: Int, monthLen: Int, todaySpent: Long, avgDaily: Long): Mood {
        if (budget > 0 && monthSpent >= budget) return Mood.SAD
        if (hour >= 23 || hour < 6) return Mood.SLEEPY
        if (budget > 0) {
            val ratio = monthSpent.toDouble() / budget
            val pace = dayOfMonth.toDouble() / monthLen
            return when {
                ratio >= 0.8 || ratio > pace + 0.15 -> Mood.WORRIED
                ratio < pace - 0.1 -> Mood.HAPPY
                else -> Mood.CALM
            }
        }
        return when {
            todaySpent == 0L -> Mood.HAPPY
            avgDaily > 0 && todaySpent > avgDaily * 2 -> Mood.WORRIED
            else -> Mood.CALM
        }
    }

    /** Mood for right now from the database (blocking). */
    fun now(ctx: Context): Mood {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val ym = YearMonth.now()
        val mStart = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val mEnd = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val dEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val last30 = Repo.sum(TxType.EXPENSE, dStart - 30L * 86_400_000, dStart)
        return of(
            java.time.LocalTime.now().hour, Prefs(ctx).budgetCents, Repo.sum(TxType.EXPENSE, mStart, mEnd),
            today.dayOfMonth, ym.lengthOfMonth(), Repo.sum(TxType.EXPENSE, dStart, dEnd), last30 / 30,
        )
    }
}

/** What the monthly recap cards show. */
data class RecapData(
    val month: YearMonth,
    val expense: Long,
    val income: Long,
    val prevExpense: Long,
    val count: Int,
    val dailyAvg: Long,
    val topDay: LocalDate?,
    val topDayAmount: Long,
    val topDayTx: Tx?,
    /** (category, cents, share 0..1), biggest first, up to 4. */
    val categories: List<Triple<String, Long, Float>>,
    val topNote: String?,
    val topNoteCount: Int,
    val biggest: Tx?,
    val noSpendDays: Int,
    val activeDays: Int,
    val lateNight: Int,
)

object RecapCalc {
    /** null when the month has too little to tell (fewer than 3 expenses). */
    fun compute(month: YearMonth, txs: List<Tx>, prevTxs: List<Tx>, active: Set<Long>, today: LocalDate = LocalDate.now()): RecapData? {
        val zone = ZoneId.systemDefault()
        fun day(t: Tx) = Instant.ofEpochMilli(t.time).atZone(zone).toLocalDate()
        val ex = txs.filter { it.type == TxType.EXPENSE && it.amountCents != null && YearMonth.from(day(it)) == month }
        if (ex.size < 3) return null
        val total = ex.sumOf { it.amountCents!! }
        val byDay = ex.groupBy { day(it) }.mapValues { (_, l) -> l.sumOf { it.amountCents!! } }
        val top = byDay.maxByOrNull { it.value }
        val lastDay = if (YearMonth.from(today) == month) today.dayOfMonth else month.lengthOfMonth()
        val cats = ex.groupBy { it.category.ifBlank { "其他" } }.map { (c, l) -> c to l.sumOf { it.amountCents!! } }
            .sortedByDescending { it.second }.take(4).map { (c, v) -> Triple(c, v, v.toFloat() / total) }
        val notes = ex.filter { it.note.isNotBlank() }.groupingBy { it.note.trim() }.eachCount().maxByOrNull { it.value }
        val days = (1..lastDay).map { month.atDay(it) }
        val activeIn = days.count { it.toEpochDay() in active }
        val noSpend = days.count { it.isBefore(today) && it.toEpochDay() in active && it !in byDay }
        return RecapData(
            month = month,
            expense = total,
            income = txs.filter { it.type == TxType.INCOME && YearMonth.from(day(it)) == month }.sumOf { it.amountCents ?: 0 },
            prevExpense = prevTxs.filter { it.type == TxType.EXPENSE }.sumOf { it.amountCents ?: 0 },
            count = txs.count { YearMonth.from(day(it)) == month },
            dailyAvg = total / lastDay.coerceAtLeast(1),
            topDay = top?.key,
            topDayAmount = top?.value ?: 0,
            topDayTx = top?.key?.let { d -> ex.filter { day(it) == d }.maxByOrNull { it.amountCents!! } },
            categories = cats,
            topNote = notes?.key,
            topNoteCount = notes?.value ?: 0,
            biggest = ex.maxByOrNull { it.amountCents!! },
            noSpendDays = noSpend,
            activeDays = activeIn,
            lateNight = ex.count { Instant.ofEpochMilli(it.time).atZone(zone).hour in 0..4 },
        )
    }
}
