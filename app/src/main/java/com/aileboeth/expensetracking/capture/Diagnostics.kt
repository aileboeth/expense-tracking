package com.aileboeth.expensetracking.capture

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aileboeth.expensetracking.data.Prefs
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.Parser
import com.aileboeth.expensetracking.ui.Perms
import com.aileboeth.expensetracking.update.Updater
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A plain-text report for "it doesn't record on my phone": device, system, permissions, listener
 * state and the recent recognition log. Notification texts can hold names and merchants, so they
 * are left out unless [withText]. No amounts of your ledger, no notes.
 */
object Diagnostics {
    private val KNOWN_TITLES = setOf("微信支付", "微信收款助手", "交易提醒", "钱已到账", "收款到账通知", "支付宝", "微信")

    fun report(ctx: Context, withText: Boolean): String {
        Repo.init(ctx)
        val prefs = Prefs(ctx)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
        val (ver, code) = Updater.currentVersion(ctx)
        val guide = Brands.current()
        val sb = StringBuilder()
        fun line(k: String, v: Any?) = sb.append(k).append("：").append(v).append('\n')
        sb.append("记账 诊断信息\n==========\n")
        line("生成时间", fmt.format(Date()))
        line("App 版本", "$ver ($code)")
        line("手机", "${Build.MANUFACTURER} / ${Build.BRAND} / ${Build.MODEL} (${Build.DEVICE})")
        line("系统", "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.DISPLAY}")
        line("识别为", "${guide.brandName(ctx)} · ${guide.system}")
        sb.append('\n')
        line("通知使用权", if (ListenerKeeper.granted(ctx)) "已开启" else "未开启")
        line("监听已连接", if (CaptureService.connected) "是" else "否")
        line("上次连上监听", prefs.listenerConnectedAt.takeIf { it > 0 }?.let { fmt.format(Date(it)) } ?: "从未")
        line("自检通知最近一次收到", SelfTest.lastSeen.takeIf { it > 0 }?.let { fmt.format(Date(it)) } ?: "没做过")
        line("悬浮窗", if (Perms.overlay(ctx)) "已允许" else "未允许")
        line("忽略电池优化", if (Perms.battery(ctx)) "是" else "否")
        line("发通知权限", if (SelfTest.canPost(ctx)) "有" else "没有")
        sb.append('\n')
        line("付款后显示悬浮胶囊", prefs.bubbleEnabled)
        line("也识别其他 App", prefs.otherApps)
        line("黑名单 App", prefs.blacklistApps.size)
        line("黑名单关键词", prefs.blacklistWords.size)
        line("账单条数", Repo.all().size)
        line("待补金额", Repo.pending().size)
        sb.append("\n最近的识别记录（最多 80 条，新的在前）")
        sb.append(if (withText) "：\n" else "，通知原文已隐藏：\n")
        val logs = Repo.logs().take(80)
        if (logs.isEmpty()) sb.append("（没有）\n")
        for (l in logs) {
            val title = if (withText || l.title in KNOWN_TITLES) l.title else "（标题已隐藏）"
            val text = if (withText) l.text else "[${l.text.length} 字]"
            val who = when (l.pkg) { Parser.WECHAT -> "微信"; Parser.ALIPAY -> "支付宝"; else -> l.pkg }
            sb.append(fmt.format(Date(l.time))).append("  ").append(who).append("  ").append(title)
                .append("：").append(text).append("  → ").append(l.result).append('\n')
        }
        return sb.toString()
    }

    private fun fileName() = "记账诊断-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    /** Saves into Download/记账/ and returns the shown path. */
    fun save(ctx: Context, text: String): String {
        val name = fileName()
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/记账")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("无法创建文件")
        ctx.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(text.toByteArray()) }
        return "下载/记账/$name"
    }

    /** Share sheet (WeChat, mail, …) with the report as a .txt file. */
    fun share(ctx: Context, text: String) {
        val dir = File(ctx.cacheDir, "diag").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, fileName()).apply { writeText(text) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "发送诊断信息").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
