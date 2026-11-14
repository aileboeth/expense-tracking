package com.aileboeth.expensetracking.capture

/**
 * WeChat empties every page for accessibility services (one empty node, real samples 2026-11-14), but
 * the events still say which page came up, what was typed into the amount box and which button was
 * pressed. From that alone this follows a red packet or transfer you send and a red packet you open.
 * Pure and single-threaded (fed from the service's main thread), so it can be unit-tested.
 *
 * Seen on the user's phone (WeChat 8.0.x, vivo, Android 16):
 * - send a red packet: LuckyMoneyNewPrepareUI (amount box: VIEW_TEXT_CHANGED "0." -> "0.5", a TextView
 *   described "金额0.¥") -> tap 塞钱进红包 -> pay sheet (state text "微信支付") -> UIPageFragmentActivity
 *   (fingerprint / password) -> back to the chat (LauncherUI).
 * - send a transfer: RemittanceUI -> pay sheet "微信支付" -> UIPageFragmentActivity -> LauncherUI.
 * - open a red packet: LuckyMoneyNewReceiveUI -> LuckyMoneyBeforeDetailUI / LuckyMoneyNewDetailUI.
 */
class WxPayFlow {
    sealed interface Out {
        /** Paid: a red packet ([redPacket]) or a transfer, for the amount typed on the page. */
        data class Sent(val redPacket: Boolean, val cents: Long) : Out
        /** A red packet was opened (its amount is not in any event). */
        data object Opened : Out
    }

    private enum class Input { RED_PACKET, TRANSFER }
    private class Armed(val input: Input, val cents: Long, val at: Long, var verified: Boolean = false)

    private var input: Input? = null
    private var typed: Long? = null
    /** False while the count box of a group red packet has the focus ("填写个数"). */
    private var amountFocused = true
    private var armed: Armed? = null
    private var receiveAt = 0L

    fun onPage(cls: String, text: String, now: Long): Out? {
        val page = cls.substringAfterLast('.')
        val inputPage = when {
            page.startsWith("LuckyMoney") && page.contains("PrepareUI") -> Input.RED_PACKET
            page == "RemittanceUI" -> Input.TRANSFER
            else -> null
        }
        if (inputPage != null) {
            // Back on the amount page: the payment was cancelled (or a new one starts).
            armed = null
            if (input != inputPage) { typed = null; amountFocused = true }
            input = inputPage
            return null
        }
        if (text.contains("微信支付")) {
            val i = input
            val c = typed
            if (i != null && c != null && c > 0) armed = Armed(i, c, now)
            return null
        }
        armed?.let { a ->
            if (now - a.at > ARM_MS) armed = null
            else if (page == "UIPageFragmentActivity") a.verified = true
        }
        if (page.startsWith("LuckyMoney") && page.contains("ReceiveUI")) {
            if (receiveAt == 0L) receiveAt = now
            return null
        }
        if (page.startsWith("LuckyMoney") && page.contains("DetailUI")) {
            if (receiveAt != 0L && now - receiveAt < OPEN_MS) { receiveAt = 0L; return Out.Opened }
            receiveAt = 0L
            return null
        }
        if (page == "LauncherUI" || page == "ChattingUI") {
            receiveAt = 0L
            val a = armed
            armed = null
            input = null
            typed = null
            if (a != null && a.verified) return Out.Sent(a.input == Input.RED_PACKET, a.cents)
        }
        return null
    }

    /** A TextView on the red packet page is described "金额0.5¥": the amount as typed. */
    fun onDescription(desc: String) {
        if (input == null) return
        LABEL.find(desc.trim())?.let { m -> cents(m.groupValues[1])?.let { typed = it } }
    }

    fun onFocus(cls: String, text: String) {
        if (input == null || !cls.contains("EditText")) return
        amountFocused = !text.contains("个")
    }

    fun onTyped(cls: String, text: String) {
        if (input == null || !cls.contains("EditText") || !amountFocused) return
        val t = text.trim()
        if (t.isEmpty()) { typed = null; return }
        BOX.find(t)?.let { m -> typed = cents(m.groupValues[1]) }
    }

    companion object {
        /** From the pay sheet to back in the chat: fingerprint or password, a result page, 完成. */
        const val ARM_MS = 3 * 60_000L
        const val OPEN_MS = 60_000L
        private val LABEL = Regex("""^金额\s*(\d+(?:\.\d{0,2})?)\s*[¥￥]?$""")
        private val BOX = Regex("""^[¥￥]?\s*(\d+(?:\.\d{0,2})?)$""")

        fun cents(raw: String): Long? = raw.trimEnd('.').takeIf { it.isNotEmpty() }?.let {
            runCatching { java.math.BigDecimal(it).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact() }.getOrNull()
        }
    }
}
