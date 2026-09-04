package com.mero.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The design's four accent options. The token names in `Mero.dc.html` map almost
 * exactly onto Material 3 colour roles, so these are real [ColorScheme]s rather
 * than a parallel set of custom colours — `--accent` is `primary`, `--surf3` is
 * `surfaceContainerHigh`, and so on.
 */
enum class MeroAccent(val label: String, val swatch: Color) {
    Violet("Violet", Color(0xFFD0BCFF)),
    Blue("Blue", Color(0xFFA8C7FA)),
    Peach("Peach", Color(0xFFFFB59D)),
    Green("Green", Color(0xFFA6D3A0)),
}

/** `--playerTint` has no Material 3 equivalent, so it travels separately. */
@Immutable
data class MeroExtras(val playerTint: Color)

val LocalMeroExtras = staticCompositionLocalOf { MeroExtras(playerTint = Color(0xFF2A2233)) }

private val NeutralDark = darkColorScheme(
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private fun schemeFor(accent: MeroAccent): ColorScheme = when (accent) {
    MeroAccent.Violet -> NeutralDark.copy(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )

    MeroAccent.Blue -> NeutralDark.copy(
        primary = Color(0xFFA8C7FA),
        onPrimary = Color(0xFF0A305F),
        primaryContainer = Color(0xFF28497A),
        onPrimaryContainer = Color(0xFFD7E3FF),
        secondaryContainer = Color(0xFF3F4759),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )

    MeroAccent.Peach -> NeutralDark.copy(
        primary = Color(0xFFFFB59D),
        onPrimary = Color(0xFF5B1A00),
        primaryContainer = Color(0xFF7D2F0F),
        onPrimaryContainer = Color(0xFFFFDBCF),
        secondaryContainer = Color(0xFF5A423A),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )

    MeroAccent.Green -> NeutralDark.copy(
        primary = Color(0xFFA6D3A0),
        onPrimary = Color(0xFF0C3910),
        primaryContainer = Color(0xFF265025),
        onPrimaryContainer = Color(0xFFC2EFBB),
        secondaryContainer = Color(0xFF3F4A3D),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )
}

private fun playerTintFor(accent: MeroAccent): Color = when (accent) {
    MeroAccent.Violet -> Color(0xFF2A2233)
    MeroAccent.Blue -> Color(0xFF1E2532)
    MeroAccent.Peach -> Color(0xFF2E211C)
    MeroAccent.Green -> Color(0xFF1C2620)
}

/** Pure black for AMOLED panels — real battery saving, not decoration. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121114),
    surfaceContainerHigh = Color(0xFF1A181D),
    surfaceContainerHighest = Color(0xFF232026),
)

private val Type = Typography().let { base ->
    // Roboto is the platform default on Android, so FontFamily.Default already
    // resolves to it — no font files to ship.
    base.copy(
        titleLarge = base.titleLarge.copy(
            fontFamily = FontFamily.Default,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
        ),
        titleMedium = base.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun MeroTheme(
    accent: MeroAccent = MeroAccent.Violet,
    dynamicColor: Boolean = false,
    amoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base = when {
        dynamicColor && supportsDynamic ->
            if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)

        else -> schemeFor(accent)
    }

    val scheme = if (amoled) base.amoled() else base

    CompositionLocalProvider(
        LocalMeroExtras provides MeroExtras(playerTint = playerTintFor(accent)),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
