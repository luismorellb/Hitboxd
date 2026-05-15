package com.hitboxd.app.common.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hitboxd.app.R
import com.hitboxd.app.data.model.*
import com.hitboxd.app.utils.DateUtils
import com.hitboxd.app.utils.ImageUtils
import com.hitboxd.app.utils.StatusUtils
import com.hitboxd.app.utils.TimeAgo

// ─── GAME CARD ───────────────────────────────────────────
// Usado en: HomeFeed (3 carruseles), Catalog, LandingPage, Profile tabs
class GameCardAdapter(private val onClick: (Game) -> Unit) :
    ListAdapter<Game, GameCardAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Game>() {
            override fun areItemsTheSame(a: Game, b: Game) = a.idGame == b.idGame
            override fun areContentsTheSame(a: Game, b: Game) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView = view.findViewById(R.id.imgCover)
        val tvTitle: TextView   = view.findViewById(R.id.tvTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_game_card, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val game = getItem(position)
        holder.tvTitle.text = game.title
        ImageUtils.loadGameCover(holder.itemView.context, game.coverUrl, holder.imgCover)
        holder.itemView.setOnClickListener { onClick(game) }
    }
}

// ─── REVIEW ADAPTER ──────────────────────────────────────
// Usado en: GameDetailFragment (lista completa con like/report/spoiler)
class ReviewAdapter(
    private val currentUserId: Int,
    private val onLike: (Review) -> Unit,
    private val onReport: (Review) -> Unit,
    private val onSpoiler: (Review) -> Unit,
    private val onAuthorClick: (String) -> Unit
) : ListAdapter<Review, ReviewAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(a: Review, b: Review) = a.idReview == b.idReview
            override fun areContentsTheSame(a: Review, b: Review) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView      = view.findViewById(R.id.imgAvatar)
        val tvUsername: TextView      = view.findViewById(R.id.tvUsername)
        val tvDate: TextView          = view.findViewById(R.id.tvDate)
        val tvRating: TextView        = view.findViewById(R.id.tvRating)
        val tvContent: TextView       = view.findViewById(R.id.tvContent)
        val spoilerOverlay: View      = view.findViewById(R.id.spoilerOverlay)
        val btnSpoiler: Button        = view.findViewById(R.id.btnSpoiler)
        val btnLike: ImageButton      = view.findViewById(R.id.btnLike)
        val tvLikes: TextView         = view.findViewById(R.id.tvLikes)
        val btnReport: ImageButton    = view.findViewById(R.id.btnReport)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val review = getItem(position)

        ImageUtils.loadAvatar(holder.itemView.context, review.avatarUrl, holder.imgAvatar)
        holder.tvUsername.text = review.username ?: "User"
        holder.tvDate.text     = DateUtils.format(review.createdAt)
        holder.tvRating.text   = holder.itemView.context.getString(R.string.format_star_rating, review.rating)
        holder.tvContent.text  = review.content

        // Lógica de spoiler
        val hasSpoiler = review.hasSpoilers
        holder.btnSpoiler.visibility    = if (hasSpoiler) View.VISIBLE else View.GONE
        holder.spoilerOverlay.visibility = if (hasSpoiler && !review.showContent) View.VISIBLE else View.GONE
        holder.tvContent.visibility      = if (!hasSpoiler || review.showContent) View.VISIBLE else View.INVISIBLE

        holder.btnSpoiler.setOnClickListener { onSpoiler(review) }
        holder.spoilerOverlay.setOnClickListener { onSpoiler(review) }

        // Like
        holder.tvLikes.text = review.likes.toString()
        holder.btnLike.setImageResource(
            if (review.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
        holder.btnLike.setOnClickListener { onLike(review) }

        // Report (solo si no es el propio usuario)
        holder.btnReport.visibility = if (review.idUser != currentUserId) View.VISIBLE else View.GONE
        holder.btnReport.setOnClickListener { onReport(review) }

        // Navegar al perfil del autor
        holder.tvUsername.setOnClickListener { review.username?.let { onAuthorClick(it) } }
        holder.imgAvatar.setOnClickListener  { review.username?.let { onAuthorClick(it) } }
    }
}

// ─── ACTIVITY CARD ───────────────────────────────────────
// Usado en: HomeFeed carrusel horizontal "Friends Activity"
class ActivityCardAdapter(private val onClick: (Activity) -> Unit) :
    ListAdapter<Activity, ActivityCardAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Activity>() {
            override fun areItemsTheSame(a: Activity, b: Activity) = a.idActivity == b.idActivity
            override fun areContentsTheSame(a: Activity, b: Activity) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView    = view.findViewById(R.id.imgCover)
        val tvUsername: TextView   = view.findViewById(R.id.tvUsername)
        val tvStatus: TextView     = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_activity_card, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = getItem(pos)
        ImageUtils.loadGameCover(holder.itemView.context, item.coverUrl, holder.imgCover)
        holder.tvUsername.text = item.username ?: ""
        holder.tvStatus.text   = StatusUtils.label(item.status)
        holder.itemView.setOnClickListener { onClick(item) }
    }
}

// ─── ACTIVITY FEED ───────────────────────────────────────
// Usado en: ProfileFragment tab ACTIVITY (lista vertical)
class ActivityFeedAdapter :
    ListAdapter<Activity, ActivityFeedAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Activity>() {
            override fun areItemsTheSame(a: Activity, b: Activity) = a.idActivity == b.idActivity
            override fun areContentsTheSame(a: Activity, b: Activity) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView    = view.findViewById(R.id.imgCover)
        val tvUsername: TextView   = view.findViewById(R.id.tvUsername)
        val tvGameTitle: TextView  = view.findViewById(R.id.tvGameTitle)
        val tvStatus: TextView     = view.findViewById(R.id.tvStatus)
        val tvDate: TextView       = view.findViewById(R.id.tvDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_activity_feed, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = getItem(pos)
        ImageUtils.loadGameCover(holder.itemView.context, item.coverUrl, holder.imgCover)
        holder.tvUsername.text  = item.username ?: "User"
        holder.tvGameTitle.text = item.title ?: ""
        holder.tvStatus.text    = StatusUtils.label(item.status)
        holder.tvDate.text      = DateUtils.format(item.createdAt)
    }
}

// ─── USER LIST ADAPTER ───────────────────────────────────
// Usado en: ProfileFragment y PublicProfileFragment tab LISTS
class UserListAdapter(private val onClick: (UserList) -> Unit) :
    ListAdapter<UserList, UserListAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<UserList>() {
            override fun areItemsTheSame(a: UserList, b: UserList) = a.idList == b.idList
            override fun areContentsTheSame(a: UserList, b: UserList) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView  = view.findViewById(R.id.tvTitle)
        val tvDesc: TextView   = view.findViewById(R.id.tvDescription)
        val tvCount: TextView  = view.findViewById(R.id.tvCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_user_list, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val list = getItem(pos)
        holder.tvTitle.text = list.title
        holder.tvDesc.text  = list.description ?: ""
        holder.tvCount.text = holder.itemView.context.getString(R.string.format_games_count, list.games.size)
        holder.itemView.setOnClickListener { onClick(list) }
    }
}

// ─── LIST ITEM ADAPTER ───────────────────────────────────
// Usado en: ListDetailFragment (juegos dentro de la lista)
class ListItemAdapter(
    private val onGameClick: (ListItem) -> Unit
) : ListAdapter<ListItem, ListItemAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(a: ListItem, b: ListItem) = a.idItem == b.idItem
            override fun areContentsTheSame(a: ListItem, b: ListItem) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView  = view.findViewById(R.id.imgCover)
        val tvTitle: TextView    = view.findViewById(R.id.tvTitle)
        val tvComment: TextView  = view.findViewById(R.id.tvComment)
        val tvPosition: TextView = view.findViewById(R.id.tvPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_list_game, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = getItem(pos)
        ImageUtils.loadGameCover(holder.itemView.context, item.coverUrl, holder.imgCover)
        holder.tvTitle.text    = item.title
        holder.tvComment.text  = item.comment ?: ""
        holder.tvPosition.text = holder.itemView.context.getString(R.string.format_hash_number, item.position)
        holder.itemView.setOnClickListener { onGameClick(item) }
    }
}

// ─── USER NETWORK ADAPTER ────────────────────────────────
// Usado en: ProfileFragment tab NETWORK (following/followers)
class UserNetworkAdapter(
    private val onUserClick: (Follow) -> Unit,
    private val onFollow: (Follow) -> Unit
) : ListAdapter<Follow, UserNetworkAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Follow>() {
            override fun areItemsTheSame(a: Follow, b: Follow) = a.idUser == b.idUser
            override fun areContentsTheSame(a: Follow, b: Follow) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvGames: TextView    = view.findViewById(R.id.tvGames)
        val btnFollow: Button    = view.findViewById(R.id.btnFollow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_user_network, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val user = getItem(pos)
        ImageUtils.loadAvatar(holder.itemView.context, user.avatarUrl, holder.imgAvatar)
        holder.tvUsername.text   = user.username
        holder.tvGames.text      = holder.itemView.context.getString(R.string.format_games_count, user.gamesCount)
        holder.btnFollow.text    = if (user.isFollowing) "Following" else "Follow"
        holder.btnFollow.isSelected = user.isFollowing
        holder.btnFollow.setOnClickListener { onFollow(user) }
        holder.itemView.setOnClickListener  { onUserClick(user) }
    }
}

// ─── DIARY ADAPTER ───────────────────────────────────────
// Usado en: ProfileFragment tab DIARY
class DiaryAdapter :
    ListAdapter<Review, DiaryAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(a: Review, b: Review) = a.idReview == b.idReview
            override fun areContentsTheSame(a: Review, b: Review) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView    = view.findViewById(R.id.tvDay)
        val tvMonth: TextView  = view.findViewById(R.id.tvMonth)
        val tvYear: TextView   = view.findViewById(R.id.tvYear)
        val imgCover: ImageView = view.findViewById(R.id.imgCover)
        val tvTitle: TextView  = view.findViewById(R.id.tvTitle)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_diary_entry, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val review = getItem(pos)
        holder.tvDay.text   = DateUtils.dayOf(review.createdAt)
        holder.tvMonth.text = DateUtils.monthOf(review.createdAt)
        holder.tvYear.text  = DateUtils.yearOf(review.createdAt)
        ImageUtils.loadGameCover(holder.itemView.context, review.coverUrl, holder.imgCover)
        holder.tvTitle.text  = review.gameTitle ?: ""
        holder.tvRating.text = holder.itemView.context.getString(R.string.format_star_rating, review.rating)
    }
}

// ─── MINI REVIEW ADAPTER ─────────────────────────────────
// Usado en: ProfileOverviewFragment (3 reseñas compactas)
class MiniReviewAdapter :
    ListAdapter<Review, MiniReviewAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(a: Review, b: Review) = a.idReview == b.idReview
            override fun areContentsTheSame(a: Review, b: Review) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvGameTitle: TextView = view.findViewById(R.id.tvGameTitle)
        val tvRating: TextView    = view.findViewById(R.id.tvRating)
        val tvContent: TextView   = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_mini_review, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val r = getItem(pos)
        holder.tvGameTitle.text = r.gameTitle ?: "Game"
        holder.tvRating.text    = holder.itemView.context.getString(R.string.format_star_rating, r.rating)
        val content = r.content ?: ""
        holder.tvContent.text   = if (content.length > 80) "${content.take(80)}…" else content
    }
}

// ─── FULL REVIEW ADAPTER ─────────────────────────────────
// Usado en: tabs REVIEWS de Profile y PublicProfile (sin botón report)
class FullReviewAdapter :
    ListAdapter<Review, FullReviewAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(a: Review, b: Review) = a.idReview == b.idReview
            override fun areContentsTheSame(a: Review, b: Review) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvGameTitle: TextView = view.findViewById(R.id.tvGameTitle)
        val tvRating: TextView    = view.findViewById(R.id.tvRating)
        val tvDate: TextView      = view.findViewById(R.id.tvDate)
        val tvContent: TextView   = view.findViewById(R.id.tvContent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_full_review, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val r = getItem(pos)
        holder.tvGameTitle.text = r.gameTitle ?: "Game"
        holder.tvRating.text    = holder.itemView.context.getString(R.string.format_star_rating, r.rating)
        holder.tvDate.text      = DateUtils.format(r.createdAt)
        holder.tvContent.text   = r.content ?: ""
    }
}

// ─── ADMIN GAMES ADAPTER ─────────────────────────────────
// Usado en: AdminFragment (catálogo de juegos)
class AdminGamesAdapter(private val onView: (Game) -> Unit) :
    ListAdapter<Game, AdminGamesAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Game>() {
            override fun areItemsTheSame(a: Game, b: Game) = a.idGame == b.idGame
            override fun areContentsTheSame(a: Game, b: Game) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvId: TextView        = view.findViewById(R.id.tvId)
        val tvTitle: TextView     = view.findViewById(R.id.tvTitle)
        val tvDeveloper: TextView = view.findViewById(R.id.tvDeveloper)
        val btnView: Button       = view.findViewById(R.id.btnView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_admin_game, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val game = getItem(pos)
        holder.tvId.text        = holder.itemView.context.getString(R.string.format_hash_number, game.idGame)
        holder.tvTitle.text     = game.title
        holder.tvDeveloper.text = game.developer ?: "Unknown"
        holder.btnView.setOnClickListener { onView(game) }
    }
}

// ─── REPORTED REVIEW ADAPTER ─────────────────────────────
// Usado en: AdminFragment (reseñas reportadas con 3 acciones)
class ReportedReviewAdapter(
    private val onView: (Review) -> Unit,
    private val onDelete: (Review) -> Unit,
    private val onApprove: (Review) -> Unit
) : ListAdapter<Review, ReportedReviewAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Review>() {
            override fun areItemsTheSame(a: Review, b: Review) = a.idReview == b.idReview
            override fun areContentsTheSame(a: Review, b: Review) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvGame: TextView     = view.findViewById(R.id.tvGame)
        val tvReports: TextView  = view.findViewById(R.id.tvReports)
        val tvReasons: TextView  = view.findViewById(R.id.tvReasons)
        val btnView: Button      = view.findViewById(R.id.btnView)
        val btnDelete: Button    = view.findViewById(R.id.btnDelete)
        val btnApprove: Button   = view.findViewById(R.id.btnApprove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, v: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_reported_review, parent, false)
    )

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val r = getItem(pos)
        holder.tvUsername.text = r.username ?: "User"
        holder.tvGame.text     = r.gameTitle ?: "Game"
        holder.tvReports.text  = holder.itemView.context.getString(R.string.format_reports_count, r.reportCount)
        holder.tvReasons.text  = r.allReasons ?: ""
        holder.btnView.setOnClickListener   { onView(r) }
        holder.btnDelete.setOnClickListener { onDelete(r) }
        holder.btnApprove.setOnClickListener{ onApprove(r) }
    }
}

// ─── USER SUGGESTION ADAPTER ─────────────────────────────
// Usado en: HomeFeedFragment "People to follow"
class UserSuggestionAdapter(
    private val onClick: (SuggestionItem) -> Unit,
    private val onFollow: (SuggestionItem) -> Unit,
    private val showFollowButton: Boolean = true
) : ListAdapter<SuggestionItem, UserSuggestionAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SuggestionItem>() {
            override fun areItemsTheSame(a: SuggestionItem, b: SuggestionItem) =
                a.user.idUser == b.user.idUser
            override fun areContentsTheSame(a: SuggestionItem, b: SuggestionItem) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvBio: TextView      = view.findViewById(R.id.tvBio)
        val btnFollow: Button    = view.findViewById(R.id.btnFollow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_user_suggestion, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        ImageUtils.loadAvatar(holder.itemView.context, item.user.avatarUrl, holder.imgAvatar)
        holder.tvUsername.text = item.user.username
        holder.tvBio.text      = item.user.bio ?: ""
        holder.btnFollow.text  = holder.itemView.context.getString(
            if (item.isFollowing) R.string.following else R.string.follow
        )
        holder.btnFollow.visibility          = if (showFollowButton) View.VISIBLE else View.GONE
        holder.btnFollow.isSelected          = item.isFollowing
        holder.btnFollow.setOnClickListener  { onFollow(item) }
        holder.itemView.setOnClickListener   { onClick(item) }
    }
}

// ─── NOTIFICATION ADAPTER ────────────────────────────────
// Usado en: NotificationsFragment
class NotificationAdapter(private val onClick: (Notification) -> Unit) :
    ListAdapter<Notification, NotificationAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Notification>() {
            override fun areItemsTheSame(a: Notification, b: Notification) =
                a.idNotification == b.idNotification
            override fun areContentsTheSame(a: Notification, b: Notification) = a == b
        }
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgAvatar: ImageView = view.findViewById(R.id.imgAvatar)
        val tvActor: TextView    = view.findViewById(R.id.tvActor)
        val tvEvent: TextView    = view.findViewById(R.id.tvEvent)
        val tvTime: TextView     = view.findViewById(R.id.tvTime)
        val dotUnread: View      = view.findViewById(R.id.dotUnread)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val notif = getItem(position)

        ImageUtils.loadAvatar(holder.itemView.context, notif.actorAvatar, holder.imgAvatar)
        holder.tvActor.text = notif.actorUsername
        holder.tvEvent.text = when (notif.type) {
            "follow"      -> "started following you"
            "review_like" -> "liked your review"
            else          -> notif.type
        }
        holder.tvTime.text = TimeAgo.format(notif.createdAt)
        holder.dotUnread.visibility = if (notif.isRead) View.GONE else View.VISIBLE

        holder.itemView.setBackgroundColor(
            holder.itemView.context.getColor(
                if (notif.isRead) R.color.bg_card else R.color.notif_unread_bg
            )
        )

        holder.itemView.setOnClickListener { onClick(notif) }
    }
}
