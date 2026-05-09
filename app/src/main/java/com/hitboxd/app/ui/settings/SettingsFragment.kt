package com.hitboxd.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.hitboxd.app.R
import com.hitboxd.app.common.dialog.ConfirmDeleteDialogFragment
import com.hitboxd.app.data.model.*
import com.hitboxd.app.data.repository.AuthRepository
import com.hitboxd.app.data.repository.UserRepository
import com.hitboxd.app.ui.landing.LandingActivity
import com.hitboxd.app.utils.ImageUtils
import com.hitboxd.app.utils.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ─── VIEWMODEL ───────────────────────────────────────────
class SettingsViewModel : ViewModel() {

    private val userRepo = UserRepository()
    private val authRepo = AuthRepository()

    private val _user      = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving  = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _toast     = MutableSharedFlow<Pair<String, Boolean>>()
    val toast: SharedFlow<Pair<String, Boolean>> = _toast

    // Avatar preview local (antes de guardar)
    val avatarPreviewUrl = MutableStateFlow<String?>(null)

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val r = userRepo.getMyProfile()) {
                is NetworkResult.Success -> {
                    _user.value            = r.data
                    avatarPreviewUrl.value = r.data.avatarUrl
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun saveProfile(bio: String, pronouns: String) {
        viewModelScope.launch {
            _isSaving.value = true
            when (val r = userRepo.updateProfile(bio = bio, pronouns = pronouns)) {
                is NetworkResult.Success -> {
                    _user.value = r.data
                    _toast.emit("Perfil actualizado" to false)
                }
                is NetworkResult.Error -> _toast.emit("Error: ${r.message}" to true)
                else -> {}
            }
            _isSaving.value = false
        }
    }

    fun saveAvatar(url: String) {
        viewModelScope.launch {
            _isSaving.value = true
            when (val r = userRepo.updateProfile(avatarUrl = url)) {
                is NetworkResult.Success -> {
                    _user.value            = r.data
                    avatarPreviewUrl.value = url
                    _toast.emit("Avatar actualizado" to false)
                }
                is NetworkResult.Error -> _toast.emit("Error: ${r.message}" to true)
                else -> {}
            }
            _isSaving.value = false
        }
    }

    fun generateRandomAvatar() {
        val seed = (1..999999).random()
        avatarPreviewUrl.value = "https://api.dicebear.com/7.x/pixel-art/svg?seed=$seed"
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            when (val r = userRepo.softDeleteUser()) {
                is NetworkResult.Success -> onSuccess()
                is NetworkResult.Error   -> _toast.emit("Error al eliminar cuenta: ${r.message}" to true)
                else -> {}
            }
            _isSaving.value = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepo.logout()
            onDone()
        }
    }
}

// ─── FRAGMENT HOST ───────────────────────────────────────
class SettingsFragment : Fragment() {

    private val vm: SettingsViewModel by viewModels()
    private val tabLabels = listOf("PROFILE", "AVATAR", "NOTIFICATIONS", "DEACTIVATE")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)

        viewPager.adapter = SettingsTabAdapter(this)
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = tabLabels[pos]
        }.attach()

        // Botón logout en toolbar
        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {
            vm.logout {
                val session = SessionManager(requireContext())
                session.clear()
                // Limpiar cookies
                com.hitboxd.app.data.network.RetrofitClient.cookieJar.clearAll()
                startActivity(
                    Intent(requireContext(), LandingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }

        vm.load()
    }

    inner class SettingsTabAdapter(f: Fragment) : FragmentStateAdapter(f) {
        override fun getItemCount() = tabLabels.size
        override fun createFragment(pos: Int): Fragment = when (pos) {
            0 -> SettingsProfileFragment()
            1 -> SettingsAvatarFragment()
            2 -> SettingsNotificationsFragment()
            3 -> SettingsDeactivateFragment()
            else -> SettingsProfileFragment()
        }
    }
}

// ─── TAB 0: PROFILE ──────────────────────────────────────
class SettingsProfileFragment : Fragment() {
    private val vm: SettingsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings_profile, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val etUsername = view.findViewById<EditText>(R.id.etUsername)
        val etEmail    = view.findViewById<EditText>(R.id.etEmail)
        val etBio      = view.findViewById<EditText>(R.id.etBio)
        val spinner    = view.findViewById<Spinner>(R.id.spinnerPronouns)
        val btnSave    = view.findViewById<Button>(R.id.btnSave)

        // Configurar spinner de pronombres
        val pronounOptions = listOf("", "he/him", "she/her", "they/them", "any")
        spinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            pronounOptions
        )

        // Poblar campos con datos actuales
        viewLifecycleOwner.lifecycleScope.launch {
            vm.user.collect { user ->
                user ?: return@collect
                etUsername.setText(user.username)
                etUsername.isEnabled = false // username no editable
                etEmail.setText(user.email)
                etEmail.isEnabled = false    // email no editable
                etBio.setText(user.bio ?: "")
                val idx = pronounOptions.indexOf(user.pronouns ?: "").coerceAtLeast(0)
                spinner.setSelection(idx)
            }
        }

        btnSave.setOnClickListener {
            val bio      = etBio.text.toString().trim()
            val pronouns = pronounOptions[spinner.selectedItemPosition]
            vm.saveProfile(bio, pronouns)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isSaving.collect { saving ->
                btnSave.isEnabled = !saving
                btnSave.text      = if (saving) "Guardando..." else "Guardar"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.toast.collect { (msg, isError) ->
                Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).apply {
                    setBackgroundTint(
                        resources.getColor(
                            if (isError) R.color.brand_red else R.color.brand_green, null
                        )
                    )
                }.show()
            }
        }
    }
}

// ─── TAB 1: AVATAR ───────────────────────────────────────
class SettingsAvatarFragment : Fragment() {
    private val vm: SettingsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings_avatar, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val imgAvatar = view.findViewById<ImageView>(R.id.imgAvatarPreview)
        val etUrl     = view.findViewById<EditText>(R.id.etAvatarUrl)
        val btnRandom = view.findViewById<Button>(R.id.btnRandomAvatar)
        val btnSave   = view.findViewById<Button>(R.id.btnSaveAvatar)

        viewLifecycleOwner.lifecycleScope.launch {
            vm.avatarPreviewUrl.collect { url ->
                ImageUtils.loadAvatar(requireContext(), url, imgAvatar)
                if (etUrl.text.isBlank()) etUrl.setText(url)
            }
        }

        btnRandom.setOnClickListener { vm.generateRandomAvatar() }

        btnSave.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isBlank()) {
                etUrl.error = "Ingresa una URL"
                return@setOnClickListener
            }
            vm.saveAvatar(url)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.isSaving.collect { saving ->
                btnSave.isEnabled = !saving
                btnSave.text      = if (saving) "Guardando..." else "Guardar Avatar"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.toast.collect { (msg, isError) ->
                Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).apply {
                    setBackgroundTint(
                        resources.getColor(
                            if (isError) R.color.brand_red else R.color.brand_green, null
                        )
                    )
                }.show()
            }
        }
    }
}

// ─── TAB 2: NOTIFICATIONS ────────────────────────────────
class SettingsNotificationsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings_notifications, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Toggles locales (sin endpoint en la API actual)
        val switchNewFollower = view.findViewById<Switch>(R.id.switchNewFollower)
        val switchNewReview   = view.findViewById<Switch>(R.id.switchNewReview)
        val switchNewLike     = view.findViewById<Switch>(R.id.switchNewLike)

        // Leer estado guardado localmente
        val prefs = requireContext().getSharedPreferences("notif_prefs", android.content.Context.MODE_PRIVATE)
        switchNewFollower.isChecked = prefs.getBoolean("notif_follower", true)
        switchNewReview.isChecked   = prefs.getBoolean("notif_review", true)
        switchNewLike.isChecked     = prefs.getBoolean("notif_like", true)

        // Guardar estado al cambiar
        switchNewFollower.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_follower", checked).apply()
        }
        switchNewReview.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_review", checked).apply()
        }
        switchNewLike.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_like", checked).apply()
        }
    }
}

// ─── TAB 3: DEACTIVATE ───────────────────────────────────
class SettingsDeactivateFragment : Fragment() {
    private val vm: SettingsViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_settings_deactivate, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<Button>(R.id.btnDeleteAccount).setOnClickListener {
            ConfirmDeleteDialogFragment(
                message     = "¿Eliminar tu cuenta permanentemente? Esta acción no se puede deshacer.",
                confirmText = "Eliminar cuenta"
            ) {
                vm.deleteAccount {
                    val session = SessionManager(requireContext())
                    session.clear()
                    com.hitboxd.app.data.network.RetrofitClient.cookieJar.clearAll()
                    startActivity(
                        Intent(requireContext(), LandingActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                }
            }.show(childFragmentManager, "deleteAccount")
        }
    }
}
