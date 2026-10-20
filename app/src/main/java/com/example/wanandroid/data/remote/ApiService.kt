package com.example.wanandroid.data.remote

import com.example.wanandroid.data.model.HotKeyword
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import com.example.wanandroid.data.model.KnowledgeCategory
import retrofit2.http.Query
import com.example.wanandroid.data.model.Website
import com.example.wanandroid.data.model.User
import com.example.wanandroid.data.model.AccountInfo
import com.example.wanandroid.data.model.CollectedPage
import com.example.wanandroid.data.model.CollectProfile
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

    @GET("friend/json")
    suspend fun getWebsites(): ApiResponse<List<Website>>

    @FormUrlEncoded
    @POST("user/login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): ApiResponse<User>

    @GET("lg/coin/userinfo/json")
    suspend fun getAccountInfo(): ApiResponse<AccountInfo>

    @GET("user/logout/json")
    suspend fun logout(): ApiResponse<Any>

    @GET("lg/collect/list/{page}/json")
    suspend fun getMyCollections(
        @Path("page") page: Int = 0
    ): ApiResponse<CollectedPage>

    @GET("user/lg/userinfo/json")
    suspend fun getCollectProfile(): ApiResponse<CollectProfile>

    @POST("lg/collect/{id}/json")
    suspend fun collectArticle(@Path("id") articleId: Int): ApiResponse<Any>

    @POST("lg/uncollect_originId/{id}/json")
    suspend fun uncollectArticle(@Path("id") articleId: Int): ApiResponse<Any>

    @FormUrlEncoded
    @POST("lg/uncollect/{id}/json")
    suspend fun removeCollection(
        @Path("id") collectionId: Int,
        @Field("originId") originId: Int
    ): ApiResponse<Any>
}
