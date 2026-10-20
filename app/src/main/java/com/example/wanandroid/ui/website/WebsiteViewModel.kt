package com.example.wanandroid.ui.website

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.model.Website
import com.example.wanandroid.data.repository.WebsiteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class WebsiteUiState(
    val websites: List<Website> = emptyList(),
    val isLoading: Boolean = false,
    val message: String = "",
    val canRetry: Boolean = false
)

class WebsiteViewModel : ViewModel() {
    private val repository = WebsiteRepository()
    private val _uiState = MutableLiveData(WebsiteUiState())
    val uiState: LiveData<WebsiteUiState> = _uiState

    init {
        loadWebsites()
    }

    fun loadWebsites() {
        if (_uiState.value?.isLoading == true) return
        _uiState.value = WebsiteUiState(
            isLoading = true,
            message = "正在加载网站…"
        )
        viewModelScope.launch {
            try {
                val websites = repository.getWebsites()
                _uiState.value = WebsiteUiState(
                    websites = websites,
                    message = if (websites.isEmpty()) "暂时没有常用网站"
                        else "点击网站名称打开网页"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = WebsiteUiState(
                    message = "网站加载失败，请重新加载",
                    canRetry = true
                )
            }
        }
    }
}
