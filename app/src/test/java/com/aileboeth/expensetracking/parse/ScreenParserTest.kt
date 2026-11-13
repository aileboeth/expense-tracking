package com.aileboeth.expensetracking.parse

import com.aileboeth.expensetracking.data.ScreenMatch
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The screen texts here are written from how these pages look in common versions, NOT recorded on
 * the user's phone yet (see ScreenSamples). Replace them with real samples when they arrive.
 */
class ScreenParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-11-13T12:30:00")
    private fun read(pkg: String, vararg l: String) = ScreenParser.read(pkg, l.toList(), now, zone)

    @Test fun wechatSuccessPage() {
        val s = read(Parser.WECHAT, "支付成功", "星巴克咖啡", "¥30.00", "完成").single()
        assertEquals(TxType.EXPENSE, s.type)
        assertEquals(3000L, s.amountCents)
        assertEquals("星巴克咖啡", s.counterpart)
        assertEquals("微信", s.channel)
        assertTrue(s.fresh)
    }

    @Test fun alipaySuccessPageAmountFirst() {
        val s = read(Parser.ALIPAY, "支付成功", "￥12.50", "某某便利店", "完成").single()
        assertEquals(1250L, s.amountCents)
        assertEquals("某某便利店", s.counterpart)
        assertEquals("支付宝", s.channel)
        assertTrue(s.fresh)
    }

    @Test fun transferToFriend() {
        val s = read(Parser.WECHAT, "支付成功", "待小明确认收款", "¥200.00", "完成").single()
        assertEquals(20000L, s.amountCents)
        assertEquals("小明", s.counterpart)
        assertEquals("screen_transfer_out", s.kind)
    }

    @Test fun billDetailFromLastWeekIsHistory() {
        val s = read(Parser.WECHAT, "账单详情", "星巴克咖啡", "-30.00", "当前状态", "支付成功", "支付时间", "2026年11月06日 08:12:40").single()
        assertEquals(3000L, s.amountCents)
        assertFalse(s.fresh)
        assertEquals(at("2026-11-06T08:12:40"), s.time)
    }

    @Test fun alipayScanPayWithoutButtonIsFresh() {
        // Real (2026-11-13): Alipay scan-to-pay showed "付款成功￥10.00" as one line, no readable 完成, and no notification came.
        val s = read(Parser.ALIPAY, "付款成功￥10.00", "某某小吃店", "支付宝").single()
        assertEquals(1000L, s.amountCents)
        assertEquals("某某小吃店", s.counterpart)
        assertTrue(s.why, s.fresh)
        assertTrue(read(Parser.WECHAT, "支付成功", "¥30.00").single().fresh)
    }

    @Test fun cardsInAChatOrListAreNotFresh() {
        val chat = read(Parser.WECHAT, "微信支付", "下午 6:05", "付款成功￥10.00", "收款方", "某店").single()
        assertFalse(chat.fresh)
        assertEquals("像聊天或列表（有时间标签）", chat.why)
        val list = read(Parser.ALIPAY, "账单", "付款成功", "-10.00", "-25.00", "-3.50", "+100.00", "-8.00").single()
        assertFalse(list.fresh)
    }

    @Test fun redPacketOpened() {
        val s = read(Parser.WECHAT, "小红的红包", "恭喜发财，大吉大利", "8.88", "元", "已存入零钱，可直接消费").single()
        assertEquals(TxType.INCOME, s.type)
        assertEquals(888L, s.amountCents)
        assertEquals("小红", s.counterpart)
        assertTrue(s.fresh)
    }

    @Test fun transferAcceptedPage() {
        val s = read(Parser.WECHAT, "你已收款", "¥100.00", "零钱余额 ¥520.00", "转账时间", "2026年11月13日 12:28:10", "收款时间", "2026年11月13日 12:29:01").single()
        assertEquals(TxType.INCOME, s.type)
        assertEquals(10000L, s.amountCents)
        assertTrue(s.fresh)
    }

    @Test fun transferBubbleInChatIsNotFresh() {
        // A chat shows "已收款 ¥100.00 微信转账" in the bubble: no page time, no 零钱 note.
        val s = read(Parser.WECHAT, "小明", "晚上一起吃饭吗", "¥100.00", "已收款", "微信转账").single()
        assertFalse(s.fresh)
    }

    @Test fun alipayIncomeDetail() {
        val s = read(Parser.ALIPAY, "多点科技", "+150.00", "交易成功", "转账备注", "劳务费", "创建时间", "2026-11-13 09:01:22").single()
        assertEquals(TxType.INCOME, s.type)
        assertEquals(15000L, s.amountCents)
        assertEquals("多点科技", s.counterpart)
        assertFalse(s.fresh)
    }

    @Test fun wechatPayChatCards() {
        val cards = read(
            Parser.WECHAT, "微信支付",
            "昨天 18:02", "微信支付凭证", "付款金额", "¥15.00", "收款方", "食堂一号窗口",
            "12:05", "微信支付凭证", "付款金额", "¥22.50", "收款方", "便利店",
        )
        assertEquals(2, cards.size)
        assertEquals(listOf(1500L, 2250L), cards.map { it.amountCents })
        assertEquals(listOf("食堂一号窗口", "便利店"), cards.map { it.counterpart })
        assertEquals(at("2026-11-12T18:02:00"), cards[0].time)
        assertEquals(at("2026-11-13T12:05:00"), cards[1].time)
        assertTrue(cards.all { !it.fresh && it.kind == "screen_voucher" })
    }

    @Test fun chatTimes() {
        assertEquals(at("2026-11-13T15:05:00"), ScreenParser.chatTime("下午 3:05", now, zone))
        assertEquals(at("2026-11-12T21:10:00"), ScreenParser.chatTime("昨天 21:10", now, zone))
        assertEquals(at("2026-11-10T20:00:00"), ScreenParser.chatTime("11月10日 晚上 8:00", now, zone))
        assertEquals(null, ScreenParser.chatTime("明天见", now, zone))
    }

    @Test fun nothingOnAChat() {
        assertTrue(read(Parser.WECHAT, "小明", "明天3点见", "好的").isEmpty())
        assertTrue(read("com.other.app", "支付成功", "¥30.00", "完成").isEmpty())
    }

    // ---- matching ----

    private fun tx(id: Long, cents: Long?, time: Long, kind: String = "wechat_paid", type: TxType = TxType.EXPENSE, channel: String = "微信", who: String = "") =
        Tx(id = id, type = type, amountCents = cents, channel = channel, counterpart = who, time = time, kind = kind,
            status = if (cents == null) Status.PENDING else Status.OK, source = "auto")

    private fun seen(cents: Long, fresh: Boolean = true, time: Long? = null, who: String = "", type: TxType = TxType.EXPENSE, channel: String = "微信") =
        ScreenParser.Seen(type, cents, channel, who, "screen_paid", fresh, time)

    @Test fun screenConfirmsTheNotification() {
        val a = ScreenMatch.plan(seen(3000, who = "星巴克"), listOf(tx(1, 3000, now - 20_000)), emptySet(), now)
        assertEquals(ScreenMatch.Action.Confirm(1, "星巴克"), a)
    }

    @Test fun twoSameAmountPaymentsPairOneToOne() {
        val recent = listOf(tx(1, 3000, now - 60_000), tx(2, 3000, now - 5_000))
        val first = ScreenMatch.plan(seen(3000), recent, emptySet(), now) as ScreenMatch.Action.Confirm
        assertEquals(2L, first.txId) // the closest one
        val second = ScreenMatch.plan(seen(3000), recent, setOf(2L), now) as ScreenMatch.Action.Confirm
        assertEquals(1L, second.txId)
        assertEquals(ScreenMatch.Action.Create, ScreenMatch.plan(seen(3000), recent, setOf(1L, 2L), now))
    }

    @Test fun noNotificationCreates() {
        assertEquals(ScreenMatch.Action.Create, ScreenMatch.plan(seen(3000), listOf(tx(1, 1800, now - 10_000)), emptySet(), now))
        // An old bill opened again never creates.
        assertEquals(ScreenMatch.Action.Skip, ScreenMatch.plan(seen(3000, fresh = false, time = now - 86_400_000L * 3), emptyList(), emptySet(), now))
    }

    @Test fun fillsTransferByName() {
        val waiting = tx(5, null, now - 3600_000, kind = "alipay_transfer_in", type = TxType.INCOME, channel = "支付宝", who = "多点科技")
        val a = ScreenMatch.plan(seen(15000, fresh = false, time = now - 3500_000, who = "多点科技", type = TxType.INCOME, channel = "支付宝"), listOf(waiting), emptySet(), now)
        assertEquals(ScreenMatch.Action.Fill(5, 15000, "多点科技"), a)
    }

    @Test fun historyWithoutNameOrTimeDoesNotFill() {
        val waiting = tx(5, null, now - 3600_000, kind = "wechat_voucher")
        assertEquals(ScreenMatch.Action.Skip, ScreenMatch.plan(seen(3000, fresh = false), listOf(waiting), emptySet(), now))
    }

    @Test fun cardsByTime() {
        val cards = listOf(
            ScreenParser.Seen(TxType.EXPENSE, 1500, "微信", "", "screen_voucher", false, at("2026-11-12T18:02:00")),
            ScreenParser.Seen(TxType.EXPENSE, 2250, "微信", "", "screen_voucher", false, at("2026-11-13T12:05:00")),
        )
        val waiting = listOf(tx(7, null, at("2026-11-13T12:05:40"), kind = "wechat_voucher"))
        assertEquals(listOf(ScreenMatch.Action.Fill(7, 2250, "")), ScreenMatch.planCards(cards, waiting, newerVoucherDone = false))
    }

    @Test fun cardsByOrderOnlyWhenNewest() {
        val cards = listOf(1000L, 2000L, 3000L).map { ScreenParser.Seen(TxType.EXPENSE, it, "微信", "", "screen_voucher", false, null) }
        val waiting = listOf(tx(8, null, now - 600_000, kind = "wechat_voucher"), tx(9, null, now - 60_000, kind = "wechat_voucher"))
        assertEquals(listOf(2000L, 3000L), ScreenMatch.planCards(cards, waiting, newerVoucherDone = false).map { it.cents })
        assertTrue(ScreenMatch.planCards(cards, waiting, newerVoucherDone = true).isEmpty())
    }
}
