package com.hitboxd.app.utils

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.widget.ImageView
import androidx.core.content.edit
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.hitboxd.app.R
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ─── SESSION MANAGER ─────────────────────────────────────
// Las cookies (token/refreshToken) las gestiona PersistentCookieJar.
// Aquí solo guardamos metadata de UI: userId, username, isAdmin, avatarUrl.
class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("hitboxd_session", MODE_PRIVATE)

    companion object {
        private const val KEY_USER_ID  = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_ADMIN = "is_admin"
        private const val KEY_AVATAR   = "avatar_url"
    }

    fun saveUserData(id: Int, username: String, role: String?, avatarUrl: String?) {
        prefs.edit {
            putInt(KEY_USER_ID, id)
            putString(KEY_USERNAME, username)
            putBoolean(KEY_IS_ADMIN, role == "admin")
            putString(KEY_AVATAR, avatarUrl)
        }
    }

    fun getUserId(): Int        = prefs.getInt(KEY_USER_ID, -1)
    fun getUsername(): String?  = prefs.getString(KEY_USERNAME, null)
    fun isAdmin(): Boolean      = prefs.getBoolean(KEY_IS_ADMIN, false)
    fun getAvatarUrl(): String? = prefs.getString(KEY_AVATAR, null)
    fun isLoggedIn(): Boolean   = getUserId() != -1

    fun clear() = prefs.edit { clear() }
}

// ─── IMAGE UTILS ─────────────────────────────────────────
object ImageUtils {

    /** Carga portada de juego con esquinas redondeadas */
    fun loadGameCover(context: Context, url: String?, view: ImageView, cornerDp: Int = 6) {
        val px = (cornerDp * context.resources.displayMetrics.density).toInt()
        Glide.with(context)
            .load(url)
            .transform(CenterCrop(), RoundedCorners(px))
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(view)
    }

    /** Carga avatar circular */
    fun loadAvatar(context: Context, url: String?, view: ImageView) {
        Glide.with(context)
            .load(url)
            .circleCrop()
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(view)
    }

    /** Carga banner/fondo de juego */
    fun loadBanner(context: Context, url: String?, view: ImageView) {
        Glide.with(context)
            .load(url)
            .centerCrop()
            .placeholder(android.R.color.darker_gray)
            .error(android.R.color.darker_gray)
            .into(view)
    }
}

// ─── DATE UTILS ──────────────────────────────────────────
object DateUtils {

    private val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }

    private fun parse(raw: String): Date? =
        formats.firstNotNullOfOrNull { fmt ->
            try { fmt.parse(raw) } catch (_: Exception) { null }
        }

    fun format(raw: String?): String {
        raw ?: return ""
        return try {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(parse(raw)!!)
        } catch (_: Exception) { raw.take(10) }
    }

    fun extractYear(raw: String?): String {
        raw ?: return "N/A"
        return try {
            SimpleDateFormat("yyyy", Locale.US).format(parse(raw)!!)
        } catch (_: Exception) { "N/A" }
    }

    fun dayOf(raw: String?): String {
        raw ?: return ""
        return try { SimpleDateFormat("d", Locale.US).format(parse(raw)!!) } catch (_: Exception) { "" }
    }

    fun monthOf(raw: String?): String {
        raw ?: return ""
        return try { SimpleDateFormat("MMM", Locale.US).format(parse(raw)!!).uppercase() } catch (_: Exception) { "" }
    }

    fun yearOf(raw: String?): String {
        raw ?: return ""
        return try { SimpleDateFormat("yyyy", Locale.US).format(parse(raw)!!) } catch (_: Exception) { "" }
    }
}

// ─── TIME AGO ────────────────────────────────────────────
object TimeAgo {

    private val parseFmts = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }

    fun format(raw: String?): String {
        raw ?: return ""
        val date = parseFmts.firstNotNullOfOrNull { fmt ->
            try { fmt.parse(raw) } catch (_: Exception) { null }
        } ?: return raw.take(10)

        val diffMs  = System.currentTimeMillis() - date.time
        val diffSec = diffMs / 1000
        val diffMin = diffSec / 60
        val diffH   = diffMin / 60
        val diffD   = diffH / 24

        return when {
            diffSec < 60  -> "just now"
            diffMin < 60  -> "${diffMin}m"
            diffH   < 24  -> "${diffH}h"
            diffD   < 7   -> "${diffD}d"
            else          -> SimpleDateFormat("MMM d", Locale.US).format(date)
        }
    }
}

// ─── NOTIFICATION BADGE MANAGER ──────────────────────────
object NotificationBadgeManager {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread
    fun set(count: Int) { _unread.value = count }
}

// ─── STATUS UTILS ────────────────────────────────────────
object StatusUtils {

    fun label(status: String?): String = when (status) {
        "played"       -> "Played"
        "playing"      -> "Playing"
        "plan_to_play" -> "Pending"
        "dropped"      -> "Abandoned"
        else           -> status ?: ""
    }

    fun colorRes(status: String?): Int = when (status) {
        "played"       -> R.color.brand_green
        "playing"      -> R.color.brand_cyan
        "plan_to_play" -> R.color.brand_yellow
        "dropped"      -> R.color.brand_red
        else           -> R.color.text_secondary
    }
}
