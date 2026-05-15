package com.hitboxd.app.data.network

import com.hitboxd.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ─── AUTH (/api/auth) ────────────────────────────────
    // Las cookies token/refreshToken son manejadas por PersistentCookieJar
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("auth/logout")
    suspend fun logout(): Response<MessageResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(): Response<MessageResponse>

    // ─── USERS (/api/users) ──────────────────────────────
    @GET("users/me")
    suspend fun getMyProfile(): Response<User>

    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): Response<List<User>>

    @GET("users/follow/{id}/check")
    suspend fun checkFollow(@Path("id") userId: Int): Response<FollowCheckResponse>

    @GET("users/{id}/followers")
    suspend fun getFollowers(@Path("id") userId: Int): Response<List<Follow>>

    @GET("users/{id}/following")
    suspend fun getFollowing(@Path("id") userId: Int): Response<List<Follow>>

    @PUT("users/profile")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): Response<User>

    @PUT("users/softdelete")
    suspend fun softDeleteUser(): Response<MessageResponse>

    @PUT("users/active")
    suspend fun activateUser(): Response<MessageResponse>

    @GET("users/suggestions")
    suspend fun getSuggestions(@Query("limit") limit: Int = 6): Response<List<User>>

    @GET("users/count")
    suspend fun getUserCount(): Response<UserCountResponse>

    @POST("users/follow/{id}")
    suspend fun followUser(@Path("id") userId: Int): Response<MessageResponse>

    @DELETE("users/follow/{id}")
    suspend fun unfollowUser(@Path("id") userId: Int): Response<MessageResponse>

    @GET("users/{username}")
    suspend fun getUserByUsername(@Path("username") username: String): Response<User>

    // ─── GAMES (/api/games) ──────────────────────────────
    @GET("games/trending")
    suspend fun getTrendingGames(): Response<List<Game>>

    @GET("games/new")
    suspend fun getNewReleases(): Response<List<Game>>

    @GET("games/search")
    suspend fun searchGames(@Query("q") query: String): Response<List<Game>>

    @GET("games/popular")
    suspend fun getPopularGames(
        @Query("limit") limit: Int = 20,
        @Query("genre") genre: String? = null
    ): Response<List<Game>>

    @GET("games/random")
    suspend fun getRandomGame(): Response<Game>

    @GET("games/search-page")
    suspend fun searchGamesPaginated(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24
    ): Response<SearchPageResponse>

    @GET("games/recommended")
    suspend fun getRecommended(@Query("limit") limit: Int = 20): Response<List<Game>>

    @GET("games/slug/{slug}")
    suspend fun getGameBySlug(@Path("slug") slug: String): Response<Game>

    @GET("games/{id}")
    suspend fun getGameById(@Path("id") id: Int): Response<Game>

    @GET("games/{id}/extras")
    suspend fun getGameExtras(@Path("id") id: Int): Response<GameExtrasResponse>

    @GET("games/{id}/stats")
    suspend fun getGameStats(@Path("id") id: Int): Response<GameStatsResponse>

    // ─── ACTIVITY (/api/activity) ────────────────────────
    @POST("activity")
    suspend fun logActivity(@Body body: ActivityRequest): Response<MessageResponse>

    @GET("activity/watchlist")
    suspend fun getWatchlist(): Response<List<Game>>

    @GET("activity/streak")
    suspend fun getStreak(): Response<StreakResponse>

    @GET("activity/stats")
    suspend fun getUserStats(): Response<UserStatsResponse>

    @GET("activity/check/{gameId}")
    suspend fun checkStatus(@Path("gameId") gameId: Int): Response<ActivityStatus>

    @GET("activity/feed")
    suspend fun getFeed(): Response<List<Activity>>

    @GET("activity/all")
    suspend fun getUserLibrary(): Response<List<Activity>>

    // ─── REVIEWS (/api/reviews) ──────────────────────────
    @POST("reviews")
    suspend fun addReview(@Body body: ReviewRequest): Response<MessageResponse>

    @GET("reviews/recent")
    suspend fun getRecentReviews(@Query("limit") limit: Int = 3): Response<List<Review>>

    @GET("reviews/reported")
    suspend fun getReportedReviews(): Response<List<Review>>

    @GET("reviews/game/{gameId}")
    suspend fun getGameReviews(@Path("gameId") gameId: Int): Response<List<Review>>

    @GET("reviews/user/{userId}")
    suspend fun getUserReviews(@Path("userId") userId: Int): Response<List<Review>>

    @DELETE("reviews/{reviewId}")
    suspend fun removeReview(@Path("reviewId") reviewId: Int): Response<MessageResponse>

    @POST("reviews/{reviewId}/like")
    suspend fun toggleReviewLike(@Path("reviewId") reviewId: Int): Response<MessageResponse>

    @POST("reviews/{reviewId}/report")
    suspend fun reportReview(
        @Path("reviewId") reviewId: Int,
        @Body body: ReportRequest
    ): Response<MessageResponse>

    @PUT("reviews/{reviewId}/approve")
    suspend fun approveReview(@Path("reviewId") reviewId: Int): Response<MessageResponse>

    // ─── LISTS (/api/lists) ──────────────────────────────
    // ⚠️ No existe PUT para editar lista ni DELETE para item individual
    @POST("lists")
    suspend fun createList(@Body body: ListRequest): Response<CreateResponse>

    @GET("lists/user/{userId}")
    suspend fun getUserLists(@Path("userId") userId: Int): Response<List<UserList>>

    @GET("lists/{listId}")
    suspend fun getListDetail(@Path("listId") listId: Int): Response<UserList>

    @POST("lists/{listId}/games")
    suspend fun addGameToList(
        @Path("listId") listId: Int,
        @Body body: AddGameToListRequest
    ): Response<MessageResponse>

    @DELETE("lists/{listId}")
    suspend fun deleteList(@Path("listId") listId: Int): Response<MessageResponse>

    // ─── NOTIFICATIONS (/api/notifications) ─────────────
    @GET("notifications")
    suspend fun getNotifications(): Response<NotificationsResponse>

    @PUT("notifications/read-all")
    suspend fun markAllNotificationsRead(): Response<MessageResponse>

    @PUT("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): Response<MessageResponse>
}
