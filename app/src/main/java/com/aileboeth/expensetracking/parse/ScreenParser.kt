package com.aileboeth.expensetracking.parse

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Reads the text on a WeChat / Alipay screen (what the accessibility service sees, flattened in
 * reading order) and finds the money on it. Pure Kotlin so it can be unit-tested.
 *
 * The screen layouts are NOT taken from real samples yet: the rules look for the words these pages
 * have shown for years (支付成功 … ¥30.00 … 完成) and stay loose. "屏幕样本" in the full-auto page
 * records what the phone really shows so the rules can be fixed from real screens.
 */
object ScreenParser {

    /** One payment seen on the screen. */
    data class Seen(
        val type: TxType,
        val amountCents: Long,
        val channel: String,
        val counterpart: String,
        val kind: String,
        /**
         * True = a page shown right after paying / receiving (支付成功 with a 完成 button, a red
         * packet just opened): it may create an entry. False = history (a card in the 微信支付 chat,
         * an old bill's detail): it may only fill an entry that is waiting for its amount.
         */
        val fresh: Boolean,
        /** When the page says it happened (a bill's 支付时间, a chat time label); null = not shown. */
        val time: Long? = null,
        val summary: String = "",
    )

    private const val NUM = """\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?"""
    /** A line that is only an amount: "¥30.00", "￥ 30.00", "-30.00", "+100.00", "30.00元". */
    private val AMOUNT_LINE = Regex("""^([-+−])?\s*[¥￥]?\s*($NUM)\s*元?$""")
    /** An amount inside a longer line, with a currency mark: "已支付¥30.00", "付款金额 ¥30.00". */
    private val AMOUNT_IN = Regex("""[¥￥]\s*($NUM)|($NUM)\s*元""")

    private val PAID_WORDS = listOf("支付成功", "付款成功", "转账成功", "已转账", "已支付")
    private val DONE_BUTTONS = setOf("完成", "返回", "Done", "确定", "返回商家")
    private val RECEIVED_PAGE = listOf("你已收款", "已收款", "收款成功", "已存入零钱", "已存入余额", "已到账", "转账已到账")
    private val TIME_LABELS = listOf("支付时间", "付款时间", "创建时间", "收款时间", "转账时间", "到账时间", "交易时间", "时间")

    /** Lines that are labels or buttons, never a shop or a person. */
    private val NOT_A_NAME = setOf(
        "完成", "返回", "确定", "支付成功", "付款成功", "转账成功", "已支付", "已转账", "微信支付", "支付宝", "零钱", "余额",
        "账单详情", "交易成功", "当前状态", "支付方式", "收款方", "付款方", "商品", "商户全称", "收单机构", "交易单号", "商户单号",
        "支付时间", "付款时间", "创建时间", "收款时间", "转账时间", "到账时间", "查看账单", "联系商家", "更多", "微信转账",
        "已收款", "你已收款", "已存入零钱", "零钱通", "红包", "转账", "收款", "付款金额", "订单金额", "优惠", "积分",
    )

    fun read(pkg: String, lines: List<String>, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Seen> {
        val l = lines.map { it.trim() }.filter { it.isNotEmpty() }
        if (l.isEmpty()) return emptyList()
        val channel = when (pkg) { Parser.WECHAT -> Parser.CH_WECHAT; Parser.ALIPAY -> Parser.CH_ALIPAY; else -> "" }
        if (channel.isEmpty()) return emptyList()
        val all = l.joinToString("\n")
        val out = mutableListOf<Seen>()

        // ---- the 微信支付 chat: cards, one per payment, with time labels between them ----
        if (pkg == Parser.WECHAT && (l.any { it.contains("支付凭证") } || (l.any { it.startsWith("付款金额") } && l.none { it in DONE_BUTTONS }))) {
            out += chatCards(l, now, zone)
            if (out.isNotEmpty()) return out
        }

        val pageTime = pageTime(l, now, zone)
        val recent = pageTime == null || now - pageTime in -120_000L..600_000L

        // ---- paid: 支付成功 … ¥30.00 … 完成 ----
        val paidAt = l.indexOfFirst { line -> PAID_WORDS.any { line == it || line.startsWith(it) } }
        if (paidAt >= 0) {
            val amountAt = firstAmountNear(l, paidAt, 8)
            if (amountAt != null) {
                val (idx, cents) = amountAt
                val hasButton = l.any { it in DONE_BUTTONS }
                val who = counterpart(l, paidAt, idx)
                val kind = if (l[paidAt].contains("转账") || all.contains("确认收款")) "screen_transfer_out" else "screen_paid"
                if (cents > 0) {
                    out += Seen(TxType.EXPENSE, cents, channel, who, kind, fresh = hasButton && recent, time = pageTime, summary = "${l[paidAt]} ${money(cents)}")
                    return out
                }
            }
        }

        // ---- received: a transfer accepted / a red packet opened ----
        val recvAt = l.indexOfFirst { line -> RECEIVED_PAGE.any { line == it || line.startsWith(it) } }
        if (recvAt >= 0) {
            val amountAt = firstAmountNear(l, recvAt, 6)
            if (amountAt != null && amountAt.second > 0) {
                val (idx, cents) = amountAt
                val redPacket = l.any { it.endsWith("的红包") }
                val who = if (redPacket) l.first { it.endsWith("的红包") }.removeSuffix("的红包").trim() else counterpart(l, recvAt, idx)
                // A chat bubble also says 已收款 + ¥100.00: only a page with its own time or the 零钱 note counts as fresh.
                val pageLike = pageTime != null || all.contains("已存入零钱") || all.contains("已存入余额")
                out += Seen(
                    TxType.INCOME, cents, channel, who, if (redPacket) "screen_red_packet" else "screen_received",
                    fresh = pageLike && recent, time = pageTime, summary = "${l[recvAt]} ${money(cents)}",
                )
                return out
            }
        }

        // ---- an Alipay / WeChat bill detail: "+100.00" or "-30.00" with a name above ----
        val signed = l.withIndex().mapNotNull { (i, s) ->
            AMOUNT_LINE.matchEntire(s)?.takeIf { it.groupValues[1].isNotEmpty() }?.let { m -> i to (m.groupValues[1] to cents(m.groupValues[2])) }
        }
        if (signed.size == 1) {
            val (idx, pair) = signed[0]
            val (sign, cents) = pair
            if (cents > 0) {
                val type = if (sign == "+") TxType.INCOME else TxType.EXPENSE
                out += Seen(type, cents, channel, counterpart(l, idx, idx), "screen_detail", fresh = false, time = pageTime, summary = "$sign${money(cents)}")
            }
        }
        return out
    }

    /** The 微信支付 chat: each card with 凭证 / 付款金额 and its amount; time labels between cards give the time. */
    private fun chatCards(l: List<String>, now: Long, zone: ZoneId): List<Seen> {
        val out = mutableListOf<Seen>()
        var label: Long? = null
        var i = 0
        while (i < l.size) {
            val s = l[i]
            chatTime(s, now, zone)?.let { label = it; i++; continue }
            val voucher = s.contains("支付凭证")
            val paid = s.startsWith("已支付") || s.startsWith("付款金额") || s.startsWith("支付成功")
            if (voucher || paid) {
                // The amount is on this line ("已支付¥30.00") or within the next few.
                val inline = AMOUNT_IN.find(s)?.let { cents(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
                val near = inline?.let { i to it } ?: firstAmountNear(l, i, 5, back = 0)
                if (near != null && near.second > 0) {
                    val who = l.subList(i, minOf(l.size, i + 10)).withIndex()
                        .firstOrNull { (_, v) -> v == "收款方" || v.startsWith("收款方") }
                        ?.let { (j, v) -> v.removePrefix("收款方").trim(' ', ':', '：').ifEmpty { l.getOrNull(i + j + 1).orEmpty() } }
                        .orEmpty()
                    out += Seen(
                        TxType.EXPENSE, near.second, Parser.CH_WECHAT, who.take(40),
                        if (voucher) "screen_voucher" else "screen_wechat_card", fresh = false, time = label, summary = "$s ${money(near.second)}",
                    )
                    i = maxOf(i + 1, near.first + 1)
                    continue
                }
            }
            i++
        }
        return out
    }

    /** First amount on or after [from] (and a little before it), as (line index, cents). */
    private fun firstAmountNear(l: List<String>, from: Int, span: Int, back: Int = 3): Pair<Int, Long>? {
        val order = (from..minOf(l.lastIndex, from + span)) + (from - 1 downTo maxOf(0, from - back))
        for (i in order) {
            val s = l[i]
            AMOUNT_LINE.matchEntire(s)?.let { m ->
                // A bare "3" or "2026" is a count or a year, not money: needs ¥, decimals, a sign or 元.
                val bare = !s.contains('¥') && !s.contains('￥') && !s.contains('.') && !s.endsWith("元") && m.groupValues[1].isEmpty()
                if (!bare) return i to cents(m.groupValues[2])
            }
            AMOUNT_IN.find(s)?.let { return i to cents(it.groupValues[1].ifEmpty { it.groupValues[2] }) }
        }
        return null
    }

    /** A shop or person near the amount: the line after 收款方/商户, else a plain line next to the amount. */
    private fun counterpart(l: List<String>, top: Int, amountAt: Int): String {
        l.forEachIndexed { i, s ->
            for (label in listOf("收款方", "商户全称", "付款方", "对方账户", "转账给", "收款人")) {
                if (s == label) return l.getOrNull(i + 1).orEmpty().take(40)
                if (s.startsWith(label) && s.length > label.length + 1) return s.removePrefix(label).trim(' ', ':', '：').take(40)
            }
        }
        Regex("""^待(.+?)确认收款""").let { r -> l.firstNotNullOfOrNull { r.find(it) }?.let { return it.groupValues[1].trim().take(40) } }
        val around = listOf(amountAt - 1, amountAt + 1, top + 1, top - 1).filter { it in l.indices && it != top && it != amountAt }
        return around.map { l[it] }.firstOrNull { isName(it) }.orEmpty().take(40)
    }

    private fun isName(s: String) =
        s.length in 1..30 && s !in NOT_A_NAME && !AMOUNT_LINE.matches(s) && !AMOUNT_IN.containsMatchIn(s) &&
            PAID_WORDS.none { s.startsWith(it) } && RECEIVED_PAGE.none { s.startsWith(it) } && !s.contains("时间") && !s.any { it == '>' }

    /** "支付时间 2026-11-10 12:30:01" (label and value may be two lines). */
    fun pageTime(l: List<String>, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        l.forEachIndexed { i, s ->
            val label = TIME_LABELS.firstOrNull { s.startsWith(it) } ?: return@forEachIndexed
            val value = s.removePrefix(label).trim(' ', ':', '：').ifEmpty { l.getOrNull(i + 1).orEmpty() }
            dateTime(value, now, zone)?.let { return it }
        }
        return null
    }

    private val FULL = Regex("""(\d{4})[-/年.](\d{1,2})[-/月.](\d{1,2})日?\s*(\d{1,2}):(\d{2})(?::(\d{2}))?""")
    private val SHORT = Regex("""^(\d{1,2})[-/月](\d{1,2})日?\s+(\d{1,2}):(\d{2})""")

    fun dateTime(s: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        FULL.find(s)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            return runCatching {
                LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), m.groupValues[6].ifEmpty { "0" }.toInt()).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
        }
        SHORT.find(s)?.let { m ->
            val (mo, d, h, mi) = m.destructured
            val year = java.time.Instant.ofEpochMilli(now).atZone(zone).year
            return runCatching { LocalDateTime.of(year, mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
        }
        return null
    }

    private val CHAT_TIME = Regex("""^(昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天]|\d{1,2}月\d{1,2}日|\d{4}年\d{1,2}月\d{1,2}日)?\s*(凌晨|早上|上午|中午|下午|晚上)?\s*(\d{1,2}):(\d{2})$""")

    /** WeChat's chat time labels: "12:30", "下午 3:05", "昨天 21:10", "11月10日 晚上 8:00". */
    fun chatTime(s: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val m = CHAT_TIME.matchEntire(s.trim()) ?: return null
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val day = m.groupValues[1]
        val date: LocalDate = when {
            day.isEmpty() -> today
            day == "昨天" -> today.minusDays(1)
            day == "前天" -> today.minusDays(2)
            day.startsWith("星期") || day.startsWith("周") -> {
                val target = "一二三四五六日天".indexOf(day.last()).let { if (it >= 6) 7 else it + 1 }
                var d = today.minusDays(1)
                while (d.dayOfWeek.value != target) d = d.minusDays(1)
                d
            }
            else -> Regex("""(?:(\d{4})年)?(\d{1,2})月(\d{1,2})日""").find(day)?.let { dm ->
                runCatching { LocalDate.of(dm.groupValues[1].ifEmpty { today.year.toString() }.toInt(), dm.groupValues[2].toInt(), dm.groupValues[3].toInt()) }.getOrNull()
            } ?: return null
        }
        var hour = m.groupValues[3].toInt()
        val minute = m.groupValues[4].toInt()
        when (m.groupValues[2]) {
            "下午", "晚上" -> if (hour < 12) hour += 12
            "中午" -> if (hour < 11) hour += 12
            "凌晨", "早上", "上午" -> if (hour == 12) hour = 0
        }
        if (hour > 23 || minute > 59) return null
        return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    }

    private fun cents(raw: String): Long = runCatching { Parser.cents(raw) }.getOrDefault(0L)

    private fun money(c: Long) = "¥" + java.math.BigDecimal.valueOf(c, 2).toPlainString()

    /** Anything on the screen that looks like money — decides whether a screen sample is worth keeping. */
    fun looksLikeMoney(lines: List<String>): Boolean =
        lines.any { s -> AMOUNT_IN.containsMatchIn(s) || AMOUNT_LINE.matchEntire(s.trim())?.let { s.contains('.') || s.contains('¥') || s.contains('￥') } == true }
}
