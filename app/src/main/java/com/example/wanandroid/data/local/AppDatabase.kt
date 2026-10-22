package com.example.wanandroid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ReadRecord::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readHistoryDao(): ReadHistoryDao
}
