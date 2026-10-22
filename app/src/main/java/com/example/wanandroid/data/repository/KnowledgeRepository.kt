package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.KnowledgeCategory
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiService
import com.example.wanandroid.data.remote.ArticlePage

class KnowledgeRepository(private val service: ApiService = ApiClient.service) {
    suspend fun getCategories(): List<KnowledgeCategory> {
        val response = service.getKnowledgeTree()
        if (response.errorCode != 0) throw ApiException(response.errorMsg ?: "获取分类失败")
        return response.data.orEmpty()
    }

    suspend fun getArticles(categoryId: Int, page: Int = 0): ArticlePage {
        val response = service.getKnowledgeArticles(page = page, categoryId = categoryId)
        if (response.errorCode != 0) throw ApiException(response.errorMsg ?: "获取分类文章失败")
        val result = response.data ?: throw ApiException("缺少文章数据")
        if (result.datas == null) throw ApiException("缺少分类文章列表")
        return result
    }
}
