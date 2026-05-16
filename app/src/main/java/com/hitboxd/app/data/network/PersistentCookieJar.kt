package com.hitboxd.app.data.network

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar que persiste las cookies HttpOnly (token + refreshToken)
 * en EncryptedSharedPreferences (AES256-GCM). Reemplaza al AuthInterceptor típico.
 *
 * Migración transparente: si existe el archivo plano "hitboxd_cookies" de una
 * versión anterior, su contenido se copia al store encriptado y se elimina.
 * Si la migración falla, ambos stores se limpian (el usuario vuelve a logearse
 * una sola vez).
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences = run {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "hitboxd_cookies_encrypted",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Cache en memoria: host -> lista de cookies
    private val store: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    init {
        migrateFromPlainPrefsIfNeeded(context)
        restoreFromPrefs()
    }

    // ── Migración plain → encrypted ──────────────────────
    private fun migrateFromPlainPrefsIfNeeded(context: Context) {
        val oldPrefs = context.getSharedPreferences("hitboxd_cookies", Context.MODE_PRIVATE)
        if (oldPrefs.all.isEmpty()) return
        try {
            oldPrefs.all.forEach { (key, value) ->
                if (value is String) prefs.edit { putString(key, value) }
            }
        } catch (_: Exception) {
            // Si la migración falla, limpiar el store encriptado por seguridad
            prefs.edit { clear() }
        } finally {
            oldPrefs.edit { clear() }
        }
    }

    private fun restoreFromPrefs() {
        prefs.all.forEach { (host, raw) ->
            if (raw is String && raw.isNotBlank()) {
                val parsed = raw.split("|||").mapNotNull { deserialize(it) }
                if (parsed.isNotEmpty()) store[host] = parsed.toMutableList()
            }
        }
    }

    // ── CookieJar ────────────────────────────────────────
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        cookies.forEach { incoming ->
            list.removeAll { it.name == incoming.name }
            list.add(incoming)
        }
        prefs.edit { putString(host, list.joinToString("|||") { serialize(it) }) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host]?.filter { !it.persistent || it.expiresAt > now } ?: emptyList()
    }

    fun clearAll() {
        store.clear()
        prefs.edit { clear() }
    }

    fun hasToken(): Boolean {
        val now = System.currentTimeMillis()
        return store.values.flatten().any { cookie ->
            cookie.name == "token" && (!cookie.persistent || cookie.expiresAt > now)
        }
    }

    // ── Serialización ────────────────────────────────────
    private fun serialize(c: Cookie): String =
        "${c.name}::${c.value}::${c.domain}::${c.path}::${c.expiresAt}::${c.secure}::${c.httpOnly}"

    private fun deserialize(raw: String): Cookie? = try {
        val p = raw.split("::")
        if (p.size < 7) null
        else Cookie.Builder()
            .name(p[0])
            .value(p[1])
            .domain(p[2])
            .path(p[3])
            .expiresAt(p[4].toLong())
            .apply { if (p[5].toBoolean()) secure() }
            .apply { if (p[6].toBoolean()) httpOnly() }
            .build()
    } catch (_: Exception) { null }
}
