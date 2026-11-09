package com.aileboeth.expensetracking

import android.app.Application
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.widget.Widgets

class App : Application() {
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(com.aileboeth.expensetracking.ui.AppLocale.wrap(base))

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        val prefs = com.aileboeth.expensetracking.data.Prefs(this)
        // Up to 0.1.x everything was in yuan: an existing ledger stays CNY whatever the phone's region is.
        if (!prefs.hasCurrency && runCatching { Repo.all().isNotEmpty() }.getOrDefault(false)) prefs.currency = "CNY"
        com.aileboeth.expensetracking.data.Money.setCurrency(prefs.currency)
        Repo.onChanged = { Widgets.scheduleUpdate(this) }
        ListenerKeeper.ensure(this)
        // Receipt files left behind by an editor that was closed by the system before saving.
        Thread { runCatching { Repo.cleanOrphanAttachments(System.currentTimeMillis() - 24L * 3600 * 1000) } }.start()
    }
}
