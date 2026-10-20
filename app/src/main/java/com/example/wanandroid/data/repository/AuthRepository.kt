package com.example.wanandroid.data.repository

import com.example.wanandroid.data.model.User
import com.example.wanandroid.data.remote.ApiClient
import com.example.wanandroid.data.remote.ApiException
import kotlinx.coroutines.CancellationException

class AuthRepository {
    suspend fun login(username: String, password: String): User {
        ApiClient.cookieJar.clear()
        try {
            val response = ApiClient.service.login(username, password)
            if (response.errorCode != 0) {
                throw ApiException(response.errorMsg ?: "登录失败")
            }
            return response.data ?: throw ApiException("缺少用户信息")
        } catch (error: Exception) {
            ApiClient.cookieJar.clear()
            throw error
        }
    }

    suspend fun checkLogin(): User? {
        val response = ApiClient.service.getAccountInfo()
        if (response.errorCode == -1001) {
            ApiClient.cookieJar.clear()
            return null
        }
        if (response.errorCode != 0) {
            throw ApiException(response.errorMsg ?: "检查登录状态失败")
        }
        val info = response.data ?: throw ApiException("缺少账号信息")
        return User(id = info.userId, username = info.username)
    }

    suspend fun logout(): Boolean {
        try {
            val response = ApiClient.service.logout()
            return response.errorCode == 0 || response.errorCode == -1001
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            return false
        } finally {
            ApiClient.cookieJar.clear()
        }
    }
}
