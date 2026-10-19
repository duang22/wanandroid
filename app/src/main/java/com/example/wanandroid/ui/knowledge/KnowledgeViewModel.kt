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
    val canRetry: Boolean = false
)

class KnowledgeViewModel : ViewModel() {
    private val repository = KnowledgeRepository()
    private val _uiState = MutableLiveData(KnowledgeUiState())
    val uiState: LiveData<KnowledgeUiState> = _uiState
    private var treeJob: Job? = null
    private var articleJob: Job? = null

    init {
        loadCategories()
    }

    private fun loadCategories() {
        if (treeJob?.isActive == true) return
        _uiState.value = KnowledgeUiState()
        treeJob = viewModelScope.launch {
            try {
                val categories = repository.getCategories()
                _uiState.value = KnowledgeUiState(
                    categories = categories,
                    isLoading = false,
                    message = "暂时没有分类"
                )
                categories.firstOrNull()?.let { selectParent(it.id) }
                //默认显示第一个 parent 子类
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = KnowledgeUiState(
                    isLoading = false,
                    message = "分类加载失败，请重新加载",
                    canRetry = true
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
        //已有文章加载任务，就请求取消它。点 a 没加载完就点 b，会取消 a
        _uiState.value = state.copy(
            selectedParentId = parentId,
            selectedChildId = null,
            articles = emptyList(),
            isLoading = false,
            message = "这个分类暂时没有子分类",
            canRetry = false
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
        val state = _uiState.value ?: return
        _uiState.value = state.copy(
            selectedChildId = childId,
            articles = emptyList(),
            isLoading = true,
            message = "正在加载文章…",
            canRetry = false
        )
        articleJob = viewModelScope.launch {
            try {
                val articles = repository.getArticles(childId)
                _uiState.value = _uiState.value?.copy(
                    articles = articles,
                    isLoading = false,
                    message = if (articles.isEmpty()) "这个分类暂时没有文章"
                        else "当前显示 ${articles.size} 篇文章"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = _uiState.value?.copy(
                    isLoading = false,
                    message = "文章加载失败，请重新加载",
                    canRetry = true
                )
            }
        }
    }

    fun retry() {
        val state = _uiState.value ?: return
        if (state.isLoading) return
        val childId = state.selectedChildId
        if (childId == null) loadCategories() else loadArticles(childId)
    }
}
