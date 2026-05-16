package com.hitboxd.app.data.network

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private class FlexibleBooleanAdapter : TypeAdapter<Boolean>() {
    override fun write(out: JsonWriter, value: Boolean) { out.value(value) }
    override fun read(reader: JsonReader): Boolean = when (reader.peek()) {
        JsonToken.BOOLEAN -> reader.nextBoolean()
        JsonToken.NUMBER  -> reader.nextInt() != 0
        JsonToken.NULL    -> { reader.nextNull(); false }
        else              -> { reader.skipValue(); false }
    }
}

object RetrofitClient {

    /**
     * ⚠️ Cambia esta URL según dónde corra tu backend:
     * - Emulador Android → http://10.0.2.2:3000/api/
     * - Dispositivo físico → IP local de tu PC en la misma red (ej: 192.168.1.X/api/)
     * - Producción → URL real de tu servidor (actualmente activa)
     */
    const val BASE_URL = "https://api-proyecto-production-519c.up.railway.app/api/"

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

        val gson = GsonBuilder()
            .registerTypeAdapter(Boolean::class.javaPrimitiveType, FlexibleBooleanAdapter())
            .registerTypeAdapter(Boolean::class.javaObjectType, FlexibleBooleanAdapter())
            .create()

        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
