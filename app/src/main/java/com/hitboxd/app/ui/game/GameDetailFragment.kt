package com.hitboxd.app.ui.game

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.GameCardAdapter
import com.hitboxd.app.common.adapter.ReviewAdapter
import com.hitboxd.app.common.dialog.ReportDialogFragment
import com.hitboxd.app.common.dialog.ReviewDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import com.hitboxd.app.data.network.SocketEvent
import com.hitboxd.app.data.network.SocketManager
import com.hitboxd.app.utils.DateUtils
import com.hitboxd.app.utils.ImageUtils
import com.hitboxd.app.utils.SessionManager
import com.hitboxd.app.utils.StatusUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

// ─── VIEWMODEL ───────────────────────────────────────────
class GameDetailViewModel : ViewModel() {

    private val gameRepo     = GameRepository()
    private val reviewRepo   = ReviewRepository()
    private val activityRepo = ActivityRepository()

    private val _game    = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _status  = MutableStateFlow(ActivityStatus())
    val status: StateFlow<ActivityStatus> = _status

    private val _extras  = MutableStateFlow<GameExtrasResponse?>(null)
    val extras: StateFlow<GameExtrasResponse?> = _extras

    private val _stats   = MutableStateFlow<GameStatsResponse?>(null)
    val stats: StateFlow<GameStatsResponse?> = _stats

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _actionError = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val actionError: SharedFlow<String> = _actionError

    private val _reviewsLoading = MutableStateFlow(false)
    val reviewsLoading: StateFlow<Boolean> = _reviewsLoading

    fun load(slug: String) {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = gameRepo.getBySlug(slug)) {
                is NetworkResult.Success -> {
                    _game.value = r.data
                    val id = r.data.idGame
                    coroutineScope {
                        awaitAll(
                            async { loadReviews(id) },
                            async { loadStatus(id) },
                            async { loadExtras(id) },
                            async { loadStats(id) }
                        )
                    }
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadReviews(gameId: Int) {
        _reviewsLoading.value = true
        when (val r = reviewRepo.getGameReviews(gameId)) {
            is NetworkResult.Success -> _reviews.value = r.data.map {
                it.copy(showContent = !it.hasSpoilers)
            }
            else -> {}
        }
        _reviewsLoading.value = false
    }

    private suspend fun loadStatus(gameId: Int) {
        when (val r = activityRepo.checkStatus(gameId)) {
            is NetworkResult.Success -> _status.value = r.data
            else -> {}
        }
    }

    private suspend fun loadExtras(gameId: Int) {
        when (val r = gameRepo.getExtras(gameId)) {
            is NetworkResult.Success -> _extras.value = r.data
            else -> {}
        }
    }

    private suspend fun loadStats(gameId: Int) {
        when (val r = gameRepo.getStats(gameId)) {
            is NetworkResult.Success -> _stats.value = r.data
            else -> {}
        }
    }

    fun updateStatus(newStatus: String) {
        val prev = _status.value
        val toSet = if (prev.status == newStatus) null else newStatus
        _status.value = prev.copy(status = toSet)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch {
            val result = activityRepo.logActivity(gameId, status = toSet)
            if (result is NetworkResult.Error) {
                _status.value = prev
                _actionError.tryEmit("No se pudo guardar el cambio")
            }
        }
    }

    fun updateRating(rating: Float) {
        _status.value = _status.value.copy(rating = rating)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch { activityRepo.logActivity(gameId, rating = rating) }
    }

    fun toggleLike() {
        val prev = _status.value
        val newFav = !prev.isLiked
        _status.value = prev.copy(isLiked = newFav)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch {
            val result = activityRepo.logActivity(gameId, isFavorite = newFav)
            if (result is NetworkResult.Error) {
                _status.value = prev
                _actionError.tryEmit("No se pudo guardar el cambio")
            }
        }
    }

    fun toggleFavorite() {
        val prev = _status.value
        val newFav = !prev.isFavorite
        _status.value = prev.copy(isFavorite = newFav)
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch {
            val result = activityRepo.logActivity(gameId, isFavorite = newFav)
            if (result is NetworkResult.Error) {
                _status.value = prev
                _actionError.tryEmit("No se pudo guardar el cambio")
            }
        }
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

    fun updateReviewLikeCount(reviewId: Int, count: Int) {
        _reviews.value = _reviews.value.map {
            if (it.idReview == reviewId) it.copy(likes = count) else it
        }
    }

    fun prependReview(review: Review) {
        _reviews.value = listOf(review.copy(showContent = !review.hasSpoilers)) + _reviews.value
    }

    fun removeReview(reviewId: Int) {
        _reviews.value = _reviews.value.filter { it.idReview != reviewId }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class GameDetailFragment : Fragment() {

    private val vm: GameDetailViewModel by viewModels()
    private lateinit var session: SessionManager
    private lateinit var reviewAdapter: ReviewAdapter
    private lateinit var similarAdapter: GameCardAdapter
    private lateinit var tvViewers: TextView

    private val currentUserId by lazy { SessionManager(requireContext()).getUserId() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_game_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        val slug = arguments?.getString("slug") ?: return

        tvViewers = view.findViewById(R.id.tvViewers)

        setupReviews(view)
        setupSimilarGames(view)
        setupButtons(view)
        observeVm(view)
        observeSocketEvents()
        vm.load(slug)
    }

    override fun onResume() {
        super.onResume()
        vm.game.value?.idGame?.let { gameId ->
            SocketManager.emit("game:join", JSONObject().put("gameId", gameId))
        }
    }

    override fun onPause() {
        super.onPause()
        vm.game.value?.idGame?.let { gameId ->
            SocketManager.emit("game:leave", JSONObject().put("gameId", gameId))
        }
    }

    private fun observeSocketEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketManager.events.collect { event ->
                    when (event) {
                        is SocketEvent.ReviewLikeChanged -> {
                            if (event.actorId == currentUserId) return@collect
                            vm.updateReviewLikeCount(event.idReview, event.count)
                        }
                        is SocketEvent.ReviewCreated -> {
                            if (event.review.idUser == currentUserId) return@collect
                            if (event.review.idGame == vm.game.value?.idGame) {
                                vm.prependReview(event.review)
                            }
                        }
                        is SocketEvent.ReviewDeleted -> {
                            vm.removeReview(event.idReview)
                        }
                        is SocketEvent.GamePresence -> {
                            if (event.gameId == vm.game.value?.idGame) {
                                tvViewers.isVisible = event.count > 1
                                tvViewers.text = getString(R.string.game_viewers, event.count)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
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

    private fun setupSimilarGames(view: View) {
        similarAdapter = GameCardAdapter { game ->
            // Pop the current GameDetail and replace with the clicked game to avoid stacking
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.gameDetailFragment, true)
                .build()
            findNavController().navigate(
                R.id.gameDetailFragment,
                bundleOf("slug" to game.slug),
                navOptions
            )
        }
        view.findViewById<RecyclerView>(R.id.rvSimilarGames).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter       = similarAdapter
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
        view.findViewById<RatingBar>(R.id.ratingBar).setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser) vm.updateRating(rating)
        }
    }

    private fun observeVm(view: View) {
        val progressBar = view.findViewById<View>(R.id.progressBar)
        val contentRoot = view.findViewById<View>(R.id.contentRoot)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.isLoading.collect { loading ->
                    progressBar.isVisible = loading
                    contentRoot.isVisible = !loading
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.game.collect { game ->
                    game ?: return@collect
                    view.findViewById<TextView>(R.id.tvGameTitle).text   = game.title
                    view.findViewById<TextView>(R.id.tvDeveloper).text   =
                        getString(R.string.game_developer_year,
                            game.developer ?: getString(R.string.developer_unknown),
                            DateUtils.extractYear(game.releaseDate))
                    view.findViewById<TextView>(R.id.tvDescription).text = game.description ?: "No description available."
                    ImageUtils.loadBanner(requireContext(), game.backgroundUrl, view.findViewById(R.id.imgBanner))
                    ImageUtils.loadGameCover(requireContext(), game.coverUrl, view.findViewById(R.id.imgCover))
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.reviews, vm.reviewsLoading) { reviews, loading -> reviews to loading }
                    .collect { (reviews, loading) ->
                        reviewAdapter.submitList(reviews)
                        view.findViewById<View>(R.id.tvNoReviews).isVisible = !loading && reviews.isEmpty()
                    }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.extras.collect { extras ->
                    extras ?: return@collect
                    renderGenres(view, extras.genres)
                    renderSimilarGames(view, extras.similarGames)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.stats.collect { stats ->
                    stats ?: return@collect
                    renderStats(view, stats)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.actionError.collect { msg ->
                    Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderGenres(view: View, genres: List<GameGenre>) {
        val section = view.findViewById<View>(R.id.llGenresSection)
        if (genres.isEmpty()) {
            section.isVisible = false
            return
        }
        section.isVisible = true
        val llGenres = view.findViewById<LinearLayout>(R.id.llGenres)
        llGenres.removeAllViews()
        val dp   = resources.displayMetrics.density
        val hPad = (10 * dp).toInt()
        val vPad = (4 * dp).toInt()
        val gap  = (8 * dp).toInt()
        genres.forEach { genre ->
            val chip = TextView(requireContext()).apply {
                text = genre.name
                textSize = 13f
                setTextColor(resources.getColor(R.color.text_main, null))
                setBackgroundResource(R.drawable.shape_rounded_card)
                setPadding(hPad, vPad, hPad, vPad)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.setMargins(0, 0, gap, 0) }
            }
            llGenres.addView(chip)
        }
    }

    private fun renderSimilarGames(view: View, games: List<Game>) {
        val section = view.findViewById<View>(R.id.llSimilarSection)
        if (games.isEmpty()) {
            section.isVisible = false
            return
        }
        section.isVisible = true
        similarAdapter.submitList(games)
    }

    private fun renderStats(view: View, stats: GameStatsResponse) {
        val isEmpty = stats.ratingDistribution.isEmpty() &&
                      stats.statusDistribution.isEmpty() &&
                      stats.totalRatings == 0
        val section = view.findViewById<View>(R.id.llStatsSection)
        if (isEmpty) {
            section.isVisible = false
            return
        }
        section.isVisible = true

        view.findViewById<TextView>(R.id.tvAvgRating).text =
            getString(R.string.game_avg_rating,
                stats.avgRating ?: getString(R.string.avg_rating_none),
                stats.totalRatings)

        val llHistogram = view.findViewById<LinearLayout>(R.id.llRatingHistogram)
        llHistogram.removeAllViews()
        val maxCount = stats.ratingDistribution.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        (1..10).map { it * 0.5f }.forEach { r ->
            val count = stats.ratingDistribution
                .find { kotlin.math.abs(it.rating - r) < 0.01f }?.count ?: 0
            llHistogram.addView(buildRatingHistogramRow(requireContext(), r, count, maxCount))
        }

        val llStatus = view.findViewById<LinearLayout>(R.id.llStatusDistribution)
        llStatus.removeAllViews()
        listOf("played", "playing", "plan_to_play", "dropped").forEach { key ->
            val entry = stats.statusDistribution.find { it.status == key }
            if (entry != null && entry.count > 0) {
                llStatus.addView(buildStatusRow(requireContext(), entry.status, entry.percentage))
            }
        }
    }

    private fun buildRatingHistogramRow(ctx: Context, rating: Float, count: Int, maxCount: Int): View {
        val dp   = ctx.resources.displayMetrics.density
        val dp4  = (4  * dp).toInt()
        val dp8  = (8  * dp).toInt()
        val dp12 = (12 * dp).toInt()
        val dp32 = (32 * dp).toInt()

        val row = LinearLayout(ctx).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp4) }
        }

        val tvLabel = TextView(ctx).apply {
            text      = getString(R.string.format_rating_label, rating)
            textSize  = 12f
            setTextColor(ctx.getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(dp32, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val fillWeight   = if (maxCount > 0) count.toFloat() / maxCount else 0f
        val barContainer = LinearLayout(ctx).apply {
            orientation  = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, dp12, 1f)
            setBackgroundColor(ctx.getColor(R.color.divider))
        }
        barContainer.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fillWeight)
            setBackgroundColor(ctx.getColor(R.color.brand_cyan))
        })
        barContainer.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fillWeight)
        })

        val tvCount = TextView(ctx).apply {
            text      = count.toString()
            textSize  = 12f
            setTextColor(ctx.getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = dp8 }
        }

        row.addView(tvLabel)
        row.addView(barContainer)
        row.addView(tvCount)
        return row
    }

    private fun buildStatusRow(ctx: Context, status: String, percentage: Int): View {
        val dp   = ctx.resources.displayMetrics.density
        val dp4  = (4  * dp).toInt()
        val dp8  = (8  * dp).toInt()
        val dp16 = (16 * dp).toInt()
        val dp80 = (80 * dp).toInt()

        val row = LinearLayout(ctx).apply {
            orientation  = LinearLayout.HORIZONTAL
            gravity      = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, dp4) }
        }

        val tvLabel = TextView(ctx).apply {
            text      = StatusUtils.label(status)
            textSize  = 12f
            setTextColor(ctx.getColor(R.color.text_main))
            layoutParams = LinearLayout.LayoutParams(dp80, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max      = 100
            progress = percentage
            layoutParams = LinearLayout.LayoutParams(0, dp16, 1f)
            progressTintList = android.content.res.ColorStateList.valueOf(
                ctx.getColor(StatusUtils.colorRes(status))
            )
        }

        val tvPct = TextView(ctx).apply {
            text      = getString(R.string.format_percentage, percentage)
            textSize  = 12f
            setTextColor(ctx.getColor(R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginStart = dp8 }
        }

        row.addView(tvLabel)
        row.addView(bar)
        row.addView(tvPct)
        return row
    }
}
