package jez.lastfleetprotocol.prototype.components.preferences

import kotlinx.coroutines.flow.StateFlow

interface UserPreferences {
    val areSoundEffectsEnabled: StateFlow<Boolean>
    val isMusicEnabled: StateFlow<Boolean>
}

fun interface SetMusicEnabled {
    operator fun invoke(isEnabled: Boolean)
}

fun interface SetSoundEffectsEnabled {
    operator fun invoke(isEnabled: Boolean)
}
