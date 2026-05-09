package com.hitboxd.app.ui.catalog

import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.EditText
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
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.GameCardAdapter
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.GameRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─── VIEWMODEL ───────────────────────────────────────────
class CatalogViewModel : ViewModel() {

    private val repo = GameRepository()

    private val _trending    = MutableStateFlow<List<Game>>(emptyList())
    val trending: StateFlow<List<Game>> = _trending

    private val _popular     = MutableStateFlow<List<Game>>(emptyList())
    val popular: StateFlow<List<Game>> = _popular

    private val _newReleases = MutableStateFlow<List<Game>>(emptyList())
    val newReleases: StateFlow<List<Game>> = _newReleases

    private val _searchResults = MutableStateFlow<List<Game>>(emptyList())
    val searchResults: StateFlow<List<Game>> = _searchResults

    val query = MutableStateFlow("")

    private var searchJob: Job? = null

    fun fetchAll() {
        viewModelScope.launch {
            launch { when (val r = repo.getTrending())    { is NetworkResult.Success -> _trending.value = r.data;    else -> {} } }
            launch { when (val r = repo.getPopular())     { is NetworkResult.Success -> _popular.value = r.data;     else -> {} } }
            launch { when (val r = repo.getNewReleases()) { is NetworkResult.Success -> _newReleases.value = r.data; else -> {} } }
        }
    }

    fun onSearchQuery(q: String) {
        query.value = q
        searchJob?.cancel()
        if (q.isBlank()) { _searchResults.value = emptyList(); return }
        searchJob = viewModelScope.launch {
            delay(300L) // debounce
            when (val r = repo.searchGames(q)) {
                is NetworkResult.Success -> _searchResults.value = r.data
                else -> {}
            }
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class CatalogFragment : Fragment() {

    private val vm: CatalogViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_catalog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fun nav(slug: String) = findNavController().navigate(
            R.id.action_catalogFragment_to_gameDetailFragment,
            bundleOf("slug" to slug)
        )

        val trendingAdapter = GameCardAdapter { nav(it.slug) }
        val popularAdapter  = GameCardAdapter { nav(it.slug) }
        val newAdapter      = GameCardAdapter { nav(it.slug) }
        val searchAdapter   = GameCardAdapter { nav(it.slug) }

        fun horizontal(id: Int, adapter: RecyclerView.Adapter<*>) {
            view.findViewById<RecyclerView>(id).apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                this.adapter  = adapter
            }
        }
        horizontal(R.id.rvTrending, trendingAdapter)
        horizontal(R.id.rvPopular,  popularAdapter)
        horizontal(R.id.rvNew,      newAdapter)

        view.findViewById<RecyclerView>(R.id.rvSearchResults).apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter       = searchAdapter
        }

        // Búsqueda con debounce
        view.findViewById<EditText>(R.id.etSearch).addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { vm.onSearchQuery(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Observar
        viewLifecycleOwner.lifecycleScope.launch { vm.trending.collect    { trendingAdapter.submitList(it) } }
        viewLifecycleOwner.lifecycleScope.launch { vm.popular.collect     { popularAdapter.submitList(it) } }
        viewLifecycleOwner.lifecycleScope.launch { vm.newReleases.collect { newAdapter.submitList(it) } }

        val defaultSections = view.findViewById<View>(R.id.defaultSections)
        val searchSection   = view.findViewById<View>(R.id.searchSection)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.searchResults.collect { results ->
                val hasQuery = vm.query.value.isNotBlank()
                defaultSections.isVisible = !hasQuery
                searchSection.isVisible   = hasQuery
                searchAdapter.submitList(results)
            }
        }

        vm.fetchAll()
    }
}
