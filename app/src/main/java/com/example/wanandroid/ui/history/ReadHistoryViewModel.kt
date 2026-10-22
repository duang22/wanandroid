package com.example.wanandroid.ui.history

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.map
import com.example.wanandroid.data.local.ReadRecord
import com.example.wanandroid.data.repository.ReadHistoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ReadHistoryViewModel(
    private val repository: ReadHistoryRepository = ReadHistoryRepository()
) : ViewModel() {
    val records: LiveData<List<ReadRecord>> = repository.records
    val readUrls: LiveData<Set<String>> = records.map { records ->
        records.map { it.url.trim() }.filter { it.isNotEmpty() }.toSet()
    }
    private val _errorMessage = MutableLiveData("")
    val errorMessage: LiveData<String> = _errorMessage
    private var hasRecorded = false

    fun record(url: String, title: String, articleId: Int) {
        if (hasRecorded) return
        hasRecorded = true
        write("保存阅读记录失败") { repository.save(url, title, articleId) }
    }

    fun delete(record: ReadRecord) {
        write("删除阅读记录失败") { repository.delete(record) }
    }

    fun clear() {
        write("清空阅读记录失败") { repository.clear() }
    }

    fun clearError() {
        _errorMessage.value = ""
    }

    private fun write(errorMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _errorMessage.value = errorMessage
            }
        }
    }
}
