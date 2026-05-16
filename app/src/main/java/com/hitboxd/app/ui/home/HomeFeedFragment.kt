package com.hitboxd.app.ui.home

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.ActivityCardAdapter
import com.hitboxd.app.common.adapter.GameCardAdapter
import com.hitboxd.app.common.adapter.UserSuggestionAdapter
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class HomeFeedViewModel : ViewModel() {

    private val gameRepo     = GameRepository()
    private val activityRepo = ActivityRepository()
    private val userRepo     = UserRepository()

    private val _newGames        = MutableStateFlow<List<Game>>(emptyList())
    val newGames: StateFlow<List<Game>> = _newGames

    private val _popularGames    = MutableStateFlow<List<Game>>(emptyList())
    val popularGames: StateFlow<List<Game>> = _popularGames

    private val _friendsActivity = MutableStateFlow<List<Activity>>(emptyList())
    val friendsActivity: StateFlow<List<Activity>> = _friendsActivity

    private val _recommended     = MutableStateFlow<List<Game>>(emptyList())
    val recommended: StateFlow<List<Game>> = _recommended

    private val _suggestions     = MutableStateFlow<List<SuggestionItem>>(emptyList())
    val suggestions: StateFlow<List<SuggestionItem>> = _suggestions

    private val _streak          = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    private val _username        = MutableStateFlow("Player")
    val username: StateFlow<String> = _username

    private val _isRefreshing    = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _activeGenre     = MutableStateFlow<String?>(null)

    private val _followError     = MutableStateFlow<String?>(null)
    val followError: StateFlow<String?> = _followError

    private val _errorMessage    = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage
    fun clearError() { _errorMessage.value = null }

    fun loadAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            coroutineScope {
                listOf(
                    async { fetchUsername() },
                    async { fetchNew() },
                    async { fetchPopular(_activeGenre.value) },
                    async { fetchFeed() },
                    async { fetchRecommended() },
                    async { fetchSuggestions() },
                    async { fetchStreak() }
                ).awaitAll()
            }
            _isRefreshing.value = false
        }
    }

    fun refresh() = loadAll()

    fun setGenre(genre: String?) {
        _activeGenre.value = genre
        viewModelScope.launch { fetchPopular(genre) }
    }

    fun toggleFollow(item: SuggestionItem) {
        val wasFollowing = item.isFollowing
        _suggestions.value = _suggestions.value.map {
            if (it.user.idUser == item.user.idUser) it.copy(isFollowing = !wasFollowing) else it
        }
        viewModelScope.launch {
            val result = if (wasFollowing) userRepo.unfollowUser(item.user.idUser)
                         else userRepo.followUser(item.user.idUser)
            if (result is NetworkResult.Error) {
                _suggestions.value = _suggestions.value.map {
                    if (it.user.idUser == item.user.idUser) it.copy(isFollowing = wasFollowing) else it
                }
                _followError.value = result.message
            }
        }
    }

    fun clearFollowError() { _followError.value = null }

    private suspend fun fetchUsername() {
        when (val r = userRepo.getMyProfile()) {
            is NetworkResult.Success -> _username.value = r.data.username
            else -> {}
        }
    }

    private suspend fun fetchNew() {
        when (val r = gameRepo.getNewReleases()) {
            is NetworkResult.Success -> _newGames.value = r.data
            is NetworkResult.Error   -> _errorMessage.value = r.message
            else -> {}
        }
    }

    private suspend fun fetchPopular(genre: String? = null) {
        when (val r = gameRepo.getPopular(genre)) {
            is NetworkResult.Success -> _popularGames.value = r.data
            is NetworkResult.Error   -> _errorMessage.value = r.message
            else -> {}
        }
    }

    private suspend fun fetchFeed() {
        when (val r = activityRepo.getFeed()) {
            is NetworkResult.Success -> _friendsActivity.value = r.data
            is NetworkResult.Error   -> _errorMessage.value = r.message
            else -> {}
        }
    }

    private suspend fun fetchRecommended() {
        when (val r = gameRepo.getRecommended()) {
            is NetworkResult.Success -> _recommended.value = r.data
            else -> {}
        }
    }

    private suspend fun fetchSuggestions() {
        when (val r = userRepo.getSuggestions(6)) {
            is NetworkResult.Success -> _suggestions.value = r.data.map { SuggestionItem(it) }
            else -> {}
        }
    }

    private suspend fun fetchStreak() {
        when (val r = activityRepo.getStreak()) {
            is NetworkResult.Success -> _streak.value = r.data.streak
            else -> {}
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class HomeFeedFragment : Fragment() {

    private val vm: HomeFeedViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home_feed, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        fun navigateToGame(slug: String) = findNavController().navigate(
            R.id.action_homeFeedFragment_to_gameDetailFragment,
            bundleOf("slug" to slug)
        )

        fun navigateToProfile(username: String) = findNavController().navigate(
            R.id.action_homeFeedFragment_to_publicProfileFragment,
            bundleOf("username" to username)
        )

        // Adapters
        val feedAdapter        = ActivityCardAdapter { it.slug?.let { s -> navigateToGame(s) } }
        val newAdapter         = GameCardAdapter     { navigateToGame(it.slug) }
        val popularAdapter     = GameCardAdapter     { navigateToGame(it.slug) }
        val recommendedAdapter = GameCardAdapter     { navigateToGame(it.slug) }
        val suggestionsAdapter = UserSuggestionAdapter(
            onClick  = { item -> navigateToProfile(item.user.username) },
            onFollow = { item -> vm.toggleFollow(item) }
        )

        // RecyclerViews
        fun setupHorizontal(id: Int, adapter: RecyclerView.Adapter<*>) {
            view.findViewById<RecyclerView>(id).apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                this.adapter  = adapter
            }
        }
        setupHorizontal(R.id.rvFriendsActivity, feedAdapter)
        setupHorizontal(R.id.rvNewReleases,     newAdapter)
        setupHorizontal(R.id.rvPopular,         popularAdapter)
        setupHorizontal(R.id.rvRecommended,     recommendedAdapter)
        view.findViewById<RecyclerView>(R.id.rvSuggestions).apply {
            layoutManager = LinearLayoutManager(context)
            adapter       = suggestionsAdapter
        }

        // Genre tabs
        val genres = listOf(
            getString(R.string.genre_tab_all) to null as String?,
            "Action"    to "Action",
            "RPG"       to "Role-playing (RPG)",
            "Strategy"  to "Strategy",
            "Adventure" to "Adventure",
            "Sports"    to "Sport"
        )
        val genreTabs = view.findViewById<TabLayout>(R.id.genreTabs)
        genres.forEach { (label, value) ->
            genreTabs.addTab(genreTabs.newTab().setText(label).setTag(value))
        }
        genreTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab)   { vm.setGenre(tab.tag as String?) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // SwipeRefresh
        view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
            .setOnRefreshListener { vm.refresh() }

        // Observe
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.isRefreshing.collect { refreshing ->
                    view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh).isRefreshing = refreshing
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.username.collect { name ->
                    view.findViewById<TextView>(R.id.tvWelcome).text =
                        getString(R.string.welcome_back, name)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.streak.collect { streak ->
                    val llStreak = view.findViewById<LinearLayout>(R.id.llStreak)
                    if (streak > 0) {
                        llStreak.isVisible = true
                        view.findViewById<TextView>(R.id.tvStreakCount).text = streak.toString()
                    } else {
                        llStreak.isVisible = false
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.recommended.collect { games ->
                    view.findViewById<View>(R.id.llRecommendedSection).isVisible = true
                    view.findViewById<View>(R.id.rvRecommended).isVisible = games.isNotEmpty()
                    view.findViewById<View>(R.id.tvRecommendationsEmpty).isVisible = games.isEmpty()
                    recommendedAdapter.submitList(games)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.friendsActivity.collect { feedAdapter.submitList(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.newGames.collect { newAdapter.submitList(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.popularGames.collect { popularAdapter.submitList(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.suggestions.collect { suggestionsAdapter.submitList(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.followError.collect { error ->
                    error ?: return@collect
                    Snackbar.make(view, error, Snackbar.LENGTH_SHORT).show()
                    vm.clearFollowError()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.errorMessage.collect { msg ->
                    msg ?: return@collect
                    Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
                    vm.clearError()
                }
            }
        }

        vm.loadAll()
    }
}
