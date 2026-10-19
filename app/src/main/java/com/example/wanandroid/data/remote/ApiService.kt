package com.example.wanandroid.data.remote

import com.example.wanandroid.data.model.HotKeyword
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import com.example.wanandroid.data.model.KnowledgeCategory
import retrofit2.http.Query
import retrofit2.http.GET
import retrofit2.http.Path
import com.example.wanandroid.Article
import com.example.wanandroid.data.model.HomeBanner
interface ApiService {
    @GET("article/list/{page}/json")
    suspend fun getHomeArticles(
        @Path("page") page: Int
    ): ApiResponse<ArticlePage>
    @GET("banner/json")
    suspend fun getBanners(): ApiResponse<List<HomeBanner>>

    @GET("article/top/json")
    suspend fun getTopArticles(): ApiResponse<List<Article>>

    @FormUrlEncoded
    @POST("article/query/{page}/json")
    suspend fun searchArticles(
        @Path("page") page: Int,
        @Field("k") keyword: String
    ): ApiResponse<ArticlePage>

    @GET("hotkey/json")
    suspend fun getHotKeywords(): ApiResponse<List<HotKeyword>>

    @GET("tree/json")
    suspend fun getKnowledgeTree(): ApiResponse<List<KnowledgeCategory>>
    @GET("article/list/{page}/json")
    suspend fun getKnowledgeArticles(
        @Path("page") page: Int,
        @Query("cid") categoryId: Int
    ): ApiResponse<ArticlePage>
    //从第一个接口（getKnowledgeTree）的 children 中取得子分类 ID，例如 101，传给第二个接口的 cid，得到该子分类的文章
}
