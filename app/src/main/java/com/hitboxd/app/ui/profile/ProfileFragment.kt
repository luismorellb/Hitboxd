package com.hitboxd.app.ui.profile

import android.content.Intent
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.*
import com.hitboxd.app.common.dialog.CreateListDialogFragment
import com.hitboxd.app.common.dialog.ConfirmDeleteDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import com.hitboxd.app.ui.landing.LandingActivity
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
    private val authRepo     = AuthRepository()

    private val _user          = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _library       = MutableStateFlow<List<Activity>>(emptyList())
    val library: StateFlow<List<Activity>> = _library

    private val _reviews       = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _feed          = MutableStateFlow<List<Activity>>(emptyList())
    val feed: StateFlow<List<Activity>> = _feed

    private val _userLists     = MutableStateFlow<List<UserList>>(emptyList())
    val userLists: StateFlow<List<UserList>> = _userLists

    private val _followingList = MutableStateFlow<List<Follow>>(emptyList())
    val followingList: StateFlow<List<Follow>> = _followingList

    private val _followersList = MutableStateFlow<List<Follow>>(emptyList())
    val followersList: StateFlow<List<Follow>> = _followersList

    private val _isAdmin       = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin

    private val _isLoading     = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Filtros derivados (no StateFlow adicionales para no duplicar datos)
    val favoriteGames get() = _library.value.filter { it.isFavorite }
    val likedGames    get() = _library.value.filter { it.isLiked }

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = userRepo.getMyProfile()) {
                is NetworkResult.Success -> {
                    _user.value   = r.data
                    _isAdmin.value = r.data.role == "admin"
                    val uid = r.data.idUser
                    launch { loadLibrary() }
                    launch { loadReviews(uid) }
                    launch { loadFeed() }
                    launch { loadLists(uid) }
                }
                else -> {}
            }
            _isLoading.value = false
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

    private suspend fun loadFeed() {
        when (val r = activityRepo.getFeed()) {
            is NetworkResult.Success -> _feed.value = r.data
            else -> {}
        }
    }

    private suspend fun loadLists(uid: Int) {
        when (val r = listRepo.getUserLists(uid)) {
            is NetworkResult.Success -> _userLists.value = r.data
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
            when (val r = listRepo.createList(title, description)) {
                is NetworkResult.Success -> {
                    // Recargar listas
                    val uid = _user.value?.idUser ?: return@launch
                    loadLists(uid)
                }
                else -> {}
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}

// ─── FRAGMENT HOST ───────────────────────────────────────
class ProfileFragment : Fragment() {

    private val vm: ProfileViewModel by viewModels()
    private val tabLabels = listOf(
        "PROFILE", "ACTIVITY", "GAMES", "REVIEWS", "LISTS", "NETWORK", "DIARY", "LIKES"
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
            3 -> ReviewsTabFragment()
            4 -> ListsTabFragment()
            5 -> NetworkTabFragment()
            6 -> DiaryTabFragment()
            7 -> LikedGamesTabFragment()
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
            vm.library.collect { list ->
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
            vm.feed.collect { adapter.submitList(it) }
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

// ─── TAB 6: DIARY ────────────────────────────────────────
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

// ─── TAB 7: LIKES ────────────────────────────────────────
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
            vm.library.collect { list ->
                adapter.submitList(vm.likedGames.map {
                    Game(idGame = it.idGame, title = it.title ?: "", slug = it.slug ?: "", coverUrl = it.coverUrl)
                })
            }
        }
    }
}
