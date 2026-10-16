package com.example.wanandroid.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.repository.HomeRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeViewModel : ViewModel() {
    private val repository = HomeRepository()
    private val _uiState = MutableLiveData<HomeUiState>()
    val uiState: LiveData<HomeUiState> = _uiState
    private var requestJob: Job? = null

    init {
        loadArticles()
    }

    fun loadArticles() {
        if (requestJob?.isActive == true) return

        requestJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val articles = repository.getHomeArticles()
                _uiState.value = if (articles.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Success(articles)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HomeViewModel", "loadArticles failed", error)
                val message = when (error) {
                    is IOException -> "网络连接失败，请检查网络后重试"
                    is HttpException -> "服务器响应异常：HTTP ${error.code()}"
                    is ApiException -> error.message ?: "接口返回异常"
                    else -> "加载失败，请稍后重试"
                }
                _uiState.value = HomeUiState.Error(message)
            }
        }
    }
}