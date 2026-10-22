package com.example.wanandroid.ui.home

import com.example.wanandroid.Article

sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(
        val articles: List<Article>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val refreshError: String? = null,
        val loadMoreError: String? = null,
        val refreshVersion: Int = 0
    ) : HomeUiState
    object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
}
