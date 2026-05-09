package com.hitboxd.app.ui.game

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.ReviewAdapter
import com.hitboxd.app.common.dialog.ReportDialogFragment
import com.hitboxd.app.common.dialog.ReviewDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import com.hitboxd.app.utils.DateUtils
import com.hitboxd.app.utils.ImageUtils
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class GameDetailViewModel : ViewModel() {

    private val gameRepo     = GameRepository()
    private val reviewRepo   = ReviewRepository()
    private val activityRepo = ActivityRepository()

    private val _game     = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    private val _reviews  = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _status   = MutableStateFlow(ActivityStatus())
    val status: StateFlow<ActivityStatus> = _status

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load(slug: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = gameRepo.getBySlug(slug)) {
                is NetworkResult.Success -> {
                    _game.value = r.data
                    val id = r.data.idGame
                    launch { loadReviews(id) }
                    launch { loadStatus(id) }
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadReviews(gameId: Int) {
        when (val r = reviewRepo.getGameReviews(gameId)) {
            is NetworkResult.Success -> _reviews.value = r.data.map {
                it.copy(showContent = !it.hasSpoilers)
            }
            else -> {}
        }
    }

    private suspend fun loadStatus(gameId: Int) {
        when (val r = activityRepo.checkStatus(gameId)) {
            is NetworkResult.Success -> _status.value = r.data
            else -> {}
        }
    }

    fun updateStatus(newStatus: String) {
        val toSet = if (_status.value.status == newStatus) null else newStatus
        _status.value = _status.value.copy(status = toSet)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch { activityRepo.logActivity(gameId, status = toSet) }
    }

    fun updateRating(rating: Float) {
        _status.value = _status.value.copy(rating = rating)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch { activityRepo.logActivity(gameId, rating = rating) }
    }

    fun toggleLike() {
        val newLiked = !_status.value.isLiked
        _status.value = _status.value.copy(isLiked = newLiked)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch { activityRepo.logActivity(gameId, isLiked = newLiked) }
    }

    fun toggleFavorite() {
        val newFav = !_status.value.isFavorite
        _status.value = _status.value.copy(isFavorite = newFav)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch { activityRepo.logActivity(gameId, isFavorite = newFav) }
    }

    fun toggleReviewLike(reviewId: Int) {
        _reviews.value = _reviews.value.map {
            if (it.idReview == reviewId) it.copy(
                isLiked = !it.isLiked,
                likes   = if (!it.isLiked) it.likes + 1 else it.likes - 1
            ) else it
        }
        viewModelScope.launch { reviewRepo.toggleLike(reviewId) }
    }

    fun toggleSpoiler(reviewId: Int) {
        _reviews.value = _reviews.value.map {
            if (it.idReview == reviewId) it.copy(showContent = !it.showContent) else it
        }
    }

    fun submitReview(content: String, rating: Float, hasSpoilers: Boolean) {
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch {
            reviewRepo.addReview(gameId, content, rating, hasSpoilers)
            loadReviews(gameId)
        }
    }

    fun reportReview(reviewId: Int, reason: String) {
        viewModelScope.launch { reviewRepo.reportReview(reviewId, reason) }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class GameDetailFragment : Fragment() {

    private val vm: GameDetailViewModel by viewModels()
    private lateinit var session: SessionManager
    private lateinit var reviewAdapter: ReviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_game_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        val slug = arguments?.getString("slug") ?: return

        setupReviews(view)
        setupButtons(view)
        observeVm(view)
        vm.load(slug)
    }

    private fun setupReviews(view: View) {
        reviewAdapter = ReviewAdapter(
            currentUserId = session.getUserId(),
            onLike        = { vm.toggleReviewLike(it.idReview) },
            onReport      = { review ->
                ReportDialogFragment.newInstance(review.idReview) { reason ->
                    vm.reportReview(review.idReview, reason)
                }.show(childFragmentManager, "report")
            },
            onSpoiler     = { vm.toggleSpoiler(it.idReview) },
            onAuthorClick = { username ->
                findNavController().navigate(
                    R.id.action_gameDetailFragment_to_publicProfileFragment,
                    bundleOf("username" to username)
                )
            }
        )
        view.findViewById<RecyclerView>(R.id.rvReviews).apply {
            layoutManager = LinearLayoutManager(context)
            adapter       = reviewAdapter
        }
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.btnPlayed).setOnClickListener    { vm.updateStatus("played") }
        view.findViewById<Button>(R.id.btnPending).setOnClickListener   { vm.updateStatus("plan_to_play") }
        view.findViewById<Button>(R.id.btnAbandoned).setOnClickListener { vm.updateStatus("dropped") }
        view.findViewById<ImageButton>(R.id.btnLike).setOnClickListener { vm.toggleLike() }
        view.findViewById<ImageButton>(R.id.btnFav).setOnClickListener  { vm.toggleFavorite() }
        view.findViewById<Button>(R.id.btnWriteReview).setOnClickListener {
            ReviewDialogFragment { c, r, s -> vm.submitReview(c, r, s) }
                .show(childFragmentManager, "review")
        }
    }

    private fun observeVm(view: View) {
        val progressBar = view.findViewById<View>(R.id.progressBar)
        val contentRoot = view.findViewById<View>(R.id.contentRoot)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isLoading.collect { loading ->
                progressBar.isVisible = loading
                contentRoot.isVisible = !loading
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.game.collect { game ->
                game ?: return@collect
                view.findViewById<TextView>(R.id.tvGameTitle).text  = game.title
                view.findViewById<TextView>(R.id.tvDeveloper).text  =
                    "${game.developer ?: "Unknown"} • ${DateUtils.extractYear(game.releaseDate)}"
                view.findViewById<TextView>(R.id.tvDescription).text = game.description ?: "No description available."
                ImageUtils.loadBanner(requireContext(), game.backgroundUrl, view.findViewById(R.id.imgBanner))
                ImageUtils.loadGameCover(requireContext(), game.coverUrl, view.findViewById(R.id.imgCover))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.reviews.collect { reviewAdapter.submitList(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.status.collect { s ->
                view.findViewById<Button>(R.id.btnPlayed).isSelected    = s.status == "played"
                view.findViewById<Button>(R.id.btnPending).isSelected   = s.status == "plan_to_play"
                view.findViewById<Button>(R.id.btnAbandoned).isSelected = s.status == "dropped"
                view.findViewById<ImageButton>(R.id.btnLike).setImageResource(
                    if (s.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                )
                view.findViewById<ImageButton>(R.id.btnFav).setImageResource(
                    if (s.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_empty
                )
                view.findViewById<RatingBar>(R.id.ratingBar).rating = s.rating ?: 0f
            }
        }
    }
}
