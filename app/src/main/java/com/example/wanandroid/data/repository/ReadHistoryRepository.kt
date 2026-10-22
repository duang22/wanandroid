package com.example.wanandroid.data.repository

import com.example.wanandroid.WanApplication
import com.example.wanandroid.data.local.ReadHistoryDao
import com.example.wanandroid.data.local.ReadRecord

class ReadHistoryRepository(
    private val dao: ReadHistoryDao = WanApplication.database.readHistoryDao()
) {
    val records = dao.observeAll()

    suspend fun save(url: String, title: String, articleId: Int) {
        dao.save(
            ReadRecord(
                url = url,
                title = title.ifBlank { url },
                readAt = System.currentTimeMillis(),
                articleId = articleId
            )
        )
    }

    suspend fun delete(record: ReadRecord) {
        dao.delete(record.url)
    }

    suspend fun clear() {
        dao.clear()
    }
}
