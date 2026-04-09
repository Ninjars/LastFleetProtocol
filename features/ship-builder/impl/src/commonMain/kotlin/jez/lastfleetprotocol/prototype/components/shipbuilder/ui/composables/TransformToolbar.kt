package jez.lastfleetprotocol.prototype.components.shipbuilder.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TransformToolbar(
    isVisible: Boolean,
    onMirrorX: () -> Unit,
    onMirrorY: () -> Unit,
    onRotateCW: () -> Unit,
    onRotateCCW: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            FilledTonalButton(onClick = onMirrorX) {
                Text("\u2194") // ↔
            }
            FilledTonalButton(onClick = onMirrorY) {
                Text("\u2195") // ↕
            }
            FilledTonalButton(onClick = onRotateCW) {
                Text("\u21BB") // ↻
            }
            FilledTonalButton(onClick = onRotateCCW) {
                Text("\u21BA") // ↺
            }
        }
    }
}
