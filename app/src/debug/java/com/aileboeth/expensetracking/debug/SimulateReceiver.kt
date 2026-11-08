package com.aileboeth.expensetracking.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aileboeth.expensetracking.capture.Ingest
import com.aileboeth.expensetracking.parse.NoticeInput

/**
 * Debug builds only: feed a fake notification through the same pipeline as a real one, for devices
 * without WeChat / Alipay. Only the shell may send it (protected by DUMP):
 *   am broadcast -a com.aileboeth.expensetracking.SIMULATE -p com.aileboeth.expensetracking.debug \
 *     --es pkg com.tencent.mm --es title 微信支付 --es text 已支付¥30.00 [--ei user 999]
 */
class SimulateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        Ingest.post(
            context,
            NoticeInput(
                pkg = intent.getStringExtra("pkg") ?: "com.tencent.mm",
                userId = intent.getIntExtra("user", 0),
                key = intent.getStringExtra("key") ?: "sim|$now",
                title = intent.getStringExtra("title") ?: "",
                text = intent.getStringExtra("text") ?: "",
                bigText = intent.getStringExtra("big") ?: "",
                whenMs = now,
                postTime = now,
            ),
        )
    }
}
