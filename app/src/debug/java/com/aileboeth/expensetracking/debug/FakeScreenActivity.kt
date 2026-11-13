package com.aileboeth.expensetracking.debug

import android.app.Activity
import android.os.Bundle
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.TextView
import com.aileboeth.expensetracking.capture.ScreenService

/**
 * Debug builds only: a plain screen that poses as a WeChat / Alipay page for the full-auto service,
 * for phones without those apps. Only the shell may open it (protected by DUMP):
 *   am start -n com.aileboeth.expensetracking.debug/com.aileboeth.expensetracking.debug.FakeScreenActivity \
 *     --es as com.tencent.mm --es lines '支付成功|星巴克|¥30.00|完成'
 */
class FakeScreenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show()
    }

    private fun show() {
        val pose = intent.getStringExtra("as") ?: "com.tencent.mm"
        val lines = (intent.getStringExtra("lines") ?: "").split('|')
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 160, 48, 48) }
        (listOf("${ScreenService.POSE}$pose]") + lines).forEach { l ->
            box.addView(TextView(this).apply { text = l; setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f); setPadding(0, 12, 0, 12) })
        }
        setContentView(box)
    }
}
