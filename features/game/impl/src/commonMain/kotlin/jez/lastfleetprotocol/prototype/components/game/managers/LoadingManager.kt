package jez.lastfleetprotocol.prototype.components.game.managers

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.audioPlayback.MusicManager
import com.pandulapeter.kubriko.audioPlayback.SoundManager
import com.pandulapeter.kubriko.manager.Manager
import com.pandulapeter.kubriko.sprites.SpriteManager
import com.pandulapeter.kubriko.uiComponents.utilities.preloadedFont
import com.pandulapeter.kubriko.uiComponents.utilities.preloadedImageVector
import com.pandulapeter.kubriko.uiComponents.utilities.preloadedString
import jez.lastfleetprotocol.prototype.components.gamecore.GameLoadingStatus
import jez.lastfleetprotocol.prototype.di.Singleton
import jez.lastfleetprotocol.prototype.ui.resources.LFRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import me.tatarka.inject.annotations.Inject

@Singleton
@Inject
class LoadingManager(

    private val musicManager: MusicManager,
    private val soundManager: SoundManager,
    private val spriteManager: SpriteManager,
) : Manager(), GameLoadingStatus {
    private val musicUris = AudioManager.getMusicUrisToPreload()
    private val soundUris = AudioManager.getSoundUrisToPreload()
    private val spriteResources = listOf(
        LFRes.Drawable.sprite_ship,
        LFRes.Drawable.sprite_alien_ship,
        LFRes.Drawable.sprite_power_up,
        LFRes.Drawable.sprite_shield,
        LFRes.Drawable.ship_player_1,
        LFRes.Drawable.ship_enemy_1,
        LFRes.Drawable.turret_simple_1,
        LFRes.Drawable.bullet_laser_green_10,
    )
    private val areGameResourcesLoaded by autoInitializingLazy {
        combine(
            musicManager.getLoadingProgress(musicUris),
            soundManager.getLoadingProgress(soundUris),
            spriteManager.getLoadingProgress(spriteResources),
        ) { musicLoadingProgress, soundLoadingProgress, spriteLoadingProgress ->
            musicLoadingProgress == 1f && soundLoadingProgress == 1f && spriteLoadingProgress == 1f
        }.asStateFlow(false)
    }
    private val isFontLoaded = MutableStateFlow(false)
    var isLoadingDone = false
        private set

    override fun onInitialize(kubriko: Kubriko) {
        musicManager.preload(musicUris)
        soundManager.preload(soundUris)
        spriteManager.preload(spriteResources)
    }

    @Composable
    override fun isGameLoaded() = (isInitialized.collectAsState().value
            && areMenuResourcesLoaded()
            && areGameResourcesLoaded.collectAsState().value).also {
        isLoadingDone = it
    }

    @Composable
    override fun Composable(windowInsets: WindowInsets) {
        if (!isFontLoaded.collectAsState().value) {
            isFontLoaded.update {
                preloadedFont(LFRes.Font.megrim_regular).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_bold).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_black).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_light).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_medium).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_regular).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_semibold).value != null
                        && preloadedFont(LFRes.Font.sairacondensed_thin).value != null
            }
        }
    }

    @Composable
    private fun areMenuResourcesLoaded() = isFontLoaded.collectAsState().value
            && areIconResourcesLoaded()
            && areImageResourcesLoaded()
            && areStringResourcesLoaded()

    @Composable
    private fun areIconResourcesLoaded() =
        preloadedImageVector(LFRes.Drawable.ic_exit).value != null
                && preloadedImageVector(LFRes.Drawable.ic_music_off).value != null
                && preloadedImageVector(LFRes.Drawable.ic_music_on).value != null
                && preloadedImageVector(LFRes.Drawable.ic_sound_effects_off).value != null
                && preloadedImageVector(LFRes.Drawable.ic_sound_effects_on).value != null
                && preloadedImageVector(LFRes.Drawable.ic_back).value != null
                && preloadedImageVector(LFRes.Drawable.ic_add).value != null

    @Composable
    private fun areImageResourcesLoaded() =
        true//preloadedImageBitmap(LFRes.Drawable.compose_multiplatform).value != null

    @Composable
    private fun areStringResourcesLoaded() =
        preloadedString(LFRes.String.button_new_game).value.isNotBlank()
                && preloadedString(LFRes.String.button_continue).value.isNotBlank()
                && preloadedString(LFRes.String.button_settings).value.isNotBlank()
}
