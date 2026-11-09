package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class InsightsTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun ex(d: LocalDate, cents: Long, cat: String, note: String = "") =
        Tx(type = TxType.EXPENSE, amountCents = cents, category = cat, note = note, time = at(d))

    private val nov = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 10)

    @Test fun comparesSameDaysOfLastMonth() {
        val cur = listOf(ex(today, 3000, "餐饮", "奶茶"), ex(today.minusDays(1), 2000, "餐饮", "奶茶"), ex(today.minusDays(2), 19800, "购物", "耳机"))
        val prev = listOf(
            ex(LocalDate.of(2026, 10, 5), 1000, "餐饮"),
            ex(LocalDate.of(2026, 10, 9), 4000, "购物"),
            ex(LocalDate.of(2026, 10, 25), 99900, "购物"), // after day 10: not compared
        )
        val i = InsightCalc.compute(cur, prev, nov, today, zone)!!
        assertEquals(24800L, i.total)
        assertEquals(5000L, i.prevSame)
        assertEquals(10, i.days)
        assertEquals(2480L, i.dailyAvg)
        assertEquals(Triple("购物", 19800L, 4000L), i.topGrowth)
        assertEquals(19800L, i.biggest!!.amountCents)
        assertEquals(Triple("奶茶", 2, 5000L), i.topNote)
    }

    @Test fun noComparisonWithoutLastMonth() {
        val i = InsightCalc.compute(listOf(ex(today, 3000, "餐饮", "午饭")), emptyList(), nov, today, zone)!!
        assertNull(i.topGrowth)
        assertNull(i.topNote) // written only once
    }

    @Test fun pastMonthCountsAllDays() {
        val oct = YearMonth.of(2026, 10)
        val i = InsightCalc.compute(listOf(ex(LocalDate.of(2026, 10, 3), 3100, "餐饮")), emptyList(), oct, today, zone)!!
        assertEquals(31, i.days)
        assertEquals(100L, i.dailyAvg)
    }

    @Test fun emptyMonthHasNoInsights() {
        assertNull(InsightCalc.compute(emptyList(), emptyList(), nov, today, zone))
    }

    @Test fun builtInWordsPreferSpecificGroups() {
        assertEquals("零食饮料", Categorizer.builtIn(TxType.EXPENSE, "奶茶"))
        assertEquals("餐饮", Categorizer.builtIn(TxType.EXPENSE, "午饭"))
        assertEquals("交通", Categorizer.builtIn(TxType.EXPENSE, "打车回宿舍"))
        assertEquals("日用", Categorizer.builtIn(TxType.EXPENSE, "牙膏"))
        assertEquals("餐饮", Categorizer.builtIn(TxType.EXPENSE, "饭卡充值"))
        assertEquals("通讯", Categorizer.builtIn(TxType.EXPENSE, "话费充值"))
        assertEquals("生活缴费", Categorizer.builtIn(TxType.EXPENSE, "宿舍电费"))
        assertEquals("兼职", Categorizer.builtIn(TxType.INCOME, "家教"))
        assertEquals("零食饮料", Categorizer.builtIn(TxType.EXPENSE, "Starbucks coffee"))
        assertEquals("餐饮", Categorizer.builtIn(TxType.EXPENSE, "Steak dinner"))
        assertEquals("交通", Categorizer.builtIn(TxType.EXPENSE, "Uber to work"))
        assertEquals("娱乐", Categorizer.builtIn(TxType.EXPENSE, "Steam game"))
        assertEquals("工资", Categorizer.builtIn(TxType.INCOME, "Payroll October"))
        assertEquals(listOf("零食饮料"), Categorizer.examples(TxType.EXPENSE, chinese = false).map { it.first }.take(1))
        assertEquals("", Categorizer.builtIn(TxType.EXPENSE, "随便写的"))
    }

    @Test fun userRulesLongestKeywordWins() {
        val rules = listOf(
            Rule(type = TxType.EXPENSE, keyword = "咖啡", category = "零食饮料"),
            Rule(type = TxType.EXPENSE, keyword = "咖啡豆", category = "日用"),
            Rule(type = TxType.INCOME, keyword = "咖啡豆", category = "兼职"),
        )
        assertEquals("日用", Categorizer.matchRules(rules, TxType.EXPENSE, "买咖啡豆"))
        assertEquals("零食饮料", Categorizer.matchRules(rules, TxType.EXPENSE, "", "某咖啡店"))
        assertNull(Categorizer.matchRules(rules, TxType.EXPENSE, "奶茶"))
    }
}
