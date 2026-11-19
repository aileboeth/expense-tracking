package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CyclesTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
    private fun ym(m: Int, y: Int = 2026) = YearMonth.of(y, m)
    private fun pays(vararg ds: LocalDate) = ds.map { PayIn(it, 150000) }

    @Test fun withoutAPayDayPeriodsAreCalendarMonths() {
        val c = PayCalendar()
        assertTrue(c.natural)
        assertEquals(ym(11), c.monthOf(d(11, 1)))
        assertEquals(ym(11), c.monthOf(d(11, 30)))
        assertEquals(30, c.days(ym(11)))
        assertEquals(18, c.passed(ym(11), d(11, 18)))
        assertEquals(d(11, 30), c.lastDay(ym(10).plusMonths(1), d(12, 5)))
    }

    @Test fun payDayFifteenRunsToTheFourteenth() {
        val c = PayCalendar(15)
        assertFalse(c.natural)
        assertEquals(d(11, 15), c.start(ym(11)))
        assertEquals(d(12, 15), c.end(ym(11)))
        assertEquals(ym(10), c.monthOf(d(11, 14)))
        assertEquals(ym(11), c.monthOf(d(11, 15)))
        assertEquals(ym(11), c.monthOf(d(12, 14)))
        assertEquals(31, c.days(ym(10)))   // Oct 15 .. Nov 14
        assertEquals(4, c.passed(ym(11), d(11, 18)))
        assertEquals(0, c.passed(ym(12), d(11, 18)))
        assertEquals(ym(11), c.current(d(11, 18)))
        assertEquals(ym(10), c.current(d(11, 10)))
        assertEquals(d(11, 17), c.date(ym(11), 2))
    }

    @Test fun shortMonthsStartOnTheirLastDay() {
        val c = PayCalendar(31)
        assertEquals(d(2, 28), c.start(ym(2)))
        assertEquals(28, c.days(ym(1)))    // Jan 31 .. Feb 27
        assertEquals(d(4, 30), c.start(ym(4)))
        assertEquals(ym(2), c.monthOf(d(3, 30)))
        assertEquals(d(2, 29, 2028), PayCalendar(30).start(ym(2, 2028)))
    }

    @Test fun earlyPayOpensThePeriodThatDay() {
        val c = PayCalendar(15, pays(d(11, 13)))
        assertEquals(d(11, 13), c.start(ym(11)))
        assertEquals(ym(10), c.monthOf(d(11, 12)))
        assertEquals(ym(11), c.monthOf(d(11, 13)))
        assertEquals(29, c.days(ym(10)))   // Oct 15 .. Nov 12
        // later months still use the set day
        assertEquals(d(12, 15), c.start(ym(12)))
    }

    @Test fun latePayKeepsThePreviousPeriodGoing() {
        val c = PayCalendar(15, pays(d(11, 18)))
        assertEquals(ym(10), c.monthOf(d(11, 16)))
        assertEquals(ym(11), c.monthOf(d(11, 18)))
    }

    @Test fun payFarFromThePayDayIsIgnored() {
        val c = PayCalendar(15, pays(d(11, 1), d(11, 30)))
        assertEquals(d(11, 15), c.start(ym(11)))
        assertEquals(d(12, 15), c.start(ym(12)))
    }

    @Test fun payDayOneWithPayAtTheEndOfTheMonth() {
        val c = PayCalendar(1, pays(d(10, 28)))
        assertFalse(c.natural)
        assertEquals(d(10, 28), c.start(ym(11)))
        assertEquals(ym(11), c.monthOf(d(10, 29)))
        assertEquals(ym(10), c.monthOf(d(10, 27)))
    }

    @Test fun theBiggestPayInTheWindowCounts() {
        val c = PayCalendar(15, listOf(PayIn(d(11, 12), 50000, 1), PayIn(d(11, 20), 150000, 2), PayIn(d(10, 15), 150000, 3)))
        assertEquals(d(11, 20), c.start(ym(11)))
        assertEquals(2L, c.openedBy(ym(11))?.id)
        assertEquals(null, c.openedBy(ym(10)))   // paid on the day itself: nothing to explain
    }

    @Test fun aSmallIncomeFiledAsPayDoesNotMoveTheMonth() {
        // pay day 20; a 300 red packet filed under 工资 on the 15th, the real allowance is 1500
        val c = PayCalendar(20, listOf(PayIn(d(10, 20), 150000, 1), PayIn(d(11, 15), 30000, 2), PayIn(d(12, 19), 150000, 3)))
        assertEquals(d(11, 20), c.start(ym(11)))
        assertEquals(null, c.openedBy(ym(11)))
        assertEquals(d(12, 19), c.start(ym(12)))
    }

    @Test fun aLoneIncomeStillCountsAndSaysWhy() {
        val c = PayCalendar(20, listOf(PayIn(d(11, 15), 30000, 7, "工资")))
        assertEquals(d(11, 15), c.start(ym(11)))
        assertEquals(PayIn(d(11, 15), 30000, 7, "工资"), c.openedBy(ym(11)))
        // once the user says it is not pay (it is left out), the month is back on the 20th
        assertEquals(d(11, 20), PayCalendar(20, emptyList()).start(ym(11)))
    }

    @Test fun forecastFollowsThePeriod() {
        val c = PayCalendar(15)
        fun tx(date: LocalDate, cents: Long) = Tx(type = TxType.EXPENSE, amountCents = cents, category = "餐饮",
            time = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())
        val txs = listOf(tx(d(11, 10), 99999), tx(d(11, 15), 1000), tx(d(11, 16), 1000), tx(d(12, 1), 1000))
        val f = Analysis.forecast(txs, emptyList(), ym(11), budget = 0, today = d(12, 1), zone = zone, cal = c)
        assertEquals(3000, f.spent)        // Nov 10 belongs to October's period
        assertEquals(30, f.daysInMonth)    // Nov 15 .. Dec 14
        assertEquals(17, f.daysPassed)     // Nov 15 .. Dec 1
        assertEquals(1000L, f.cumulative[0])
        assertEquals(3000L, f.cumulative[16])
    }

    @Test fun recapCountsThePeriodDays() {
        val c = PayCalendar(15)
        fun tx(date: LocalDate, cents: Long) = Tx(type = TxType.EXPENSE, amountCents = cents, category = "餐饮",
            time = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val txs = listOf(tx(d(10, 20), 1000), tx(d(11, 1), 2000), tx(d(11, 14), 3000), tx(d(11, 15), 5000))
        val r = RecapCalc.compute(ym(10), txs, emptyList(), emptySet(), today = d(11, 20), cal = c)!!
        assertEquals(6000, r.expense)
        assertEquals(6000L / 31, r.dailyAvg)
    }
}
