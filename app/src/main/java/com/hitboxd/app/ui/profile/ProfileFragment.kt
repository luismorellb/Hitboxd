package com.hitboxd.app.ui.profile

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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.*
import com.hitboxd.app.common.dialog.CreateListDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import com.hitboxd.app.utils.ImageUtils
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class ProfileViewModel : ViewModel() {

    private val userRepo     = UserRepository()
    private val activityRepo = ActivityRepository()
    private val reviewRepo   = ReviewRepository()
    private val listRepo     = ListRepository()

    private val _user          = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _library       = MutableStateFlow<List<Activity>>(emptyList())
    val library: StateFlow<List<Activity>> = _library

    private val _reviews       = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _userLists     = MutableStateFlow<List<UserList>>(emptyList())
    val userLists: StateFlow<List<UserList>> = _userLists

    private val _followingList = MutableStateFlow<List<Follow>>(emptyList())
    val followingList: StateFlow<List<Follow>> = _followingList

    private val _followersList = MutableStateFlow<List<Follow>>(emptyList())
    val followersList: StateFlow<List<Follow>> = _followersList

    private val _watchlist     = MutableStateFlow<List<Game>>(emptyList())
    val watchlist: StateFlow<List<Game>> = _watchlist

    private val _stats         = MutableStateFlow<UserStatsResponse?>(null)
    val stats: StateFlow<UserStatsResponse?> = _stats

    // Filtros derivados (no StateFlow adicionales para no duplicar datos)
    val favoriteGames get() = _library.value.filter { it.isFavorite }
    val likedGames    get() = _library.value.filter { it.isLiked }

    fun loadAll() {
        viewModelScope.launch {
            when (val r = userRepo.getMyProfile()) {
                is NetworkResult.Success -> {
                    _user.value = r.data
                    val uid = r.data.idUser
                    launch { loadLibrary() }
                    launch { loadReviews(uid) }
                    launch { loadLists(uid) }
                    launch { loadWatchlist() }
                    launch { loadStats() }
                }
                else -> {}
            }
        }
    }

    private suspend fun loadLibrary() {
        when (val r = activityRepo.getUserLibrary()) {
            is NetworkResult.Success -> _library.value = r.data
            else -> {}
        }
    }

    private suspend fun loadReviews(uid: Int) {
        when (val r = reviewRepo.getUserReviews(uid)) {
            is NetworkResult.Success -> _reviews.value = r.data.sortedByDescending { it.createdAt }
            else -> {}
        }
    }

    private suspend fun loadLists(uid: Int) {
        when (val r = listRepo.getUserLists(uid)) {
            is NetworkResult.Success -> _userLists.value = r.data
            else -> {}
        }
    }

    private suspend fun loadWatchlist() {
        when (val r = activityRepo.getWatchlist()) {
            is NetworkResult.Success -> _watchlist.value = r.data
            else -> {}
        }
    }

    private suspend fun loadStats() {
        when (val r = activityRepo.getUserStats()) {
            is NetworkResult.Success -> _stats.value = r.data
            else -> {}
        }
    }

    fun loadNetwork(uid: Int) {
        viewModelScope.launch {
            launch {
                when (val r = userRepo.getFollowing(uid)) {
                    is NetworkResult.Success -> _followingList.value = r.data
                    else -> {}
                }
            }
            launch {
                when (val r = userRepo.getFollowers(uid)) {
                    is NetworkResult.Success -> _followersList.value = r.data
                    else -> {}
                }
            }
        }
    }

    fun createList(title: String, description: String?) {
        viewModelScope.launch {
            when (listRepo.createList(title, description)) {
                is NetworkResult.Success -> {
                    val uid = _user.value?.idUser ?: return@launch
                    loadLists(uid)
                }
                else -> {}
            }
        }
    }
}

// ─── FRAGMENT HOST ───────────────────────────────────────
class ProfileFragment : Fragment() {

    private val vm: ProfileViewModel by viewModels()
    private val tabLabels = listOf(
        "PROFILE", "ACTIVITY", "GAMES", "BACKLOG", "REVIEWS", "LISTS", "NETWORK", "DIARY", "LIKES", "STATS"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)

        viewPager.adapter = ProfileTabAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = tabLabels[pos]
        }.attach()

        observeHeader(view)

        // Botón de logout
        view.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_settingsFragment)
        }

        vm.loadAll()
    }

    private fun observeHeader(view: View) {
        val session = SessionManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            vm.user.collect { user ->
                user ?: return@collect
                view.findViewById<TextView>(R.id.tvUsername).text     = user.username
                view.findViewById<TextView>(R.id.tvBio).text          = user.bio ?: ""
                val tvPronouns = view.findViewById<TextView>(R.id.tvPronouns)
                tvPronouns.isVisible = !user.pronouns.isNullOrBlank()
                tvPronouns.text      = user.pronouns ?: ""
                view.findViewById<TextView>(R.id.tvGamesCount).text     = user.gamesCount.toString()
                view.findViewById<TextView>(R.id.tvFollowersCount).text = user.followersCount.toString()
                view.findViewById<TextView>(R.id.tvFollowingCount).text = user.followingCount.toString()
                ImageUtils.loadAvatar(requireContext(), user.avatarUrl, view.findViewById(R.id.imgAvatar))

                // Guardar en sesión para acceso rápido
                session.saveUserData(user.idUser, user.username, user.role, user.avatarUrl)

                // Botón Admin
                view.findViewById<Button>(R.id.btnAdmin).isVisible = user.role == "admin"
                view.findViewById<Button>(R.id.btnAdmin).setOnClickListener {
                    findNavController().navigate(R.id.action_profileFragment_to_adminFragment)
                }
            }
        }
    }

    inner class ProfileTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = tabLabels.size
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ProfileOverviewFragment()
            1 -> ActivityFeedTabFragment()
            2 -> GamesTabFragment()
            3 -> WatchlistTabFragment()
            4 -> ReviewsTabFragment()
            5 -> ListsTabFragment()
            6 -> NetworkTabFragment()
            7 -> DiaryTabFragment()
            8 -> LikedGamesTabFragment()
            9 -> StatsTabFragment()
            else -> ProfileOverviewFragment()
        }
    }
}

// ─── TAB 0: PROFILE OVERVIEW ─────────────────────────────
class ProfileOverviewFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_profile_overview, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val favAdapter    = GameCardAdapter { game ->
            findNavController().navigate(
                R.id.action_profileFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }
        val miniReviewAdapter = MiniReviewAdapter()

        view.findViewById<RecyclerView>(R.id.rvFavoriteGames).apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = favAdapter
        }
        view.findViewById<RecyclerView>(R.id.rvRecentReviews).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = miniReviewAdapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.library.collect { _ ->
                val favGames = vm.favoriteGames.take(4).map {
                    Game(idGame = it.idGame, title = it.title ?: "", slug = it.slug ?: "", coverUrl = it.coverUrl)
                }
                favAdapter.submitList(favGames)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.reviews.collect { miniReviewAdapter.submitList(it.take(3)) }
        }
    }
}

// ─── TAB 1: ACTIVITY FEED ────────────────────────────────
class ActivityFeedTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_activity_feed, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = ActivityFeedAdapter()
        view.findViewById<RecyclerView>(R.id.rvActivityFeed).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.library.collect { adapter.submitList(it) }
        }
    }
}

// ─── TAB 2: GAMES ────────────────────────────────────────
class GamesTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_games_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = GameCardAdapter { game ->
            findNavController().navigate(
                R.id.action_profileFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvGames).apply {
            layoutManager = GridLayoutManager(context, 3)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.library.collect { list ->
                adapter.submitList(list.map {
                    Game(idGame = it.idGame, title = it.title ?: "", slug = it.slug ?: "", coverUrl = it.coverUrl)
                })
            }
        }
    }
}

// ─── TAB 3: REVIEWS ──────────────────────────────────────
class ReviewsTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_reviews_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = FullReviewAdapter()
        view.findViewById<RecyclerView>(R.id.rvReviews).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.reviews.collect { adapter.submitList(it) }
        }
    }
}

// ─── TAB 4: LISTS ────────────────────────────────────────
class ListsTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_lists_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = UserListAdapter { list ->
            findNavController().navigate(
                R.id.action_profileFragment_to_listDetailFragment,
                bundleOf("listId" to list.idList)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvLists).apply {
            layoutManager = GridLayoutManager(context, 2)
            this.adapter  = adapter
        }
        view.findViewById<Button>(R.id.btnNewList).setOnClickListener {
            CreateListDialogFragment { title, desc ->
                vm.createList(title, desc)
            }.show(childFragmentManager, "createList")
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.userLists.collect { adapter.submitList(it) }
        }
    }
}

// ─── TAB 5: NETWORK ──────────────────────────────────────
class NetworkTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })
    private var showingFollowing = true

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_network_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = UserNetworkAdapter(
            onUserClick = { user ->
                findNavController().navigate(
                    R.id.action_profileFragment_to_publicProfileFragment,
                    bundleOf("username" to user.username)
                )
            },
            onFollow = { follow ->
                viewModelScope().launch {
                    val repo = UserRepository()
                    if (follow.isFollowing) repo.unfollowUser(follow.idUser)
                    else repo.followUser(follow.idUser)
                }
            }
        )
        view.findViewById<RecyclerView>(R.id.rvUsers).apply {
            layoutManager = GridLayoutManager(context, 2)
            this.adapter  = adapter
        }

        val btnFollowing = view.findViewById<Button>(R.id.btnFollowing)
        val btnFollowers = view.findViewById<Button>(R.id.btnFollowers)

        btnFollowing.setOnClickListener {
            showingFollowing = true
            btnFollowing.isSelected = true
            btnFollowers.isSelected = false
            viewLifecycleOwner.lifecycleScope.launch {
                vm.followingList.collect { adapter.submitList(it) }
            }
        }
        btnFollowers.setOnClickListener {
            showingFollowing = false
            btnFollowing.isSelected = false
            btnFollowers.isSelected = true
            viewLifecycleOwner.lifecycleScope.launch {
                vm.followersList.collect { adapter.submitList(it) }
            }
        }

        // Cargar red al abrir el tab
        val uid = vm.user.value?.idUser
        if (uid != null) vm.loadNetwork(uid)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.followingList.collect { if (showingFollowing) adapter.submitList(it) }
        }
    }

    // Helper para lanzar coroutine sin ViewModel scope
    private fun viewModelScope() = viewLifecycleOwner.lifecycleScope
}

// ─── TAB 7: DIARY ────────────────────────────────────────
class DiaryTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_diary_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = DiaryAdapter()
        view.findViewById<RecyclerView>(R.id.rvDiary).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.reviews.collect { adapter.submitList(it) }
        }
    }
}

// ─── TAB 8: LIKES ────────────────────────────────────────
class LikedGamesTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_liked_games_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = GameCardAdapter { game ->
            findNavController().navigate(
                R.id.action_profileFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvLikedGames).apply {
            layoutManager = GridLayoutManager(context, 3)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.library.collect { _ ->
                adapter.submitList(vm.likedGames.map {
                    Game(idGame = it.idGame, title = it.title ?: "", slug = it.slug ?: "", coverUrl = it.coverUrl)
                })
            }
        }
    }
}

// ─── TAB 3: BACKLOG ──────────────────────────────────────
class WatchlistTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_watchlist_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val adapter = GameCardAdapter { game ->
            findNavController().navigate(
                R.id.action_profileFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvWatchlist).apply {
            layoutManager = GridLayoutManager(context, 3)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.watchlist.collect { games ->
                adapter.submitList(games)
                tvEmpty.isVisible = games.isEmpty()
            }
        }
    }
}

// ─── TAB 9: STATS ────────────────────────────────────────
// NOTE: /activity/stats returns data for the authenticated user only.
// This tab is intentionally absent from PublicProfileFragment.
class StatsTabFragment : Fragment() {
    private val vm: ProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_stats_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.stats.collect { stats ->
                stats ?: return@collect
                renderStats(view, stats)
            }
        }
    }

    private fun renderStats(view: View, stats: UserStatsResponse) {
        val ctx = requireContext()

        view.findViewById<TextView>(R.id.tvAvgRating).text =
            stats.avgRating ?: getString(R.string.avg_rating_none)
        view.findViewById<TextView>(R.id.tvRatedCount).text =
            getString(R.string.format_games_rated, stats.ratedCount)

        val llGenres = view.findViewById<LinearLayout>(R.id.llTopGenres)
        llGenres.removeAllViews()
        val maxGenre = stats.genreDistribution.maxOfOrNull { it.count } ?: 1
        stats.genreDistribution.take(5).forEach { entry ->
            llGenres.addView(buildStatRow(ctx, llGenres, entry.genre, entry.count, maxGenre))
        }

        val llYear = view.findViewById<LinearLayout>(R.id.llByYear)
        llYear.removeAllViews()
        val maxYear = stats.yearDistribution.maxOfOrNull { it.count } ?: 1
        stats.yearDistribution.sortedByDescending { it.year }.take(8).forEach { entry ->
            llYear.addView(buildStatRow(ctx, llYear, entry.year.toString(), entry.count, maxYear))
        }

        val llStatus = view.findViewById<LinearLayout>(R.id.llStatusBreakdown)
        llStatus.removeAllViews()
        val maxStatus = stats.statusDistribution.maxOfOrNull { it.count } ?: 1
        stats.statusDistribution.forEach { entry ->
            llStatus.addView(buildStatRow(ctx, llStatus, entry.status, entry.count, maxStatus))
        }
    }

    private fun buildStatRow(
        ctx: android.content.Context,
        parent: ViewGroup,
        label: String,
        count: Int,
        maxCount: Int
    ): View {
        val row = LayoutInflater.from(ctx).inflate(R.layout.row_stat_bar, parent, false)
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<ProgressBar>(R.id.progressBar).apply {
            max      = maxCount
            progress = count
        }
        row.findViewById<TextView>(R.id.tvCount).text = count.toString()
        return row
    }
}
