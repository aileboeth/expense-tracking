package com.aileboeth.expensetracking.capture

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen samples (off by default): when switched on, WeChat / Alipay screens that show money are kept
 * so the reading rules can be fixed from what the phone really shows. Chats are private, so a line is
 * only kept when it has a digit or a payment word; every other line becomes "·". At most [MAX]
 * screens, only in this app's own storage, cleared when the switch goes off or after 3 days.
 */
object ScreenSamples {
    private const val MAX = 120
    private const val KEEP_MS = 3 * 24 * 3600_000L
    private val WORDS = listOf(
        "支付", "付款", "收款", "转账", "到账", "红包", "零钱", "余额", "账单", "金额", "凭证", "完成", "成功", "收入", "支出",
        "商户", "收款方", "付款方", "交易", "时间", "退款", "已存入", "确认", "¥", "￥", "元",
    )
    private var last = ""

    private fun file(ctx: Context) = File(ctx.filesDir, "screen-samples.txt")

    fun keep(line: String): String =
        if (line.any { it.isDigit() } || WORDS.any { line.contains(it) }) line.take(80) else "·"

    fun add(ctx: Context, pkg: String, lines: List<String>, now: Long) {
        val kept = lines.map(::keep)
        val body = kept.joinToString(" | ")
        if (body == last) return
        last = body
        val f = file(ctx)
        val who = if (pkg == com.aileboeth.expensetracking.parse.Parser.WECHAT) "微信" else if (pkg == com.aileboeth.expensetracking.parse.Parser.ALIPAY) "支付宝" else pkg
        val entry = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(now)) + "\t" + now + "\t" + who + "\t" + body
        val old = if (f.exists()) f.readLines() else emptyList()
        val fresh = old.filter { l -> l.split('\t').getOrNull(1)?.toLongOrNull()?.let { now - it < KEEP_MS } == true }
        f.writeText((fresh + entry).takeLast(MAX).joinToString("\n") + "\n")
    }

    fun count(ctx: Context): Int = file(ctx).takeIf { it.exists() }?.readLines()?.count { it.isNotBlank() } ?: 0

    fun clear(ctx: Context) {
        file(ctx).delete(); last = ""
    }

    /** The text for the diagnostics report / share sheet. */
    fun text(ctx: Context): String {
        val f = file(ctx)
        if (!f.exists()) return "（没有屏幕样本）\n"
        return f.readLines().filter { it.isNotBlank() }.joinToString("\n") { l ->
            val p = l.split('\t')
            if (p.size >= 4) "${p[0]}  ${p[2]}\n    ${p[3]}" else l
        } + "\n"
    }
}
