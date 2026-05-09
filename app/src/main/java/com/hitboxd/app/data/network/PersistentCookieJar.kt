package com.hitboxd.app.data.network

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * CookieJar que persiste las cookies HttpOnly (token + refreshToken)
 * en SharedPreferences. Reemplaza al AuthInterceptor típico.
 */
class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hitboxd_cookies", Context.MODE_PRIVATE)

    // Cache en memoria: host -> lista de cookies
    private val store: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    init {
        // Restaurar cookies al iniciar
        prefs.all.forEach { (host, raw) ->
            if (raw is String && raw.isNotBlank()) {
                val parsed = raw.split("|||").mapNotNull { deserialize(it) }
                if (parsed.isNotEmpty()) store[host] = parsed.toMutableList()
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = store.getOrPut(host) { mutableListOf() }
        cookies.forEach { incoming ->
            list.removeAll { it.name == incoming.name }
            list.add(incoming)
        }
        // Persistir
        prefs.edit()
            .putString(host, list.joinToString("|||") { serialize(it) })
            .apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host]?.filter { !it.persistent || it.expiresAt > now } ?: emptyList()
    }

    fun clearAll() {
        store.clear()
        prefs.edit().clear().apply()
    }

    fun hasToken(): Boolean =
        store.values.flatten().any { it.name == "token" }

    // ── Serialización simple ─────────────────────────────
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
    } catch (e: Exception) { null }
}
