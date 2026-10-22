package com.example.wanandroid.ui.knowledge

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.Article
import com.example.wanandroid.data.model.KnowledgeCategory
import com.example.wanandroid.data.repository.KnowledgeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class KnowledgeUiState(
    val categories: List<KnowledgeCategory> = emptyList(),
    val selectedParentId: Int? = null,
    val selectedChildId: Int? = null,
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = true,
    val message: String = "正在加载分类…",
    val canRetry: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreError: String? = null
)

class KnowledgeViewModel(
    private val repository: KnowledgeRepository = KnowledgeRepository()
) : ViewModel() {
    private val _uiState = MutableLiveData(KnowledgeUiState())
    val uiState: LiveData<KnowledgeUiState> = _uiState
    private var treeJob: Job? = null
    private var articleJob: Job? = null
    private var articleRequestId = 0
    private var nextPage = 0

    init { loadCategories() }

    private fun loadCategories() {
        if (treeJob?.isActive == true) return
        _uiState.value = KnowledgeUiState()
        treeJob = viewModelScope.launch {
            try {
                val categories = repository.getCategories()
                _uiState.value = KnowledgeUiState(
                    categories = categories, isLoading = false, message = "暂时没有分类"
                )
                categories.firstOrNull()?.let { selectParent(it.id) }
                //默认显示第一个 parent 子类
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = KnowledgeUiState(
                    isLoading = false, message = "分类加载失败，请重新加载", canRetry = true
                )
            }
        }
    }

    fun selectParent(parentId: Int) {
        //换一级分类，取出它的 children，默认选第一个子分类
        val state = _uiState.value ?: return
        if (state.selectedParentId == parentId) return
        val parent = state.categories.firstOrNull { it.id == parentId } ?: return
        articleJob?.cancel()
        articleRequestId += 1
        nextPage = 0
        _uiState.value = state.copy(
            selectedParentId = parentId, selectedChildId = null, articles = emptyList(),
            isLoading = false, message = "这个分类暂时没有子分类", canRetry = false,
            isLoadingMore = false, endReached = true, loadMoreError = null
        )
        parent.children.orEmpty().firstOrNull()?.let { selectChild(it.id) }
    }

    fun selectChild(childId: Int) {
        val state = _uiState.value ?: return
        if (state.selectedChildId == childId) return
        val parent = state.categories.firstOrNull { it.id == state.selectedParentId }
        if (parent?.children.orEmpty().none { it.id == childId }) return
        loadArticles(childId)
    }

    private fun loadArticles(childId: Int) {
        articleJob?.cancel()
        val requestId = ++articleRequestId
        nextPage = 0
        val state = _uiState.value ?: return
        _uiState.value = state.copy(
            selectedChildId = childId, articles = emptyList(), isLoading = true,
            message = "正在加载文章…", canRetry = false, isLoadingMore = false,
            endReached = false, loadMoreError = null
        )
        articleJob = viewModelScope.launch {
            try {
                val page = repository.getArticles(childId, page = 0)
                if (requestId != articleRequestId) return@launch
                val articles = page.datas.orEmpty().distinctBy { it.id }
                nextPage = 1
                _uiState.value = _uiState.value?.copy(
                    articles = articles, isLoading = false,
                    endReached = page.over || page.datas.orEmpty().isEmpty(),
                    message = if (articles.isEmpty()) "这个分类暂时没有文章" else "当前显示 ${articles.size} 篇文章"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestId == articleRequestId) _uiState.value = _uiState.value?.copy(
                    isLoading = false, message = "文章加载失败，请重新加载", canRetry = true
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value ?: return
        val childId = state.selectedChildId ?: return
        if (state.isLoading || state.isLoadingMore || state.endReached || state.articles.isEmpty()) return
        val requestId = articleRequestId
        val pageNumber = nextPage
        _uiState.value = state.copy(isLoadingMore = true, loadMoreError = null)
        articleJob = viewModelScope.launch {
            try {
                val page = repository.getArticles(childId, pageNumber)
                if (requestId != articleRequestId) return@launch
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
                if (requestId == articleRequestId) _uiState.value = state.copy(
                    isLoadingMore = false, loadMoreError = "分类文章加载失败，请重试"
                )
            }
        }
    }

    fun retry() {
        val state = _uiState.value ?: return
        if (state.isLoading || state.isLoadingMore) return
        val childId = state.selectedChildId
        if (childId == null) loadCategories() else loadArticles(childId)
    }
}
