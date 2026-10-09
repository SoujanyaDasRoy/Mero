package com.mero.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** How long things move, in milliseconds. Short enough to feel instant, long enough to be seen. */
object Motion {
    const val Press = 120
    const val Sheet = 200
    const val Page = 280
}

/**
 * A tap target that dips to 97% under the finger and springs back, so everything pressable answers
 * the touch before the action happens. Same parameters as `clickable`.
 */
fun Modifier.clickableScaled(
    enabled: Boolean = true,
    role: Role? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

private val revealed = HashSet<String>()

/**
 * Lifts a section in (fades up a little, one after another) the first time it appears in this run of
 * the app, and never again: scrolling back to it, or returning to the screen, shows it as it is.
 */
fun Modifier.reveal(key: String, index: Int = 0): Modifier = composed {
    val first = remember(key) { revealed.add(key) }
    val progress = remember(key) { Animatable(if (first) 0f else 1f) }
    LaunchedEffect(key) {
        if (first) {
            delay(index * 45L)
            progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}
