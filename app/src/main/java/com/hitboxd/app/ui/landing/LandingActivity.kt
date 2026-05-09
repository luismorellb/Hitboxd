package com.hitboxd.app.ui.landing

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hitboxd.app.R
import com.hitboxd.app.common.adapter.GameCardAdapter
import com.hitboxd.app.common.dialog.AuthDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.AuthRepository
import com.hitboxd.app.data.repository.GameRepository
import com.hitboxd.app.ui.home.HomeActivity
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class LandingViewModel : ViewModel() {

    private val gameRepo = GameRepository()
    private val authRepo = AuthRepository()

    private val _trendingGames = MutableStateFlow<List<Game>>(emptyList())
    val trendingGames: StateFlow<List<Game>> = _trendingGames

    private val _loginResult = MutableStateFlow<NetworkResult<AuthResponse>?>(null)
    val loginResult: StateFlow<NetworkResult<AuthResponse>?> = _loginResult

    private val _registerResult = MutableStateFlow<NetworkResult<AuthResponse>?>(null)
    val registerResult: StateFlow<NetworkResult<AuthResponse>?> = _registerResult

    fun fetchTrending() {
        viewModelScope.launch {
            when (val r = gameRepo.getTrending()) {
                is NetworkResult.Success -> _trendingGames.value = r.data.take(12)
                else -> { /* ignorar error en landing */ }
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginResult.value = NetworkResult.Loading
            _loginResult.value = authRepo.login(username, password)
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _registerResult.value = NetworkResult.Loading
            _registerResult.value = authRepo.register(username, email, password)
        }
    }
}

// ─── ACTIVITY ────────────────────────────────────────────
class LandingActivity : AppCompatActivity() {

    private val vm: LandingViewModel by viewModels()
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)

        // Si ya tiene sesión (cookie) ir directo a Home
        if (session.isLoggedIn()) { goHome(); return }

        setContentView(R.layout.activity_landing)
        setupButtons()
        setupRecycler()
        observeVm()
        vm.fetchTrending()
    }

    private fun setupButtons() {
        fun showAuth(page: Int) {
            AuthDialogFragment.newInstance(
                startPage  = page,
                onLogin    = { u, p -> vm.login(u, p) },
                onRegister = { u, e, p -> vm.register(u, e, p) }
            ).show(supportFragmentManager, "auth")
        }
        findViewById<Button>(R.id.btnSignIn).setOnClickListener       { showAuth(AuthDialogFragment.VIEW_LOGIN) }
        findViewById<Button>(R.id.btnCreateAccount).setOnClickListener{ showAuth(AuthDialogFragment.VIEW_REGISTER) }
        findViewById<Button>(R.id.btnGetStarted).setOnClickListener   { showAuth(AuthDialogFragment.VIEW_REGISTER) }
    }

    private fun setupRecycler() {
        val rv      = findViewById<RecyclerView>(R.id.rvTrendingGames)
        rv.layoutManager = GridLayoutManager(this, 3)
        val adapter = GameCardAdapter { /* en landing no navegar al detalle */ }
        rv.adapter  = adapter
        lifecycleScope.launch {
            vm.trendingGames.collect { adapter.submitList(it) }
        }
    }

    private fun observeVm() {
        lifecycleScope.launch {
            vm.loginResult.collect { result ->
                when (result) {
                    is NetworkResult.Success -> handleAuthSuccess(result.data)
                    is NetworkResult.Error   -> Toast.makeText(this@LandingActivity, result.message, Toast.LENGTH_SHORT).show()
                    else -> { /* Loading: podrías mostrar un spinner */ }
                }
            }
        }
        lifecycleScope.launch {
            vm.registerResult.collect { result ->
                when (result) {
                    is NetworkResult.Success -> handleAuthSuccess(result.data)
                    is NetworkResult.Error   -> Toast.makeText(this@LandingActivity, result.message, Toast.LENGTH_SHORT).show()
                    else -> {}
                }
            }
        }
    }

    private fun handleAuthSuccess(response: AuthResponse) {
        response.user?.let { u ->
            session.saveUserData(u.id, u.username, null, null)
        }
        goHome()
    }

    private fun goHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
