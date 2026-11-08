package com.aileboeth.expensetracking.parse

import java.math.BigDecimal

/** What the listener read from one notification. Plain data so the parser can be unit-tested on the JVM. */
data class NoticeInput(
    val pkg: String,
    val userId: Int,
    val key: String,
    val title: String,
    val text: String,
    val bigText: String = "",
    val ticker: String = "",
    val whenMs: Long = 0L,
    val postTime: Long = 0L,
) {
    /** Same notification posted again (rebind snapshot, refresh) gives the same signature; a new message does not. */
    val signature: String get() = "$pkg|$userId|$key|${text.trim()}|$whenMs"
}

enum class TxType { EXPENSE, INCOME }

sealed interface ParseResult {
    /**
     * A money movement. [amountCents] null = the notification has no amount (watch-pay 凭证, Alipay 转了1笔钱):
     * it is saved as pending and the user fills the amount in.
     * [needsConfirm] = guessed from an unknown app; never counted until the user confirms.
     */
    data class Tx(
        val type: TxType,
        val amountCents: Long?,
        val channel: String,
        val counterpart: String,
        val summary: String,
        val kind: String,
        val needsConfirm: Boolean = false,
    ) : ParseResult

    /** Payment-related but deliberately not recorded (0.00 免密 hint, marketing from 微信支付 ...). */
    data class Ignored(val reason: String, val summary: String) : ParseResult

    data object NotPayment : ParseResult
}

object Parser {
    const val WECHAT = "com.tencent.mm"
    const val ALIPAY = "com.eg.android.AlipayGphone"
    val KNOWN_APPS = setOf(WECHAT, ALIPAY)

    const val CH_WECHAT = "微信"
    const val CH_ALIPAY = "支付宝"

    private const val AMT = """(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)"""
    private const val YUAN = """[¥￥]\s*"""

    /** WeChat only posts payment news from these service accounts; every other title is a chat. */
    private val WECHAT_TITLES = setOf("微信支付", "微信收款助手", "微信转账")
    private val COUNT_PREFIX = Regex("""^\[\d+条]\s*""")

    private val WX_PAID = Regex("""^(?:已支付|已付款|支付成功|付款成功)\s*$YUAN$AMT""")
    private val WX_INCOME = Regex("""(?:收款到账|已收款|收款|到账|已收到|收到|退款)[^\d¥￥]{0,16}(?:[¥￥]\s*)?$AMT\s*元?""")

    private val ALI_AUTOPAY = Regex("""你在(.+?)有一笔${AMT}元的(?:免密/自动扣款|免密|自动扣款)?支付""")
    private val ALI_EXPENSE = Regex("""一笔${AMT}元的支出""")
    private val ALI_INCOME = Regex("""一笔${AMT}元的(?:收入|退款)""")
    private val ALI_RECEIVED = Regex("""(?:成功收款|已收款|收款|到账|退款)${AMT}元""")
    private val ALI_TRANSFER = Regex("""^(.+?)给你转了(?:\d+|一)笔钱""")

    private val GENERIC_AMOUNT = Regex("""$YUAN$AMT|${AMT}\s*元""")
    private val GENERIC_INCOME = Regex("收入|到账|收款|退款|入账|转入|存入")
    private val GENERIC_EXPENSE = Regex("支付|付款|消费|支出|扣款|转出|扣费")

    fun parse(n: NoticeInput): ParseResult = when (n.pkg) {
        WECHAT -> wechat(n)
        ALIPAY -> alipay(n)
        else -> ParseResult.NotPayment
    }

    /** For apps the user turned on in settings: a rough guess that always needs confirming. */
    fun generic(n: NoticeInput): ParseResult {
        val t = body(n)
        val amount = GENERIC_AMOUNT.find(t) ?: return ParseResult.NotPayment
        val type = when {
            GENERIC_INCOME.containsMatchIn(t) -> TxType.INCOME
            GENERIC_EXPENSE.containsMatchIn(t) -> TxType.EXPENSE
            else -> return ParseResult.NotPayment
        }
        val raw = amount.groupValues[1].ifEmpty { amount.groupValues[2] }
        return ParseResult.Tx(type, cents(raw), n.title.trim().ifEmpty { n.pkg }, "", t, "generic", needsConfirm = true)
    }

    private fun body(n: NoticeInput): String =
        n.bigText.trim().ifEmpty { n.text.trim() }.ifEmpty { n.ticker.trim() }

    private fun wechat(n: NoticeInput): ParseResult {
        val title = n.title.trim()
        if (title !in WECHAT_TITLES) return ParseResult.NotPayment
        // "[2条]微信支付: 已支付¥30.00" -> "已支付¥30.00"
        val t = body(n).replace(COUNT_PREFIX, "")
            .removePrefix("$title:").removePrefix("$title：").trim()

        WX_PAID.find(t)?.let {
            return ParseResult.Tx(TxType.EXPENSE, cents(it.groupValues[1]), CH_WECHAT, "", t, "wechat_paid")
        }
        if (t.contains("支付凭证")) {
            // Paid with the watch: WeChat only says "微信支付凭证", the amount is inside the chat card.
            return ParseResult.Tx(TxType.EXPENSE, null, CH_WECHAT, "", t, "wechat_voucher")
        }
        WX_INCOME.find(t)?.let {
            return ParseResult.Tx(TxType.INCOME, cents(it.groupValues[1]), CH_WECHAT, "", t, "wechat_income")
        }
        if (title == "微信收款助手" || t.contains("收款") || t.contains("到账")) {
            return ParseResult.Tx(TxType.INCOME, null, CH_WECHAT, "", t, "wechat_income_no_amount")
        }
        return ParseResult.Ignored("微信支付的其他消息", t)
    }

    private fun alipay(n: NoticeInput): ParseResult {
        val title = n.title.trim()
        // Drop the advert tail: "你有一笔10.00元的支出，点击领取8个支付宝积分。"
        val t = body(n).substringBefore("，点击").substringBefore(",点击").trim()

        ALI_AUTOPAY.find(t)?.let {
            val amount = cents(it.groupValues[2])
            val merchant = it.groupValues[1].trim()
            if (amount == 0L) return ParseResult.Ignored("0元的免密/自动扣款提示", t)
            return ParseResult.Tx(TxType.EXPENSE, amount, CH_ALIPAY, merchant, t, "alipay_autopay")
        }
        ALI_EXPENSE.find(t)?.let {
            val amount = cents(it.groupValues[1])
            if (amount == 0L) return ParseResult.Ignored("0元的支出提示", t)
            return ParseResult.Tx(TxType.EXPENSE, amount, CH_ALIPAY, "", t, "alipay_expense")
        }
        ALI_INCOME.find(t)?.let {
            return ParseResult.Tx(TxType.INCOME, cents(it.groupValues[1]), CH_ALIPAY, "", t, "alipay_income")
        }
        ALI_TRANSFER.find(t)?.let {
            // "多点科技给你转了1笔钱": Alipay does not say how much.
            return ParseResult.Tx(TxType.INCOME, null, CH_ALIPAY, it.groupValues[1].trim(), t, "alipay_transfer_in")
        }
        if (title.contains("到账") || title.contains("收款")) {
            ALI_RECEIVED.find(t)?.let {
                return ParseResult.Tx(TxType.INCOME, cents(it.groupValues[1]), CH_ALIPAY, "", t, "alipay_received")
            }
            return ParseResult.Tx(TxType.INCOME, null, CH_ALIPAY, "", t, "alipay_received_no_amount")
        }
        if (title == "交易提醒") return ParseResult.Ignored("没认出的交易提醒", t)
        return ParseResult.NotPayment
    }

    /** "1,234.5" -> 123450. */
    fun cents(raw: String): Long =
        BigDecimal(raw.replace(",", "")).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()

    /** Anything in a WeChat/Alipay notification that smells of money, for the "未识别" log. */
    fun looksLikeMoney(n: NoticeInput): Boolean {
        val t = "${n.title} ${body(n)}"
        return GENERIC_AMOUNT.containsMatchIn(t) || t.contains("支付") || t.contains("到账") || t.contains("转账")
    }
}
