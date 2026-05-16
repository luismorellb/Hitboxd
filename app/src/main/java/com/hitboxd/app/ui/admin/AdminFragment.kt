package com.hitboxd.app.ui.admin

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.AdminGamesAdapter
import com.hitboxd.app.common.adapter.AdminUserAdapter
import com.hitboxd.app.common.adapter.ReportedReviewAdapter
import com.hitboxd.app.common.dialog.ReviewDetailDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.AdminRepository
import com.hitboxd.app.data.repository.GameRepository
import com.hitboxd.app.data.repository.ReviewRepository
import com.hitboxd.app.data.repository.UserRepository
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class AdminViewModel : ViewModel() {

    private val adminRepo  = AdminRepository()
    private val gameRepo   = GameRepository()
    private val reviewRepo = ReviewRepository()
    private val userRepo   = UserRepository()

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    private val _reportedReviews = MutableStateFlow<List<Review>>(emptyList())
    val reportedReviews: StateFlow<List<Review>> = _reportedReviews

    private val _totalUsers = MutableStateFlow(0)
    val totalUsers: StateFlow<Int> = _totalUsers

    private val _adminUserId = MutableStateFlow(-1)
    val adminUserId: StateFlow<Int> = _adminUserId

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _stats = MutableStateFlow<AdminGlobalStats?>(null)
    val stats: StateFlow<AdminGlobalStats?> = _stats

    private val _toast = MutableSharedFlow<Pair<String, Boolean>>()
    val toast: SharedFlow<Pair<String, Boolean>> = _toast

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users

    private val _userPage = MutableStateFlow(1)
    val userPage: StateFlow<Int> = _userPage

    private val _userTotalPages = MutableStateFlow(1)
    val userTotalPages: StateFlow<Int> = _userTotalPages

    private val _userQuery = MutableStateFlow("")

    fun loadUsers() {
        viewModelScope.launch {
            when (val r = userRepo.getAllUsersAdmin(
                query = _userQuery.value.ifBlank { null },
                page  = _userPage.value,
                limit = 20
            )) {
                is NetworkResult.Success -> {
                    _users.value = r.data.users
                    _userTotalPages.value =
                        ((r.data.total + r.data.limit - 1) / r.data.limit).coerceAtLeast(1)
                }
                else -> {}
            }
        }
    }

    fun loadGlobalStats() {
        viewModelScope.launch {
            when (val r = adminRepo.getGlobalStats()) {
                is NetworkResult.Success -> _stats.value = r.data
                is NetworkResult.Error   -> _toast.emit("Stats error: ${r.message}" to true)
                else -> {}
            }
        }
    }

    fun setQuery(q: String) {
        _userQuery.value = q
        _userPage.value  = 1
        loadUsers()
    }

    fun nextPage() {
        if (_userPage.value < _userTotalPages.value) {
            _userPage.value += 1
            loadUsers()
        }
    }

    fun prevPage() {
        if (_userPage.value > 1) {
            _userPage.value -= 1
            loadUsers()
        }
    }

    fun toggleBanUser(user: User) {
        viewModelScope.launch {
            val result = if (user.isVisible) userRepo.banUser(user.idUser)
                         else userRepo.unbanUser(user.idUser)
            when (result) {
                is NetworkResult.Success -> {
                    loadUsers()
                    _toast.emit((if (user.isVisible) "User banned" else "User unbanned") to false)
                }
                is NetworkResult.Error -> _toast.emit("Error: ${result.message}" to true)
                else -> {}
            }
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            launch {
                when (val r = gameRepo.getTrending()) {
                    is NetworkResult.Success -> _games.value = r.data
                    else -> {}
                }
            }
            launch {
                when (val r = reviewRepo.getReported()) {
                    is NetworkResult.Success -> _reportedReviews.value = r.data
                    else -> {}
                }
            }
            launch {
                when (val r = userRepo.getUserCount()) {
                    is NetworkResult.Success -> _totalUsers.value = r.data.count
                    else -> {}
                }
            }
            launch {
                when (val r = userRepo.getMyProfile()) {
                    is NetworkResult.Success -> _adminUserId.value = r.data.idUser
                    else -> {}
                }
            }
            launch { loadUsers() }
            launch { loadGlobalStats() }
            _isLoading.value = false
        }
    }

    fun approveReview(reviewId: Int) {
        viewModelScope.launch {
            when (val r = reviewRepo.approveReview(reviewId)) {
                is NetworkResult.Success -> {
                    _reportedReviews.value = _reportedReviews.value.filter { it.idReview != reviewId }
                    _toast.emit("Review approved" to false)
                }
                is NetworkResult.Error -> _toast.emit("Error: ${r.message}" to true)
                else -> {}
            }
        }
    }

    fun deleteReview(reviewId: Int) {
        viewModelScope.launch {
            when (val r = reviewRepo.removeReview(reviewId)) {
                is NetworkResult.Success -> {
                    _reportedReviews.value = _reportedReviews.value.filter { it.idReview != reviewId }
                    _toast.emit("Review deleted" to false)
                }
                is NetworkResult.Error -> _toast.emit("Error: ${r.message}" to true)
                else -> {}
            }
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class AdminFragment : Fragment() {

    private val vm: AdminViewModel by viewModels()
    private lateinit var session: SessionManager
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_admin, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())

        if (!session.isAdmin()) {
            Snackbar.make(view, "Acceso denegado", Snackbar.LENGTH_SHORT)
                .setBackgroundTint(resources.getColor(R.color.brand_red, null))
                .show()
            findNavController().popBackStack()
            return
        }

        setupUsersRecycler(view)
        setupGamesRecycler(view)
        setupReportedRecycler(view)
        observeVm(view)

        vm.loadAll()
    }

    private fun setupUsersRecycler(view: View) {
        val adapter = AdminUserAdapter { user ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(if (user.isVisible) R.string.admin_btn_ban else R.string.admin_btn_unban))
                .setMessage(buildString {
                    append(if (user.isVisible) getString(R.string.admin_btn_ban) else getString(R.string.admin_btn_unban))
                    append(" ")
                    append(user.username)
                    append("?")
                })
                .setPositiveButton(R.string.save) { _, _ -> vm.toggleBanUser(user) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        view.findViewById<RecyclerView>(R.id.rvUsers).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }

        view.findViewById<SearchView>(R.id.searchUsers)
            .setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    searchJob?.cancel()
                    searchJob = viewLifecycleOwner.lifecycleScope.launch {
                        delay(400)
                        vm.setQuery(newText.orEmpty())
                    }
                    return true
                }
            })

        view.findViewById<Button>(R.id.btnPrevPage).setOnClickListener { vm.prevPage() }
        view.findViewById<Button>(R.id.btnNextPage).setOnClickListener { vm.nextPage() }

        val tvPage = view.findViewById<TextView>(R.id.tvPageIndicator)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.users.collect { adapter.submitList(it) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            combine(vm.userPage, vm.userTotalPages) { page, total -> page to total }
                .collect { (page, total) ->
                    tvPage.text = getString(R.string.admin_page_indicator, page, total)
                    view.findViewById<Button>(R.id.btnPrevPage).isEnabled = page > 1
                    view.findViewById<Button>(R.id.btnNextPage).isEnabled = page < total
                }
        }
    }

    private fun setupGamesRecycler(view: View) {
        val adapter = AdminGamesAdapter { game ->
            findNavController().navigate(
                R.id.action_adminFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvGames).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.games.collect { adapter.submitList(it) }
        }
    }

    private fun setupReportedRecycler(view: View) {
        val adapter = ReportedReviewAdapter(
            onView = { review ->
                ReviewDetailDialogFragment.newInstance(review)
                    .show(parentFragmentManager, "review_detail")
            },
            onDelete = { review ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm")
                    .setMessage("Delete review #${review.idReview}? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> vm.deleteReview(review.idReview) }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onApprove = { review ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Confirm")
                    .setMessage("Approve review #${review.idReview}? This will dismiss all reports.")
                    .setPositiveButton("Approve") { _, _ -> vm.approveReview(review.idReview) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        view.findViewById<RecyclerView>(R.id.rvReportedReviews).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.reportedReviews.collect { reviews ->
                adapter.submitList(reviews)
                view.findViewById<TextView>(R.id.tvReportCount).text = reviews.size.toString()
            }
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
            vm.stats.collect { stats ->
                stats ?: return@collect
                view.findViewById<TextView>(R.id.tvActiveUsers).text    = stats.counts.activeUsers.toString()
                view.findViewById<TextView>(R.id.tvBannedUsers).text    = stats.counts.bannedUsers.toString()
                view.findViewById<TextView>(R.id.tvTotalGames).text     = stats.counts.totalGames.toString()
                view.findViewById<TextView>(R.id.tvTotalReviews).text   = stats.counts.totalReviews.toString()
                view.findViewById<TextView>(R.id.tvPendingReports).text = stats.counts.pendingReports.toString()
                view.findViewById<TextView>(R.id.tvRecentSignups).text  = stats.counts.recentSignups7d.toString()
                view.findViewById<TextView>(R.id.tvAdminId).text        = stats.admin.idUser.toString()
                renderChart(view.findViewById(R.id.llReviewsChart), stats.reviewsPerDay7d)
            }
        }

        view.findViewById<Button>(R.id.btnRefreshStats).setOnClickListener { vm.loadGlobalStats() }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.toast.collect { (msg, isError) ->
                Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).apply {
                    setBackgroundTint(
                        resources.getColor(
                            if (isError) R.color.brand_red else R.color.brand_green, null
                        )
                    )
                }.show()
            }
        }
    }

    private fun renderChart(container: LinearLayout, data: List<DayCount>) {
        container.removeAllViews()
        if (data.isEmpty()) return
        val maxCount = data.maxOf { it.count }.coerceAtLeast(1)
        val maxBarPx = (100 * resources.displayMetrics.density).toInt()
        data.forEach { item ->
            val col = layoutInflater.inflate(R.layout.col_bar_chart, container, false)
            col.findViewById<TextView>(R.id.tvBarCount).text = item.count.toString()
            col.findViewById<TextView>(R.id.tvBarDate).text  = item.date.takeLast(5)
            val barH    = ((item.count.toFloat() / maxCount) * maxBarPx).toInt().coerceAtLeast(4)
            val spacerH = maxBarPx - barH
            col.findViewById<View>(R.id.barSpacer).layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, spacerH)
            col.findViewById<View>(R.id.barFill).layoutParams =
                LinearLayout.LayoutParams(
                    (16 * resources.displayMetrics.density).toInt(), barH
                )
            container.addView(col)
        }
    }
}
