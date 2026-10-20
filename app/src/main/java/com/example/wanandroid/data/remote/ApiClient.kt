package com.example.wanandroid.data.remote

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    lateinit var cookieJar: PersistentCookieJar
        private set

    fun init(context: Context) {
        if (::cookieJar.isInitialized) return
        val preferences = context.applicationContext.getSharedPreferences(
            "wan_session", Context.MODE_PRIVATE
        )
        cookieJar = PersistentCookieJar(
            preferences, "https://www.wanandroid.com/".toHttpUrl()
        )
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .build()
    }

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.wanandroid.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}