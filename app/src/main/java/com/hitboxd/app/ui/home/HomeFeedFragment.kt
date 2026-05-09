package com.hitboxd.app.ui.home

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.ActivityCardAdapter
import com.hitboxd.app.common.adapter.GameCardAdapter
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.*
import com.hitboxd.app.utils.SessionManager
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

    private val _username        = MutableStateFlow("Player")
    val username: StateFlow<String> = _username

    fun loadAll() {
        viewModelScope.launch {
            launch {
                when (val r = userRepo.getMyProfile()) {
                    is NetworkResult.Success -> _username.value = r.data.username
                    else -> {}
                }
            }
            launch {
                when (val r = gameRepo.getNewReleases()) {
                    is NetworkResult.Success -> _newGames.value = r.data
                    else -> {}
                }
            }
            launch {
                when (val r = gameRepo.getPopular()) {
                    is NetworkResult.Success -> _popularGames.value = r.data
                    else -> {}
                }
            }
            launch {
                when (val r = activityRepo.getFeed()) {
                    is NetworkResult.Success -> _friendsActivity.value = r.data
                    else -> {}
                }
            }
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

        // Adapters
        val feedAdapter    = ActivityCardAdapter { it.slug?.let { s -> navigateToGame(s) } }
        val newAdapter     = GameCardAdapter     { navigateToGame(it.slug) }
        val popularAdapter = GameCardAdapter     { navigateToGame(it.slug) }

        // RecyclerViews
        fun setupHorizontal(id: Int, adapter: RecyclerView.Adapter<*>) {
            view.findViewById<RecyclerView>(id).apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                this.adapter  = adapter
            }
        }
        setupHorizontal(R.id.rvFriendsActivity, feedAdapter)
        setupHorizontal(R.id.rvNewReleases, newAdapter)
        setupHorizontal(R.id.rvPopular, popularAdapter)

        // Observar
        viewLifecycleOwner.lifecycleScope.launch {
            vm.username.collect { name ->
                view.findViewById<TextView>(R.id.tvWelcome).text = "Welcome back, $name."
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { vm.friendsActivity.collect { feedAdapter.submitList(it) } }
        viewLifecycleOwner.lifecycleScope.launch { vm.newGames.collect       { newAdapter.submitList(it) } }
        viewLifecycleOwner.lifecycleScope.launch { vm.popularGames.collect   { popularAdapter.submitList(it) } }

        vm.loadAll()
    }
}
