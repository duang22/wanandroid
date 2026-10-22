package com.example.wanandroid.data.repository

import com.example.wanandroid.Article
import com.example.wanandroid.data.model.HomeBanner
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiService
import com.example.wanandroid.data.remote.ArticlePage

class HomeRepository(private val service: ApiService = ApiClient.service) {
    suspend fun getTopArticles(): List<Article> {
        val response = service.getTopArticles()
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg?.takeIf { it.isNotBlank() } ?: "置顶文章请求失败")
        }
        return response.data.orEmpty().map { it.copy(isTop = true) }
    }

    suspend fun getHomeArticles(page: Int): ArticlePage {
        val response = service.getHomeArticles(page)
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg?.takeIf { it.isNotBlank() } ?: "文章列表请求失败")
        }
        val result = response.data ?: throw ApiException("接口响应中缺少分页数据")
        if (result.datas == null) throw ApiException("接口响应中缺少文章列表")
        return result
    }

    suspend fun getBanners(): List<HomeBanner> {
        val response = service.getBanners()
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg?.takeIf { it.isNotBlank() } ?: "Banner 请求失败")
        }
        return response.data.orEmpty()
    }
}
