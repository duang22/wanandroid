package com.example.wanandroid

import android.app.Application
import com.example.wanandroid.data.remote.ApiClient

class WanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
    }
}
