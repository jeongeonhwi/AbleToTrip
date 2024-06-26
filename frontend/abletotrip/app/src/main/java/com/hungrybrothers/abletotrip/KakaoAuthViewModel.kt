package com.hungrybrothers.abletotrip

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hungrybrothers.abletotrip.ui.network.KtorClient
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.kakao.sdk.user.model.User
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import java.util.Date

class KakaoAuthViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val TAG = "KakaoAuthViewModel"
        const val PREFS_NAME = "KakaoAuthPrefs"
        const val TOKEN_KEY = "KakaoAuthToken"
        const val ACCESS_TOKEN_EXPIRES_AT_KEY = "AccessTokenExpiresAt"
        const val REFRESH_TOKEN_KEY = "RefreshToken"
        const val REFRESH_TOKEN_EXPIRES_AT_KEY = "RefreshTokenExpiresAt"
    }

    private val context = application.applicationContext
    private val _loggedIn = MutableLiveData<Boolean>()
    val loggedIn: LiveData<Boolean> = _loggedIn

    private val _loginResult = MutableLiveData<HttpStatusCode?>()
    val loginResult: LiveData<HttpStatusCode?> = _loginResult

    private val masterKey =
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val encryptedPrefs =
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    init {
        attemptAutoLogin()
    }

    fun setLoggedIn(loggedIn: Boolean) {
        _loggedIn.value = loggedIn
    }

    fun fetchKakaoUserInfo(token: OAuthToken) {
        UserApiClient.instance.me { user: User?, error: Throwable? ->
            if (error != null) {
                Log.e(TAG, "카카오 사용자 정보 요청 실패", error)
            } else if (user != null) {
                Log.i(TAG, "카카오 사용자 정보 요청 성공")
                val email = user.kakaoAccount?.email ?: ""
                Log.d(TAG, "이메일 $email")
                sendTokenAndEmailToServer(email)
            }
        }
    }

    private fun saveToken(token: OAuthToken) {
        encryptedPrefs.edit()
            .putString(TOKEN_KEY, token.accessToken)
            .putLong(ACCESS_TOKEN_EXPIRES_AT_KEY, token.accessTokenExpiresAt?.time ?: 0L)
            .putString(REFRESH_TOKEN_KEY, token.refreshToken)
            .putLong(REFRESH_TOKEN_EXPIRES_AT_KEY, token.refreshTokenExpiresAt?.time ?: 0L)
            .apply()
    }

    private fun loadToken(): OAuthToken? {
        val accessToken = encryptedPrefs.getString(TOKEN_KEY, null) ?: return null
        val accessTokenExpiresAt = Date(encryptedPrefs.getLong(ACCESS_TOKEN_EXPIRES_AT_KEY, 0L))
        val refreshToken = encryptedPrefs.getString(REFRESH_TOKEN_KEY, null) ?: ""
        val refreshTokenExpiresAt = Date(encryptedPrefs.getLong(REFRESH_TOKEN_EXPIRES_AT_KEY, 0L))
        return OAuthToken(
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessTokenExpiresAt,
            refreshTokenExpiresAt = refreshTokenExpiresAt,
        )
    }

    private fun attemptAutoLogin() {
        val token = loadToken()
        if (token != null) {
            KtorClient.authToken = token.accessToken
            setLoggedIn(true)
            fetchKakaoUserInfo(token)
        } else {
            setLoggedIn(false)
        }
    }

    fun handleKakaoLogin() {
        // 카카오계정으로 로그인 공통 callback 구성
        val callback: (OAuthToken?, Throwable?) -> Unit = { token, error ->
            if (error != null) {
                Log.e(TAG, "카카오계정으로 로그인 실패", error)
            } else if (token != null) {
                Log.i(TAG, "카카오계정으로 로그인 성공 ${token.accessToken}")
                KtorClient.authToken = "${token.accessToken}"
                saveToken(token) // 로그인 성공 시 토큰 저장
                setLoggedIn(true)
                fetchKakaoUserInfo(token)
            }
        }

        if (UserApiClient.instance.isKakaoTalkLoginAvailable(context)) {
            UserApiClient.instance.loginWithKakaoTalk(context) { token, error ->
                if (error != null) {
                    Log.e(TAG, "카카오톡으로 로그인 실패", error)

                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        return@loginWithKakaoTalk
                    }

                    UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
                } else if (token != null) {
                    Log.i(TAG, "카카오톡으로 로그인 성공 ${token.accessToken}")
                    KtorClient.authToken = "${token.accessToken}"
                    saveToken(token)
                    setLoggedIn(true)
                }
            }
        } else {
            UserApiClient.instance.loginWithKakaoAccount(context, callback = callback)
        }
    }

    private fun sendTokenAndEmailToServer(email: String) {
        viewModelScope.launch {
            try {
                val response =
                    KtorClient.client.post("member/signin/") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            mapOf(
                                "email" to email,
                            ),
                        )
                    }
                _loginResult.postValue(response.status)
            } catch (e: Exception) {
                Log.e(TAG, "서버로 토큰과 이메일 전송 중 오류 발생: ${e.message}")
            }
        }
    }

    fun clearAuthData() {
        encryptedPrefs.edit()
            .remove(TOKEN_KEY)
            .remove(ACCESS_TOKEN_EXPIRES_AT_KEY)
            .remove(REFRESH_TOKEN_KEY)
            .remove(REFRESH_TOKEN_EXPIRES_AT_KEY)
            .apply()
        _loggedIn.postValue(false)
        _loginResult.postValue(null)
    }
}
