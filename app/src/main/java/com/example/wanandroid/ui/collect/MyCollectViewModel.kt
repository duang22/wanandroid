package com.example.wanandroid.ui.collect

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.model.CollectedArticle
import com.example.wanandroid.data.repository.CollectRepository
import com.example.wanandroid.data.repository.LoginRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class MyCollectUiState(
    val articles: List<CollectedArticle> = emptyList(),
    val isLoading: Boolean = false,
    val needsLogin: Boolean = false,
    val message: String = "",
    val isLoadingMore: Boolean = false,
    val isRemoving: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreError: String? = null
)

class MyCollectViewModel(
    private val repository: CollectRepository = CollectRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(MyCollectUiState())
    val uiState: LiveData<MyCollectUiState> = _uiState
    private var nextPage = 0
    private var paginationDirty = false

    fun loadCollections() {
        val state = _uiState.value ?: return
        if (state.isLoading || state.isLoadingMore || state.isRemoving) return
        val pagesToReload = maxOf(1, nextPage)
        _uiState.value = state.copy(isLoading = true, message = "正在加载收藏…", loadMoreError = null)
        viewModelScope.launch {
            try {
                val window = fetchWindow(pagesToReload)
                nextPage = window.nextPage
                paginationDirty = false
                _uiState.value = MyCollectUiState(
                    articles = window.articles, endReached = window.endReached,
                    message = messageFor(window.articles)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                requireLogin()
            } catch (error: Exception) {
                _uiState.value = state.copy(
                    isLoading = false, message = "收藏加载失败，请点击右上角刷新"
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value ?: return
        if (state.isLoading || state.isLoadingMore || state.isRemoving || state.needsLogin) return
        if (paginationDirty) { loadCollections(); return }
        if (state.endReached || state.articles.isEmpty()) return
        val pageNumber = nextPage
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        viewModelScope.launch {
            try {
                val page = repository.getMyCollectionPage(pageNumber)
                val articles = (state.articles + page.datas.orEmpty()).distinctBy { it.id }
                nextPage = pageNumber + 1
                _uiState.value = state.copy(
                    articles = articles, isLoadingMore = false, loadMoreError = null,
                    endReached = page.over || page.datas.orEmpty().isEmpty(),
                    message = messageFor(articles)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                requireLogin()
            } catch (error: Exception) {
                _uiState.value = state.copy(
                    isLoadingMore = false, loadMoreError = "收藏加载失败，请重试"
                )
            }
        }
    }

    fun remove(item: CollectedArticle) {
        val state = _uiState.value ?: return
        if (state.isLoading || state.isLoadingMore || state.isRemoving || state.needsLogin) return
        if (state.articles.none { it.id == item.id }) return
        val pagesToReload = maxOf(1, nextPage)
        _uiState.value = state.copy(isRemoving = true, message = "正在取消收藏…")
        var remaining = state.articles
        var removed = false
        viewModelScope.launch {
            try {
                repository.removeCollection(item)
                removed = true
                paginationDirty = true
                remaining = state.articles.filter { it.id != item.id }
                _uiState.value = state.copy(
                    articles = remaining, isRemoving = true, message = "已取消收藏，正在同步列表…"
                )
                val window = fetchWindow(pagesToReload)
                nextPage = window.nextPage
                paginationDirty = false
                _uiState.value = MyCollectUiState(
                    articles = window.articles, endReached = window.endReached,
                    message = if (window.articles.isEmpty()) "还没有收藏文章" else "已取消收藏，列表已同步"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (login: LoginRequiredException) {
                requireLogin()
            } catch (error: Exception) {
                _uiState.value = if (removed) state.copy(
                    articles = remaining, isRemoving = false,
                    message = "已取消收藏，但同步失败，请刷新列表",
                    loadMoreError = "请重新加载收藏列表"
                ) else state.copy(isRemoving = false, message = "取消收藏失败，请重试")
            }
        }
    }

    private data class Window(
        val articles: List<CollectedArticle>, val nextPage: Int, val endReached: Boolean
    )

    private suspend fun fetchWindow(pageLimit: Int): Window {
        val articles = mutableListOf<CollectedArticle>()
        var pageNumber = 0
        var endReached = false
        while (pageNumber < pageLimit && !endReached) {
            val page = repository.getMyCollectionPage(pageNumber)
            val items = page.datas.orEmpty()
            articles.addAll(items)
            pageNumber += 1
            endReached = page.over || items.isEmpty()
        }
        return Window(articles.distinctBy { it.id }, pageNumber, endReached)
    }

    private fun requireLogin() {
        nextPage = 0
        paginationDirty = false
        _uiState.value = MyCollectUiState(needsLogin = true, message = "请先登录后查看我的收藏")
    }

    private fun messageFor(articles: List<CollectedArticle>): String =
        if (articles.isEmpty()) "还没有收藏文章" else "当前显示 ${articles.size} 篇收藏，点击阅读或取消收藏"
}
