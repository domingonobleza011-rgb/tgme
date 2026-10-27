package com.tgme.timekeeping

import android.app.Application
import com.tgme.timekeeping.data.AppDatabase

class TgmeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
    }

    companion object {
        lateinit var db: AppDatabase
            private set
    }
}
