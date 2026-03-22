package jez.lastfleetprotocol.prototype.components.gamecore

import androidx.compose.runtime.Composable
import com.pandulapeter.kubriko.Kubriko

interface GameSessionState {
    val gameKubriko: Kubriko
}

interface GameLoadingStatus {
    @Composable
    fun isGameLoaded(): Boolean
}
