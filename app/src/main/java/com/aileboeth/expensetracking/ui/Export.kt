package com.aileboeth.expensetracking.ui

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.aileboeth.expensetracking.R
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.parse.TxType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** CSV into Download/记账/ (opens in Excel / WPS; UTF-8 with BOM). */
object Export {
    const val FOLDER = "记账"

    fun csv(ctx: Context): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val sb = StringBuilder("\uFEFF" + ctx.getString(R.string.csv_header) + "\n")
        val counts = Repo.attachmentCounts()
        for (t in Repo.all()) {
            val cols = listOf(
                fmt.format(Date(t.time)),
                ctx.getString(if (t.type == TxType.EXPENSE) R.string.expense else R.string.income),
                t.amountCents?.let { Money.format(it) } ?: "",
                Labels.category(ctx, t.category), t.note, Labels.channel(ctx, t.channel), t.counterpart,
                if (t.amountCents == null) ctx.getString(R.string.csv_pending_amount) else if (t.status.name == "PENDING") ctx.getString(R.string.csv_pending_confirm) else "",
                if (t.source == "auto") Labels.kind(ctx, t.kind) else ctx.getString(R.string.ch_manual),
                counts[t.id]?.let { ctx.getString(R.string.csv_receipts, it) } ?: "",
            )
            sb.append(cols.joinToString(",") { quote(it) }).append('\n')
        }
        val name = "记账导出-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".csv"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建文件")
        ctx.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(sb.toString().toByteArray()) }
        return "下载/$FOLDER/$name"
    }

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
