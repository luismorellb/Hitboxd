package com.hitboxd.app.data.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Intercepta 401. Llama a POST /auth/refresh.
 * Si refresca OK, reintenta la request original.
 * Si falla, limpia cookies y emite AuthEvent.LoggedOut.
 *
 * El Mutex garantiza que requests 401 concurrentes no disparen N refreshes
 * en paralelo: el primero en entrar refresca, los demás esperan y luego
 * detectan que ya hay un token válido y omiten el refresh.
 *
 * Los endpoints de auth (/auth/login, /auth/register, /auth/refresh) nunca
 * se reintentan — sus 401 se devuelven directamente al caller.
 */
class TokenRefreshInterceptor(
    private val cookieJar: PersistentCookieJar
) : Interceptor {

    private val refreshMutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request  = chain.request()
        val response = chain.proceed(request)

        if (response.code != 401) return response

        // Auth endpoints devuelven su propio 401 al caller — no reintentar
        val path = request.url.encodedPath
        val isAuthEndpoint = path.contains("/auth/login") ||
                             path.contains("/auth/register") ||
                             path.contains("/auth/refresh")
        if (isAuthEndpoint) {
            AuthEventBus.tryEmit(AuthEvent.LoggedOut)
            return response
        }

        // A partir de aquí vamos a reintentar, así que cerramos la response original
        response.close()

        val refreshed = runBlocking {
            refreshMutex.withLock {
                // Si otro thread ya refrescó el token mientras esperábamos el lock,
                // saltar el refresh y dejar que el reintento use el token nuevo
                if (cookieJar.hasToken()) return@withLock true
                try {
                    RetrofitClient.api.refreshToken().isSuccessful
                } catch (_: Exception) {
                    false
                }
            }
        }

        return if (refreshed) {
            AuthEventBus.tryEmit(AuthEvent.SessionRefreshed)
            chain.proceed(request)
        } else {
            cookieJar.clearAll()
            AuthEventBus.tryEmit(AuthEvent.LoggedOut)
            // Reintentamos para que OkHttp tenga una Response válida que devolver;
            // la UI redirigirá a Landing antes de que el resultado importe
            chain.proceed(request)
        }
    }
}
