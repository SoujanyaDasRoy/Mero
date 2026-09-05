package com.mero.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
    Amber("Amber", Color(0xFFFED703)),
    Rose("Rose", Color(0xFFFFB1C8)),
    Teal("Teal", Color(0xFF7FD8CA)),
    Lime("Lime", Color(0xFFC6E77D)),
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

private val NeutralLight = lightColorScheme(
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF201A18),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF201A18),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EC),
    surfaceContainer = Color(0xFFFFEAE3),
    surfaceContainerHigh = Color(0xFFFFE3DB),
    surfaceContainerHighest = Color(0xFFF5D8D0),
    surfaceVariant = Color(0xFFF0DED8),
    onSurfaceVariant = Color(0xFF53433E),
    outline = Color(0xFF85736D),
    outlineVariant = Color(0xFFD8C4BE),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
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

    // Mero's own brand yellow, for people who want the app to match its icon.
    MeroAccent.Amber -> NeutralDark.copy(
        primary = Color(0xFFFED703),
        onPrimary = Color(0xFF3A2F00),
        primaryContainer = Color(0xFF5A4A00),
        onPrimaryContainer = Color(0xFFFFF0A8),
        secondaryContainer = Color(0xFF4C4636),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )

    MeroAccent.Rose -> NeutralDark.copy(
        primary = Color(0xFFFFB1C8),
        onPrimary = Color(0xFF5E1133),
        primaryContainer = Color(0xFF7B2949),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondaryContainer = Color(0xFF574149),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )

    MeroAccent.Teal -> NeutralDark.copy(
        primary = Color(0xFF7FD8CA),
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF005045),
        onPrimaryContainer = Color(0xFF9BF5E6),
        secondaryContainer = Color(0xFF3A4B48),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )

    MeroAccent.Lime -> NeutralDark.copy(
        primary = Color(0xFFC6E77D),
        onPrimary = Color(0xFF2C3400),
        primaryContainer = Color(0xFF414C00),
        onPrimaryContainer = Color(0xFFE2FF96),
        secondaryContainer = Color(0xFF454B39),
        onSecondaryContainer = Color(0xFFE6E0E9),
    )
}

private fun lightSchemeFor(accent: MeroAccent): ColorScheme = when (accent) {
    MeroAccent.Violet -> NeutralLight.copy(
        primary = Color(0xFF6750A4), onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D),
        secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1D192B),
    )
    MeroAccent.Blue -> NeutralLight.copy(
        primary = Color(0xFF3F5F90), onPrimary = Color.White,
        primaryContainer = Color(0xFFD7E3FF), onPrimaryContainer = Color(0xFF001B3E),
        secondaryContainer = Color(0xFFDCE2F9), onSecondaryContainer = Color(0xFF171B25),
    )
    MeroAccent.Peach -> NeutralLight.copy(
        primary = Color(0xFF9A4529), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF3A0B00),
        secondaryContainer = Color(0xFFFFDDBE), onSecondaryContainer = Color(0xFF2B1708),
    )
    MeroAccent.Green -> NeutralLight.copy(
        primary = Color(0xFF3F683A), onPrimary = Color.White,
        primaryContainer = Color(0xFFC2EFBB), onPrimaryContainer = Color(0xFF0C390D),
        secondaryContainer = Color(0xFFDCE8D5), onSecondaryContainer = Color(0xFF172016),
    )
    MeroAccent.Amber -> NeutralLight.copy(
        primary = Color(0xFF765900), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE088), onPrimaryContainer = Color(0xFF241A00),
        secondaryContainer = Color(0xFFF6E4B8), onSecondaryContainer = Color(0xFF211B0D),
    )
    MeroAccent.Rose -> NeutralLight.copy(
        primary = Color(0xFF98405E), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E2), onPrimaryContainer = Color(0xFF3E001B),
        secondaryContainer = Color(0xFFF8DCE4), onSecondaryContainer = Color(0xFF26171C),
    )
    MeroAccent.Teal -> NeutralLight.copy(
        primary = Color(0xFF00695B), onPrimary = Color.White,
        primaryContainer = Color(0xFF9BF5E6), onPrimaryContainer = Color(0xFF00201B),
        secondaryContainer = Color(0xFFD1E8E2), onSecondaryContainer = Color(0xFF10201D),
    )
    MeroAccent.Lime -> NeutralLight.copy(
        primary = Color(0xFF566500), onPrimary = Color.White,
        primaryContainer = Color(0xFFE2FF96), onPrimaryContainer = Color(0xFF191E00),
        secondaryContainer = Color(0xFFE7E8C5), onSecondaryContainer = Color(0xFF1D1E0F),
    )
}

private fun playerTintFor(accent: MeroAccent): Color = when (accent) {
    MeroAccent.Violet -> Color(0xFF2A2233)
    MeroAccent.Blue -> Color(0xFF1E2532)
    MeroAccent.Peach -> Color(0xFF2E211C)
    MeroAccent.Green -> Color(0xFF1C2620)
    MeroAccent.Amber -> Color(0xFF2C2718)
    MeroAccent.Rose -> Color(0xFF2F2027)
    MeroAccent.Teal -> Color(0xFF162725)
    MeroAccent.Lime -> Color(0xFF25291A)
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
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base = when {
        dynamicColor && supportsDynamic ->
            if (darkMode) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)

        darkMode -> schemeFor(accent)
        else -> lightSchemeFor(accent)
    }

    val scheme = if (amoled && darkMode) base.amoled() else base

    CompositionLocalProvider(
        LocalMeroExtras provides MeroExtras(playerTint = playerTintFor(accent)),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Type, content = content)
    }
}
