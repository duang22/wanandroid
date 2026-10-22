package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.HotKeyword
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiService
import com.example.wanandroid.data.remote.ArticlePage

class SearchRepository(private val service: ApiService = ApiClient.service) {
    suspend fun searchArticles(keyword: String, page: Int = 0): ArticlePage {
        val response = service.searchArticles(page = page, keyword = keyword)
        if (response.errorCode != 0) throw ApiException(response.errorMsg ?: "搜索失败")
        val result = response.data ?: throw ApiException("缺少搜索结果")
        if (result.datas == null) throw ApiException("缺少搜索文章列表")
        return result
        // response.data 是这一页；response.data.datas 是这一页的文章列表。
    }

    suspend fun getHotKeywords(): List<HotKeyword> {
        val response = service.getHotKeywords()
        if (response.errorCode != 0) throw ApiException(response.errorMsg ?: "获取搜索热词失败")
        return response.data.orEmpty().filter { !it.name.isNullOrBlank() }
    }
}
