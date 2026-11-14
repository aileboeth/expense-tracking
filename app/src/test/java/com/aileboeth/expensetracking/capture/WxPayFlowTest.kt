package com.aileboeth.expensetracking.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Event sequences as recorded on the user's phone (screen samples 2026-11-14, WeChat main account). */
class WxPayFlowTest {
    private val ui = "com.tencent.mm.ui."
    private var t = 1_000_000L
    private fun next() = t.also { t += 1000 }

    @Test fun redPacketSentWithTypedAmount() {
        val f = WxPayFlow()
        assertNull(f.onPage("${ui}LauncherUI", "", next()))
        assertNull(f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewPrepareUI", "", next()))
        f.onFocus("android.widget.EditText", "¥0.00")
        f.onDescription("金额0¥")
        f.onTyped("android.widget.EditText", "0.")
        f.onDescription("金额0.¥")
        f.onTyped("android.widget.EditText", "0.8")
        f.onTyped("android.widget.EditText", "0.")
        f.onTyped("android.widget.EditText", "0.5")
        assertNull(f.onPage("com.tencent.mm.k2", "微信支付", next()))
        assertNull(f.onPage("com.tencent.mm.UIPageFragmentActivity", "", next()))
        assertNull(f.onPage("com.tencent.mm.UIPageFragmentActivity", "", next()))
        assertEquals(WxPayFlow.Out.Sent(redPacket = true, cents = 50), f.onPage("${ui}LauncherUI", "", next()))
        // Nothing more until the next payment.
        assertNull(f.onPage("${ui}LauncherUI", "", next()))
    }

    @Test fun transferSent() {
        val f = WxPayFlow()
        f.onPage("com.tencent.mm.plugin.remittance.ui.RemittanceUI", "", next())
        f.onTyped("android.widget.EditText", "12.30")
        f.onPage("com.tencent.mm.k2", "微信支付", next())
        f.onPage("com.tencent.mm.UIPageFragmentActivity", "", next())
        assertEquals(WxPayFlow.Out.Sent(redPacket = false, cents = 1230), f.onPage("${ui}LauncherUI", "", next()))
    }

    @Test fun cancelledAtThePaySheetIsNotRecorded() {
        val f = WxPayFlow()
        f.onPage("com.tencent.mm.plugin.remittance.ui.RemittanceUI", "", next())
        f.onTyped("android.widget.EditText", "5")
        f.onPage("com.tencent.mm.k2", "微信支付", next())
        // Closed the sheet: never reached the fingerprint / password page.
        assertNull(f.onPage("${ui}LauncherUI", "", next()))
    }

    @Test fun backToTheAmountPageCancels() {
        val f = WxPayFlow()
        f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewPrepareUI", "", next())
        f.onTyped("android.widget.EditText", "2")
        f.onPage("com.tencent.mm.k2", "微信支付", next())
        f.onPage("com.tencent.mm.UIPageFragmentActivity", "", next())
        f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewPrepareUI", "", next())
        assertNull(f.onPage("${ui}ChattingUI", "", next()))
    }

    @Test fun groupRedPacketCountBoxIsNotTheAmount() {
        val f = WxPayFlow()
        f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewPrepareUI", "", next())
        f.onFocus("android.widget.EditText", "¥0.00")
        f.onTyped("android.widget.EditText", "6.66")
        f.onFocus("android.widget.EditText", "填写个数")
        f.onTyped("android.widget.EditText", "3")
        f.onPage("com.tencent.mm.k2", "微信支付", next())
        f.onPage("com.tencent.mm.UIPageFragmentActivity", "", next())
        assertEquals(WxPayFlow.Out.Sent(redPacket = true, cents = 666), f.onPage("${ui}LauncherUI", "", next()))
    }

    @Test fun openedRedPacket() {
        val f = WxPayFlow()
        f.onPage("${ui}LauncherUI", "", next())
        assertNull(f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewReceiveUI", "", next()))
        assertNull(f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewReceiveUI", "", next()))
        assertEquals(WxPayFlow.Out.Opened, f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyBeforeDetailUI", "", next()))
        // The second detail page of the same packet counts once.
        assertNull(f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewDetailUI", "", next()))
    }

    @Test fun detailWithoutOpeningIsNotIncome() {
        // Looking at a red packet already opened (or your own) goes straight to the details.
        val f = WxPayFlow()
        f.onPage("${ui}LauncherUI", "", next())
        assertNull(f.onPage("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewDetailUI", "", next()))
    }
}
