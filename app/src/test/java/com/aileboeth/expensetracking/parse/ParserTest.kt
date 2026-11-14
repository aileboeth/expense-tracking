package com.aileboeth.expensetracking.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Samples are the real notifications recorded on the user's vivo phone (2026-11-06 .. 11-08,
 * WeChat 8.0.76, Alipay 12.12.6.8000), with private names replaced.
 */
class ParserTest {

    private fun wx(text: String, title: String = "微信支付", whenMs: Long = 1L, user: Int = 999) =
        NoticeInput(Parser.WECHAT, user, "999|com.tencent.mm|-656511598|null|99910326", title, text, whenMs = whenMs)

    private fun ali(title: String, text: String, bigText: String = "") =
        NoticeInput(Parser.ALIPAY, 0, "0|com.eg.android.AlipayGphone|-1|null|10295", title, text, bigText)

    private fun tx(r: ParseResult): ParseResult.Tx {
        assertTrue("expected Tx but got $r", r is ParseResult.Tx)
        return r as ParseResult.Tx
    }

    // ---- WeChat -------------------------------------------------------------------------

    @Test fun wechatPaid() {
        val t = tx(Parser.parse(wx("已支付¥30.00")))
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(3000L, t.amountCents)
        assertEquals("微信", t.channel)
        assertEquals("wechat_paid", t.kind)
    }

    @Test fun wechatPaidSmallAndLarge() {
        assertEquals(999L, tx(Parser.parse(wx("已支付¥9.99"))).amountCents)
        assertEquals(160L, tx(Parser.parse(wx("已支付¥1.60"))).amountCents)
        assertEquals(19800L, tx(Parser.parse(wx("已支付¥198.00"))).amountCents)
        assertEquals(123456L, tx(Parser.parse(wx("已支付¥1,234.56"))).amountCents)
    }

    @Test fun wechatSecondMessageInSameNotification() {
        // A second ¥30 payment a minute later re-posts the same notification with a count prefix.
        val first = wx("已支付¥30.00", whenMs = 1793971373358)
        val second = wx("[2条]微信支付: 已支付¥30.00", whenMs = 1793971420291)
        assertEquals(3000L, tx(Parser.parse(second)).amountCents)
        assertNotEquals("two payments must not look like one", first.signature, second.signature)
    }

    @Test fun samePostAgainHasSameSignature() {
        // Rebind snapshot / refresh of the same message: same key, text and time.
        assertEquals(wx("已支付¥30.00", whenMs = 5).signature, wx("已支付¥30.00", whenMs = 5).signature)
    }

    @Test fun samePaymentTextLaterIsNewSignature() {
        // Read the chat, then pay ¥30 again: same text, but a new message time.
        assertNotEquals(wx("已支付¥30.00", whenMs = 5).signature, wx("已支付¥30.00", whenMs = 9).signature)
    }

    @Test fun wechatWatchVoucherHasNoAmount() {
        val t = tx(Parser.parse(wx("微信支付凭证")))
        assertEquals(TxType.EXPENSE, t.type)
        assertNull(t.amountCents)
        assertEquals("wechat_voucher", t.kind)
    }

    @Test fun wechatTransferAcceptedIsPendingExpense() {
        // Real sample 2026-11-14 (main WeChat, user 0): the chat notification when the other side takes the money.
        val t = tx(Parser.parse(wx("[转账] 已被接收", title = "张三", user = 0)))
        assertEquals(TxType.EXPENSE, t.type)
        assertNull(t.amountCents)
        assertEquals("张三", t.counterpart)
        assertEquals("wechat_transfer_out", t.kind)
        val more = tx(Parser.parse(wx("[2条]张三: [转账] 已被接收", title = "张三", user = 0)))
        assertEquals("张三", more.counterpart)
        // Someone sent you money (real sample): pending income, the person is the chat.
        val incoming = tx(Parser.parse(wx("[转账] 请收款", title = "李四", user = 0)))
        assertEquals(TxType.INCOME, incoming.type)
        assertNull(incoming.amountCents)
        assertEquals("李四", incoming.counterpart)
        assertEquals("wechat_transfer_in", incoming.kind)
        // Anything else about a transfer in a chat stays a chat.
        assertEquals(ParseResult.NotPayment, Parser.parse(wx("[转账] 已退还", title = "张三", user = 0)))
        assertEquals(ParseResult.NotPayment, Parser.parse(wx("张三: 我说的[转账] 已被接收了吗", title = "群聊", user = 0)))
    }

    @Test fun wechatChatsAreIgnored() {
        assertEquals(ParseResult.NotPayment, Parser.parse(wx("[5条]张三: 然后退款不到账", title = "张三", user = 0)))
        assertEquals(ParseResult.NotPayment, Parser.parse(wx("我给你转了¥50", title = "李四", user = 0)))
    }

    @Test fun wechatIncomeGuesses() {
        val r = tx(Parser.parse(wx("收款到账通知 ¥12.50", title = "微信收款助手")))
        assertEquals(TxType.INCOME, r.type)
        assertEquals(1250L, r.amountCents)
        val refund = tx(Parser.parse(wx("退款到账¥8.00")))
        assertEquals(TxType.INCOME, refund.type)
        assertEquals(800L, refund.amountCents)
    }

    @Test fun wechatOtherServiceMessageIgnored() {
        assertTrue(Parser.parse(wx("你有一张优惠券即将过期")) is ParseResult.Ignored)
    }

    // ---- Alipay -------------------------------------------------------------------------

    @Test fun alipayExpenseWithAdvert() {
        val t = tx(Parser.parse(ali("交易提醒", "你有一笔10.00元的支出，点击领取8个支付宝积分。")))
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(1000L, t.amountCents)
        assertEquals("支付宝", t.channel)
        assertEquals("你有一笔10.00元的支出", t.summary)
    }

    @Test fun alipayExpenseOtherAdverts() {
        assertEquals(3000L, tx(Parser.parse(ali("交易提醒", "你有一笔30.00元的支出，点击领1元话费券。"))).amountCents)
        assertEquals(3000L, tx(Parser.parse(ali("交易提醒", "你有一笔30.00元的支出，点击领0.5元话费券。"))).amountCents)
        assertEquals(19800L, tx(Parser.parse(ali("交易提醒", "你有一笔198.00元的支出，点击领取2元流量红包。"))).amountCents)
    }

    @Test fun alipayZeroAutopayIsOnlyAHint() {
        val text = "你在慧生活798校园版有一笔0.00元的免密/自动扣款支付，点击领取8个支付宝积分。"
        assertTrue(Parser.parse(ali("交易提醒", text, bigText = text)) is ParseResult.Ignored)
    }

    @Test fun alipayRealAutopayKeepsMerchant() {
        val t = tx(Parser.parse(ali("交易提醒", "你在慧生活798校园版有一笔3.50元的免密/自动扣款支付，点击领取8个支付宝积分。")))
        assertEquals(350L, t.amountCents)
        assertEquals("慧生活798校园版", t.counterpart)
    }

    @Test fun alipayTransferInHasNoAmount() {
        // Seen both on Alipay's own channel and through vivo push (bigText set too).
        for (r in listOf(
            Parser.parse(ali("钱已到账", "某公司给你转了1笔钱")),
            Parser.parse(ali("钱已到账", "某公司给你转了1笔钱", bigText = "某公司给你转了1笔钱")),
        )) {
            val t = tx(r)
            assertEquals(TxType.INCOME, t.type)
            assertNull(t.amountCents)
            assertEquals("某公司", t.counterpart)
            assertEquals("alipay_transfer_in", t.kind)
        }
    }

    @Test fun alipayReceivedWithAmount() {
        val t = tx(Parser.parse(ali("收款到账", "支付宝成功收款12.00元")))
        assertEquals(TxType.INCOME, t.type)
        assertEquals(1200L, t.amountCents)
    }

    // ---- Other apps -----------------------------------------------------------------------

    @Test fun otherAppsAreNotParsedByDefault() {
        val n = NoticeInput("com.example.rent", 0, "k", "出租结束提醒", "您的账号已出租结束！收益¥4已入账。...")
        assertEquals(ParseResult.NotPayment, Parser.parse(n))
    }

    @Test fun genericGuessNeedsConfirm() {
        val n = NoticeInput("com.example.rent", 0, "k", "出租结束提醒", "您的账号已出租结束！收益¥7.2已入账。...")
        val t = tx(Parser.generic(n))
        assertEquals(TxType.INCOME, t.type)
        assertEquals(720L, t.amountCents)
        assertTrue(t.needsConfirm)
    }

    // ---- other currencies / payment apps (formats from public examples, no device samples yet) ----

    private fun other(pkg: String, title: String, text: String) = NoticeInput(pkg, 0, "k", title, text)
    private val wallet = PaymentApps.find("com.google.android.apps.walletnfcrel")!!
    private val paypal = PaymentApps.find("com.paypal.android.p2pmobile")!!

    @Test fun walletAmountAloneIsSpendingAtTheTitleShop() {
        val t = tx(Parser.generic(other(wallet.pkg, "Starbucks", "$5.25 with Visa •••• 1234"), wallet))
        assertEquals(TxType.EXPENSE, t.type)
        assertEquals(525L, t.amountCents)
        assertEquals("Google Wallet", t.channel)
        assertEquals("Starbucks", t.counterpart)
        assertTrue(t.needsConfirm)
    }

    @Test fun paypalSentAndReceived() {
        val sent = tx(Parser.generic(other(paypal.pkg, "PayPal", "You sent $12.00 USD to Alex"), paypal))
        assertEquals(TxType.EXPENSE, sent.type)
        assertEquals(1200L, sent.amountCents)
        val got = tx(Parser.generic(other(paypal.pkg, "PayPal", "Alex sent you €20,00"), paypal))
        assertEquals(TxType.INCOME, got.type)
        assertEquals(2000L, got.amountCents)
    }

    @Test fun nonWalletNeedsADirectionWord() {
        assertEquals(ParseResult.NotPayment, Parser.generic(other(paypal.pkg, "PayPal", "Your balance is $40.00"), paypal))
    }

    @Test fun manyCurrencyStyles() {
        fun amount(text: String) = tx(Parser.generic(other("x", "Bank", text))).amountCents
        assertEquals(450L, amount("Card payment €4,50 at Lidl"))
        assertEquals(450L, amount("Kartenzahlung 4,50 € bei Lidl"))
        assertEquals(123456L, amount("Purchase of 1.234,56 EUR approved"))
        assertEquals(123456L, amount("Purchase of $1,234.56 approved"))
        assertEquals(3990L, amount("Paid HK$39.90 to 7-Eleven"))
        assertEquals(50000L, amount("Payment of ₹500 debited"))
        assertEquals(1200000L, amount("12,000원 결제"))
        assertEquals(2500L, amount("Spent £25 at Tesco"))
        assertEquals(1999L, amount("You paid 19.99 USD"))
    }

    @Test fun datesAndCardNumbersAreNotAmounts() {
        assertEquals(ParseResult.NotPayment, Parser.generic(other("x", "Bank", "Payment due on 12.05 for card 1234")))
    }

    @Test fun regionsRankTheirOwnAppsFirst() {
        assertEquals(Parser.WECHAT, PaymentApps.ranked("CN").first().pkg)
        assertEquals("com.venmo", PaymentApps.ranked("US").first { "US" in it.regions }.pkg)
        assertTrue(PaymentApps.defaultOn(wallet, "US"))
        assertTrue(!PaymentApps.defaultOn(wallet, "CN"))
        assertTrue(PaymentApps.defaultOn(PaymentApps.find("com.phonepe.app")!!, "IN"))
        assertEquals("CN", PaymentApps.country("zh", ""))
    }

    @Test fun centsAny() {
        assertEquals(450L, Parser.centsAny("4,50"))
        assertEquals(123400L, Parser.centsAny("1,234"))
        assertEquals(123400L, Parser.centsAny("1.234"))
        assertEquals(123456L, Parser.centsAny("1.234,56"))
        assertEquals(100000000L, Parser.centsAny("1 000 000"))
        assertEquals(5L, Parser.centsAny("0.05"))
    }

    @Test fun cents() {
        assertEquals(1L, Parser.cents("0.01"))
        assertEquals(400L, Parser.cents("4"))
        assertEquals(720L, Parser.cents("7.2"))
        assertEquals(600476L, Parser.cents("6,004.76"))
    }
}
