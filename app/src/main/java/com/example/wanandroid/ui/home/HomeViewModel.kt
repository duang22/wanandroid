package com.example.wanandroid.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.model.HomeBanner
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.repository.HomeRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

class HomeViewModel(
    private val repository: HomeRepository = HomeRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData<HomeUiState>()
    val uiState: LiveData<HomeUiState> = _uiState
    private val _banners = MutableLiveData<List<HomeBanner>>(emptyList())
    val banners: LiveData<List<HomeBanner>> = _banners
    private var nextPage = 0
    private var refreshVersion = 0

    init {
        refresh()
    }

    fun refresh() {
        val previous = _uiState.value as? HomeUiState.Success
        if (_uiState.value is HomeUiState.Loading ||
            previous?.isRefreshing == true || previous?.isLoadingMore == true) return

        _uiState.value = previous?.copy(
            isRefreshing = true, refreshError = null, loadMoreError = null
        ) ?: HomeUiState.Loading
        loadBanners()
        viewModelScope.launch {
            try {
                val topArticles = repository.getTopArticles()
                val page = repository.getHomeArticles(page = 0)
                val normalArticles = page.datas.orEmpty()
                val articles = (topArticles + normalArticles).distinctBy { it.id }
                nextPage = 1
                refreshVersion += 1
                _uiState.value = if (articles.isEmpty()) {
                    HomeUiState.Empty
                } else {
                    HomeUiState.Success(
                        articles = articles,
                        endReached = page.over || normalArticles.isEmpty(),
                        refreshVersion = refreshVersion
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HomeViewModel", "refresh failed", error)
                _uiState.value = previous?.copy(
                    isRefreshing = false, refreshError = errorMessage(error)
                ) ?: HomeUiState.Error(errorMessage(error))
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? HomeUiState.Success ?: return
        if (state.isRefreshing || state.isLoadingMore || state.endReached) return
        val requestedPage = nextPage
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        viewModelScope.launch {
            try {
                val page = repository.getHomeArticles(requestedPage)
                val newArticles = page.datas.orEmpty()
                nextPage = requestedPage + 1
                _uiState.value = state.copy(
                    articles = (state.articles + newArticles).distinctBy { it.id },
                    isLoadingMore = false,
                    loadMoreError = null,
                    endReached = page.over || newArticles.isEmpty()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HomeViewModel", "loadMore failed", error)
                _uiState.value = state.copy(
                    isLoadingMore = false, loadMoreError = errorMessage(error)
                )
            }
        }
    }

    private fun loadBanners() {
        viewModelScope.launch {
            try {
                _banners.value = repository.getBanners()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("HomeBanner", "loadBanners failed", error)
            }
        }
    }

    private fun errorMessage(error: Exception): String = when (error) {
        is IOException -> "网络连接失败，请检查网络后重试"
        is HttpException -> "服务器响应异常：HTTP ${error.code()}"
        is ApiException -> error.message ?: "接口返回异常"
        else -> "加载失败，请稍后重试"
    }
}
