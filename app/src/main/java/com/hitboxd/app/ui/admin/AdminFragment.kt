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
import com.hitboxd.app.common.adapter.ReportedReviewAdapter
import com.hitboxd.app.common.dialog.ReviewDetailDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.GameRepository
import com.hitboxd.app.data.repository.ReviewRepository
import com.hitboxd.app.data.repository.UserRepository
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class AdminViewModel : ViewModel() {

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

    private val _toast = MutableSharedFlow<Pair<String, Boolean>>()
    val toast: SharedFlow<Pair<String, Boolean>> = _toast

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

        setupGamesRecycler(view)
        setupReportedRecycler(view)
        observeVm(view)

        vm.loadAll()
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
            vm.games.collect { games ->
                view.findViewById<TextView>(R.id.tvCachedGames).text = games.size.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.reportedReviews.collect { reviews ->
                view.findViewById<TextView>(R.id.tvPendingReports).text = reviews.size.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.totalUsers.collect { count ->
                view.findViewById<TextView>(R.id.tvTotalUsers).text = count.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.adminUserId.collect { id ->
                view.findViewById<TextView>(R.id.tvAdminId).text =
                    if (id != -1) id.toString() else session.getUserId().toString()
            }
        }

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
}
