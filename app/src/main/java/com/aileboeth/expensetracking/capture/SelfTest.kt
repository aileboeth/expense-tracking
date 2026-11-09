package com.aileboeth.expensetracking.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import com.aileboeth.expensetracking.R
import kotlinx.coroutines.delay

/**
 * Posts one notification of our own and waits for the listener to see it. That proves the whole
 * chain the payments go through (system -> listener in this process) works right now.
 */
object SelfTest {
    const val EXTRA = "et_selftest"
    private const val CHANNEL = "selftest"
    private const val ID = 4242

    /** Set by [CaptureService] when it sees the test notification. */
    @Volatile var lastSeen = 0L

    sealed interface Result {
        /** Seen after [ms] milliseconds. */
        data class Ok(val ms: Long) : Result
        data object NoListenerPermission : Result
        data object CannotPost : Result
        data object NotSeen : Result
    }

    fun canPost(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    suspend fun run(ctx: Context): Result {
        val app = ctx.applicationContext
        if (!ListenerKeeper.granted(app)) return Result.NoListenerPermission
        val nm = app.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return Result.CannotPost
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "自检", NotificationManager.IMPORTANCE_LOW).apply {
            description = "「自检」时发一条测试通知，几秒后自动消失"
        })
        // Not connected (e.g. after an update on vivo): try to bring it back first.
        if (!CaptureService.connected) {
            ListenerKeeper.force(app)
            repeat(20) { if (!CaptureService.connected) delay(250) }
        }
        val start = System.currentTimeMillis()
        val n = Notification.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_feather)
            .setContentTitle("记账自检")
            .setContentText("这是一条测试通知，马上会自动消失")
            .setTimeoutAfter(15_000)
            .addExtras(Bundle().apply { putBoolean(EXTRA, true) })
            .build()
        return try {
            nm.notify(ID, n)
            var waited = 0
            while (lastSeen < start && waited < 6000) { delay(150); waited += 150 }
            if (lastSeen >= start) Result.Ok(lastSeen - start) else Result.NotSeen
        } catch (_: SecurityException) {
            Result.CannotPost
        } finally {
            delay(800)
            nm.cancel(ID)
        }
    }
}
