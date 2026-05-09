package com.hitboxd.app.ui.listdetail

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
import com.google.android.material.snackbar.Snackbar
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.ListItemAdapter
import com.hitboxd.app.common.dialog.AddGameToListDialogFragment
import com.hitboxd.app.common.dialog.ConfirmDeleteDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.ListRepository
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class ListDetailViewModel : ViewModel() {

    private val repo = ListRepository()

    private val _listData  = MutableStateFlow<UserList?>(null)
    val listData: StateFlow<UserList?> = _listData

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving  = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _toast     = MutableSharedFlow<Pair<String, Boolean>>() // mensaje, isError
    val toast: SharedFlow<Pair<String, Boolean>> = _toast

    private var listId: Int = -1

    fun init(id: Int) {
        listId = id
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = repo.getListDetail(id)) {
                is NetworkResult.Success -> _listData.value = r.data
                is NetworkResult.Error   -> _toast.emit("Error al cargar la lista" to true)
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun addGame(gameId: Int, comment: String?) {
        viewModelScope.launch {
            _isSaving.value = true
            when (val r = repo.addGameToList(listId, gameId, comment)) {
                is NetworkResult.Success -> {
                    _toast.emit("Juego agregado" to false)
                    // Recargar la lista para mostrar el nuevo juego
                    when (val r2 = repo.getListDetail(listId)) {
                        is NetworkResult.Success -> _listData.value = r2.data
                        else -> {}
                    }
                }
                is NetworkResult.Error -> _toast.emit("Error al agregar: ${r.message}" to true)
                else -> {}
            }
            _isSaving.value = false
        }
    }

    fun deleteList(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            when (val r = repo.deleteList(listId)) {
                is NetworkResult.Success -> {
                    _toast.emit("Lista eliminada" to false)
                    onSuccess()
                }
                is NetworkResult.Error -> _toast.emit("Error al eliminar: ${r.message}" to true)
                else -> {}
            }
            _isSaving.value = false
        }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class ListDetailFragment : Fragment() {

    private val vm: ListDetailViewModel by viewModels()
    private lateinit var session: SessionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        val listId = arguments?.getInt("listId") ?: return

        val adapter = ListItemAdapter(
            onGameClick = { item ->
                findNavController().navigate(
                    R.id.action_listDetailFragment_to_gameDetailFragment,
                    bundleOf("slug" to (item.slug ?: ""))
                )
            }
        )
        view.findViewById<RecyclerView>(R.id.rvGames).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter  = adapter
        }

        // Botón agregar juego
        view.findViewById<Button>(R.id.btnAddGame).setOnClickListener {
            // En producción aquí abriría un buscador; por ahora pide el ID directamente
            AddGameToListDialogFragment.newInstance(0) { comment ->
                // El ID real del juego vendría de un buscador; aquí es placeholder
                vm.addGame(0, comment)
            }.show(childFragmentManager, "addGame")
        }

        // Botón eliminar lista (solo si soy el dueño)
        view.findViewById<Button>(R.id.btnDeleteList).setOnClickListener {
            ConfirmDeleteDialogFragment(
                message     = "¿Eliminar esta lista permanentemente?",
                confirmText = "Eliminar"
            ) {
                vm.deleteList { findNavController().navigateUp() }
            }.show(childFragmentManager, "confirmDelete")
        }

        observeVm(view, adapter, listId)
        vm.init(listId)
    }

    private fun observeVm(view: View, adapter: ListItemAdapter, listId: Int) {
        val progressBar = view.findViewById<View>(R.id.progressBar)
        val contentRoot = view.findViewById<View>(R.id.contentRoot)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isLoading.collect { loading ->
                progressBar.isVisible = loading
                contentRoot.isVisible = !loading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.listData.collect { list ->
                list ?: return@collect
                view.findViewById<TextView>(R.id.tvListTitle).text = list.title
                view.findViewById<TextView>(R.id.tvListDescription).text = list.description ?: ""
                view.findViewById<TextView>(R.id.tvListInfo).text =
                    "${list.games.size} juegos • ${list.username ?: "Tú"}"
                adapter.submitList(list.games)

                // Mostrar botones de edición solo si es mi lista
                val isMine = list.idUser == session.getUserId()
                view.findViewById<Button>(R.id.btnAddGame).isVisible    = isMine
                view.findViewById<Button>(R.id.btnDeleteList).isVisible = isMine
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isSaving.collect { saving ->
                view.findViewById<Button>(R.id.btnAddGame).isEnabled    = !saving
                view.findViewById<Button>(R.id.btnDeleteList).isEnabled = !saving
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.toast.collect { (msg, isError) ->
                Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).apply {
                    setBackgroundTint(
                        resources.getColor(
                            if (isError) R.color.brand_red else R.color.brand_green,
                            null
                        )
                    )
                }.show()
            }
        }
    }
}
