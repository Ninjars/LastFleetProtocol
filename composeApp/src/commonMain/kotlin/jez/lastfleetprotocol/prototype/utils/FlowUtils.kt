package jez.lastfleetprotocol.prototype.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

fun <T> Flow<T>.stateInWhileSubscribed(
    scope: CoroutineScope,
    initialValue: T,
) = stateIn(
    scope = scope,
    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 7000),
    initialValue = initialValue
)