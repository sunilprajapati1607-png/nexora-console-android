package org.nexoraofficial.console.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The card's inset 3px accent — the application's own signature. */
fun Modifier.drawAccentEdge(color: Color, alpha: Float = 0.45f): Modifier = drawBehind {
    drawRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset.Zero,
        size = Size(3.dp.toPx(), size.height)
    )
}

/**
 * .acts — buttons that wrap, because a row of six actions is a row of six on
 * a desktop and three lines of two on a phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}
