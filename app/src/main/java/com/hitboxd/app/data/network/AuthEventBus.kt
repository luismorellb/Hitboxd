package com.hitboxd.app.data.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class AuthEvent {
    object LoggedOut : AuthEvent()
    object SessionRefreshed : AuthEvent()
}

object AuthEventBus {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()
    @Suppress("unused") // API para callers suspend; el interceptor usa tryEmit
    suspend fun emit(event: AuthEvent) = _events.emit(event)
    fun tryEmit(event: AuthEvent) = _events.tryEmit(event)
}
