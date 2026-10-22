package com.example.wanandroid.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadHistoryDao {
    @Query("SELECT * FROM read_history ORDER BY readAt DESC, url ASC")
    fun observeAll(): LiveData<List<ReadRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(record: ReadRecord)

    @Query("DELETE FROM read_history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM read_history")
    suspend fun clear()
}
