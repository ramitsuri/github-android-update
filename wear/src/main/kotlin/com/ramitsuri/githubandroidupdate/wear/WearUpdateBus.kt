package com.ramitsuri.githubandroidupdate.wear

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface WearUpdateEvent {
    data class Progress(val progress: Float) : WearUpdateEvent
    data class Ready(val apkPath: String) : WearUpdateEvent
}

object WearUpdateBus {
    private val _events = MutableSharedFlow<WearUpdateEvent>(
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<WearUpdateEvent> = _events.asSharedFlow()

    fun sendEvent(event: WearUpdateEvent) {
        _events.tryEmit(event)
    }
}
