package com.example.wanandroid.ui.home

import com.example.wanandroid.Article


sealed interface HomeUiState {
    object Loading : HomeUiState
    data class Success(val articles: List<Article>) : HomeUiState
    object Empty : HomeUiState
    data class Error(val message: String) : HomeUiState
}