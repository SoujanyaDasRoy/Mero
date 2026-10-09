package com.mero.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.R

/**
 * Mero's own splash, shown after the system one hands over: the logo's lavender gradient across
 * the whole screen with the M on it, so the two splashes read as one. The system one
 * (Theme.Mero.Splash) only covers the cold-start gap and cannot animate a raster logo, so the
 * motion lives here.
 */
@Composable
fun MeroSplash(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.82f) }
    val fade = remember { Animatable(0f) }
    val wordmark = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        wordmark.animateTo(1f, tween(280))
        kotlinx.coroutines.delay(420)
        fade.animateTo(0f, tween(260))
        onFinished()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(colorResource(R.color.splash_centre), colorResource(R.color.splash_edge))))
            .alpha(fade.value),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.mero_mark),
            contentDescription = "Mero",
            modifier = Modifier.width(190.dp).scale(scale.value),
        )
        Text(
            "Mero",
            Modifier
                .padding(top = 20.dp)
                .alpha(wordmark.value),
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF3A0A8C),
        )
    }
}
