package com.example.wanandroid.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_history")
data class ReadRecord(
    @PrimaryKey val url: String,
    val title: String,
    val readAt: Long,
    val articleId: Int = 0
)
