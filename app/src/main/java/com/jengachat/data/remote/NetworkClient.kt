package com.jengachat.data.remote

import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface RefreshApi {
    @POST("api/auth/refresh")
    fun refreshToken(@Body request: RefreshRequest): Call<RefreshResponse>
}

/**
 * Network module for Talksy API
 */
@Singleton
class NetworkClient @Inject constructor(
    private val tokenManager: TokenManager
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = tokenManager.accessToken
        
        val request = if (token != null) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }
        
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            // If we get 401, try to refresh the token
            if (response.code == 401) {
                val refreshToken = tokenManager.refreshToken ?: return null
                
                try {
                    val refreshResponse = createRefreshApi().refreshToken(
                        RefreshRequest(refreshToken)
                    ).execute()
                    
                    if (refreshResponse.isSuccessful && refreshResponse.body()?.success == true) {
                        val data = refreshResponse.body()?.data
                        if (data != null) {
                            tokenManager.accessToken = data.accessToken
                            if (!data.refreshToken.isNullOrEmpty()) {
                                tokenManager.refreshToken = data.refreshToken
                            }
                            return response.request.newBuilder()
                                .header("Authorization", "Bearer ${data.accessToken}")
                                .build()
                        }
                    }
                    
                    // Refresh failed, clear tokens
                    tokenManager.clearTokens()
                    return null
                } catch (e: Exception) {
                    tokenManager.clearTokens()
                    return null
                }
            }
            return null
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: TalksyApi = retrofit.create(TalksyApi::class.java)

    // Separate client for refresh to avoid circular dependency
    private fun createRefreshApi(): RefreshApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RefreshApi::class.java)
    }

    companion object {
        // Change this to your server URL
        // For local testing with Android Emulator: http://10.0.2.2:3000/
        // For local testing with physical device: http://<your-computer-ip>:3000/
        // For production: https://your-domain.com/
        const val BASE_URL = "https://jengachat-server.onrender.com/"
        
        // WebSocket URL
        const val WS_URL = "wss://jengachat-server.onrender.com/ws"
    }
}
