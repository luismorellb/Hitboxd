package com.hitboxd.app.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Intercepta 401. Llama a POST /auth/refresh.
 * Si refresca OK, reintenta la request original.
 * Si falla, limpia cookies y deja que la UI maneje el logout.
 */
class TokenRefreshInterceptor(
    private val cookieJar: PersistentCookieJar
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request  = chain.request()
        val response = chain.proceed(request)

        if (response.code == 401) {
            response.close()

            val refreshed = runBlocking {
                try {
                    RetrofitClient.api.refreshToken().isSuccessful
                } catch (e: Exception) {
                    false
                }
            }

            return if (refreshed) {
                // El CookieJar ya guardó el nuevo token automáticamente
                chain.proceed(request)
            } else {
                cookieJar.clearAll()
                response
            }
        }

        return response
    }
}
