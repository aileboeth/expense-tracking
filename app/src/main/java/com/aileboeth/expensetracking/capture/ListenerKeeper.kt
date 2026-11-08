package com.aileboeth.expensetracking.capture

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/**
 * Keeps the notification listener bound. On vivo (OriginOS 4) the system does NOT rebind it after the
 * app is updated, so payments would be missed until the user toggles the permission. Toggling our own
 * component and asking for a rebind brings it back.
 */
object ListenerKeeper {
    private val main = Handler(Looper.getMainLooper())

    private fun component(ctx: Context) = ComponentName(ctx, CaptureService::class.java)

    fun granted(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(ctx))

    /** After [delayMs], rebind if the permission is on but the listener is not connected in this process. */
    fun ensure(ctx: Context, delayMs: Long = 2500) {
        val app = ctx.applicationContext
        main.postDelayed({
            if (granted(app) && !CaptureService.connected) force(app)
        }, delayMs)
    }

    /** Unconditional rebind (also behind the "重新连接通知监听" button). */
    fun force(ctx: Context) {
        val app = ctx.applicationContext
        val cn = component(app)
        val pm = app.packageManager
        try {
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        } catch (_: Throwable) {
        }
        try {
            NotificationListenerService.requestRebind(cn)
        } catch (_: Throwable) {
        }
    }
}

/** App updated or phone restarted: make sure the listener comes back without opening the app. */
class RebindReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (ListenerKeeper.granted(app) && !CaptureService.connected) ListenerKeeper.force(app)
            } finally {
                pending.finish()
            }
        }, 3000)
    }
}
