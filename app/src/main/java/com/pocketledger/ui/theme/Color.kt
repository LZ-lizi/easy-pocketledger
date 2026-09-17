package com.pocketledger.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * The look is deliberately flat-and-airy: a single confident blue for actions,
 * teal for income, warm orange reserved for the "娱乐开销" main category so the
 * two halves of the ledger are distinguishable at a glance without reading text.
 */

// ---------------------------------------------------------------- light scheme
internal val LightPrimary = Color(0xFF2F6BFF)
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = Color(0xFFDCE6FF)
internal val LightOnPrimaryContainer = Color(0xFF00174A)

internal val LightSecondary = Color(0xFF00A88F)
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Color(0xFFC7F2E9)
internal val LightOnSecondaryContainer = Color(0xFF00382E)

internal val LightTertiary = Color(0xFFFF7A45)
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Color(0xFFFFE0D2)
internal val LightOnTertiaryContainer = Color(0xFF3A1200)

internal val LightError = Color(0xFFE5484D)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFFFDAD8)
internal val LightOnErrorContainer = Color(0xFF410004)

internal val LightBackground = Color(0xFFF7F9FC)
internal val LightOnBackground = Color(0xFF11151C)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightOnSurface = Color(0xFF11151C)
internal val LightSurfaceVariant = Color(0xFFE7ECF3)
internal val LightOnSurfaceVariant = Color(0xFF444C58)
internal val LightSurfaceContainerLow = Color(0xFFF2F5FA)
internal val LightSurfaceContainer = Color(0xFFEDF1F8)
internal val LightSurfaceContainerHigh = Color(0xFFE6EBF4)
internal val LightOutline = Color(0xFF737C8A)
internal val LightOutlineVariant = Color(0xFFC2CAD8)
internal val LightInverseSurface = Color(0xFF2C3138)
internal val LightInverseOnSurface = Color(0xFFF1F3F7)
internal val LightInversePrimary = Color(0xFFAFC6FF)

// ----------------------------------------------------------------- dark scheme
internal val DarkPrimary = Color(0xFFAFC6FF)
internal val DarkOnPrimary = Color(0xFF002A78)
internal val DarkPrimaryContainer = Color(0xFF1B4593)
internal val DarkOnPrimaryContainer = Color(0xFFDCE6FF)

internal val DarkSecondary = Color(0xFF6FDCC4)
internal val DarkOnSecondary = Color(0xFF00382E)
internal val DarkSecondaryContainer = Color(0xFF005142)
internal val DarkOnSecondaryContainer = Color(0xFFC7F2E9)

internal val DarkTertiary = Color(0xFFFFB59A)
internal val DarkOnTertiary = Color(0xFF5A1B00)
internal val DarkTertiaryContainer = Color(0xFF7E2A00)
internal val DarkOnTertiaryContainer = Color(0xFFFFE0D2)

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)

internal val DarkBackground = Color(0xFF0F1216)
internal val DarkOnBackground = Color(0xFFE3E6EB)
internal val DarkSurface = Color(0xFF14181E)
internal val DarkOnSurface = Color(0xFFE3E6EB)
internal val DarkSurfaceVariant = Color(0xFF3F4650)
internal val DarkOnSurfaceVariant = Color(0xFFBFC6D2)
internal val DarkSurfaceContainerLow = Color(0xFF181C23)
internal val DarkSurfaceContainer = Color(0xFF1D2229)
internal val DarkSurfaceContainerHigh = Color(0xFF252A32)
internal val DarkOutline = Color(0xFF89909C)
internal val DarkOutlineVariant = Color(0xFF3F4650)
internal val DarkInverseSurface = Color(0xFFE3E6EB)
internal val DarkInverseOnSurface = Color(0xFF2C3138)
internal val DarkInversePrimary = Color(0xFF2F6BFF)

/**
 * Ledger-specific semantic colours that Material 3 has no slot for.
 *
 * Income/expense must never be conveyed by red/green alone (colour-blind users),
 * so the UI always pairs these with a sign or an icon as well.
 */
@Immutable
data class LedgerColors(
    val income: Color,
    val onIncomeContainer: Color,
    val incomeContainer: Color,
    val expense: Color,
    val onExpenseContainer: Color,
    val expenseContainer: Color,
    val transfer: Color,
    /** Accent for the 「娱乐开销」 main category. */
    val leisure: Color,
    val leisureContainer: Color,
    /** Accent for the 「日常生活」 main category. */
    val daily: Color,
    val dailyContainer: Color,
)

internal val LightLedgerColors = LedgerColors(
    income = Color(0xFF12A150),
    onIncomeContainer = Color(0xFF04361B),
    incomeContainer = Color(0xFFD3F5E0),
    expense = Color(0xFFE5484D),
    onExpenseContainer = Color(0xFF410004),
    expenseContainer = Color(0xFFFFDAD8),
    transfer = Color(0xFF6B7280),
    leisure = Color(0xFFFF7A45),
    leisureContainer = Color(0xFFFFE0D2),
    daily = Color(0xFF2F6BFF),
    dailyContainer = Color(0xFFDCE6FF),
)

internal val DarkLedgerColors = LedgerColors(
    income = Color(0xFF3DD68C),
    onIncomeContainer = Color(0xFFB6F2CE),
    incomeContainer = Color(0xFF0B4A2A),
    expense = Color(0xFFFF8A8F),
    onExpenseContainer = Color(0xFFFFDAD8),
    expenseContainer = Color(0xFF6B1418),
    transfer = Color(0xFF9AA3B2),
    leisure = Color(0xFFFFB59A),
    leisureContainer = Color(0xFF7E2A00),
    daily = Color(0xFFAFC6FF),
    dailyContainer = Color(0xFF1B4593),
)

val LocalLedgerColors = staticCompositionLocalOf {
    LightLedgerColors
}
