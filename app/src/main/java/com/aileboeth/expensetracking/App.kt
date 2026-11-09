package com.aileboeth.expensetracking

import android.app.Application
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.widget.Widgets

class App : Application() {
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(com.aileboeth.expensetracking.ui.AppLocale.wrap(base))

    override fun onCreate() {
        super.onCreate()
        com.aileboeth.expensetracking.data.Money.setCurrency(com.aileboeth.expensetracking.data.Prefs(this).currency)
        Repo.init(this)
        Repo.onChanged = { Widgets.scheduleUpdate(this) }
        ListenerKeeper.ensure(this)
        // Receipt files left behind by an editor that was closed by the system before saving.
        Thread { runCatching { Repo.cleanOrphanAttachments(System.currentTimeMillis() - 24L * 3600 * 1000) } }.start()
    }
}
