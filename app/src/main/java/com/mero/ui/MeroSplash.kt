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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.R

/**
 * Mero's own splash, shown after the system one hands over. The system splash
 * (Theme.Mero.Splash) only covers the cold-start gap and can't animate a raster
 * logo, so the actual motion lives here.
 */
@Composable
fun MeroSplash(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.72f) }
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
            .background(MaterialTheme.colorScheme.background)
            .alpha(fade.value),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.mero_logo),
            contentDescription = "Mero",
            modifier = Modifier
                .size(132.dp)
                .scale(scale.value)
                .clip(RoundedCornerShape(30.dp)),
        )
        Text(
            "Mero",
            Modifier
                .padding(top = 22.dp)
                .alpha(wordmark.value),
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
