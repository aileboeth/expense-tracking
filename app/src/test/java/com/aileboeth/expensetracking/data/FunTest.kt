package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FunTest {
    private val today = LocalDate.of(2026, 11, 11).toEpochDay()

    @Test fun streakCountsBackFromToday() {
        assertEquals(3, Streak.current(setOf(today, today - 1, today - 2, today - 4), today))
    }

    @Test fun streakSurvivesUntilTodayIsCheckedIn() {
        // yesterday and the day before: still a 2-day streak this morning
        assertEquals(2, Streak.current(setOf(today - 1, today - 2), today))
        assertEquals(0, Streak.current(setOf(today - 2, today - 3), today))
    }

    @Test fun bestStreak() {
        assertEquals(4, Streak.best(setOf(1, 2, 3, 4, 10, 11, 20)))
        assertEquals(0, Streak.best(emptySet()))
    }

    @Test fun noSpendDaysNeedACheckIn() {
        val active = setOf(today - 3, today - 2, today - 1, today)
        val spent = setOf(today - 3, today - 1)
        // today is not over yet, so it does not count
        assertEquals(listOf(today - 2), Streak.noSpendDays(active, spent, today))
    }

    @Test fun moodFollowsBudgetPace() {
        // day 10 of 30, budget 3000: 1000 is on pace
        assertEquals(Mood.CALM, MoodCalc.of(12, 300000, 100000, 10, 30, 0, 0))
        assertEquals(Mood.HAPPY, MoodCalc.of(12, 300000, 30000, 10, 30, 0, 0))
        assertEquals(Mood.WORRIED, MoodCalc.of(12, 300000, 200000, 10, 30, 0, 0))
        assertEquals(Mood.SAD, MoodCalc.of(12, 300000, 300000, 10, 30, 0, 0))
        // over budget beats sleeping
        assertEquals(Mood.SAD, MoodCalc.of(2, 300000, 310000, 10, 30, 0, 0))
        assertEquals(Mood.SLEEPY, MoodCalc.of(23, 300000, 1000, 10, 30, 0, 0))
    }

    @Test fun moodWithoutBudgetLooksAtToday() {
        assertEquals(Mood.HAPPY, MoodCalc.of(12, 0, 50000, 10, 30, 0, 3000))
        assertEquals(Mood.WORRIED, MoodCalc.of(12, 0, 50000, 10, 30, 9000, 3000))
        assertEquals(Mood.CALM, MoodCalc.of(12, 0, 50000, 10, 30, 3000, 3000))
    }

    @Test fun budgetMonthNeedsRealUse() {
        val oct = YearMonth.of(2026, 10)
        val days15 = (1..15).map { oct.atDay(it).toEpochDay() }.toSet()
        val days5 = (1..5).map { oct.atDay(it).toEpochDay() }.toSet()
        val now = YearMonth.of(2026, 11)
        assertEquals(1, Achievements.budgetMonthsKept(200000, mapOf("2026-10" to 150000L), days15, now))
        assertEquals(0, Achievements.budgetMonthsKept(200000, mapOf("2026-10" to 250000L), days15, now))
        assertEquals(0, Achievements.budgetMonthsKept(200000, mapOf("2026-10" to 150000L), days5, now))
        assertEquals(0, Achievements.budgetMonthsKept(0, mapOf("2026-10" to 150000L), days15, now))
    }

    private fun tx(day: LocalDate, hour: Int, cents: Long, cat: String, note: String = "", type: TxType = TxType.EXPENSE) =
        Tx(type = type, amountCents = cents, category = cat, note = note,
            time = day.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    @Test fun recap() {
        val oct = YearMonth.of(2026, 10)
        val d = { n: Int -> oct.atDay(n) }
        val txs = listOf(
            tx(d(1), 12, 2000, "餐饮", "午饭"), tx(d(1), 19, 3000, "餐饮", "晚饭"),
            tx(d(2), 12, 2000, "餐饮", "午饭"), tx(d(5), 2, 19800, "购物", "耳机"),
            tx(d(6), 12, 1600, "零食饮料", "奶茶"), tx(d(7), 12, 1600, "零食饮料", "奶茶"),
            tx(d(8), 12, 1600, "零食饮料", "奶茶"), tx(d(3), 10, 400000, "工资", type = TxType.INCOME),
        )
        val prev = listOf(tx(d(1).minusMonths(1), 12, 50000, "购物"))
        val active = (1..10).map { d(it).toEpochDay() }.toSet()
        val r = RecapCalc.compute(oct, txs, prev, active, today = LocalDate.of(2026, 11, 11))
        assertNotNull(r); r!!
        assertEquals(31600L, r.expense)
        assertEquals(400000L, r.income)
        assertEquals(50000L, r.prevExpense)
        assertEquals(d(5), r.topDay)
        assertEquals(19800L, r.topDayAmount)
        assertEquals("购物", r.categories.first().first)
        assertEquals("奶茶", r.topNote)
        assertEquals(3, r.topNoteCount)
        // active days 3, 4, 9, 10 had no expense
        assertEquals(4, r.noSpendDays)
        assertEquals(1, r.lateNight)
        assertEquals(31600L / 31, r.dailyAvg)
    }

    @Test fun recapNeedsAFewEntries() {
        val oct = YearMonth.of(2026, 10)
        assertNull(RecapCalc.compute(oct, listOf(tx(oct.atDay(1), 12, 100, "餐饮")), emptyList(), emptySet()))
    }
}
