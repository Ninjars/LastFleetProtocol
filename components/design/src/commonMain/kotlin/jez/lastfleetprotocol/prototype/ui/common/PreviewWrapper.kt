package jez.lastfleetprotocol.prototype.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import jez.lastfleetprotocol.prototype.ui.theme.LFTheme

@Composable
fun PreviewWrapper(
    content: @Composable () -> Unit,
) {
    LFTheme {
        Surface(
            color = MaterialTheme.colorScheme.background,
            content = content
        )
    }
}