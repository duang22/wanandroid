package com.example.wanandroid.data.repository

import com.example.wanandroid.Article
import com.example.wanandroid.data.model.HomeBanner
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException

class HomeRepository {
    suspend fun getHomeArticles(): List<Article> {
        val topResponse = ApiClient.service.getTopArticles()
        if (topResponse.errorCode != 0) {
            val message = topResponse.errorMsg?.takeIf { it.isNotBlank() }
                ?: "置顶文章请求失败"
            throw ApiException(message)
        }
        val topArticles = topResponse.data.orEmpty().map { article ->
            article.copy(isTop = true)
        }

        val response = ApiClient.service.getHomeArticles(page = 0)
        if (response.errorCode != 0) {
            val message = response.errorMsg?.takeIf { it.isNotBlank() }
                ?: "接口返回业务错误：${response.errorCode}"
            throw ApiException(message)
        }
        val articles = response.data?.datas
            ?: throw ApiException("接口响应中缺少文章列表")

        return topArticles + articles
    }
    suspend fun getBanners(): List<HomeBanner> {
        val response = ApiClient.service.getBanners()
        if (response.errorCode != 0) {
            val message = response.errorMsg?.takeIf { it.isNotBlank() }
                ?: "Banner 请求失败"
            throw ApiException(message)
        }
        return response.data.orEmpty()
    }
}
