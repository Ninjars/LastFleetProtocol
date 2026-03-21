package jez.lastfleetprotocol.prototype.components.shared.usecases

import jez.lastfleetprotocol.prototype.components.game.audio.SoundEffect
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import me.tatarka.inject.annotations.Inject

@Inject
class PlaySoundEffectUseCase(
    private val audioManager: AudioManager,
) {
    operator fun invoke(soundEffect: SoundEffect) {
        audioManager.playSoundEffect(soundEffect)
    }
}