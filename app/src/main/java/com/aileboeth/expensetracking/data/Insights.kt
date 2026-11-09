package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** A few plain sentences about one month's spending. Comparisons use the same days of last month. */
data class Insights(
    val total: Long,
    /** Last month, days 1..[days] (0 = nothing recorded then). */
    val prevSame: Long,
    /** Days counted: up to today for the current month, the whole month otherwise. */
    val days: Int,
    val dailyAvg: Long,
    /** Category whose spending grew most vs the same days last month: (category, now, then). */
    val topGrowth: Triple<String, Long, Long>?,
    val biggest: Tx?,
    /** Note written most often (at least twice): (note, times, sum). */
    val topNote: Triple<String, Int, Long>?,
)

object InsightCalc {
    fun compute(
        txs: List<Tx>,
        prevTxs: List<Tx>,
        month: YearMonth,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Insights? {
        fun day(t: Tx) = Instant.ofEpochMilli(t.time).atZone(zone).toLocalDate()
        val cur = txs.filter { it.type == TxType.EXPENSE && it.status == Status.OK && it.amountCents != null }
        if (cur.isEmpty()) return null
        val days = when {
            month == YearMonth.from(today) -> today.dayOfMonth
            month.isAfter(YearMonth.from(today)) -> 0
            else -> month.lengthOfMonth()
        }.coerceAtLeast(1)
        val prev = prevTxs.filter { it.type == TxType.EXPENSE && it.status == Status.OK && it.amountCents != null && day(it).dayOfMonth <= days }
        val total = cur.sumOf { it.amountCents!! }
        val prevSame = prev.sumOf { it.amountCents!! }

        val curBy = cur.groupBy { it.category.ifBlank { "其他" } }.mapValues { (_, l) -> l.sumOf { it.amountCents!! } }
        val prevBy = prev.groupBy { it.category.ifBlank { "其他" } }.mapValues { (_, l) -> l.sumOf { it.amountCents!! } }
        val growth = curBy.map { (c, v) -> Triple(c, v, prevBy[c] ?: 0L) }
            .filter { it.second > it.third }
            .maxByOrNull { it.second - it.third }
            // Only worth saying when there is something to compare with.
            ?.takeIf { prevSame > 0 }

        val topNote = cur.filter { it.note.isNotBlank() }
            .groupBy { it.note.trim() }
            .map { (n, l) -> Triple(n, l.size, l.sumOf { it.amountCents!! }) }
            .filter { it.second >= 2 }
            .sortedWith(compareByDescending<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third })
            .firstOrNull()

        return Insights(
            total = total, prevSame = prevSame, days = days, dailyAvg = total / days,
            topGrowth = growth, biggest = cur.maxByOrNull { it.amountCents!! }, topNote = topNote,
        )
    }

    /** Per-note totals inside one category: (note or "", times, sum), biggest first. */
    fun byNote(txs: List<Tx>): List<Triple<String, Int, Long>> =
        txs.groupBy { it.note.trim() }
            .map { (n, l) -> Triple(n, l.size, l.sumOf { it.amountCents ?: 0 }) }
            .sortedByDescending { it.third }
}
