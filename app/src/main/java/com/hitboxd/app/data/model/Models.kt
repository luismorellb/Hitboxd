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
    val username: String = "",
    val role: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
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

data class ListUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    @SerializedName("is_public") val isPublic: Boolean? = null,
    @SerializedName("list_type") val listType: String? = null
)

data class AdminGlobalStats(
    val counts: StatsCounts = StatsCounts(),
    @SerializedName("reviews_per_day_7d") val reviewsPerDay7d: List<DayCount> = emptyList(),
    @SerializedName("top_games_30d")      val topGames30d: List<Game> = emptyList(),
    val admin: AdminInfo = AdminInfo(),
    @SerializedName("generated_at")       val generatedAt: String? = null
)

data class StatsCounts(
    @SerializedName("active_users")      val activeUsers: Int = 0,
    @SerializedName("banned_users")      val bannedUsers: Int = 0,
    @SerializedName("total_games")       val totalGames: Int = 0,
    @SerializedName("total_reviews")     val totalReviews: Int = 0,
    @SerializedName("total_lists")       val totalLists: Int = 0,
    @SerializedName("total_follows")     val totalFollows: Int = 0,
    @SerializedName("total_activities")  val totalActivities: Int = 0,
    @SerializedName("pending_reports")   val pendingReports: Int = 0,
    @SerializedName("recent_signups_7d") val recentSignups7d: Int = 0
)

data class DayCount(val date: String = "", val count: Int = 0)

data class AdminInfo(
    @SerializedName("id_user") val idUser: Int = 0,
    val username: String = ""
)

data class AdminUsersResponse(
    val users: List<User> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20
)

data class AddGameToListRequest(
    val gameId: Int,
    val comment: String? = null
)

data class ReorderItem(
    @SerializedName("id_item") val idItem: Int,
    val position: Int
)

data class ReorderRequest(val items: List<ReorderItem>)

data class ProfileUpdateRequest(
    val bio: String? = null,
    val pronouns: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null
)

// ─── GENERIC RESPONSES ───────────────────────────────────
data class MessageResponse(val message: String = "")
data class CreateResponse(val message: String = "", val id: Int = 0)

// ─── NOTIFICATION ────────────────────────────────────────
data class Notification(
    @SerializedName("id_notification") val idNotification: Int = 0,
    val type: String = "",
    @SerializedName("id_reference")    val idReference: Int? = null,
    @SerializedName("is_read")         val isRead: Boolean = false,
    @SerializedName("created_at")      val createdAt: String? = null,
    @SerializedName("actor_username")  val actorUsername: String = "",
    @SerializedName("actor_avatar")    val actorAvatar: String? = null,
    @SerializedName("target_slug")     val targetSlug: String? = null
)

data class GameSlugResponse(
    @SerializedName("id_review")   val idReview: Int = 0,
    @SerializedName("id_game")     val idGame: Int = 0,
    val slug: String = "",
    @SerializedName("game_title")  val gameTitle: String = ""
)

data class NotificationsResponse(
    val notifications: List<Notification> = emptyList(),
    @SerializedName("unread_count") val unreadCount: Int = 0
)

// ─── USER EXTRAS ─────────────────────────────────────────
data class UserCountResponse(val count: Int = 0)

// ─── ACTIVITY EXTRAS ─────────────────────────────────────
data class StreakResponse(val streak: Int = 0)

data class GenreCount(val genre: String = "", val count: Int = 0)
data class YearCount(val year: Int = 0, val count: Int = 0)
data class StatusCount(val status: String = "", val count: Int = 0)

data class UserStatsResponse(
    @SerializedName("genre_distribution")  val genreDistribution: List<GenreCount> = emptyList(),
    @SerializedName("year_distribution")   val yearDistribution: List<YearCount> = emptyList(),
    @SerializedName("avg_rating")          val avgRating: String? = null,
    @SerializedName("rated_count")         val ratedCount: Int = 0,
    @SerializedName("status_distribution") val statusDistribution: List<StatusCount> = emptyList()
)

// ─── GAME EXTRAS ─────────────────────────────────────────
data class SearchPageResponse(
    val results: List<Game> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 24
)

data class GameGenre(val id: Int = 0, val name: String = "")

data class GameExtrasResponse(
    val genres: List<GameGenre> = emptyList(),
    @SerializedName("similarGames") val similarGames: List<Game> = emptyList()
)

data class RatingDistributionEntry(val rating: Float = 0f, val count: Int = 0)
data class StatusDistributionEntry(val status: String = "", val count: Int = 0, val percentage: Int = 0)

data class GameStatsResponse(
    @SerializedName("rating_distribution") val ratingDistribution: List<RatingDistributionEntry> = emptyList(),
    @SerializedName("avg_rating")          val avgRating: String? = null,
    @SerializedName("total_ratings")       val totalRatings: Int = 0,
    @SerializedName("status_distribution") val statusDistribution: List<StatusDistributionEntry> = emptyList()
)

// ─── PUBLIC ACTIVITY (libreria de otro usuario) ──────────
data class PublicActivity(
    @SerializedName("id_game")        val idGame: Int = 0,
    val title: String = "",
    val slug: String = "",
    @SerializedName("cover_url")      val coverUrl: String? = null,
    @SerializedName("background_url") val backgroundUrl: String? = null,
    val developer: String? = null,
    @SerializedName("release_date")   val releaseDate: String? = null,
    val status: String? = null,
    val rating: Float? = null,
    @SerializedName("is_favorite")    val isFavorite: Boolean = false,
    @SerializedName("is_liked")       val isLiked: Boolean = false,
    @SerializedName("added_at")       val addedAt: String? = null
)

// ─── UI STATE ────────────────────────────────────────────
data class SuggestionItem(val user: User, val isFollowing: Boolean = false)

// ─── NETWORK RESULT ──────────────────────────────────────
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val message: String, val code: Int? = null) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
