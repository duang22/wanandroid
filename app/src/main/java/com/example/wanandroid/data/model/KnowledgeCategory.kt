package com.example.wanandroid.data.model

data class KnowledgeCategory(
    val id: Int = 0,
    val name: String? = null,
    val children: List<KnowledgeCategory>? = null
)
