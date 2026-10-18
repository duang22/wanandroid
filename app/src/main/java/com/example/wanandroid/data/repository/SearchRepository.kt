package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.HotKeyword
import com.example.wanandroid.Article
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException

class SearchRepository {
    suspend fun searchArticles(keyword: String): List<Article> {
        val response = ApiClient.service.searchArticles(page = 0, keyword = keyword)
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "搜索失败")
        }
        return response.data?.datas
            ?: throw ApiException("缺少搜索结果")
        //response：完整响应
        //└── data：这一页的数据
        //    └── datas：这一页的文章列表
    }

    suspend fun getHotKeywords(): List<HotKeyword> {
        val response = ApiClient.service.getHotKeywords()
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "获取搜索热词失败")
        }
        return response.data.orEmpty().filter { !it.name.isNullOrBlank() }
    }
}
