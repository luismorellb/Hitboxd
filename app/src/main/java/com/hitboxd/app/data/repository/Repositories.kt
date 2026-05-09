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
}

// ─── GAME REPOSITORY ─────────────────────────────────────
class GameRepository {
    suspend fun getTrending() =
        safeCall { api.getTrendingGames() }

    suspend fun getNewReleases() =
        safeCall { api.getNewReleases() }

    suspend fun searchGames(query: String) =
        safeCall { api.searchGames(query) }

    suspend fun getPopular() =
        safeCall { api.getPopularGames() }

    suspend fun getRandom() =
        safeCall { api.getRandomGame() }

    suspend fun getBySlug(slug: String) =
        safeCall { api.getGameBySlug(slug) }

    suspend fun getById(id: Int) =
        safeCall { api.getGameById(id) }
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
}

// ─── LIST REPOSITORY ─────────────────────────────────────
// ⚠️ No existe PUT para editar lista ni DELETE para item individual
class ListRepository {
    suspend fun createList(title: String, description: String? = null) =
        safeCall { api.createList(ListRequest(title, description)) }

    suspend fun getUserLists(userId: Int) =
        safeCall { api.getUserLists(userId) }

    suspend fun getListDetail(listId: Int) =
        safeCall { api.getListDetail(listId) }

    suspend fun addGameToList(listId: Int, gameId: Int, comment: String? = null) =
        safeCall { api.addGameToList(listId, AddGameToListRequest(gameId, comment)) }

    suspend fun deleteList(listId: Int) =
        safeCall { api.deleteList(listId) }
}
