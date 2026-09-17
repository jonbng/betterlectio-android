package dk.betterlectio.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Brand seed from BetterLectio (Flutter splash / product blue)
val BrandBlue = Color(0xFF3362E1)
val BrandBlueDark = Color(0xFF8AB4FF)
val BrandBlueContainer = Color(0xFFD8E2FF)
val BrandBlueContainerDark = Color(0xFF1A3A8F)

val Neutral10 = Color(0xFF1A1C1E)
val Neutral90 = Color(0xFFE2E2E6)
val Neutral99 = Color(0xFFFCFCFF)

val SurfaceLight = Color(0xFFF8F9FC)
val SurfaceDark = Color(0xFF111318)

val ErrorLight = Color(0xFFBA1A1A)
val ErrorDark = Color(0xFFFFB4AB)

// Schedule status accents (Lectio)
val StatusChanged = Color(0xFFE6A817)
val StatusCancelled = Color(0xFFD32F2F)
val StatusNormal = BrandBlue

/** Mix [amount] of [accent] into this surface while keeping the result fully opaque. */
fun Color.tinted(accent: Color, amount: Float = 0.18f): Color {
    val fraction = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (accent.red - red) * fraction,
        green = green + (accent.green - green) * fraction,
        blue = blue + (accent.blue - blue) * fraction,
        alpha = 1f,
    )
}

/** A quiet subject tint that remains legible with the active theme's surface text colors. */
fun Color.scheduleWash(accent: Color): Color =
    tinted(accent, amount = if (luminance() < 0.5f) 0.24f else 0.18f)
