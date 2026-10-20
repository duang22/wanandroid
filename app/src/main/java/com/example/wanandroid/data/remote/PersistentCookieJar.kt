package com.example.wanandroid.data.remote

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    private val preferences: SharedPreferences,
    private val origin: HttpUrl
) : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    init {
        val saved = preferences.getStringSet("cookies", emptySet()).orEmpty()
        //取出 存着的 cookie
        val now = System.currentTimeMillis()
        cookies.addAll(saved.mapNotNull { Cookie.parse(origin, it) }
            .filter { it.persistent && it.expiresAt > now })
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        //OkHttp 收到带 Cookie 的响应时
        if (url.host != origin.host || !url.isHttps) return
        val now = System.currentTimeMillis()
        this.cookies.removeAll { it.expiresAt <= now }
        cookies.forEach { incoming ->
            this.cookies.removeAll {
                it.name == incoming.name && it.domain == incoming.domain &&
                    it.path == incoming.path
            }
            if (incoming.expiresAt > now) this.cookies.add(incoming)
        }
        val saved = this.cookies.filter { it.persistent }
            .map { it.toString() }.toSet()
        preferences.edit().putStringSet("cookies", saved).apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        //OkHttp 准备发送请求时
        val now = System.currentTimeMillis()
        return cookies.filter { it.expiresAt > now && it.matches(url) }
            .sortedByDescending { it.path.length }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        preferences.edit().remove("cookies").apply()
    }
}
