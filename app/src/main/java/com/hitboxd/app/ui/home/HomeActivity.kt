package com.hitboxd.app.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.hitboxd.app.R
import com.hitboxd.app.data.model.NetworkResult
import com.hitboxd.app.data.repository.NotificationRepository
import com.hitboxd.app.utils.NotificationBadgeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setupWithNavController(navController)

        setupNotificationBadge(bottomNav)
        startNotificationPolling()
    }

    private fun setupNotificationBadge(bottomNav: BottomNavigationView) {
        lifecycleScope.launch {
            NotificationBadgeManager.unread.collect { count ->
                val badge = bottomNav.getOrCreateBadge(R.id.notificationsFragment)
                badge.isVisible = count > 0
                badge.number    = count
            }
        }
    }

    private fun startNotificationPolling() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    val result = NotificationRepository().list()
                    if (result is NetworkResult.Success) {
                        NotificationBadgeManager.set(result.data.unreadCount)
                    }
                    delay(60_000)
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
