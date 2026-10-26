package com.aeriotv.android.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An aeriotv://channel deep link that arrives while the live player is
 * already up re-tunes it in place instead of navigating a second player on
 * top. Navigation posts the channel id here; PlayerScreen moves its current
 * index, so the persistent ExoPlayer re-primes without a route change.
 */
object LiveChannelRequest {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun request(channelId: String) {
        _pending.value = channelId
    }

    fun consume() {
        _pending.value = null
    }
}
