package com.pocketledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icon set, drawn with Canvas.
 *
 * Hand-drawn rather than pulled from an icon library: `material-icons-extended` is
 * frozen at an old Compose version, and adding it would tie a 3 MB dependency's
 * version to Compose. Everything here reduces to lines, arcs and rectangles, so it
 * scales cleanly at any size and tints correctly in both themes.
 *
 * Category icons exist so a grid of leaf categories is scannable by shape. A
 * first-character badge was the earlier placeholder and read poorly: 早/午/晚 and
 * 水/电/燃 all start with visually similar glyphs.
 */
enum class LedgerIcon {
    // Navigation
    LIST,
    CHART,
    /** A month grid, for the header button that switches to calendar view. */
    CALENDAR,
    WALLET,
    PERSON,
    PLUS,
    SETTINGS,
    CHEVRON_RIGHT,
    /** Three dots: opens the full category list from the entry grid. */
    ELLIPSIS,
    /** A magnifier: opens the search screen. */
    SEARCH,

    // Expense 大类
    FOOD,
    TRANSPORT,
    SHOPPING,
    HOME,
    COMMS,
    STUDY,
    CAMPUS,
    FUN,
    MEDICAL,
    GIFT,
    FINANCE,
    WORK,
    PET,
    OTHER,

    // Income and accounts
    INCOME,
    CASH,
    CARD,
    BANK,
    /** 支付宝: a card with the 支 stroke. */
    ALIPAY,
    /** 微信: two overlapping chat bubbles. */
    WECHAT,

    /** Used for custom categories that carry no curated key. */
    TAG,
    ;

    companion object {
        /**
         * Maps a stored `iconKey` to a glyph.
         *
         * Unknown or missing keys fall back to [TAG] rather than [OTHER] so a custom
         * category looks deliberate rather than mis-filed under 其他.
         */
        fun forKey(key: String?): LedgerIcon = when (key) {
            "food" -> FOOD
            "transport" -> TRANSPORT
            "shopping" -> SHOPPING
            "home" -> HOME
            "comms" -> COMMS
            "study" -> STUDY
            "campus" -> CAMPUS
            "fun" -> FUN
            "medical" -> MEDICAL
            "gift" -> GIFT
            "finance" -> FINANCE
            "work" -> WORK
            "pet" -> PET
            "other" -> OTHER
            "calendar" -> CALENDAR
            "income" -> INCOME
            "cash" -> CASH
            "card", "prepaid" -> CARD
            "bank" -> BANK
            "alipay" -> ALIPAY
            "wechat" -> WECHAT
            "wallet" -> WALLET
            else -> TAG
        }
    }
}

@Composable
fun LedgerIconView(
    icon: LedgerIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        drawLedgerIcon(icon, tint)
    }
}

/** Normalised drawing over a square canvas; every coordinate is a fraction of the side. */
private fun DrawScope.drawLedgerIcon(icon: LedgerIcon, tint: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.085f
    val thin = w * 0.065f
    val line = { x1: Float, y1: Float, x2: Float, y2: Float, width: Float ->
        drawLine(
            color = tint,
            start = Offset(w * x1, h * y1),
            end = Offset(w * x2, h * y2),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }
    val outline = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (icon) {
        LedgerIcon.LIST -> repeat(3) { i ->
            line(0.22f, 0.28f + i * 0.22f, 0.78f, 0.28f + i * 0.22f, stroke)
        }

        LedgerIcon.CHART -> listOf(0.28f to 0.36f, 0.50f to 0.58f, 0.72f to 0.46f)
            .forEach { (cx, f) -> line(cx, 0.80f, cx, 0.80f - f, w * 0.13f) }

        /**
         * A calendar: page, header rule, two binding rings and a grid of dates.
         *
         * The earlier toggle used a bar chart, which said "statistics" rather than
         * "month view" -- the ring-and-grid silhouette is what makes it unmistakable at
         * 22dp.
         */
        LedgerIcon.CALENDAR -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.14f, h * 0.26f),
                size = Size(w * 0.72f, h * 0.58f),
                cornerRadius = CornerRadius(w * 0.11f),
                style = outline,
            )
            // Header rule, so the top strip reads as the month/date bar.
            line(0.14f, 0.44f, 0.86f, 0.44f, thin)
            // Binding rings.
            line(0.34f, 0.16f, 0.34f, 0.30f, thin)
            line(0.66f, 0.16f, 0.66f, 0.30f, thin)
            // Date dots.
            listOf(0.34f, 0.50f, 0.66f).forEach { x ->
                drawCircle(tint, w * 0.045f, Offset(w * x, h * 0.58f))
                drawCircle(tint, w * 0.045f, Offset(w * x, h * 0.72f))
            }
        }

        /**
         * A magnifier: a ring with the handle running out to the lower right.
         *
         * Drawn as an outline circle rather than a filled one so it stays legible at
         * 18dp next to the ledger chip, where a solid disc would read as a bullet.
         */
        LedgerIcon.SEARCH -> {
            drawCircle(
                color = tint,
                radius = w * 0.26f,
                center = Offset(w * 0.44f, h * 0.42f),
                style = outline,
            )
            line(0.63f, 0.61f, 0.82f, 0.80f, stroke)
        }

        LedgerIcon.WALLET -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.14f, h * 0.26f),
                size = Size(w * 0.72f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.12f),
                style = outline,
            )
            drawCircle(tint, w * 0.07f, Offset(w * 0.70f, h * 0.50f))
        }

        LedgerIcon.PERSON -> {
            drawCircle(tint, w * 0.16f, Offset(w * 0.5f, h * 0.33f))
            drawArc(
                color = tint,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.52f),
                size = Size(w * 0.64f, h * 0.56f),
                style = outline,
            )
        }

        LedgerIcon.PLUS -> {
            line(0.5f, 0.24f, 0.5f, 0.76f, stroke * 1.25f)
            line(0.24f, 0.5f, 0.76f, 0.5f, stroke * 1.25f)
        }

        LedgerIcon.SETTINGS -> listOf(0.30f to 0.66f, 0.50f to 0.36f, 0.70f to 0.58f)
            .forEach { (cy, kx) ->
                line(0.20f, cy, 0.80f, cy, thin)
                drawCircle(tint, w * 0.085f, Offset(w * kx, h * cy))
            }

        LedgerIcon.CHEVRON_RIGHT -> {
            line(0.40f, 0.26f, 0.62f, 0.50f, stroke)
            line(0.62f, 0.50f, 0.40f, 0.74f, stroke)
        }

        // Three dots. Distinct from OTHER, which is a category glyph and must keep
        // meaning 其他 wherever a category icon is drawn.
        LedgerIcon.ELLIPSIS -> listOf(0.26f, 0.50f, 0.74f)
            .forEach { x -> drawCircle(tint, w * 0.085f, Offset(w * x, h * 0.5f)) }

        // A bowl: the bottom half of a circle plus its rim.
        LedgerIcon.FOOD -> {
            drawArc(
                color = tint,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.18f, h * 0.36f),
                size = Size(w * 0.64f, h * 0.44f),
                style = outline,
            )
            line(0.12f, 0.36f, 0.88f, 0.36f, stroke)
        }

        LedgerIcon.TRANSPORT -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.14f, h * 0.40f),
                size = Size(w * 0.72f, h * 0.28f),
                cornerRadius = CornerRadius(w * 0.09f),
                style = outline,
            )
            // The cabin, so it reads as a vehicle rather than a plain bar.
            val cabin = Path().apply {
                moveTo(w * 0.30f, h * 0.40f)
                lineTo(w * 0.40f, h * 0.26f)
                lineTo(w * 0.60f, h * 0.26f)
                lineTo(w * 0.70f, h * 0.40f)
            }
            drawPath(cabin, tint, style = outline)
            drawCircle(tint, w * 0.07f, Offset(w * 0.30f, h * 0.74f))
            drawCircle(tint, w * 0.07f, Offset(w * 0.70f, h * 0.74f))
        }

        // A shopping bag: body plus handle.
        LedgerIcon.SHOPPING -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.22f, h * 0.36f),
                size = Size(w * 0.56f, h * 0.44f),
                cornerRadius = CornerRadius(w * 0.09f),
                style = outline,
            )
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.36f, h * 0.18f),
                size = Size(w * 0.28f, h * 0.30f),
                style = Stroke(width = thin, cap = StrokeCap.Round),
            )
        }

        LedgerIcon.HOME -> {
            val roof = Path().apply {
                moveTo(w * 0.5f, h * 0.20f)
                lineTo(w * 0.86f, h * 0.48f)
                lineTo(w * 0.14f, h * 0.48f)
                close()
            }
            drawPath(roof, tint, style = outline)
            line(0.26f, 0.48f, 0.26f, 0.80f, stroke)
            line(0.74f, 0.48f, 0.74f, 0.80f, stroke)
            line(0.26f, 0.80f, 0.74f, 0.80f, stroke)
        }

        LedgerIcon.COMMS -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.32f, h * 0.16f),
                size = Size(w * 0.36f, h * 0.68f),
                cornerRadius = CornerRadius(w * 0.09f),
                style = outline,
            )
            line(0.43f, 0.73f, 0.57f, 0.73f, thin)
        }

        LedgerIcon.STUDY -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.26f),
                size = Size(w * 0.64f, h * 0.50f),
                cornerRadius = CornerRadius(w * 0.07f),
                style = outline,
            )
            line(0.5f, 0.26f, 0.5f, 0.76f, thin)
        }

        // A graduation cap: the board plus its tassel.
        LedgerIcon.CAMPUS -> {
            val board = Path().apply {
                moveTo(w * 0.5f, h * 0.22f)
                lineTo(w * 0.90f, h * 0.42f)
                lineTo(w * 0.5f, h * 0.62f)
                lineTo(w * 0.10f, h * 0.42f)
                close()
            }
            drawPath(board, tint, style = outline)
            line(0.76f, 0.47f, 0.76f, 0.72f, thin)
        }

        LedgerIcon.FUN -> {
            val play = Path().apply {
                moveTo(w * 0.34f, h * 0.24f)
                lineTo(w * 0.78f, h * 0.50f)
                lineTo(w * 0.34f, h * 0.76f)
                close()
            }
            drawPath(play, tint)
        }

        LedgerIcon.MEDICAL -> {
            line(0.5f, 0.20f, 0.5f, 0.80f, w * 0.16f)
            line(0.20f, 0.5f, 0.80f, 0.5f, w * 0.16f)
        }

        LedgerIcon.GIFT -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.18f, h * 0.42f),
                size = Size(w * 0.64f, h * 0.40f),
                cornerRadius = CornerRadius(w * 0.06f),
                style = outline,
            )
            line(0.12f, 0.42f, 0.88f, 0.42f, stroke)
            line(0.5f, 0.42f, 0.5f, 0.82f, thin)
            line(0.5f, 0.42f, 0.5f, 0.24f, thin)
        }

        LedgerIcon.FINANCE -> {
            drawCircle(tint, w * 0.30f, Offset(w * 0.5f, h * 0.5f), style = outline)
            line(0.5f, 0.32f, 0.5f, 0.68f, thin)
        }

        LedgerIcon.WORK -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.14f, h * 0.38f),
                size = Size(w * 0.72f, h * 0.42f),
                cornerRadius = CornerRadius(w * 0.08f),
                style = outline,
            )
            drawArc(
                color = tint,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(w * 0.38f, h * 0.22f),
                size = Size(w * 0.24f, h * 0.30f),
                style = Stroke(width = thin, cap = StrokeCap.Round),
            )
        }

        // A paw: three toes and the pad.
        LedgerIcon.PET -> {
            drawCircle(tint, w * 0.070f, Offset(w * 0.30f, h * 0.34f))
            drawCircle(tint, w * 0.070f, Offset(w * 0.50f, h * 0.27f))
            drawCircle(tint, w * 0.070f, Offset(w * 0.70f, h * 0.34f))
            drawCircle(tint, w * 0.155f, Offset(w * 0.5f, h * 0.66f))
        }

        LedgerIcon.OTHER -> repeat(3) { i ->
            drawCircle(tint, w * 0.070f, Offset(w * (0.28f + i * 0.22f), h * 0.5f))
        }

        // Money arriving: an arrow dropping into a tray.
        LedgerIcon.INCOME -> {
            line(0.50f, 0.20f, 0.50f, 0.58f, stroke)
            line(0.36f, 0.45f, 0.50f, 0.59f, stroke)
            line(0.64f, 0.45f, 0.50f, 0.59f, stroke)
            line(0.22f, 0.78f, 0.78f, 0.78f, stroke)
        }

        LedgerIcon.CASH -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.12f, h * 0.32f),
                size = Size(w * 0.76f, h * 0.36f),
                cornerRadius = CornerRadius(w * 0.07f),
                style = outline,
            )
            drawCircle(tint, w * 0.09f, Offset(w * 0.5f, h * 0.5f), style = Stroke(width = thin))
        }

        LedgerIcon.CARD -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.12f, h * 0.30f),
                size = Size(w * 0.76f, h * 0.40f),
                cornerRadius = CornerRadius(w * 0.08f),
                style = outline,
            )
            line(0.12f, 0.44f, 0.88f, 0.44f, w * 0.10f)
            line(0.24f, 0.60f, 0.46f, 0.60f, thin)
        }

        // A bank: pediment, columns, base.
        LedgerIcon.BANK -> {
            val pediment = Path().apply {
                moveTo(w * 0.5f, h * 0.18f)
                lineTo(w * 0.88f, h * 0.42f)
                lineTo(w * 0.12f, h * 0.42f)
                close()
            }
            drawPath(pediment, tint, style = outline)
            listOf(0.28f, 0.50f, 0.72f).forEach { x -> line(x, 0.48f, x, 0.74f, thin) }
            line(0.14f, 0.80f, 0.86f, 0.80f, stroke)
        }

        // 支付宝: a card with a stylised 支, so it is not confused with a generic card.
        LedgerIcon.ALIPAY -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.12f, h * 0.28f),
                size = Size(w * 0.76f, h * 0.44f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = outline,
            )
            line(0.36f, 0.42f, 0.64f, 0.42f, thin)
            line(0.50f, 0.42f, 0.50f, 0.60f, thin)
            line(0.34f, 0.58f, 0.66f, 0.58f, thin)
        }

        // 微信: two overlapping speech bubbles.
        LedgerIcon.WECHAT -> {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.10f, h * 0.22f),
                size = Size(w * 0.56f, h * 0.42f),
                cornerRadius = CornerRadius(w * 0.14f),
                style = outline,
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.36f, h * 0.42f),
                size = Size(w * 0.54f, h * 0.38f),
                cornerRadius = CornerRadius(w * 0.14f),
                style = outline,
            )
            drawCircle(tint, w * 0.045f, Offset(w * 0.28f, h * 0.43f))
            drawCircle(tint, w * 0.045f, Offset(w * 0.48f, h * 0.43f))
        }

        // A luggage tag: the fallback for custom categories.
        LedgerIcon.TAG -> {
            val tag = Path().apply {
                moveTo(w * 0.52f, h * 0.18f)
                lineTo(w * 0.86f, h * 0.52f)
                lineTo(w * 0.52f, h * 0.86f)
                lineTo(w * 0.16f, h * 0.50f)
                lineTo(w * 0.16f, h * 0.18f)
                close()
            }
            drawPath(tag, tint, style = outline)
            drawCircle(tint, w * 0.06f, Offset(w * 0.30f, h * 0.31f))
        }
    }
}
