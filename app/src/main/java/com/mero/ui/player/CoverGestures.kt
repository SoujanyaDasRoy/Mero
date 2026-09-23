package com.mero.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How far the cover has to be pulled sideways before it changes track. */
private const val SKIP_THRESHOLD_PX = 160f

/**
 * The cover as something to touch, not just look at.
 *
 * - Swipe it left for the next track, right for the previous one. It follows
 *   the finger and springs back, so a half-hearted drag visibly does nothing.
 * - Double-tap it to like the song, with a heart that pops over it. Only ever
 *   likes: a double-tap that silently un-liked would be found out too late.
 *
 * Sideways and taps only; the player's own swipe-down-to-close stays vertical.
 */
@Composable
fun CoverGestures(
    liked: Boolean,
    onLike: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offset = remember { Animatable(0f) }
    val heart = remember { Animatable(0f) }

    Box(
        modifier
            .pointerInput(liked) {
                detectTapGestures(onDoubleTap = {
                    if (!liked) onLike()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        heart.snapTo(0f)
                        heart.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        heart.animateTo(0f, tween(250, delayMillis = 350))
                    }
                })
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val travelled = offset.value
                        scope.launch {
                            if (abs(travelled) > SKIP_THRESHOLD_PX) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (travelled < 0) onNext() else onPrev()
                            }
                            offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { change, dx ->
                    change.consume()
                    // Resistance past the threshold: it gives, but not forever.
                    val next = offset.value + dx * if (abs(offset.value) > SKIP_THRESHOLD_PX) 0.35f else 1f
                    scope.launch { offset.snapTo(next) }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / 40f
                alpha = 1f - (abs(offset.value) / 1200f).coerceAtMost(0.4f)
            },
            contentAlignment = Alignment.Center,
            content = content,
        )
        Icon(
            Icons.Rounded.Favorite,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    scaleX = 0.4f + heart.value * 0.8f
                    scaleY = 0.4f + heart.value * 0.8f
                    alpha = heart.value.coerceIn(0f, 1f)
                    shadowElevation = 12f
                },
        )
    }
}
