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

    // review events
    data class ReviewLikeChanged(val idReview: Int, val count: Int, val actorId: Int) : SocketEvent()
    data class ReviewCreated(val review: com.hitboxd.app.data.model.Review) : SocketEvent()
    data class ReviewDeleted(val idReview: Int) : SocketEvent()

    // presence
    data class GamePresence(val gameId: Int, val count: Int) : SocketEvent()

    // moderation
    data class ModerationReportNew(val review: com.hitboxd.app.data.model.Review) : SocketEvent()
    data class ModerationResolved(val idReview: Int) : SocketEvent()
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
            reconnection = true
            reconnectionDelay = 1000L
            reconnectionDelayMax = 5000L
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
            on("notification:new") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                try {
                    val notif = gson.fromJson(obj.toString(), Notification::class.java)
                    _events.tryEmit(SocketEvent.NotificationNew(notif))
                } catch (_: Exception) {}
            }
            on("notification:unread_count") { args ->
                val count = when (val raw = args.getOrNull(0)) {
                    is Int        -> raw
                    is JSONObject -> raw.optInt("count", 0)
                    else          -> 0
                }
                _events.tryEmit(SocketEvent.UnreadCount(count))
            }
            on("notification:read") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                if (obj.optBoolean("all", false)) {
                    _events.tryEmit(SocketEvent.ReadAll)
                } else if (obj.has("id")) {
                    val id = obj.getInt("id")
                    _events.tryEmit(SocketEvent.ReadOne(id))
                }
            }
            on("review:like_changed") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(SocketEvent.ReviewLikeChanged(
                    obj.getInt("id_review"),
                    obj.getInt("count"),
                    obj.optInt("actor_id", -1)
                ))
            }
            on("review:created") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                try {
                    val review = gson.fromJson(obj.toString(), com.hitboxd.app.data.model.Review::class.java)
                    _events.tryEmit(SocketEvent.ReviewCreated(review))
                } catch (_: Exception) {}
            }
            on("review:deleted") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(SocketEvent.ReviewDeleted(obj.getInt("id_review")))
            }
            on("game:presence") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(SocketEvent.GamePresence(obj.getInt("gameId"), obj.getInt("count")))
            }
            on("moderation:report_new") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                try {
                    val review = gson.fromJson(obj.toString(), com.hitboxd.app.data.model.Review::class.java)
                    _events.tryEmit(SocketEvent.ModerationReportNew(review))
                } catch (_: Exception) {}
            }
            on("moderation:resolved") { args ->
                val obj = args.getOrNull(0) as? JSONObject ?: return@on
                _events.tryEmit(SocketEvent.ModerationResolved(obj.getInt("id_review")))
            }
            connect()
        }
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
    }
}
