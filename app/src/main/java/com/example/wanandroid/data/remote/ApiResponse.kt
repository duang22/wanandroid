package com.example.wanandroid.data.remote


import com.example.wanandroid.Article


data class ApiResponse<T>(
    val errorCode: Int = -1,
    val errorMsg: String? = null,
    val data: T? = null
)

data class ArticlePage(
    val datas: List<Article>? = null
)

class ApiException(message: String) : Exception(message)
