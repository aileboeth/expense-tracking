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
        if (sbn == null || sbn.packageName == packageName) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        try {
            Ingest.post(this, toInput(sbn))
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
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun post(ctx: Context, n: NoticeInput) {
        val app = ctx.applicationContext
        worker.execute {
            try {
                handle(app, n)
            } catch (e: Throwable) {
                runCatching { Repo.log(n.pkg, n.title, n.text, "出错：$e") }
            }
        }
    }

    private fun handle(ctx: Context, n: NoticeInput) {
        Repo.init(ctx)
        val prefs = Prefs(ctx)
        val known = n.pkg in Parser.KNOWN_APPS
        if (!known && !prefs.otherApps) return
        val text = n.bigText.ifBlank { n.text }

        if (n.pkg in prefs.blacklistApps) {
            if (known || Parser.looksLikeMoney(n)) Repo.log(n.pkg, n.title, text, "黑名单 App，不记")
            return
        }

        val result = if (known) Parser.parse(n) else Parser.generic(n)
        when (result) {
            ParseResult.NotPayment -> {
                if (known && Parser.looksLikeMoney(n) && isServiceTitle(n)) Repo.log(n.pkg, n.title, text, "没认出")
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
            }
        }
    }

    /** Only WeChat's payment accounts are worth logging; chats must not end up in the log. */
    private fun isServiceTitle(n: NoticeInput) =
        n.pkg != Parser.WECHAT || n.title.contains("微信支付") || n.title.contains("收款") || n.title.contains("转账")
}
