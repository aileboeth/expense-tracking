package com.aileboeth.expensetracking.capture

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.Categorizer
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.ScreenMatch
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.ScreenParser
import java.util.concurrent.Executors

/**
 * Puts what the accessibility service read off the screen into the ledger: confirms or completes
 * entries the notifications made, fills watch-pay amounts, and records payments no notification
 * told us about. Runs on the same single worker as [Ingest] so the two never race.
 */
object ScreenLink {
    private val main = Handler(Looper.getMainLooper())

    /** Entries a screen sighting already accounts for (so a second sighting does not pair with them). */
    private val claimed = LinkedHashMap<Long, Long>()
    /** Screen-made entries a notification already paired with. */
    private val noticed = LinkedHashMap<Long, Long>()

    /**
     * Sightings of the last 10 minutes. A page fires many change events while it stays open (and pop-ups
     * re-fire it), so the same payment on screen counts once. The price: a second identical payment at
     * the same shop within 10 minutes is only recorded if its notification comes.
     */
    private val seenSigs = LinkedHashMap<String, Long>()

    /** What the full-auto page shows: the last few things read. */
    data class Event(val time: Long, val text: String)
    private val events = ArrayDeque<Event>()
    fun events(): List<Event> = synchronized(events) { events.toList().asReversed() }
    @Volatile var lastWindow: Pair<Long, String>? = null
    /** What the last WeChat / Alipay read found, for "timed out" messages: lines, money-looking lines, payments understood. */
    @Volatile var lastScan = ""

    fun note(text: String) = synchronized(events) {
        events.addLast(Event(System.currentTimeMillis(), text))
        while (events.size > 30) events.removeFirst()
    }

    private val worker get() = Ingest.worker

    fun onScreen(ctx: Context, pkg: String, lines: List<String>) {
        val app = ctx.applicationContext
        worker.execute {
            runCatching { handle(app, pkg, lines) }.onFailure { note("出错：$it") }
        }
    }

    private fun trim(now: Long) {
        claimed.entries.removeAll { now - it.value > 6 * 3600_000L }
        noticed.entries.removeAll { now - it.value > 6 * 3600_000L }
        seenSigs.entries.removeAll { now - it.value > 10 * 60_000L }
    }

    private fun handle(ctx: Context, pkg: String, lines: List<String>) {
        Repo.init(ctx)
        val now = System.currentTimeMillis()
        trim(now)
        // While paying, the service already keeps every read (see ScreenService.inPayContext).
        if (Prefs(ctx).screenSamples && !ScreenService.inPayContext() && ScreenParser.looksLikeMoney(lines)) ScreenSamples.add(ctx, pkg, lines, now)
        val seen = ScreenParser.read(pkg, lines, now)
        val moneyLines = lines.count { ScreenParser.looksLikeMoney(listOf(it)) }
        lastScan = "${if (pkg == com.aileboeth.expensetracking.parse.Parser.ALIPAY) "支付宝" else "微信"}画面 ${lines.size} 行，像金额的 $moneyLines 行，认出 ${seen.size} 笔" +
            (if (seen.isNotEmpty()) "（${seen.joinToString("、") { it.kind.removePrefix("screen_") + " " + it.amountCents }}）" else "")
        if (seen.isEmpty()) return
        val recent = Repo.listAll(now - ScreenMatch.FILL_WINDOW - 3600_000L, now + 60_000L)

        // The 微信支付 chat: cards for the watch payments still waiting for their amount.
        if (seen.any { it.kind == "screen_voucher" || it.kind == "screen_wechat_card" }) {
            val waiting = recent.filter { it.status == Status.PENDING && it.amountCents == null && it.kind == "wechat_voucher" && it.id !in claimed }
            if (waiting.isEmpty()) return
            val newestWaiting = waiting.maxOf { it.time }
            val newerDone = recent.any { it.kind == "wechat_voucher" && it.amountCents != null && it.time > newestWaiting }
            val fills = ScreenMatch.planCards(seen, waiting, newerDone)
            for (f in fills) fill(ctx, f.txId, f.cents, f.counterpart, "微信支付聊天卡片")
            if (fills.isEmpty()) note("看到微信支付卡片 ${seen.size} 张，对不上待补的 ${waiting.size} 笔")
            return
        }

        for (s in seen) {
            val sig = "${s.type}|${s.amountCents}|${s.counterpart}|${s.kind}"
            if (seenSigs.containsKey(sig)) continue
            seenSigs[sig] = now
            when (val a = ScreenMatch.plan(s, recent, claimed.keys, now)) {
                is ScreenMatch.Action.Confirm -> {
                    claimed[a.txId] = now
                    val tx = Repo.get(a.txId) ?: continue
                    if (tx.counterpart.isBlank() && a.counterpart.isNotBlank()) {
                        val cat = if (tx.category.isBlank() || tx.category == Bubble.OTHER) Categorizer.auto(tx.type, a.counterpart, tx.kind).ifBlank { tx.category } else tx.category
                        Repo.update(tx.copy(counterpart = a.counterpart, category = cat))
                        main.post { Bubble.refresh() }
                    }
                    note("屏幕和通知是同一笔 #${a.txId}" + if (a.counterpart.isNotBlank()) "（${a.counterpart}）" else "")
                }
                is ScreenMatch.Action.Fill -> fill(ctx, a.txId, a.cents, a.counterpart, s.summary)
                ScreenMatch.Action.Create -> {
                    val prefs = Prefs(ctx)
                    if (prefs.blacklistHit(s.counterpart, s.summary) != null) { note("黑名单，不记：${s.summary}"); continue }
                    val id = Repo.insert(
                        Tx(
                            type = s.type, amountCents = s.amountCents,
                            category = Categorizer.auto(s.type, s.counterpart, s.kind).ifBlank { Bubble.OTHER },
                            channel = s.channel, counterpart = s.counterpart, time = now,
                            status = Status.OK, source = "auto", kind = s.kind, raw = s.summary,
                        ),
                    )
                    claimed[id] = now
                    Repo.log(pkg, "屏幕", s.summary, "已记账 #$id（从屏幕读到，通知没来）")
                    note("从屏幕记了一笔 #$id：${s.summary}")
                    main.post { Bubble.show(ctx, id) }
                }
                ScreenMatch.Action.Skip -> note("看到 ${s.summary}，当成以前的账，不记" + if (s.why.isNotEmpty()) "（${s.why}）" else "")
            }
        }
    }

    /** What [WxPayFlow] made of WeChat's events: a red packet / transfer you sent, or a red packet you opened. */
    fun onWx(ctx: Context, out: WxPayFlow.Out) {
        val app = ctx.applicationContext
        worker.execute { runCatching { recordWx(app, out) }.onFailure { note("出错：$it") } }
    }

    private fun recordWx(ctx: Context, out: WxPayFlow.Out) {
        Repo.init(ctx)
        val now = System.currentTimeMillis()
        val wx = com.aileboeth.expensetracking.parse.Parser.WECHAT
        val tx = when (out) {
            is WxPayFlow.Out.Sent -> {
                val what = if (out.redPacket) "发红包" else "转账"
                Tx(
                    type = com.aileboeth.expensetracking.parse.TxType.EXPENSE, amountCents = out.cents,
                    category = Categorizer.builtIn(com.aileboeth.expensetracking.parse.TxType.EXPENSE, if (out.redPacket) "红包" else "").ifBlank { Bubble.OTHER },
                    channel = com.aileboeth.expensetracking.parse.Parser.CH_WECHAT, counterpart = "", time = now,
                    status = Status.OK, source = "auto",
                    kind = if (out.redPacket) "screen_wx_redpacket_out" else "screen_wx_transfer_sent",
                    raw = "$what ${com.aileboeth.expensetracking.data.Money.money(out.cents)}（按输入的金额）",
                )
            }
            WxPayFlow.Out.Opened -> Tx(
                type = com.aileboeth.expensetracking.parse.TxType.INCOME, amountCents = null,
                category = Categorizer.builtIn(com.aileboeth.expensetracking.parse.TxType.INCOME, "红包"),
                channel = com.aileboeth.expensetracking.parse.Parser.CH_WECHAT, counterpart = "", time = now,
                status = Status.PENDING, source = "auto", kind = "wechat_redpacket_in", raw = "领红包",
            )
        }
        if (Prefs(ctx).blacklistHit("", tx.raw) != null) { note("黑名单，不记：${tx.raw}"); return }
        val id = Repo.insert(tx)
        claimed[id] = now
        if (tx.kind == "screen_wx_transfer_sent") Prefs(ctx).addSentTransfer(id, now)
        Repo.log(wx, "微信", tx.raw, (if (tx.status == Status.PENDING) "待补金额 #$id" else "已记账 #$id") + "（从微信的操作看出来的）")
        note("微信：${tx.raw} → #$id")
        main.post { Bubble.show(ctx, id) }
    }

    /**
     * Called by [Ingest] for "[转账] 已被接收". One transfer, one entry: if it was recorded when it was sent
     * (with the amount typed in), the acceptance only adds the person's name — and adds nothing at all when
     * that entry was deleted meanwhile. Returns a log line, or null when the transfer was not seen being sent.
     */
    fun pairAcceptedTransfer(ctx: Context, counterpart: String, at: Long): String? {
        val id = Prefs(ctx).takeSentTransfer(at)
            // Sent before this list existed (or the list was lost): any nameless one of the last day.
            ?: Repo.listAll(at - 24 * 3600_000L, at + 60_000L)
                .filter { it.kind == "screen_wx_transfer_sent" && it.counterpart.isBlank() }
                .minByOrNull { it.time }?.id
            ?: return null
        val hit = Repo.get(id) ?: return "转账已被接收：发出时记的 #$id 已经删了，不再记"
        if (hit.counterpart.isBlank() && counterpart.isNotBlank()) {
            val cat = if (hit.category.isBlank() || hit.category == Bubble.OTHER) Categorizer.auto(hit.type, counterpart, hit.kind).ifBlank { hit.category } else hit.category
            Repo.update(hit.copy(counterpart = counterpart, category = cat))
            main.post { Bubble.refresh() }
        }
        return "转账已被接收，就是发出时记的 #$id"
    }

    private fun fill(ctx: Context, id: Long, cents: Long, counterpart: String, from: String) {
        val tx = Repo.get(id) ?: return
        if (tx.amountCents != null) return
        claimed[id] = System.currentTimeMillis()
        val who = tx.counterpart.ifBlank { counterpart }
        val cat = tx.category.ifBlank { Categorizer.auto(tx.type, who, tx.kind).ifBlank { Bubble.OTHER } }
        Repo.update(tx.copy(amountCents = cents, counterpart = who, category = cat, status = Status.OK))
        Repo.log(if (tx.channel == "支付宝") com.aileboeth.expensetracking.parse.Parser.ALIPAY else com.aileboeth.expensetracking.parse.Parser.WECHAT, "屏幕", from, "补上金额 #$id")
        note("补上金额 #$id：${com.aileboeth.expensetracking.data.Money.money(cents)}（$from）")
        AutoFetch.done(id)
        main.post { Bubble.refresh() }
    }

    /**
     * Called by [Ingest] (on the worker) before it records a notification with an amount: true = a
     * screen sighting already recorded this payment, so the notification must not add it again.
     */
    fun alreadyFromScreen(type: com.aileboeth.expensetracking.parse.TxType, channel: String, cents: Long, at: Long): Long? {
        trim(at)
        val hit = Repo.listAll(at - ScreenMatch.PAIR_WINDOW, at + ScreenMatch.PAIR_WINDOW).filter {
            ScreenMatch.isScreenKind(it.kind) && it.id !in noticed && it.type == type && it.channel == channel && it.amountCents == cents
        }.minByOrNull { kotlin.math.abs(it.time - at) } ?: return null
        noticed[hit.id] = at
        return hit.id
    }
}
