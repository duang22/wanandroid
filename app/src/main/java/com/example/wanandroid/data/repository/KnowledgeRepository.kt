package com.example.wanandroid.data.repository

import com.example.wanandroid.Article
import com.example.wanandroid.data.model.KnowledgeCategory
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException

class KnowledgeRepository {
    suspend fun getCategories(): List<KnowledgeCategory> {
        val response = ApiClient.service.getKnowledgeTree()
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "获取分类失败")
        }
        return response.data.orEmpty()
    }

    suspend fun getArticles(categoryId: Int): List<Article> {
        val response = ApiClient.service.getKnowledgeArticles(page = 0, categoryId = categoryId)
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "获取分类文章失败")
        }
        return response.data?.datas ?: throw ApiException("缺少文章数据")
    }
}
