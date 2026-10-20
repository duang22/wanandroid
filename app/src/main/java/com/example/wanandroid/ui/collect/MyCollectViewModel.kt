package com.example.wanandroid.ui.collect

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.repository.CollectRepository
import com.example.wanandroid.data.repository.LoginRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class MyCollectUiState(
    val articles: List<CollectedArticle> = emptyList(),
    val isLoading: Boolean = false,
    val needsLogin: Boolean = false,
    val message: String = ""
)

class MyCollectViewModel : ViewModel() {
    private val repository = CollectRepository()
    private val _uiState = MutableLiveData(MyCollectUiState())
    val uiState: LiveData<MyCollectUiState> = _uiState

    fun loadCollections() {
        val state = _uiState.value ?: return
        if (state.isLoading) return
        _uiState.value = MyCollectUiState(isLoading = true, message = "正在加载收藏…")
        viewModelScope.launch {
            try {
                val articles = repository.getMyCollections()
                _uiState.value = MyCollectUiState(
                    articles = articles,
                    message = if (articles.isEmpty()) "还没有收藏文章"
                        else "点击文章阅读，或点击按钮取消收藏"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                _uiState.value = MyCollectUiState(
                    needsLogin = true, message = "请先登录后查看我的收藏"
                )
            } catch (error: Exception) {
                _uiState.value = MyCollectUiState(
                    message = "收藏加载失败，请点击右上角刷新"
                )
            }
        }
    }

    fun remove(item: CollectedArticle) {
        val state = _uiState.value ?: return
        if (state.isLoading || state.needsLogin) return
        _uiState.value = state.copy(isLoading = true, message = "正在取消收藏…")
        viewModelScope.launch {
            try {
                repository.removeCollection(item)
                val remaining = state.articles.filter { it.id != item.id }
                _uiState.value = MyCollectUiState(
                    articles = remaining,
                    message = if (remaining.isEmpty()) "还没有收藏文章" else "已取消收藏"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                _uiState.value = MyCollectUiState(
                    needsLogin = true, message = "登录已失效，请重新登录"
                )
            } catch (error: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    message = if (error is ApiException) error.message ?: "取消收藏失败"
                        else "取消收藏失败，请重试"
                )
            }
        }
    }
}
