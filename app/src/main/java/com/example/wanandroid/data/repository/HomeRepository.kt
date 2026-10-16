package com.example.wanandroid.data.repository

import com.example.wanandroid.Article
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException

class HomeRepository {
    suspend fun getHomeArticles(): List<Article> {
        val response = ApiClient.service.getHomeArticles(page = 0)
        if (response.errorCode != 0) {
            val message = response.errorMsg?.takeIf { it.isNotBlank() }
                ?: "接口返回业务错误：${response.errorCode}"
            throw ApiException(message)
        }

        return response.data?.datas
            ?: throw ApiException("接口响应中缺少文章列表")
    }
}
