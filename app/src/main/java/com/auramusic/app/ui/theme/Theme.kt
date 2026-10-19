package com.auramusic.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.auramusic.app.data.ThemeMode

val AuraCyan = Color(0xFF24E7FF)
val AuraBlue = Color(0xFF3D7BFF)
val AuraPurple = Color(0xFFB65CFF)
val AuraPink = Color(0xFFFF4FB3)
val AuraOrange = Color(0xFFFF9F43)
val AuraGreen = Color(0xFF2EE59D)

val NeonGradient = Brush.horizontalGradient(listOf(AuraCyan, AuraPurple, AuraPink))
val CyanPurple = Brush.horizontalGradient(listOf(AuraCyan, AuraPurple))

@Immutable
data class AuraPalette(
    val isDark: Boolean,
    val bg: Color,
    val bgTop: Color,
    val card: Color,
    val card2: Color,
    val stroke: Color,
    val text: Color,
    val muted: Color,
    val navBar: Color
)

private val DarkPalette = AuraPalette(
    isDark = true,
    bg = Color(0xFF070A12),
    bgTop = Color(0xFF120B2A),
    card = Color(0xFF0F1522),
    card2 = Color(0xFF161E2E),
    stroke = Color(0x33B65CFF),
    text = Color(0xFFF5F7FA),
    muted = Color(0xFF93A1B5),
    navBar = Color(0xF20A0F1A)
)

private val LightPalette = AuraPalette(
    isDark = false,
    bg = Color(0xFFF4F6FB),
    bgTop = Color(0xFFE9E3FF),
    card = Color(0xFFFFFFFF),
    card2 = Color(0xFFEDEFF6),
    stroke = Color(0x337A3CFF),
    text = Color(0xFF111522),
    muted = Color(0xFF5F6B7E),
    navBar = Color(0xF5FFFFFF)
)

val LocalAura = staticCompositionLocalOf { DarkPalette }

object Aura {
    val colors: AuraPalette @Composable get() = LocalAura.current
}

private val AuraTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp)
    )
}

@Composable
fun AuraTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = AuraCyan, onPrimary = Color(0xFF001117),
            secondary = AuraPurple, onSecondary = Color.White,
            tertiary = AuraPink,
            background = palette.bg, onBackground = palette.text,
            surface = palette.card, onSurface = palette.text,
            surfaceVariant = palette.card2, onSurfaceVariant = palette.muted,
            surfaceContainer = palette.card, surfaceContainerHigh = palette.card2, surfaceContainerHighest = palette.card2,
            surfaceContainerLow = palette.card, surfaceContainerLowest = palette.bg,
            outline = palette.stroke, outlineVariant = palette.stroke
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0097B2), onPrimary = Color.White,
            secondary = Color(0xFF7A3CFF), onSecondary = Color.White,
            tertiary = Color(0xFFE0338F),
            background = palette.bg, onBackground = palette.text,
            surface = palette.card, onSurface = palette.text,
            surfaceVariant = palette.card2, onSurfaceVariant = palette.muted,
            surfaceContainer = palette.card, surfaceContainerHigh = palette.card2, surfaceContainerHighest = palette.card2,
            surfaceContainerLow = palette.card, surfaceContainerLowest = palette.bg,
            outline = palette.stroke, outlineVariant = palette.stroke
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalAura provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AuraTypography, content = content)
    }
}
