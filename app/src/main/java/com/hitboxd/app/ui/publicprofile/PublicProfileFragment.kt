package com.hitboxd.app.ui.publicprofile

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
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import com.hitboxd.app.utils.ImageUtils
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class PublicProfileViewModel : ViewModel() {

    private val userRepo     = UserRepository()
    private val reviewRepo   = ReviewRepository()
    private val listRepo     = ListRepository()

    private val _targetUser    = MutableStateFlow<User?>(null)
    val targetUser: StateFlow<User?> = _targetUser

    private val _reviews       = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews

    private val _userLists     = MutableStateFlow<List<UserList>>(emptyList())
    val userLists: StateFlow<List<UserList>> = _userLists

    private val _isFollowing   = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing

    private val _followersCount = MutableStateFlow(0)
    val followersCount: StateFlow<Int> = _followersCount

    private val _publicLibrary = MutableStateFlow<List<PublicActivity>>(emptyList())
    val publicLibrary: StateFlow<List<PublicActivity>> = _publicLibrary

    var myUserId: Int = -1

    fun load(username: String) {
        viewModelScope.launch {
            when (val r = userRepo.getUserByUsername(username)) {
                is NetworkResult.Success -> {
                    val user = r.data
                    _targetUser.value    = user
                    _followersCount.value = user.followersCount

                    // Verificar si lo sigo
                    if (myUserId != -1) {
                        when (val fc = userRepo.checkFollow(user.idUser)) {
                            is NetworkResult.Success -> _isFollowing.value = fc.data.isFollowing
                            else -> {}
                        }
                    }

                    // Cargar contenido en paralelo
                    launch {
                        when (val r2 = reviewRepo.getUserReviews(user.idUser)) {
                            is NetworkResult.Success -> _reviews.value = r2.data.sortedByDescending { it.createdAt }
                            else -> {}
                        }
                    }
                    launch {
                        when (val r2 = listRepo.getUserLists(user.idUser)) {
                            is NetworkResult.Success -> _userLists.value = r2.data
                            else -> {}
                        }
                    }
                    launch {
                        when (val r2 = userRepo.getUserPublicLibrary(user.idUser)) {
                            is NetworkResult.Success -> _publicLibrary.value = r2.data
                            else -> {}
                        }
                    }
                }
                else -> {}
            }
        }
    }

    fun toggleFollow() {
        val targetId    = _targetUser.value?.idUser ?: return
        val wasFollowing = _isFollowing.value

        // Optimistic update
        _isFollowing.value    = !wasFollowing
        _followersCount.value += if (!wasFollowing) 1 else -1

        viewModelScope.launch {
            val result = if (wasFollowing) userRepo.unfollowUser(targetId)
                         else              userRepo.followUser(targetId)
            if (result is NetworkResult.Error) {
                // Revertir
                _isFollowing.value    = wasFollowing
                _followersCount.value += if (wasFollowing) 1 else -1
            }
        }
    }
}

// ─── FRAGMENT HOST ───────────────────────────────────────
class PublicProfileFragment : Fragment() {

    private val vm: PublicProfileViewModel by viewModels()
    private val tabLabels = listOf("PROFILE", "GAMES", "REVIEWS", "LISTS", "LIKES")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_public_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val session  = SessionManager(requireContext())
        vm.myUserId  = session.getUserId()

        val username = arguments?.getString("username") ?: return

        // Si es mi propio perfil → redirigir
        if (session.getUsername() == username) {
            findNavController().navigate(R.id.profileFragment)
            return
        }

        // ViewPager2 + TabLayout
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        viewPager.adapter = PublicTabAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = tabLabels[pos]
        }.attach()

        // Botón Follow
        view.findViewById<Button>(R.id.btnFollow).setOnClickListener { vm.toggleFollow() }
        view.findViewById<Button>(R.id.btnFollow).isVisible = (vm.myUserId != -1)

        observeHeader(view)
        vm.load(username)
    }

    private fun observeHeader(view: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            vm.targetUser.collect { user ->
                user ?: return@collect
                view.findViewById<TextView>(R.id.tvUsername).text      = user.username
                view.findViewById<TextView>(R.id.tvBio).text           = user.bio ?: ""
                val tvPronouns = view.findViewById<TextView>(R.id.tvPronouns)
                tvPronouns.isVisible = !user.pronouns.isNullOrBlank()
                tvPronouns.text      = user.pronouns ?: ""
                view.findViewById<TextView>(R.id.tvGamesCount).text    = user.gamesCount.toString()
                view.findViewById<TextView>(R.id.tvFollowingCount).text = user.followingCount.toString()
                ImageUtils.loadAvatar(requireContext(), user.avatarUrl, view.findViewById(R.id.imgAvatar))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.followersCount.collect {
                view.findViewById<TextView>(R.id.tvFollowersCount).text = it.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.isFollowing.collect { following ->
                val btn = view.findViewById<Button>(R.id.btnFollow)
                btn.text       = if (following) "FOLLOWING" else "+ FOLLOW"
                btn.isSelected = following
            }
        }
    }

    inner class PublicTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount() = tabLabels.size
        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> PubOverviewFragment()
            1 -> PubGamesFragment()
            2 -> PubReviewsFragment()
            3 -> PubListsFragment()
            4 -> PubLikesFragment()
            else -> PubOverviewFragment()
        }
    }
}

// ─── TAB 0: OVERVIEW ─────────────────────────────────────
class PubOverviewFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_profile_overview, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.rvRecentReviews).isVisible = false
        view.findViewById<View>(R.id.rvFavoriteGames).isVisible = false
        view.findViewById<TextView>(R.id.tvLibraryPrivate).isVisible = true
    }
}

// ─── TAB 1: GAMES ────────────────────────────────────────
class PubGamesFragment : Fragment() {
    private val vm: PublicProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_games_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = GameCardAdapter { game ->
            findNavController().navigate(
                R.id.action_publicProfileFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvGames).apply {
            layoutManager = GridLayoutManager(context, 3)
            this.adapter  = adapter
        }
        view.findViewById<TextView>(R.id.tvLibraryPrivate).isVisible = false

        viewLifecycleOwner.lifecycleScope.launch {
            vm.publicLibrary.collect { library ->
                val games = library.map { a ->
                    Game(idGame = a.idGame, title = a.title, slug = a.slug, coverUrl = a.coverUrl)
                }
                adapter.submitList(games)
                view.findViewById<View>(R.id.rvGames).isVisible = games.isNotEmpty()
            }
        }
    }
}

// ─── TAB 2: REVIEWS ──────────────────────────────────────
class PubReviewsFragment : Fragment() {
    private val vm: PublicProfileViewModel by viewModels({ requireParentFragment() })

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

// ─── TAB 3: LISTS ────────────────────────────────────────
class PubListsFragment : Fragment() {
    private val vm: PublicProfileViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_lists_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Ocultar botón de nueva lista en perfil público
        view.findViewById<Button>(R.id.btnNewList).isVisible = false

        val adapter = UserListAdapter { list ->
            findNavController().navigate(
                R.id.action_publicProfileFragment_to_listDetailFragment,
                bundleOf("listId" to list.idList)
            )
        }
        view.findViewById<RecyclerView>(R.id.rvLists).apply {
            layoutManager = GridLayoutManager(context, 2)
            this.adapter  = adapter
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.userLists.collect { adapter.submitList(it) }
        }
    }
}

// ─── TAB 4: LIKES ────────────────────────────────────────
// is_liked es informacion privada — el backend devuelve FALSE para perfiles ajenos;
// mostramos mensaje fijo independientemente del valor del campo.
class PubLikesFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_liked_games_tab, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.rvLikedGames).isVisible = false
        view.findViewById<TextView>(R.id.tvLibraryPrivate).apply {
            isVisible = true
            setText(R.string.public_library_private)
        }
    }
}
