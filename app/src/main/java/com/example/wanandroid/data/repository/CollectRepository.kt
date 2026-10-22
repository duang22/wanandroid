package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.data.model.CollectedPage
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.remote.ApiResponse
import com.example.wanandroid.data.remote.ApiService

class LoginRequiredException : Exception("请先登录后使用收藏")

class CollectRepository(
    private val service: ApiService = ApiClient.service
) {
    suspend fun getMyCollections(): List<CollectedArticle> =
        getMyCollectionPage(page = 0).datas.orEmpty()

    suspend fun getMyCollectionPage(page: Int): CollectedPage {
        val result = checked(service.getMyCollections(page))
            ?: throw ApiException("缺少收藏列表")
        if (result.datas == null) throw ApiException("缺少收藏文章列表")
        return result
    }

    suspend fun isCollected(articleId: Int): Boolean {
        val profile = checked(service.getCollectProfile())
            ?: throw ApiException("缺少用户信息")
        val user = profile.userInfo ?: throw ApiException("缺少用户信息")
        return articleId in user.collectIds.orEmpty()
    }

    suspend fun collectArticle(articleId: Int) {
        checked(service.collectArticle(articleId))
    }

    suspend fun uncollectArticle(articleId: Int) {
        checked(service.uncollectArticle(articleId))
    }

    suspend fun removeCollection(item: CollectedArticle) {
        checked(service.removeCollection(item.id, item.originId))
    }

    private fun <T> checked(response: ApiResponse<T>): T? {
        if (response.errorCode == -1001) throw LoginRequiredException()
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "收藏请求失败")
        }
        return response.data
    }
}
