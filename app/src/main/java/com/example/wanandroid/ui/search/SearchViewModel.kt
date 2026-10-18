package com.example.wanandroid.ui.search

import com.example.wanandroid.data.model.HotKeyword
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.Article
import com.example.wanandroid.data.repository.SearchRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class SearchUiState(
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val message: String = "输入关键词搜索文章"
)//对象保存搜索页面当前的情况

class SearchViewModel : ViewModel() {
    private val repository = SearchRepository()
    private val _uiState = MutableLiveData(SearchUiState())
    val uiState: LiveData<SearchUiState> = _uiState

    private val _hotKeywords = MutableLiveData<List<HotKeyword>>(emptyList())
    val hotKeywords: LiveData<List<HotKeyword>> = _hotKeywords

    init {
        loadHotKeywords()
    }


    fun search(keyword: String) {
        val query = keyword.trim()
        if (query.isEmpty() || _uiState.value?.isLoading == true) return

        _uiState.value = SearchUiState(isLoading = true, message = "正在搜索…")
        viewModelScope.launch {
            try {
                val articles = repository.searchArticles(query)
                _uiState.value = SearchUiState(
                    articles = articles,
                    message = if (articles.isEmpty()) "没有找到相关文章"
                        else "当前显示 ${articles.size} 篇文章"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = SearchUiState(message = "搜索失败，请再次点击搜索")
            }
        }
    }

    private fun loadHotKeywords() {
        viewModelScope.launch {
            try {
                _hotKeywords.value = repository.getHotKeywords()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("SearchHotKeywords", "loadHotKeywords failed", error)
            }
        }
    }
}
