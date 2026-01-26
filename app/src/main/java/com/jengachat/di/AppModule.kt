package com.jengachat.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.jengachat.crypto.CryptoManager
import com.jengachat.crypto.SessionManager
import com.jengachat.data.remote.NetworkClient
import com.jengachat.data.remote.TokenManager
import com.jengachat.data.remote.WebSocketClient
import com.jengachat.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "jenga_chat_prefs")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    // ==================== Network Components ====================

    @Provides
    @Singleton
    fun provideTokenManager(@ApplicationContext context: Context): TokenManager {
        return TokenManager(context)
    }

    @Provides
    @Singleton
    fun provideNetworkClient(tokenManager: TokenManager): NetworkClient {
        return NetworkClient(tokenManager)
    }

    @Provides
    @Singleton
    fun provideWebSocketClient(tokenManager: TokenManager): WebSocketClient {
        return WebSocketClient(tokenManager)
    }

    // ==================== Crypto Components ====================

    @Provides
    @Singleton
    fun provideCryptoManager(@ApplicationContext context: Context): CryptoManager {
        return CryptoManager(context)
    }

    @Provides
    @Singleton
    fun provideSessionManager(
        @ApplicationContext context: Context,
        cryptoManager: CryptoManager
    ): SessionManager {
        return SessionManager(context, cryptoManager)
    }

    // ==================== Repositories ====================

    @Provides
    @Singleton
    fun provideAuthRepository(
        networkClient: NetworkClient,
        tokenManager: TokenManager,
        webSocketClient: WebSocketClient,
        cryptoManager: CryptoManager,
        sessionManager: SessionManager
    ): AuthRepository = CustomAuthRepositoryImpl(
        networkClient,
        tokenManager,
        webSocketClient,
        cryptoManager,
        sessionManager
    )

    @Provides
    @Singleton
    fun provideChatRepository(
        networkClient: NetworkClient,
        tokenManager: TokenManager,
        webSocketClient: WebSocketClient,
        cryptoManager: CryptoManager,
        sessionManager: SessionManager
    ): ChatRepository = CustomChatRepositoryImpl(
        networkClient,
        tokenManager,
        webSocketClient,
        cryptoManager,
        sessionManager
    )

    @Provides
    @Singleton
    fun provideUserRepository(
        networkClient: NetworkClient,
        tokenManager: TokenManager
    ): UserRepository = CustomUserRepositoryImpl(
        networkClient,
        tokenManager
    )

    @Provides
    @Singleton
    fun provideCallRepository(
        networkClient: NetworkClient,
        tokenManager: TokenManager,
        webSocketClient: WebSocketClient,
        cryptoManager: CryptoManager
    ): CallRepository = CustomCallRepositoryImpl(
        networkClient,
        tokenManager,
        webSocketClient,
        cryptoManager
    )
}
