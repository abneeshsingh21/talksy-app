package com.jengachat.data.remote

import retrofit2.Response
import retrofit2.http.*

/**
 * Talksy REST API Interface
 * All endpoints for the custom E2EE server
 */
interface TalksyApi {

    // ==================== AUTH ====================

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(): Response<ApiResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): Response<RefreshResponse>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: Map<String, String>): Response<ApiResponse>

    // ==================== USERS ====================

    @GET("api/users/me")
    suspend fun getMe(): Response<UserResponse>

    @PUT("api/users/me")
    suspend fun updateProfile(@Body request: Map<String, Any?>): Response<UserResponse>

    @PUT("api/users/presence")
    suspend fun updatePresence(@Body request: Map<String, String>): Response<ApiResponse>

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<UsersResponse>

    @GET("api/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<UserResponse>

    @POST("api/users/{userId}/block")
    suspend fun blockUser(@Path("userId") userId: String): Response<ApiResponse>

    @DELETE("api/users/{userId}/block")
    suspend fun unblockUser(@Path("userId") userId: String): Response<ApiResponse>

    @POST("api/users/fcm-token")
    suspend fun registerFcmToken(@Body request: Map<String, String>): Response<ApiResponse>

    // ==================== KEYS (Signal Protocol) ====================

    @POST("api/keys")
    suspend fun uploadKeys(@Body request: UploadKeysRequest): Response<ApiResponse>

    @GET("api/keys/{userId}")
    suspend fun getUserKeys(@Path("userId") userId: String): Response<KeyBundleResponse>

    @GET("api/keys/prekey/count")
    suspend fun getPreKeyCount(): Response<ApiResponse>

    // ==================== CONVERSATIONS ====================

    @POST("api/conversations")
    suspend fun createConversation(@Body request: CreateConversationRequest): Response<ConversationResponse>

    @GET("api/conversations")
    suspend fun getConversations(): Response<ConversationsResponse>

    @GET("api/conversations/{conversationId}")
    suspend fun getConversation(@Path("conversationId") conversationId: String): Response<ConversationResponse>

    @PUT("api/conversations/{conversationId}")
    suspend fun updateConversation(
        @Path("conversationId") conversationId: String,
        @Body request: Map<String, Any?>
    ): Response<ConversationResponse>

    @POST("api/conversations/{conversationId}/participants")
    suspend fun addParticipants(
        @Path("conversationId") conversationId: String,
        @Body request: Map<String, List<String>>
    ): Response<ApiResponse>

    @DELETE("api/conversations/{conversationId}/participants/{userId}")
    suspend fun removeParticipant(
        @Path("conversationId") conversationId: String,
        @Path("userId") userId: String
    ): Response<ApiResponse>

    @DELETE("api/conversations/{conversationId}/leave")
    suspend fun leaveConversation(@Path("conversationId") conversationId: String): Response<ApiResponse>

    // ==================== MESSAGES ====================

    @POST("api/messages")
    suspend fun sendMessage(@Body request: SendMessageRequest): Response<MessageResponse>

    @GET("api/messages/{conversationId}")
    suspend fun getMessages(
        @Path("conversationId") conversationId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): Response<MessagesResponse>

    @POST("api/messages/{messageId}/read")
    suspend fun markMessageRead(@Path("messageId") messageId: String): Response<ApiResponse>

    @DELETE("api/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("messageId") messageId: String,
        @Query("forEveryone") forEveryone: Boolean = false
    ): Response<ApiResponse>


    // ==================== CALLS ====================

    @POST("api/calls")
    suspend fun initiateCall(@Body request: InitiateCallRequest): Response<CallResponse>

    @GET("api/calls/{callId}")
    suspend fun getCall(@Path("callId") callId: String): Response<CallResponse>

    @POST("api/calls/{callId}/join")
    suspend fun joinCall(@Path("callId") callId: String): Response<CallResponse>

    @POST("api/calls/{callId}/leave")
    suspend fun leaveCall(@Path("callId") callId: String): Response<ApiResponse>

    @PUT("api/calls/{callId}/status")
    suspend fun updateCallStatus(
        @Path("callId") callId: String,
        @Body request: Map<String, Any>
    ): Response<ApiResponse>

    @POST("api/calls/{callId}/end")
    suspend fun endCall(
        @Path("callId") callId: String,
        @Body request: Map<String, String>? = null
    ): Response<CallResponse>

    @POST("api/calls/{callId}/participants")
    suspend fun addCallParticipant(
        @Path("callId") callId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse>

    @GET("api/calls/history")
    suspend fun getCallHistory(): Response<CallsResponse>

    // ==================== CONTACTS SYNC ====================

    @POST("api/users/lookup/emails")
    suspend fun lookupUsersByEmails(@Body request: EmailLookupRequest): Response<UsersResponse>

    @POST("api/users/lookup/phones")
    suspend fun lookupUsersByPhones(@Body request: PhoneLookupRequest): Response<UsersResponse>

    // ==================== REACTIONS ====================

    @POST("api/messages/{messageId}/reactions")
    suspend fun addMessageReaction(
        @Path("messageId") messageId: String,
        @Body request: Map<String, String>
    ): Response<ApiResponse>

    @DELETE("api/messages/{messageId}/reactions")
    suspend fun removeMessageReaction(@Path("messageId") messageId: String): Response<ApiResponse>

    @GET("api/messages/{messageId}/reactions")
    suspend fun getMessageReactions(@Path("messageId") messageId: String): Response<ApiResponse>

    // ==================== STARRED MESSAGES ====================

    @POST("api/messages/{messageId}/star")
    suspend fun toggleStar(
        @Path("messageId") messageId: String,
        @Body request: Map<String, Boolean>
    ): Response<ApiResponse>

    @GET("api/messages/starred/all")
    suspend fun getStarredMessages(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): Response<MessagesResponse>

    // ==================== SEARCH & FORWARD ====================

    @GET("api/messages/{conversationId}/search")
    suspend fun searchMessages(
        @Path("conversationId") conversationId: String,
        @Query("type") type: String? = null,
        @Query("senderId") senderId: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<MessagesResponse>

    @POST("api/messages/{messageId}/forward")
    suspend fun forwardMessage(
        @Path("messageId") messageId: String,
        @Body request: Map<String, List<String>>
    ): Response<ApiResponse>
}
