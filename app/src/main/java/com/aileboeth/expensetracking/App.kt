package com.aileboeth.expensetracking

import android.app.Application
import com.aileboeth.expensetracking.capture.ListenerKeeper
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.widget.Widgets

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Repo.onChanged = { Widgets.scheduleUpdate(this) }
        ListenerKeeper.ensure(this)
    }
}
