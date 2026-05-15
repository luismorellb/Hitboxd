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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    fun submitReorder(newOrder: List<ListItem>) {
        viewModelScope.launch {
            val items = newOrder.mapIndexed { i, g -> ReorderItem(g.idItem, i + 1) }
            when (repo.reorderItems(listId, items)) {
                is NetworkResult.Success -> _toast.emit("Order updated" to false)
                is NetworkResult.Error   -> {
                    load(listId)
                    _toast.emit("Could not save order" to true)
                }
                else -> {}
            }
        }
    }

    private fun load(id: Int) {
        viewModelScope.launch {
            when (val r = repo.getListDetail(id)) {
                is NetworkResult.Success -> _listData.value = r.data
                else -> {}
            }
        }
    }

    fun removeItem(itemId: Int) {
        viewModelScope.launch {
            when (val r = repo.removeItem(listId, itemId)) {
                is NetworkResult.Success -> {
                    val updated = _listData.value?.let { list ->
                        val filtered = list.games.filter { it.idItem != itemId }
                            .mapIndexed { i, g -> g.copy(position = i + 1) }
                        list.copy(games = filtered)
                    }
                    _listData.value = updated
                    _toast.emit("Game removed" to false)
                }
                is NetworkResult.Error -> {
                    val msg = if (r.code == 404) "Game could not be removed" else "Error: ${r.message}"
                    _toast.emit(msg to true)
                }
                else -> {}
            }
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
    private var dragOccurred = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_list_detail, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        val listId = arguments?.getInt("listId") ?: return

        val rv = view.findViewById<RecyclerView>(R.id.rvGames).apply {
            layoutManager = LinearLayoutManager(context)
        }

        // Botón agregar juego
        view.findViewById<Button>(R.id.btnAddGame).setOnClickListener {
            AddGameToListDialogFragment.newInstance(0) { comment ->
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

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                (recyclerView.adapter as? ListItemAdapter)?.moveItem(from, to)
                dragOccurred = true
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = false

            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && dragOccurred) {
                    dragOccurred = false
                    (rv.adapter as? ListItemAdapter)?.let { vm.submitReorder(it.currentList) }
                }
            }
        })

        observeVm(view, rv, touchHelper)
        vm.init(listId)
    }

    private fun observeVm(view: View, rv: RecyclerView, touchHelper: ItemTouchHelper) {
        val progressBar = view.findViewById<View>(R.id.progressBar)
        val contentRoot = view.findViewById<View>(R.id.contentRoot)
        var adapter: ListItemAdapter? = null

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
                view.findViewById<TextView>(R.id.tvListInfo).text = buildString {
                    append(list.games.size)
                    append(' ')
                    append(getString(R.string.list_label_games))
                    append(" • ")
                    append(list.username ?: getString(R.string.list_owner_you))
                }

                val isMine = list.idUser == session.getUserId()
                val isDraggable = isMine && list.listType == "ranking"

                if (adapter == null) {
                    adapter = ListItemAdapter(
                        isOwner = isMine,
                        isDraggable = isDraggable,
                        onGameClick = { item ->
                            findNavController().navigate(
                                R.id.action_listDetailFragment_to_gameDetailFragment,
                                bundleOf("slug" to (item.slug ?: ""))
                            )
                        },
                        onRemove = { itemId ->
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle("Remove game")
                                .setMessage("Remove this game from the list?")
                                .setPositiveButton("Remove") { _, _ -> vm.removeItem(itemId) }
                                .setNegativeButton("Cancel", null)
                                .show()
                        },
                        onStartDrag = { vh -> touchHelper.startDrag(vh) }
                    )
                    rv.adapter = adapter
                    if (isDraggable) touchHelper.attachToRecyclerView(rv)
                }

                checkNotNull(adapter).submitList(list.games)

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
