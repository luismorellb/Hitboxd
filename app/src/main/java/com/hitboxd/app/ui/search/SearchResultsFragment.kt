package com.hitboxd.app.ui.search

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
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.GameCardAdapter
import com.hitboxd.app.common.adapter.UserSuggestionAdapter
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.GameRepository
import com.hitboxd.app.data.repository.UserRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class SearchResultsViewModel : ViewModel() {

    private val gameRepo = GameRepository()
    private val userRepo = UserRepository()

    private val _gameResults   = MutableStateFlow<List<Game>>(emptyList())
    val gameResults: StateFlow<List<Game>> = _gameResults

    private val _userResults   = MutableStateFlow<List<User>>(emptyList())
    val userResults: StateFlow<List<User>> = _userResults

    private val _isLoading     = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _hasMoreGames  = MutableStateFlow(false)
    val hasMoreGames: StateFlow<Boolean> = _hasMoreGames

    private val _currentPage   = MutableStateFlow(1)

    private val _total         = MutableStateFlow(0)
    val total: StateFlow<Int> = _total

    private val _userTotal     = MutableStateFlow(0)
    val userTotal: StateFlow<Int> = _userTotal

    private val _errorMessage  = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _currentQuery  = MutableStateFlow("")
    val currentQuery: StateFlow<String> = _currentQuery

    fun setQuery(q: String) {
        if (q.isBlank() || q == _currentQuery.value) return
        _currentQuery.value = q
        _gameResults.value  = emptyList()
        _userResults.value  = emptyList()
        _currentPage.value  = 1
        _total.value        = 0
        _userTotal.value    = 0
        _hasMoreGames.value = false
        search(q, 1)
    }

    fun search(q: String, page: Int = 1) {
        if (q.isBlank()) return
        viewModelScope.launch {
            if (page == 1) _isLoading.value = true else _isLoadingMore.value = true

            when (val r = gameRepo.searchPaginated(q, page, 24)) {
                is NetworkResult.Success -> {
                    val d = r.data
                    _total.value        = d.total
                    _currentPage.value  = d.page
                    _hasMoreGames.value = d.page * 24 < d.total
                    _gameResults.value  = if (page == 1) d.results else _gameResults.value + d.results
                }
                is NetworkResult.Error  -> _errorMessage.value = r.message
                else                    -> {}
            }

            if (page == 1) {
                when (val r = userRepo.searchUsers(q)) {
                    is NetworkResult.Success -> {
                        _userResults.value = r.data
                        _userTotal.value   = r.data.size
                    }
                    else -> {}
                }
                _isLoading.value = false
            } else {
                _isLoadingMore.value = false
            }
        }
    }

    fun loadMoreGames() {
        if (_hasMoreGames.value && !_isLoadingMore.value) {
            search(_currentQuery.value, _currentPage.value + 1)
        }
    }

    fun clearError() { _errorMessage.value = null }
}

// ─── PAGER ADAPTER ───────────────────────────────────────
class SearchPagerAdapter(fragment: SearchResultsFragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 2
    override fun createFragment(position: Int): Fragment = when (position) {
        0    -> GameSearchResultsFragment()
        else -> UserSearchResultsFragment()
    }
}

// ─── GAMES TAB ───────────────────────────────────────────
class GameSearchResultsFragment : Fragment() {

    private val parentVm: SearchResultsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_game_search_results, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rvGames          = view.findViewById<RecyclerView>(R.id.rvGames)
        val progressInitial  = view.findViewById<ProgressBar>(R.id.progressInitial)
        val progressLoadMore = view.findViewById<ProgressBar>(R.id.progressLoadMore)
        val tvEmpty          = view.findViewById<TextView>(R.id.tvEmpty)

        val adapter = GameCardAdapter { game ->
            findNavController().navigate(
                R.id.action_searchResultsFragment_to_gameDetailFragment,
                bundleOf("slug" to game.slug)
            )
        }

        val layoutManager = GridLayoutManager(requireContext(), 3)
        rvGames.layoutManager = layoutManager
        rvGames.adapter = adapter

        rvGames.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= layoutManager.itemCount - 6) parentVm.loadMoreGames()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            parentVm.gameResults.collect { games -> adapter.submitList(games) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            parentVm.isLoading.collect { loading ->
                progressInitial.isVisible = loading
                rvGames.isVisible         = !loading
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            parentVm.isLoadingMore.collect { loading ->
                progressLoadMore.isVisible = loading
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            combine(parentVm.gameResults, parentVm.isLoading) { games, loading ->
                games.isEmpty() && !loading
            }.collect { showEmpty ->
                tvEmpty.isVisible = showEmpty
                if (showEmpty) {
                    tvEmpty.text = getString(R.string.search_empty_games, parentVm.currentQuery.value)
                }
            }
        }
    }
}

// ─── USERS TAB ───────────────────────────────────────────
class UserSearchResultsFragment : Fragment() {

    private val parentVm: SearchResultsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_user_search_results, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rvUsers         = view.findViewById<RecyclerView>(R.id.rvUsers)
        val progressInitial = view.findViewById<ProgressBar>(R.id.progressInitial)
        val tvEmpty         = view.findViewById<TextView>(R.id.tvEmpty)

        val adapter = UserSuggestionAdapter(
            onClick          = { item ->
                findNavController().navigate(
                    R.id.action_searchResultsFragment_to_publicProfileFragment,
                    bundleOf("username" to item.user.username)
                )
            },
            onFollow         = {},
            showFollowButton = false
        )

        rvUsers.layoutManager = LinearLayoutManager(requireContext())
        rvUsers.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            parentVm.userResults.collect { users ->
                adapter.submitList(users.map { SuggestionItem(it) })
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            parentVm.isLoading.collect { loading ->
                progressInitial.isVisible = loading
                rvUsers.isVisible         = !loading
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            combine(parentVm.userResults, parentVm.isLoading) { users, loading ->
                users.isEmpty() && !loading
            }.collect { showEmpty ->
                tvEmpty.isVisible = showEmpty
                if (showEmpty) {
                    tvEmpty.text = getString(R.string.search_empty_users, parentVm.currentQuery.value)
                }
            }
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class SearchResultsFragment : Fragment() {

    private val vm: SearchResultsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search_results, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val query = arguments?.getString("query").orEmpty()

        val toolbar   = view.findViewById<MaterialToolbar>(R.id.toolbar)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)

        toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        toolbar.title = getString(R.string.search_results_title, query)

        viewPager.adapter = SearchPagerAdapter(this)
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0    -> getString(R.string.tab_games)
                else -> getString(R.string.tab_users)
            }
        }.attach()

        viewLifecycleOwner.lifecycleScope.launch {
            vm.total.collect { total ->
                tabLayout.getTabAt(0)?.text = getString(R.string.tab_games_count, total)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.userTotal.collect { total ->
                tabLayout.getTabAt(1)?.text = getString(R.string.tab_users_count, total)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.errorMessage.collect { msg ->
                msg ?: return@collect
                vm.clearError()
            }
        }

        vm.setQuery(query)
    }
}
