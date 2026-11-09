package com.aileboeth.expensetracking.data

import com.aileboeth.expensetracking.parse.TxType
import java.math.BigDecimal

/** OK = counted in totals. PENDING = waits for the user: amount missing, or a guess to confirm. */
enum class Status { OK, PENDING }

data class Tx(
    val id: Long = 0,
    val type: TxType,
    /** null while the amount is unknown (watch pay, Alipay transfer without amount). */
    val amountCents: Long?,
    val category: String = "",
    val note: String = "",
    /** 微信 / 支付宝 / 手动 / other app label. */
    val channel: String = "",
    val counterpart: String = "",
    val time: Long,
    val status: Status = Status.OK,
    /** auto or manual. */
    val source: String = "manual",
    /** Parser kind, e.g. wechat_voucher. */
    val kind: String = "",
    /** The notification text it came from. */
    val raw: String = "",
    /** Order number from an imported bill (empty otherwise); stops importing the same line twice. */
    val ref: String = "",
)

/** User rule: a note / payer containing [keyword] goes to [category]. */
data class Rule(val id: Long = 0, val type: TxType, val keyword: String, val category: String)

/** A receipt photo or invoice file kept with one entry, stored in filesDir/attachments/[file]. */
data class Attachment(
    val id: Long = 0,
    val txId: Long = 0,
    val name: String,
    val mime: String,
    val file: String,
    val size: Long,
    val time: Long = System.currentTimeMillis(),
) {
    val isImage get() = mime.startsWith("image/")
}

data class LogEntry(val id: Long, val time: Long, val pkg: String, val title: String, val text: String, val result: String)

object Money {
    /** 3000 -> "30.00" */
    fun format(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    /** "30" / "30.5" / "30.50" -> cents; null when not a positive amount. */
    fun parse(input: String): Long? {
        val s = input.trim().replace(",", "").removePrefix("¥").removePrefix("￥")
        if (s.isEmpty()) return null
        return try {
            val v = BigDecimal(s)
            if (v.signum() <= 0 || v.scale() > 2) null else v.movePointRight(2).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    /** 2550 -> "25.5", 3000 -> "30" (for short labels). */
    fun short(cents: Long): String = format(cents).let { if (it.contains('.')) it.trimEnd('0').trimEnd('.') else it }

    fun signed(tx: Tx): String {
        val a = tx.amountCents ?: return "?"
        return (if (tx.type == TxType.EXPENSE) "-" else "+") + format(a)
    }
}

object Kinds {
    fun label(kind: String): String = when (kind) {
        "wechat_voucher" -> "手表/凭证付款"
        "wechat_paid" -> "微信付款"
        "wechat_income", "wechat_income_no_amount" -> "微信收款"
        "alipay_expense" -> "支付宝付款"
        "alipay_autopay" -> "支付宝免密支付"
        "alipay_transfer_in" -> "支付宝转账到账"
        "alipay_received", "alipay_received_no_amount", "alipay_income" -> "支付宝收款"
        "generic" -> "其他 App"
        "wechat_bill" -> "微信账单导入"
        "alipay_bill" -> "支付宝账单导入"
        "" -> "手动"
        else -> kind
    }
}
