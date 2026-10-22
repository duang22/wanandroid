package com.example.wanandroid

import android.app.Application
import androidx.room.Room
import com.example.wanandroid.data.local.AppDatabase
import com.example.wanandroid.data.remote.ApiClient

class WanApplication : Application() {
    companion object {
        lateinit var database: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "wanandroid.db"
        ).build()
    }
}
