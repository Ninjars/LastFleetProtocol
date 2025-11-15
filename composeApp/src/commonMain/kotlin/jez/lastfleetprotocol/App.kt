package jez.lastfleetprotocol

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.kubriko.KubrikoViewport
import jez.lastfleetprotocol.prototype.di.AppComponent
import jez.lastfleetprotocol.prototype.ui.theme.LFTheme

@Composable
fun App(appComponent: AppComponent) {
    LFTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
        ) {
            Box {
                KubrikoViewport(
                    kubriko = appComponent.gameStateHolder.backgroundKubriko,
                    modifier = Modifier
                        .fillMaxSize(),
                )
                appComponent.navHost()
            }
        }
    }
}