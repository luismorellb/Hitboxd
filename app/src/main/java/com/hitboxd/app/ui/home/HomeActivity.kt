package com.hitboxd.app.ui.home

import android.content.Intent
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
import com.hitboxd.app.data.network.AuthEvent
import com.hitboxd.app.data.network.AuthEventBus
import com.hitboxd.app.data.network.RetrofitClient
import com.hitboxd.app.data.network.SocketEvent
import com.hitboxd.app.data.network.SocketManager
import com.hitboxd.app.ui.landing.LandingActivity
import com.hitboxd.app.utils.NotificationBadgeManager
import com.hitboxd.app.utils.SessionManager
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
        observeAuthEvents()
        setupSocket()

        if (RetrofitClient.cookieJar.hasToken()) {
            SocketManager.connect(RetrofitClient.cookieJar)
        }
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

    private fun observeAuthEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AuthEventBus.events.collect { event ->
                    when (event) {
                        is AuthEvent.LoggedOut -> {
                            SocketManager.disconnect()
                            SessionManager(this@HomeActivity).clear()
                            startActivity(
                                Intent(this@HomeActivity, LandingActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                }
                            )
                            finish()
                        }
                        is AuthEvent.SessionRefreshed -> {
                            SocketManager.disconnect()
                            SocketManager.connect(RetrofitClient.cookieJar)
                        }
                    }
                }
            }
        }
    }

    private fun setupSocket() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SocketManager.events.collect { event ->
                    if (event is SocketEvent.UnreadCount) {
                        NotificationBadgeManager.set(event.count)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        SocketManager.disconnect()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
