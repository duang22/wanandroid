package com.example.wanandroid.ui.search

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.Article
import com.example.wanandroid.data.model.HotKeyword
import com.example.wanandroid.data.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SearchUiState(
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val message: String = "输入关键词搜索文章",
    val keyword: String = "",
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreError: String? = null,
    val requestVersion: Int = 0
)//对象保存搜索页面当前的情况

class SearchViewModel(private val repository: SearchRepository = SearchRepository()) : ViewModel() {
    private val _uiState = MutableLiveData(SearchUiState())
    val uiState: LiveData<SearchUiState> = _uiState
    private val _hotKeywords = MutableLiveData<List<HotKeyword>>(emptyList())
    val hotKeywords: LiveData<List<HotKeyword>> = _hotKeywords
    private var requestJob: Job? = null
    private var requestVersion = 0
    private var nextPage = 0

    init { loadHotKeywords() }

    fun search(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty()) return
        val state = _uiState.value
        if (state?.keyword == query && state.isLoading) return
        requestJob?.cancel()
        val version = ++requestVersion
        nextPage = 0
        _uiState.value = SearchUiState(
            keyword = query, isLoading = true, message = "正在搜索…", requestVersion = version
        )
        requestJob = viewModelScope.launch {
            try {
                val page = repository.searchArticles(query, page = 0)
                if (version != requestVersion) return@launch
                val articles = page.datas.orEmpty().distinctBy { it.id }
                nextPage = 1
                _uiState.value = SearchUiState(
                    keyword = query, articles = articles, requestVersion = version,
                    endReached = page.over || page.datas.orEmpty().isEmpty(),
                    message = if (articles.isEmpty()) "没有找到相关文章" else "当前显示 ${articles.size} 篇文章"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == requestVersion) _uiState.value = SearchUiState(
                    keyword = query, requestVersion = version, message = "搜索失败，请再次点击搜索"
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value ?: return
        if (state.isLoading || state.isLoadingMore || state.endReached || state.articles.isEmpty()) return
        val version = requestVersion
        val pageNumber = nextPage
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        requestJob = viewModelScope.launch {
            try {
                val page = repository.searchArticles(state.keyword, pageNumber)
                if (version != requestVersion) return@launch
                val articles = (state.articles + page.datas.orEmpty()).distinctBy { it.id }
                nextPage = pageNumber + 1
                _uiState.value = state.copy(
                    articles = articles, isLoadingMore = false, loadMoreError = null,
                    endReached = page.over || page.datas.orEmpty().isEmpty(),
                    message = "当前显示 ${articles.size} 篇文章"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (version == requestVersion) _uiState.value = state.copy(
                    isLoadingMore = false, loadMoreError = "搜索结果加载失败，请重试"
                )
            }
        }
    }

    private fun loadHotKeywords() {
        viewModelScope.launch {
            try { _hotKeywords.value = repository.getHotKeywords() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Log.e("SearchHotKeywords", "loadHotKeywords failed", error) }
        }
    }
}
