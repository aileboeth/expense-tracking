package com.aileboeth.expensetracking.capture

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A short on-disk record of when the process started and when the listener / full-auto service connected or
 * went away, plus the last few crashes — kept across process deaths, for the diagnostics report.
 */
object Life {
    private const val KEEP = 40
    private const val CRASH_KEEP = 4
    private val fmt get() = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
    private val lock = Any()

    private fun file(ctx: Context) = File(ctx.filesDir, "life.txt")
    private fun crashFile(ctx: Context) = File(ctx.filesDir, "crashes.txt")

    fun mark(ctx: Context, what: String) {
        runCatching {
            synchronized(lock) {
                val f = file(ctx)
                val old = if (f.exists()) f.readLines() else emptyList()
                f.writeText((old + "${fmt.format(Date())}  $what").takeLast(KEEP).joinToString("\n"))
            }
        }
    }

    /** Newest first. */
    fun lines(ctx: Context): List<String> = runCatching { file(ctx).takeIf { it.exists() }?.readLines()?.reversed() }.getOrNull().orEmpty()

    fun crashes(ctx: Context): String = runCatching { crashFile(ctx).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty().replace("\n\u0000\n", "\n\n")

    /** Writes uncaught exceptions down (then lets the system handle them as before). */
    fun installCrashLog(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val entry = "== ${fmt.format(Date())} 线程 ${t.name}\n" + sw.toString().lines().take(30).joinToString("\n")
                synchronized(lock) {
                    val f = crashFile(app)
                    val old = if (f.exists()) f.readText().split("\n\u0000\n").filter { it.isNotBlank() } else emptyList()
                    f.writeText((listOf(entry) + old).take(CRASH_KEEP).joinToString("\n\u0000\n"))
                }
                mark(app, "崩溃：${e.javaClass.simpleName}")
            }
            previous?.uncaughtException(t, e)
        }
    }
}
