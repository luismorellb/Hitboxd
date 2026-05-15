@file:Suppress("unused")
package com.hitboxd.app.data.repository

import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.network.RetrofitClient
import retrofit2.Response

// ─── Helper genérico ─────────────────────────────────────
private val api get() = RetrofitClient.api

private suspend fun <T> safeCall(block: suspend () -> Response<T>): NetworkResult<T> {
    return try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) NetworkResult.Success(body)
            else NetworkResult.Error("Respuesta vacía", response.code())
        } else {
            NetworkResult.Error(
                response.message().ifBlank { "Error ${response.code()}" },
                response.code()
            )
        }
    } catch (e: Exception) {
        NetworkResult.Error(e.localizedMessage ?: "Error de conexión")
    }
}

// ─── AUTH REPOSITORY ─────────────────────────────────────
class AuthRepository {
    // ⚠️ login usa username, NO email
    suspend fun login(username: String, password: String) =
        safeCall { api.login(LoginRequest(username, password)) }

    suspend fun register(username: String, email: String, password: String) =
        safeCall { api.register(RegisterRequest(username, email, password)) }

    suspend fun logout() = safeCall { api.logout() }

    suspend fun refreshToken() = safeCall { api.refreshToken() }
}

// ─── USER REPOSITORY ─────────────────────────────────────
class UserRepository {
    suspend fun getMyProfile() =
        safeCall { api.getMyProfile() }

    suspend fun getUserByUsername(username: String) =
        safeCall { api.getUserByUsername(username) }

    suspend fun searchUsers(query: String) =
        safeCall { api.searchUsers(query) }

    suspend fun updateProfile(
        bio: String? = null,
        pronouns: String? = null,
        avatarUrl: String? = null
    ) = safeCall { api.updateProfile(ProfileUpdateRequest(bio, pronouns, avatarUrl)) }

    suspend fun softDeleteUser() =
        safeCall { api.softDeleteUser() }

    suspend fun followUser(userId: Int) =
        safeCall { api.followUser(userId) }

    suspend fun unfollowUser(userId: Int) =
        safeCall { api.unfollowUser(userId) }

    suspend fun checkFollow(userId: Int) =
        safeCall { api.checkFollow(userId) }

    suspend fun getFollowers(userId: Int) =
        safeCall { api.getFollowers(userId) }

    suspend fun getFollowing(userId: Int) =
        safeCall { api.getFollowing(userId) }

    suspend fun getSuggestions(limit: Int = 6) =
        safeCall { api.getSuggestions(limit) }

    suspend fun getUserCount() =
        safeCall { api.getUserCount() }

    suspend fun activateUser() =
        safeCall { api.activateUser() }
}

// ─── GAME REPOSITORY ─────────────────────────────────────
class GameRepository {
    suspend fun getTrending() =
        safeCall { api.getTrendingGames() }

    suspend fun getNewReleases() =
        safeCall { api.getNewReleases() }

    suspend fun searchGames(query: String) =
        safeCall { api.searchGames(query) }

    suspend fun getPopular(genre: String? = null, limit: Int = 20) =
        safeCall { api.getPopularGames(limit, genre) }

    suspend fun getRandom() =
        safeCall { api.getRandomGame() }

    suspend fun getBySlug(slug: String) =
        safeCall { api.getGameBySlug(slug) }

    suspend fun getById(id: Int) =
        safeCall { api.getGameById(id) }

    suspend fun searchPaginated(query: String, page: Int = 1, limit: Int = 24) =
        safeCall { api.searchGamesPaginated(query, page, limit) }

    suspend fun getRecommended(limit: Int = 20) =
        safeCall { api.getRecommended(limit) }

    suspend fun getExtras(id: Int) =
        safeCall { api.getGameExtras(id) }

    suspend fun getStats(id: Int) =
        safeCall { api.getGameStats(id) }
}

// ─── ACTIVITY REPOSITORY ─────────────────────────────────
class ActivityRepository {
    suspend fun logActivity(
        gameId: Int,
        status: String? = null,
        rating: Float? = null,
        isFavorite: Boolean? = null,
        isLiked: Boolean? = null
    ) = safeCall {
        api.logActivity(ActivityRequest(gameId, status, rating, isFavorite, isLiked))
    }

    suspend fun checkStatus(gameId: Int) =
        safeCall { api.checkStatus(gameId) }

    suspend fun getFeed() =
        safeCall { api.getFeed() }

    suspend fun getUserLibrary() =
        safeCall { api.getUserLibrary() }

    suspend fun getWatchlist() =
        safeCall { api.getWatchlist() }

    suspend fun getStreak() =
        safeCall { api.getStreak() }

    suspend fun getUserStats() =
        safeCall { api.getUserStats() }
}

// ─── REVIEW REPOSITORY ───────────────────────────────────
class ReviewRepository {
    suspend fun addReview(
        idGame: Int,
        content: String,
        rating: Float,
        hasSpoilers: Boolean
    ) = safeCall { api.addReview(ReviewRequest(idGame, content, rating, hasSpoilers)) }

    suspend fun getGameReviews(gameId: Int) =
        safeCall { api.getGameReviews(gameId) }

    suspend fun getUserReviews(userId: Int) =
        safeCall { api.getUserReviews(userId) }

    suspend fun removeReview(reviewId: Int) =
        safeCall { api.removeReview(reviewId) }

    suspend fun toggleLike(reviewId: Int) =
        safeCall { api.toggleReviewLike(reviewId) }

    suspend fun reportReview(reviewId: Int, reason: String) =
        safeCall { api.reportReview(reviewId, ReportRequest(reason)) }

    suspend fun getReported() =
        safeCall { api.getReportedReviews() }

    suspend fun approveReview(reviewId: Int) =
        safeCall { api.approveReview(reviewId) }

    suspend fun getRecent(limit: Int = 3) =
        safeCall { api.getRecentReviews(limit) }
}

// ─── LIST REPOSITORY ─────────────────────────────────────
// ⚠️ No existe PUT para editar lista
class ListRepository {
    suspend fun createList(title: String, description: String? = null) =
        safeCall { api.createList(ListRequest(title, description)) }

    suspend fun getUserLists(userId: Int) =
        safeCall { api.getUserLists(userId) }

    suspend fun getListDetail(listId: Int) =
        safeCall { api.getListDetail(listId) }

    suspend fun addGameToList(listId: Int, gameId: Int, comment: String? = null) =
        safeCall { api.addGameToList(listId, AddGameToListRequest(gameId, comment)) }

    suspend fun reorderItems(listId: Int, items: List<ReorderItem>) =
        safeCall { api.reorderList(listId, ReorderRequest(items)) }

    suspend fun removeItem(listId: Int, itemId: Int) =
        safeCall { api.removeListItem(listId, itemId) }

    suspend fun deleteList(listId: Int) =
        safeCall { api.deleteList(listId) }
}

// ─── NOTIFICATION REPOSITORY ─────────────────────────────
class NotificationRepository {
    suspend fun list() = safeCall { api.getNotifications() }
    suspend fun markAllRead() = safeCall { api.markAllNotificationsRead() }
    suspend fun markOneRead(id: Int) = safeCall { api.markNotificationRead(id) }
}
