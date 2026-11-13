package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.ScreenParser.Seen

/**
 * What to do with a payment read off the screen, given the entries already in the ledger.
 * Pure (no database) so the rules can be tested; [com.aileboeth.expensetracking.capture.ScreenLink]
 * runs it against the real ledger.
 *
 * The notification and the screen usually both see a phone payment. The rule that keeps them from
 * counting twice: each screen sighting pairs with at most one notification entry and the other way
 * round, and only within a few minutes. Two real ¥30 payments a minute apart give two screens and
 * two notifications, so they still pair up one to one.
 */
object ScreenMatch {
    /** How far apart a notification and its screen may be. */
    const val PAIR_WINDOW = 5 * 60_000L
    /** How old an entry waiting for its amount may be and still be filled from a screen. */
    const val FILL_WINDOW = 48 * 3600_000L
    /** A chat card's time label must be this close to the entry it fills. */
    const val CARD_SLACK = 10 * 60_000L

    sealed interface Action {
        /** The entry already exists (from the notification); maybe add the shop name the screen shows. */
        data class Confirm(val txId: Long, val counterpart: String) : Action
        /** An entry without an amount gets this one. */
        data class Fill(val txId: Long, val cents: Long, val counterpart: String) : Action
        /** Nothing matches and the page is fresh: record it. */
        data object Create : Action
        data object Skip : Action
    }

    fun isScreenKind(kind: String) = kind.startsWith("screen_")

    /**
     * [recent] = entries of the last hours, any status. [claimed] = entries some earlier screen
     * sighting already accounts for.
     */
    fun plan(seen: Seen, recent: List<Tx>, claimed: Set<Long>, now: Long): Action {
        val at = seen.time ?: now
        // 1. the notification already recorded it
        recent.filter {
            it.id !in claimed && !isScreenKind(it.kind) && it.type == seen.type && it.channel == seen.channel &&
                it.amountCents == seen.amountCents && kotlin.math.abs(it.time - at) <= PAIR_WINDOW
        }.minByOrNull { kotlin.math.abs(it.time - at) }?.let { return Action.Confirm(it.id, seen.counterpart) }

        // 2. an entry waiting for its amount (watch pay, "X给你转了1笔钱")
        val waiting = recent.filter {
            it.id !in claimed && it.amountCents == null && it.type == seen.type && it.channel == seen.channel &&
                at - it.time in -PAIR_WINDOW..FILL_WINDOW
        }
        val byName = if (seen.counterpart.isNotBlank()) waiting.filter { it.counterpart.isNotBlank() && (seen.counterpart.contains(it.counterpart) || it.counterpart.contains(seen.counterpart)) } else emptyList()
        val pick = byName.minByOrNull { kotlin.math.abs(it.time - at) }
            // Without a name: a history page only fills when its time is shown and close; a fresh page takes the newest.
            ?: if (seen.fresh) waiting.filter { at - it.time <= PAIR_WINDOW }.maxByOrNull { it.time }
            else if (seen.time != null) waiting.filter { kotlin.math.abs(it.time - seen.time) <= CARD_SLACK }.minByOrNull { kotlin.math.abs(it.time - seen.time) }
            else null
        if (pick != null) return Action.Fill(pick.id, seen.amountCents, seen.counterpart)

        return if (seen.fresh) Action.Create else Action.Skip
    }

    /**
     * Cards in the 微信支付 chat (oldest first, as on screen) against watch-pay entries waiting for their
     * amount. With time labels: each entry takes the closest card within [CARD_SLACK]. Without: only when
     * the waiting entries are the newest vouchers of all, the last cards go to them in order.
     */
    fun planCards(cards: List<Seen>, waiting: List<Tx>, newerVoucherDone: Boolean): List<Action.Fill> {
        val vouchers = cards.filter { it.kind == "screen_voucher" }.ifEmpty { cards }
        val todo = waiting.filter { it.amountCents == null }.sortedBy { it.time }
        if (vouchers.isEmpty() || todo.isEmpty()) return emptyList()
        val out = mutableListOf<Action.Fill>()
        if (vouchers.all { it.time != null }) {
            val used = HashSet<Int>()
            for (tx in todo) {
                val best = vouchers.withIndex()
                    .filter { (i, c) -> i !in used && kotlin.math.abs(c.time!! - tx.time) <= CARD_SLACK }
                    .minByOrNull { (_, c) -> kotlin.math.abs(c.time!! - tx.time) } ?: continue
                used += best.index
                out += Action.Fill(tx.id, best.value.amountCents, best.value.counterpart)
            }
            return out
        }
        if (newerVoucherDone || todo.size > vouchers.size) return emptyList()
        vouchers.takeLast(todo.size).zip(todo).forEach { (c, tx) -> out += Action.Fill(tx.id, c.amountCents, c.counterpart) }
        return out
    }
}
