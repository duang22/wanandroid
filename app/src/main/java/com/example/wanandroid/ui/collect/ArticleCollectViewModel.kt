package com.example.wanandroid.ui.collect

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.repository.CollectRepository
import com.example.wanandroid.data.repository.LoginRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ArticleCollectUiState(
    val isCollected: Boolean = false,
    val isReady: Boolean = false,
    val isLoading: Boolean = false,
    val needsLogin: Boolean = false,
    val message: String = ""
)

class ArticleCollectViewModel : ViewModel() {
    private val repository = CollectRepository()
    private val _uiState = MutableLiveData(ArticleCollectUiState())
    val uiState: LiveData<ArticleCollectUiState> = _uiState

    fun refresh(articleId: Int) {
        val state = _uiState.value ?: return
        if (articleId <= 0 || state.isLoading) return
        _uiState.value = state.copy(isLoading = true, message = "")
        viewModelScope.launch {
            try {
                val collected = repository.isCollected(articleId)
                _uiState.value = ArticleCollectUiState(
                    isCollected = collected, isReady = true
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                _uiState.value = ArticleCollectUiState(
                    isReady = true, needsLogin = true,
                    message = "登录后可以收藏这篇文章"
                )
            } catch (error: Exception) {
                _uiState.value = ArticleCollectUiState(
                    message = "收藏状态获取失败，请点击右上角重试"
                )
            }
        }
    }

    fun toggle(articleId: Int) {
        val state = _uiState.value ?: return
        if (articleId <= 0 || state.isLoading || !state.isReady || state.needsLogin) return
        _uiState.value = state.copy(isLoading = true, message = "")
        viewModelScope.launch {
            try {
                if (state.isCollected) repository.uncollectArticle(articleId)
                else repository.collectArticle(articleId)
                _uiState.value = state.copy(
                    isCollected = !state.isCollected,
                    isLoading = false,
                    message = if (state.isCollected) "已取消收藏" else "收藏成功"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                _uiState.value = ArticleCollectUiState(
                    isReady = true, needsLogin = true,
                    message = "登录已失效，请重新登录后再操作"
                )
            } catch (error: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    message = if (error is ApiException) error.message ?: "操作失败"
                        else "网络请求失败，请重试"
                )
            }
        }
    }
}
