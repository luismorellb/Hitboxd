package com.hitboxd.app.data.network

import com.google.gson.Gson
import com.hitboxd.app.data.model.Notification
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

sealed class SocketEvent {
    data class NotificationNew(val notification: Notification) : SocketEvent()
    data class UnreadCount(val count: Int) : SocketEvent()
    object ReadAll : SocketEvent()
    data class ReadOne(val id: Int) : SocketEvent()
    object Connected : SocketEvent()
    object Disconnected : SocketEvent()
}

object SocketManager {

    private val gson = Gson()
    private var socket: Socket? = null

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    val isConnected: Boolean get() = socket?.connected() == true

    fun connect(cookieJar: PersistentCookieJar) {
        if (socket?.connected() == true) return

        val socketUrl = RetrofitClient.BASE_URL.removeSuffix("api/")

        val cookieHeader = socketUrl.toHttpUrlOrNull()?.let { url ->
            cookieJar.loadForRequest(url)
                .joinToString("; ") { "${it.name}=${it.value}" }
        }.orEmpty()

        val options = IO.Options().apply {
            transports = arrayOf("websocket")
            if (cookieHeader.isNotBlank()) {
                extraHeaders = mapOf("Cookie" to listOf(cookieHeader))
            }
        }

        socket = IO.socket(socketUrl, options).apply {
            on(Socket.EVENT_CONNECT) {
                _events.tryEmit(SocketEvent.Connected)
            }
            on(Socket.EVENT_DISCONNECT) {
                _events.tryEmit(SocketEvent.Disconnected)
            }
            on("notification_new") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                try {
                    val notif = gson.fromJson(obj.toString(), Notification::class.java)
                    _events.tryEmit(SocketEvent.NotificationNew(notif))
                } catch (_: Exception) {}
            }
            on("unread_count") { args ->
                val count = when (val raw = args.getOrNull(0)) {
                    is Int        -> raw
                    is JSONObject -> raw.optInt("count", 0)
                    else          -> 0
                }
                _events.tryEmit(SocketEvent.UnreadCount(count))
            }
            on("read_all") {
                _events.tryEmit(SocketEvent.ReadAll)
            }
            on("read_one") { args ->
                val id = when (val raw = args.getOrNull(0)) {
                    is Int        -> raw
                    is JSONObject -> raw.optInt("id", -1)
                    else          -> -1
                }
                if (id != -1) _events.tryEmit(SocketEvent.ReadOne(id))
            }
            connect()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
