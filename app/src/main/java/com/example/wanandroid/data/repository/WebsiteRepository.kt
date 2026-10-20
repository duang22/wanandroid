package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.Website
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException

class WebsiteRepository {
    suspend fun getWebsites(): List<Website> {
        val response = ApiClient.service.getWebsites()
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "获取常用网站失败")
        }
        return response.data.orEmpty().filter {
            !it.name.isNullOrBlank() && !it.link.isNullOrBlank()
        }
    }
}
