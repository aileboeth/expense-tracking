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
        // --es openlines '…|…': the fake notification's tap opens a fake chat with these lines (tests 自动去读金额).
        val open = intent.getStringExtra("openlines")?.let { lines ->
            android.app.PendingIntent.getActivity(
                context, now.toInt(),
                Intent(context, FakeScreenActivity::class.java).putExtra("as", intent.getStringExtra("pkg") ?: "com.tencent.mm").putExtra("lines", lines)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
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
            open,
        )
    }
}
