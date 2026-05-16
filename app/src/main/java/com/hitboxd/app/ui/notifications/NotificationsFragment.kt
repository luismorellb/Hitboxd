package com.hitboxd.app.ui.notifications

import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.NotificationAdapter
import com.hitboxd.app.data.model.GameSlugResponse
import com.hitboxd.app.data.model.NetworkResult
import com.hitboxd.app.data.model.Notification
import com.hitboxd.app.data.network.SocketEvent
import com.hitboxd.app.data.network.SocketManager
import com.hitboxd.app.data.repository.NotificationRepository
import com.hitboxd.app.data.repository.ReviewRepository
import com.hitboxd.app.utils.NotificationBadgeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_LIST_SIZE = 30

// ─── VIEWMODEL ───────────────────────────────────────────
class NotificationsViewModel : ViewModel() {

    private val notificationRepo = NotificationRepository()
    private val reviewRepo = ReviewRepository()

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

    // ── Socket-driven mutations (no API calls) ────────────
    fun prependNotification(notif: Notification) {
        val updated = (listOf(notif) + _notifications.value).take(MAX_LIST_SIZE)
        _notifications.value = updated
        val newCount = _unreadCount.value + 1
        _unreadCount.value = newCount
        NotificationBadgeManager.set(newCount)
    }

    fun applyUnreadCount(count: Int) {
        _unreadCount.value = count
        NotificationBadgeManager.set(count)
    }

    fun applyReadAll() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _unreadCount.value = 0
        NotificationBadgeManager.set(0)
    }

    suspend fun resolveReviewSlug(reviewId: Int): NetworkResult<GameSlugResponse> =
        reviewRepo.getReviewGameSlug(reviewId)

    fun applyReadOne(id: Int) {
        _notifications.value = _notifications.value.map {
            if (it.idNotification == id) it.copy(isRead = true) else it
        }
        val newCount = (_unreadCount.value - 1).coerceAtLeast(0)
        _unreadCount.value = newCount
        NotificationBadgeManager.set(newCount)
    }
}

// ─── FRAGMENT ────────────────────────────────────────────
class NotificationsFragment : Fragment() {

    private val vm: NotificationsViewModel by viewModels()
    private var offlineBannerJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_notifications, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rvNotifications = view.findViewById<RecyclerView>(R.id.rvNotifications)
        val swipeRefresh    = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val btnMarkAllRead  = view.findViewById<Button>(R.id.btnMarkAllRead)
        val tvEmpty         = view.findViewById<TextView>(R.id.tvEmpty)
        val tvOfflineBanner = view.findViewById<TextView>(R.id.tvOfflineBanner)

        val adapter = NotificationAdapter { notif -> handleNotificationClick(notif) }

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

        observeSocketEvents(tvOfflineBanner)
        startPollingFallback()

        vm.load()
    }

    private fun handleNotificationClick(notif: Notification) {
        if (!notif.isRead) vm.markOneRead(notif.idNotification)

        when (notif.type) {
            "follow" -> {
                findNavController().navigate(
                    R.id.action_notificationsFragment_to_publicProfileFragment,
                    bundleOf("username" to notif.actorUsername)
                )
            }
            "review_like" -> {
                if (!notif.targetSlug.isNullOrBlank()) {
                    findNavController().navigate(
                        R.id.action_notificationsFragment_to_gameDetailFragment,
                        bundleOf("slug" to notif.targetSlug)
                    )
                    return
                }

                val reviewId = notif.idReference
                if (reviewId == null) {
                    Snackbar.make(requireView(), "This review is no longer available", Snackbar.LENGTH_SHORT).show()
                    return
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val r = vm.resolveReviewSlug(reviewId)) {
                        is NetworkResult.Success -> {
                            findNavController().navigate(
                                R.id.action_notificationsFragment_to_gameDetailFragment,
                                bundleOf("slug" to r.data.slug)
                            )
                        }
                        is NetworkResult.Error -> {
                            Snackbar.make(requireView(), "This review is no longer available", Snackbar.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun observeSocketEvents(tvOfflineBanner: TextView) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketManager.events.collect { event ->
                    when (event) {
                        is SocketEvent.NotificationNew -> vm.prependNotification(event.notification)
                        is SocketEvent.UnreadCount     -> vm.applyUnreadCount(event.count)
                        is SocketEvent.ReadAll         -> vm.applyReadAll()
                        is SocketEvent.ReadOne         -> vm.applyReadOne(event.id)
                        is SocketEvent.Connected       -> {
                            offlineBannerJob?.cancel()
                            offlineBannerJob = null
                            tvOfflineBanner.isVisible = false
                        }
                        is SocketEvent.Disconnected    -> {
                            offlineBannerJob?.cancel()
                            offlineBannerJob = viewLifecycleOwner.lifecycleScope.launch {
                                delay(30_000)
                                tvOfflineBanner.isVisible = true
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun startPollingFallback() {
        var lastPollMs = 0L
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay(5_000)
                    if (!SocketManager.isConnected) {
                        val now = System.currentTimeMillis()
                        if (now - lastPollMs >= 60_000) {
                            lastPollMs = now
                            vm.load()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.markAllRead()
    }

    override fun onDestroyView() {
        offlineBannerJob?.cancel()
        offlineBannerJob = null
        super.onDestroyView()
    }
}
