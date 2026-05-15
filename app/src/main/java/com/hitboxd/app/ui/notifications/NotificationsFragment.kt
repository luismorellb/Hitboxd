package com.hitboxd.app.ui.notifications

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.NotificationAdapter
import com.hitboxd.app.data.model.NetworkResult
import com.hitboxd.app.data.model.Notification
import com.hitboxd.app.data.repository.NotificationRepository
import com.hitboxd.app.utils.NotificationBadgeManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class NotificationsViewModel : ViewModel() {

    private val notificationRepo = NotificationRepository()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = notificationRepo.list()) {
                is NetworkResult.Success -> {
                    _notifications.value = r.data.notifications
                    _unreadCount.value   = r.data.unreadCount
                    NotificationBadgeManager.set(r.data.unreadCount)
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value = 0
        NotificationBadgeManager.set(0)
        viewModelScope.launch { notificationRepo.markAllRead() }
    }

    fun markOneRead(id: Int) {
        _notifications.value = _notifications.value.map {
            if (it.idNotification == id) it.copy(isRead = true) else it
        }
        val newCount = (_unreadCount.value - 1).coerceAtLeast(0)
        _unreadCount.value = newCount
        NotificationBadgeManager.set(newCount)
        viewModelScope.launch { notificationRepo.markOneRead(id) }
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class NotificationsFragment : Fragment() {

    private val vm: NotificationsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rvNotifications  = view.findViewById<RecyclerView>(R.id.rvNotifications)
        val swipeRefresh     = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val btnMarkAllRead   = view.findViewById<Button>(R.id.btnMarkAllRead)
        val tvEmpty          = view.findViewById<TextView>(R.id.tvEmpty)

        val adapter = NotificationAdapter { notif ->
            vm.markOneRead(notif.idNotification)
            when (notif.type) {
                "follow" -> findNavController().navigate(
                    R.id.action_notificationsFragment_to_publicProfileFragment,
                    bundleOf("username" to notif.actorUsername)
                )
                "review_like" -> {
                    // TODO: agregar target_slug en response del backend o crear GET /reviews/:id/game-slug
                    Snackbar.make(view, "Proximamente", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter

        swipeRefresh.setOnRefreshListener { vm.load() }

        btnMarkAllRead.setOnClickListener { vm.markAllRead() }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.notifications.collect { list ->
                adapter.submitList(list)
                tvEmpty.isVisible = list.isEmpty()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.unreadCount.collect { count ->
                btnMarkAllRead.isVisible = count > 0
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isLoading.collect { loading ->
                swipeRefresh.isRefreshing = loading
            }
        }

        vm.load()
    }

    override fun onResume() {
        super.onResume()
        vm.markAllRead()
    }
}
