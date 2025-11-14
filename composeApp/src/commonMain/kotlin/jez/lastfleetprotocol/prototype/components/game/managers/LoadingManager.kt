/*
 * This file is part of Kubriko.
 * Copyright (c) Pandula Péter 2025.
 * https://github.com/pandulapeter/kubriko
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
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
import com.pandulapeter.kubriko.uiComponents.utilities.preloadedImageBitmap
import com.pandulapeter.kubriko.uiComponents.utilities.preloadedImageVector
import com.pandulapeter.kubriko.uiComponents.utilities.preloadedString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import lastfleetprotocol.composeapp.generated.resources.*

internal class LoadingManager() : Manager() {
    private val musicManager by manager<MusicManager>()
    private val soundManager by manager<SoundManager>()
    private val spriteManager by manager<SpriteManager>()
    private val musicUris = AudioManager.getMusicUrisToPreload()
    private val soundUris = AudioManager.getSoundUrisToPreload()
    private val spriteResources = listOf(
        Res.drawable.sprite_ship,
        Res.drawable.sprite_alien_ship,
        Res.drawable.sprite_power_up,
        Res.drawable.sprite_shield,
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
    fun isGameLoaded() = (isInitialized.collectAsState().value
            && areMenuResourcesLoaded()
            && areGameResourcesLoaded.collectAsState().value).also {
        isLoadingDone = it
    }

    @Composable
    override fun Composable(windowInsets: WindowInsets) {
        if (!isFontLoaded.collectAsState().value) {
            isFontLoaded.update { preloadedFont(Res.font.orbitron).value != null }
        }
    }

    @Composable
    private fun areMenuResourcesLoaded() = isFontLoaded.collectAsState().value
            && areIconResourcesLoaded()
            && areImageResourcesLoaded()
            && areStringResourcesLoaded()

    @Composable
    private fun areIconResourcesLoaded() = preloadedImageVector(Res.drawable.ic_exit).value != null
            && preloadedImageVector(Res.drawable.ic_music_off).value != null
            && preloadedImageVector(Res.drawable.ic_music_on).value != null
            && preloadedImageVector(Res.drawable.ic_sound_effects_off).value != null
            && preloadedImageVector(Res.drawable.ic_sound_effects_on).value != null
            && preloadedImageVector(Res.drawable.ic_back).value != null

    @Composable
    private fun areImageResourcesLoaded() = preloadedImageBitmap(Res.drawable.compose_multiplatform).value != null

    @Composable
    private fun areStringResourcesLoaded() = preloadedString(Res.string.button_new_game).value.isNotBlank()
            && preloadedString(Res.string.button_continue).value.isNotBlank()
            && preloadedString(Res.string.button_settings).value.isNotBlank()
}