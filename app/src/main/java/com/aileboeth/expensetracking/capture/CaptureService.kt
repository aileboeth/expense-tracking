package com.aileboeth.expensetracking.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.Categorizer
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.NoticeInput
import com.aileboeth.expensetracking.parse.ParseResult
import com.aileboeth.expensetracking.parse.Parser
import com.aileboeth.expensetracking.parse.PaymentApps
import java.util.concurrent.Executors

class CaptureService : NotificationListenerService() {

    companion object {
        /** True while the system has this listener bound in the current process. */
        @Volatile var connected = false
            private set
    }

    override fun onListenerConnected() {
        connected = true
        Repo.init(this)
        Prefs(this).listenerConnectedAt = System.currentTimeMillis()
        // Payments posted while we were not connected: the signature check drops ones already recorded.
        try {
            activeNotifications?.forEach { sbn -> if (sbn.packageName in Parser.KNOWN_APPS) Ingest.post(this, toInput(sbn)) }
        } catch (_: Throwable) {
        }
    }

    override fun onListenerDisconnected() {
        connected = false
        try {
            requestRebind(ComponentName(this, CaptureService::class.java))
        } catch (_: Throwable) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName == packageName) {
            if (sbn.notification.extras.getBoolean(SelfTest.EXTRA)) SelfTest.lastSeen = System.currentTimeMillis()
            return
        }
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        try {
            Ingest.post(this, toInput(sbn), sbn.notification.contentIntent)
        } catch (_: Throwable) {
        }
    }

    private fun toInput(sbn: StatusBarNotification): NoticeInput {
        val n = sbn.notification
        val ex = n.extras
        fun cs(key: String) = ex.getCharSequence(key)?.toString().orEmpty()
        return NoticeInput(
            pkg = sbn.packageName,
            userId = sbn.uid / 100000, // vivo app clone (应用分身) runs as user 999
            key = sbn.key,
            title = cs(Notification.EXTRA_TITLE).ifEmpty { cs(Notification.EXTRA_TITLE_BIG) },
            text = cs(Notification.EXTRA_TEXT),
            bigText = cs(Notification.EXTRA_BIG_TEXT),
            ticker = n.tickerText?.toString().orEmpty(),
            whenMs = n.`when`,
            postTime = sbn.postTime,
        )
    }
}

/** Notification -> parsed -> filtered -> saved -> bubble. One worker thread keeps it in order. */
object Ingest {
    /** Shared with [ScreenLink] so screens and notifications are handled one at a time, in order. */
    internal val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** [open] = the notification's tap action; full auto uses it to open the chat that shows a missing amount. */
    fun post(ctx: Context, n: NoticeInput, open: android.app.PendingIntent? = null) {
        val app = ctx.applicationContext
        worker.execute {
            try {
                handle(app, n, open)
            } catch (e: Throwable) {
                runCatching { Repo.log(n.pkg, n.title, n.text, "出错：$e") }
            }
        }
    }

    private fun handle(ctx: Context, n: NoticeInput, open: android.app.PendingIntent?) {
        Repo.init(ctx)
        val prefs = Prefs(ctx)
        val known = n.pkg in Parser.KNOWN_APPS
        // A payment app turned on for this region (Google Wallet, PayPal, PayPay, …): read with the generic parser.
        val payApp = if (known) null else PaymentApps.find(n.pkg)?.takeIf { it.pkg in prefs.payApps() }
        if (!known && payApp == null && !prefs.otherApps) return
        val text = n.bigText.ifBlank { n.text }
        if (known && prefs.screenSamples) {
            val service = if (n.pkg == Parser.WECHAT) isServiceTitle(n)
                else listOf("提醒", "到账", "支付", "交易", "收款", "转账", "账单", "余额", "通知").any { n.title.contains(it) }
            runCatching { ScreenSamples.addNotice(ctx, n.pkg, n.userId, n.title, text, service, System.currentTimeMillis()) }
            // "[转账] 已被接收" and the like: whatever WeChat / Alipay shows next is worth keeping.
            if (listOf("[转账]", "[红包]", "转账", "收款").any { text.contains(it) }) ScreenService.markPayContext()
        }

        if (n.pkg in prefs.blacklistApps) {
            if (known || Parser.looksLikeMoney(n)) Repo.log(n.pkg, n.title, text, "黑名单 App，不记")
            return
        }

        val result = if (known) Parser.parse(n) else Parser.generic(n, payApp)
        when (result) {
            ParseResult.NotPayment -> {
                // Payment apps without real samples yet: log what looked like money so the format can be added.
                if ((known && isServiceTitle(n) || payApp != null) && Parser.looksLikeMoney(n) && Repo.markSeen(n.signature)) Repo.log(n.pkg, n.title, text, "没认出")
                return
            }
            is ParseResult.Ignored -> {
                if (Repo.markSeen(n.signature)) Repo.log(n.pkg, n.title, text, "不记：${result.reason}")
                return
            }
            is ParseResult.Tx -> {
                prefs.blacklistHit(result.counterpart, result.summary, n.title)?.let { word ->
                    if (Repo.markSeen(n.signature)) Repo.log(n.pkg, n.title, text, "黑名单关键词「$word」，不记")
                    return
                }
                if (!Repo.markSeen(n.signature)) return // same message posted again
                val at = if (n.postTime > 0) n.postTime else System.currentTimeMillis()
                // Full auto read this payment off the screen first (paying inside WeChat / Alipay): it is the same one.
                if (result.amountCents != null && !result.needsConfirm) {
                    ScreenLink.alreadyFromScreen(result.type, result.channel, result.amountCents, at)?.let { id ->
                        Repo.log(n.pkg, n.title, text, "屏幕已经记过 #$id")
                        return
                    }
                }
                // "[转账] 已被接收": the transfer was already recorded when it was sent, with the amount typed in.
                if (result.kind == "wechat_transfer_out") {
                    ScreenLink.pairAcceptedTransfer(ctx, result.counterpart, at)?.let { line ->
                        Repo.log(n.pkg, n.title, text, line)
                        return
                    }
                }
                val pending = result.amountCents == null || result.needsConfirm
                val tx = Tx(
                    type = result.type,
                    amountCents = result.amountCents,
                    category = Categorizer.auto(result.type, result.counterpart, result.kind).ifBlank { if (pending) "" else "其他" },
                    channel = result.channel,
                    counterpart = result.counterpart,
                    time = if (n.postTime > 0) n.postTime else System.currentTimeMillis(),
                    status = if (pending) Status.PENDING else Status.OK,
                    source = "auto",
                    kind = result.kind,
                    raw = result.summary,
                )
                val id = Repo.insert(tx)
                Repo.log(n.pkg, n.title, text, if (pending) "待补金额/待确认 #$id" else "已记账 #$id")
                main.post { Bubble.show(ctx, id) }
                // WeChat shows the service no text at all (every page is one empty node, 2026-11-14), so opening it
                // to read an amount only flashes it: auto-fetch is for Alipay only.
                if (result.amountCents == null && n.pkg != Parser.WECHAT) AutoFetch.offer(ctx, id, n.pkg, open)
            }
        }
    }

    /** Only WeChat's payment accounts are worth logging; chats must not end up in the log. */
    private fun isServiceTitle(n: NoticeInput) =
        n.pkg != Parser.WECHAT || n.title.contains("微信支付") || n.title.contains("收款") || n.title.contains("转账")
}
