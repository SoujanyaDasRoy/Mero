package com.mero.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fades scrolling content into the page colour behind the floating tab bar and mini player, so text
 * never runs under a bare capsule. Nothing else sits behind them: this is the only thing there.
 * (Taken from the Bhagavatam app.)
 */
@Composable
fun BottomScrim(modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val page = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.verticalGradient(listOf(page.copy(alpha = 0f), page.copy(alpha = 0.92f), page))),
    )
}
