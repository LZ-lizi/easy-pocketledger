package com.pocketledger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pocketledger.R

/**
 * MiSans is bundled as a GB2312 subset (three real weights, ~4.9 MB total).
 *
 * Only [FontWeight.Normal], [Medium] and [SemiBold] exist as real files. Every
 * weight the UI asks for is mapped onto one of them so Compose never has to
 * synthesise a faux-bold, which would look muddy on a CJK face.
 */
val MiSans = FontFamily(
    Font(R.font.misans_regular, FontWeight.Light),
    Font(R.font.misans_regular, FontWeight.Normal),
    Font(R.font.misans_medium, FontWeight.Medium),
    Font(R.font.misans_semibold, FontWeight.SemiBold),
    Font(R.font.misans_semibold, FontWeight.Bold),
)

private val Default = Typography()

/**
 * Material 3's default weights are all 400 or 500, so mapping the family onto
 * every role needs no weight changes -- only the family swap.
 */
val LedgerTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = MiSans),
    displayMedium = Default.displayMedium.copy(fontFamily = MiSans),
    displaySmall = Default.displaySmall.copy(fontFamily = MiSans),
    headlineLarge = Default.headlineLarge.copy(fontFamily = MiSans),
    headlineMedium = Default.headlineMedium.copy(fontFamily = MiSans),
    headlineSmall = Default.headlineSmall.copy(fontFamily = MiSans),
    titleLarge = Default.titleLarge.copy(fontFamily = MiSans),
    titleMedium = Default.titleMedium.copy(fontFamily = MiSans),
    titleSmall = Default.titleSmall.copy(fontFamily = MiSans),
    bodyLarge = Default.bodyLarge.copy(fontFamily = MiSans),
    bodyMedium = Default.bodyMedium.copy(fontFamily = MiSans),
    bodySmall = Default.bodySmall.copy(fontFamily = MiSans),
    labelLarge = Default.labelLarge.copy(fontFamily = MiSans),
    labelMedium = Default.labelMedium.copy(fontFamily = MiSans),
    labelSmall = Default.labelSmall.copy(fontFamily = MiSans),
)

/**
 * Money styles. `tnum` asks the font for tabular figures so amounts line up in
 * columns; MiSans ignores the feature harmlessly if it is absent.
 */
object MoneyTextStyles {
    val Hero = TextStyle(
        fontFamily = MiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        fontFeatureSettings = "tnum",
    )
    val Large = TextStyle(
        fontFamily = MiSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = "tnum",
    )
    val Medium = TextStyle(
        fontFamily = MiSans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum",
    )
    val Small = TextStyle(
        fontFamily = MiSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    )
}
