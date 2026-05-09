package com.hitboxd.app.data.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * ⚠️ Cambia esta URL según dónde corra tu backend:
     * - Emulador Android → 10.0.2.2 apunta al localhost de tu PC
     * - Dispositivo físico → IP local de tu PC en la misma red (ej: 192.168.1.X)
     * - Producción → URL real de tu servidor
     */
    const val BASE_URL = "http://10.0.2.2:3000/api/"

    lateinit var cookieJar: PersistentCookieJar
        private set

    lateinit var api: ApiService
        private set

    fun init(context: Context) {
        cookieJar = PersistentCookieJar(context)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(TokenRefreshInterceptor(cookieJar))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
