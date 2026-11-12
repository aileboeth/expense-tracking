package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class AnalysisTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    private fun tx(date: LocalDate, cents: Long, cat: String = "餐饮", note: String = "", hour: Int = 12, type: TxType = TxType.EXPENSE) =
        Tx(type = type, amountCents = cents, category = cat, note = note, time = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli())

    @Test fun forecastExtrapolatesThePaceWithoutHistory() {
        val m = YearMonth.of(2026, 11)
        val today = m.atDay(10)
        val txs = (1..10).map { tx(m.atDay(it), 3000) }
        val f = Analysis.forecast(txs, emptyList(), m, budget = 0, today = today, zone = zone)
        assertEquals(30000, f.spent)
        assertEquals(30 * 3000L, f.projected)
        assertEquals(3000, f.rate)
        assertNull(f.safeDaily)
        assertEquals(30000L, f.cumulative[9])
        assertNull(f.cumulative[10])
    }

    @Test fun oneOffBigExpenseIsNotExtrapolated() {
        val m = YearMonth.of(2026, 11)
        val today = m.atDay(10)
        val txs = (1..10).map { tx(m.atDay(it), 2000) } + tx(m.atDay(3), 200000, "购物")
        val f = Analysis.forecast(txs, emptyList(), m, budget = 0, today = today, zone = zone)
        assertEquals(200000, f.oneOffs)
        // 2000 a day for 30 days plus the one-off once
        assertEquals(60000L + 200000L, f.projected)
    }

    @Test fun budgetRunOutDayAndSafeDaily() {
        val m = YearMonth.of(2026, 11)
        val today = m.atDay(10)
        val txs = (1..10).map { tx(m.atDay(it), 5000) }
        val f = Analysis.forecast(txs, emptyList(), m, budget = 100000, today = today, zone = zone)
        // 50 000 left over 20 days
        assertEquals(2500L, f.safeDaily)
        // 5 000 a day: the remaining 50 000 last 10 more days
        assertEquals(20, f.runOutDay)
    }

    @Test fun historySteadiesAnEarlyMonth() {
        val m = YearMonth.of(2026, 11)
        val today = m.atDay(2)
        val txs = listOf(tx(m.atDay(1), 10000), tx(m.atDay(2), 10000))
        val prev = (1..31).map { tx(LocalDate.of(2026, 10, it), 1000) }
        val f = Analysis.forecast(txs, prev, m, budget = 0, today = today, zone = zone)
        // without history it would be 30 * 10 000; with it the pace is pulled towards 1 000 a day
        assertTrue(f.projected < 150000)
        assertTrue(f.projected > 20000)
    }

    @Test fun pastMonthIsComplete() {
        val m = YearMonth.of(2026, 9)
        val f = Analysis.forecast(listOf(tx(m.atDay(5), 1234)), emptyList(), m, budget = 1000, today = LocalDate.of(2026, 11, 11), zone = zone)
        assertEquals(30, f.daysPassed)
        assertEquals(1234, f.projected)
        assertEquals(5, f.runOutDay)
    }

    @Test fun habitsWeekdaysHoursBucketsAndNoSpendRuns() {
        val from = LocalDate.of(2026, 11, 2) // a Monday
        val to = from.plusDays(13)
        val txs = listOf(
            tx(from, 500, hour = 8),                 // Mon, < 10
            tx(from.plusDays(4), 4000, hour = 20),   // Fri
            tx(from.plusDays(11), 6000, hour = 20),  // Fri next week
            tx(from.plusDays(13), 25000, hour = 23), // Sun
        )
        val h = Analysis.habits(txs, from, to, zone)
        assertEquals(14, h.days)
        assertEquals(4, h.count)
        assertEquals(5000, h.weekdayAvg[4]) // two Fridays: (4000 + 6000) / 2
        assertEquals(2, h.hourCount[20])
        assertEquals(1, h.bucketCount[0])
        assertEquals(1, h.bucketCount[5]) // 200–500
        assertEquals(10, h.noSpendDays)
        assertEquals(6, h.longestNoSpend) // Nov 7..12
    }

    @Test fun categoryTrendsPaceTheRunningMonthAndFindAnomalies() {
        val m = YearMonth.of(2026, 11)
        val today = m.atDay(15)
        val txs = buildList {
            for (k in 1..5) {
                val pm = m.minusMonths(k.toLong())
                add(tx(pm.atDay(10), 30000, "餐饮"))
                add(tx(pm.atDay(10), 20000, "购物"))
            }
            add(tx(m.atDay(5), 30000, "餐饮")) // half a month: paced to 60 000 = 2x
            add(tx(m.atDay(5), 2000, "购物"))  // paced 4 000 vs 20 000
        }
        val t = Analysis.categoryTrends(txs, m, today = today, zone = zone)
        val food = t.first { it.category == "餐饮" }
        assertEquals(30000, food.usual)
        assertEquals(60000, food.paced)
        val odd = Analysis.anomalies(t)
        assertEquals(listOf("餐饮", "购物"), odd.map { it.category })
    }

    @Test fun yearViewAndProjections() {
        val txs = listOf(
            tx(LocalDate.of(2026, 1, 1), 1000, note = "奶茶"),
            tx(LocalDate.of(2026, 1, 2), 1500, note = "奶茶"),
            tx(LocalDate.of(2026, 1, 3), 1500, note = "奶茶"),
            tx(LocalDate.of(2026, 3, 1), 50000, type = TxType.INCOME),
            tx(LocalDate.of(2025, 12, 31), 9999),
        )
        val y = Analysis.year(txs, 2026, zone)
        assertEquals(4000, y.expense)
        assertEquals(50000, y.income)
        assertEquals(1000, y.daily[0])
        assertEquals(4, y.activeDays)
        val notes = Analysis.noteHabits(txs, 90)
        assertEquals("奶茶", notes.single().note)
        assertEquals(4000L * 365 / 90, notes.single().perYear)
        assertEquals(3, Analysis.smallSpend(txs, 90).count)
        assertEquals(3, Analysis.monthsToGoal(25000, 10000))
        assertNull(Analysis.monthsToGoal(100, 0))
    }
}
