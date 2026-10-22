package com.example.wanandroid.data.model

data class CollectedArticle(
    val id: Int = 0,
    val originId: Int = -1,
    val title: String? = null,
    val author: String? = null,
    val link: String? = null
)

data class CollectedPage(
    val datas: List<CollectedArticle>? = null,
    val over: Boolean = false
)

data class CollectProfile(
    val userInfo: CollectUser? = null
)

data class CollectUser(
    val collectIds: List<Int>? = null
)
