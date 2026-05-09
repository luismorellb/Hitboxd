package com.hitboxd.app.data.model

import com.google.gson.annotations.SerializedName

// ─── GAME ────────────────────────────────────────────────
data class Game(
    @SerializedName("id_game")        val idGame: Int = 0,
    @SerializedName("igdb_id")        val igdbId: Int? = null,
    val title: String = "",
    val slug: String = "",
    @SerializedName("cover_url")      val coverUrl: String? = null,
    @SerializedName("background_url") val backgroundUrl: String? = null,
    val developer: String? = null,
    @SerializedName("release_date")   val releaseDate: String? = null,
    val description: String? = null
)

// ─── USER ────────────────────────────────────────────────
data class User(
    @SerializedName("id_user")         val idUser: Int = 0,
    val username: String = "",
    val email: String = "",
    val bio: String? = null,
    val pronouns: String? = null,
    @SerializedName("avatar_url")      val avatarUrl: String? = null,
    val role: String? = "user",
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("following_count") val followingCount: Int = 0,
    @SerializedName("games_count")     val gamesCount: Int = 0,
    @SerializedName("is_visible")      val isVisible: Boolean = true
)

// ─── AUTH RESPONSES ──────────────────────────────────────
data class AuthResponse(
    val message: String = "",
    val user: AuthUser? = null
)

data class AuthUser(
    val id: Int = 0,
    val username: String = ""
)

// ─── REVIEW ──────────────────────────────────────────────
data class Review(
    @SerializedName("id_review")    val idReview: Int = 0,
    @SerializedName("id_game")      val idGame: Int = 0,
    @SerializedName("id_user")      val idUser: Int = 0,
    val username: String? = null,
    @SerializedName("avatar_url")   val avatarUrl: String? = null,
    val content: String? = null,
    val rating: Float = 0f,
    @SerializedName("has_spoilers") val hasSpoilers: Boolean = false,
    val likes: Int = 0,
    @SerializedName("is_liked")     val isLiked: Boolean = false,
    @SerializedName("is_reported")  val isReported: Boolean = false,
    @SerializedName("report_count") val reportCount: Int = 0,
    @SerializedName("all_reasons")  val allReasons: String? = null,
    @SerializedName("game_title")   val gameTitle: String? = null,
    @SerializedName("cover_url")    val coverUrl: String? = null,
    @SerializedName("created_at")   val createdAt: String? = null,
    var showContent: Boolean = true  // solo UI
)

// ─── ACTIVITY ────────────────────────────────────────────
data class Activity(
    @SerializedName("id_activity")  val idActivity: Int = 0,
    @SerializedName("id_game")      val idGame: Int = 0,
    @SerializedName("id_user")      val idUser: Int = 0,
    val username: String? = null,
    @SerializedName("avatar_url")   val avatarUrl: String? = null,
    val status: String? = null,
    val rating: Float = 0f,
    @SerializedName("is_liked")     val isLiked: Boolean = false,
    @SerializedName("is_favorite")  val isFavorite: Boolean = false,
    @SerializedName("cover_url")    val coverUrl: String? = null,
    val title: String? = null,
    val slug: String? = null,
    @SerializedName("created_at")   val createdAt: String? = null
)

// Estado del juego para el usuario actual
data class ActivityStatus(
    val status: String? = null,
    @SerializedName("is_favorite") val isFavorite: Boolean = false,
    @SerializedName("is_liked")    val isLiked: Boolean = false,
    val rating: Float? = null
)

// ─── USER LIST ───────────────────────────────────────────
data class UserList(
    @SerializedName("id_list")    val idList: Int = 0,
    @SerializedName("id_user")    val idUser: Int = 0,
    val title: String = "",
    val description: String? = null,
    val username: String? = null,
    @SerializedName("is_public")  val isPublic: Boolean = true,
    @SerializedName("list_type")  val listType: String? = null,
    val games: List<ListItem> = emptyList()
)

// ─── LIST ITEM ───────────────────────────────────────────
data class ListItem(
    @SerializedName("id_item")   val idItem: Int = 0,
    @SerializedName("id_game")   val idGame: Int = 0,
    val title: String = "",
    @SerializedName("cover_url") val coverUrl: String? = null,
    val comment: String? = null,
    val position: Int = 0,
    val slug: String? = null
)

// ─── FOLLOW ──────────────────────────────────────────────
data class Follow(
    @SerializedName("id_user")     val idUser: Int = 0,
    val username: String = "",
    @SerializedName("avatar_url")  val avatarUrl: String? = null,
    @SerializedName("games_count") val gamesCount: Int = 0,
    var isFollowing: Boolean = false
)

data class FollowCheckResponse(
    @SerializedName("isFollowing") val isFollowing: Boolean = false
)

// ─── REQUESTS ────────────────────────────────────────────
// ⚠️ Login usa username, NO email
data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class ActivityRequest(
    val gameId: Int,
    val status: String? = null,
    val rating: Float? = null,
    val isFavorite: Boolean? = null,
    val isLiked: Boolean? = null
)

data class ReviewRequest(
    @SerializedName("id_game")      val idGame: Int,
    val content: String,
    val rating: Float,
    @SerializedName("has_spoilers") val hasSpoilers: Boolean
)

data class ReportRequest(val reason: String)

data class ListRequest(
    val title: String,
    val description: String? = null,
    @SerializedName("is_public") val isPublic: Boolean = true,
    @SerializedName("list_type") val listType: String = "collection"
)

data class AddGameToListRequest(
    val gameId: Int,
    val comment: String? = null
)

data class ProfileUpdateRequest(
    val bio: String? = null,
    val pronouns: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

// ─── GENERIC RESPONSES ───────────────────────────────────
data class MessageResponse(val message: String = "")
data class CreateResponse(val message: String = "", val id: Int = 0)

// ─── NETWORK RESULT ──────────────────────────────────────
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
