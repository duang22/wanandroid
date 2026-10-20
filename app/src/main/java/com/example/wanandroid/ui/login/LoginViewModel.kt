package com.example.wanandroid.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wanandroid.data.model.User
import com.example.wanandroid.data.remote.ApiException
import com.example.wanandroid.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class LoginUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val message: String = "请输入账号和密码"
)

class LoginViewModel : ViewModel() {
    private val repository = AuthRepository()
    private val _uiState = MutableLiveData(LoginUiState())
    val uiState: LiveData<LoginUiState> = _uiState

    init {
        checkLogin()
    }

    fun login(username: String, password: String) {
        val state = _uiState.value ?: return
        if (state.isLoading) return
        val name = username.trim()
        if (name.isEmpty() || password.isEmpty()) {
            _uiState.value = state.copy(message = "账号和密码不能为空")
            return
        }
        _uiState.value = state.copy(isLoading = true, message = "正在登录…")
        viewModelScope.launch {
            try {
                val user = repository.login(name, password)
                _uiState.value = LoginUiState(user = user, message = "登录成功")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = if (error is ApiException) error.message ?: "登录失败"
                    else "网络请求失败，请再次登录"
                _uiState.value = LoginUiState(message = message)
            }
        }
    }

    fun checkLogin() {
        val state = _uiState.value ?: return
        if (state.isLoading) return
        _uiState.value = state.copy(isLoading = true, message = "正在检查登录状态…")
        viewModelScope.launch {
            try {
                val user = repository.checkLogin()
                _uiState.value = LoginUiState(
                    user = user,
                    message = if (user == null) "尚未登录或登录已过期" else "登录状态有效"
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.value = state.copy(message = "暂时无法检查登录状态，请重试")
            }
        }
    }

    fun logout() {
        val state = _uiState.value ?: return
        if (state.isLoading) return
        _uiState.value = state.copy(isLoading = true, message = "正在退出…")
        viewModelScope.launch {
            val serverConfirmed = repository.logout()
            _uiState.value = LoginUiState(
                message = if (serverConfirmed) "已退出登录"
                    else "已清除本机登录信息，服务端退出请求未确认"
            )
        }
    }
}
