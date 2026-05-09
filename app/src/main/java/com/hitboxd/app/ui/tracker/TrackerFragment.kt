package com.hitboxd.app.ui.tracker

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
import com.hitboxd.app.R
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.ActivityRepository
import com.hitboxd.app.data.repository.GameRepository
import com.hitboxd.app.utils.DateUtils
import com.hitboxd.app.utils.ImageUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class TrackerViewModel : ViewModel() {

    private val gameRepo     = GameRepository()
    private val activityRepo = ActivityRepository()

    private val _game      = MutableStateFlow<Game?>(null)
    val game: StateFlow<Game?> = _game

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isEmpty   = MutableStateFlow(false)
    val isEmpty: StateFlow<Boolean> = _isEmpty

    fun loadRandom() {
        viewModelScope.launch {
            _isLoading.value = true
            _game.value      = null
            _isEmpty.value   = false
            when (val r = gameRepo.getRandom()) {
                is NetworkResult.Success -> _game.value = r.data
                is NetworkResult.Error   -> _isEmpty.value = true
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun handleAction(action: String) {
        val gameId = _game.value?.idGame ?: return
        viewModelScope.launch {
            if (action == "played") {
                activityRepo.logActivity(gameId, status = "played")
            }
            // "skip" no registra nada, solo carga el siguiente
            loadRandom()
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class TrackerFragment : Fragment() {

    private val vm: TrackerViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_tracker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btnSkip).setOnClickListener   { vm.handleAction("skip") }
        view.findViewById<Button>(R.id.btnPlayed).setOnClickListener { vm.handleAction("played") }

        // Tap en la tarjeta → ir al detalle del juego
        view.findViewById<View>(R.id.cardGame).setOnClickListener {
            vm.game.value?.slug?.let { slug ->
                findNavController().navigate(
                    R.id.action_trackerFragment_to_gameDetailFragment,
                    bundleOf("slug" to slug)
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isLoading.collect { loading ->
                view.findViewById<View>(R.id.progressBar).isVisible = loading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isEmpty.collect { empty ->
                view.findViewById<View>(R.id.emptyState).isVisible  = empty
                view.findViewById<View>(R.id.cardGame).isVisible     = !empty
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.game.collect { game ->
                game ?: return@collect
                view.findViewById<TextView>(R.id.tvGameTitle).text = game.title
                view.findViewById<TextView>(R.id.tvGameInfo).text  =
                    "${game.developer ?: "Unknown"} • ${DateUtils.extractYear(game.releaseDate)}"
                ImageUtils.loadBanner(
                    requireContext(),
                    game.coverUrl ?: game.backgroundUrl,
                    view.findViewById(R.id.imgPoster)
                )
                view.findViewById<View>(R.id.cardGame).isVisible = true
                view.findViewById<View>(R.id.emptyState).isVisible = false
            }
        }

        vm.loadRandom()
    }
}
