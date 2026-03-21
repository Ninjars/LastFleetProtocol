package jez.lastfleetprotocol.prototype.di

import com.pandulapeter.kubriko.Kubriko
import com.pandulapeter.kubriko.audioPlayback.MusicManager
import com.pandulapeter.kubriko.audioPlayback.SoundManager
import com.pandulapeter.kubriko.manager.ActorManager
import com.pandulapeter.kubriko.manager.StateManager
import com.pandulapeter.kubriko.manager.ViewportManager
import com.pandulapeter.kubriko.persistence.PersistenceManager
import com.pandulapeter.kubriko.pointerInput.PointerInputManager
import com.pandulapeter.kubriko.sprites.SpriteManager
import jez.lastfleetprotocol.prototype.components.game.GameStateHolder
import jez.lastfleetprotocol.prototype.components.game.managers.AudioManager
import jez.lastfleetprotocol.prototype.components.game.managers.GameStateManager
import jez.lastfleetprotocol.prototype.components.game.managers.LoadingManager
import jez.lastfleetprotocol.prototype.components.game.managers.UiManager
import jez.lastfleetprotocol.prototype.components.game.GameScreenEntry
import jez.lastfleetprotocol.prototype.components.game.ui.GameScreen
import jez.lastfleetprotocol.prototype.components.landingscreen.LandingScreenEntry
import jez.lastfleetprotocol.prototype.components.landingscreen.ui.LandingScreen
import jez.lastfleetprotocol.prototype.components.splashscreen.SplashScreenEntry
import jez.lastfleetprotocol.prototype.components.splashscreen.ui.SplashScreen
import jez.lastfleetprotocol.prototype.di.DependencyName.KUBRIKO_BACKGROUND
import jez.lastfleetprotocol.prototype.di.DependencyName.KUBRIKO_GAME
import jez.lastfleetprotocol.prototype.ui.navigation.LFNavHost
import jez.lastfleetprotocol.prototype.utils.Constants
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@Singleton
@Component
abstract class AppComponent(
    private val enableLogging: Boolean,
) {
    abstract val gameStateHolder: GameStateHolder

    abstract val navHost: LFNavHost

    @Provides
    protected fun splashScreenEntry(splashScreen: SplashScreen): SplashScreenEntry = splashScreen

    @Provides
    protected fun landingScreenEntry(landingScreen: LandingScreen): LandingScreenEntry = landingScreen

    @Provides
    protected fun gameScreenEntry(gameScreen: GameScreen): GameScreenEntry = gameScreen

    @Singleton
    @Provides
    protected fun stateManager(): StateManager = StateManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun persistenceManager(): PersistenceManager = PersistenceManager.newInstance(
        fileName = "lastfleetprotocol_persistence",
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun soundManager(): SoundManager = SoundManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun musicManager(): MusicManager = MusicManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun spriteManager(): SpriteManager = SpriteManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun ActorManager(): ActorManager = ActorManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun PointerInputManager(): PointerInputManager = PointerInputManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun ViewportManager(): ViewportManager = ViewportManager.newInstance(
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun backgroundKubriko(
        stateManager: StateManager,
        musicManager: MusicManager,
        spriteManager: SpriteManager,
        soundManager: SoundManager,
        loadingManager: LoadingManager,
    ): @Named(KUBRIKO_BACKGROUND) Kubriko = Kubriko.newInstance(
        stateManager,
        musicManager,
        spriteManager,
        soundManager,
        loadingManager,
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )

    @Singleton
    @Provides
    protected fun gameKubriko(
        stateManager: StateManager,
        persistenceManager: PersistenceManager,
        musicManager: MusicManager,
        spriteManager: SpriteManager,
        soundManager: SoundManager,
        loadingManager: LoadingManager,
        audioManager: AudioManager,
        actorManager: ActorManager,
        uiManager: UiManager,
        gameStateManager: GameStateManager,
        viewportManager: ViewportManager,
        pointerInputManager: PointerInputManager,
    ): @Named(KUBRIKO_GAME) Kubriko = Kubriko.newInstance(
        stateManager,
        persistenceManager,
        musicManager,
        spriteManager,
        soundManager,
        audioManager,
        loadingManager,
        actorManager,
        uiManager,
        gameStateManager,
        viewportManager,
        pointerInputManager,
        isLoggingEnabled = enableLogging,
        instanceNameForLogging = Constants.GAME_LOG_TAG,
    )
}

