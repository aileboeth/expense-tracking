package com.aileboeth.expensetracking.ui

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.aileboeth.expensetracking.data.Kinds
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
        val sb = StringBuilder("﻿时间,类型,金额,分类,备注,渠道,对方,状态,来源说明\n")
        for (t in Repo.all()) {
            val cols = listOf(
                fmt.format(Date(t.time)),
                if (t.type == TxType.EXPENSE) "支出" else "收入",
                t.amountCents?.let { Money.format(it) } ?: "",
                t.category, t.note, t.channel, t.counterpart,
                if (t.amountCents == null) "待补金额" else if (t.status.name == "PENDING") "待确认" else "",
                if (t.source == "auto") Kinds.label(t.kind) else "手动",
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
