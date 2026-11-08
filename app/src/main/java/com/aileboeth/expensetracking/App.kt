package com.aileboeth.expensetracking

import android.app.Application
import com.aileboeth.expensetracking.data.Repo

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}
