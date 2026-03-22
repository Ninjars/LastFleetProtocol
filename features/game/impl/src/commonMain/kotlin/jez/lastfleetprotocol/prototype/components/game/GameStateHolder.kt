package jez.lastfleetprotocol.prototype.components.game

import com.pandulapeter.kubriko.Kubriko
import jez.lastfleetprotocol.prototype.components.gamecore.GameSessionState
import jez.lastfleetprotocol.prototype.di.DependencyName.KUBRIKO_BACKGROUND
import jez.lastfleetprotocol.prototype.di.DependencyName.KUBRIKO_GAME
import jez.lastfleetprotocol.prototype.di.Named
import jez.lastfleetprotocol.prototype.di.Singleton
import me.tatarka.inject.annotations.Inject

@Singleton
@Inject
class GameStateHolder(

    @Named(KUBRIKO_BACKGROUND) val backgroundKubriko: Kubriko,
    @Named(KUBRIKO_GAME) lazyGameKubriko: Lazy<Kubriko>,
) : GameSessionState {
    override val gameKubriko by lazyGameKubriko
}