package com.example.wanandroid.data.remote

import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @GET("article/list/{page}/json")
    suspend fun getHomeArticles(
        @Path("page") page: Int
    ): ApiResponse<ArticlePage>
}