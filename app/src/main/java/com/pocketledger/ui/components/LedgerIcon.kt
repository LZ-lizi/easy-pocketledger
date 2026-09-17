package com.pocketledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's small icon set, drawn with Canvas.
 *
 * Hand-drawn rather than pulled from an icon library: the app needs six glyphs,
 * `material-icons-extended` is frozen at an old Compose version, and drawing them
 * keeps the binary free of a 3 MB dependency whose version must track Compose.
 * Reducing to geometric primitives also means they scale cleanly at any tint.
 */
enum class LedgerIcon {
    LIST,
    CHART,
    WALLET,
    PERSON,
    PLUS,
    SETTINGS,
}

@Composable
fun LedgerIconView(
    icon: LedgerIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f

        when (icon) {
            LedgerIcon.LIST -> {
                for (i in 0..2) {
                    val y = h * (0.28f + i * 0.22f)
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.22f, y),
                        end = Offset(w * 0.78f, y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }

            LedgerIcon.CHART -> {
                val baseline = h * 0.80f
                val bars = listOf(0.28f to 0.36f, 0.50f to 0.58f, 0.72f to 0.46f)
                bars.forEach { (cx, heightFraction) ->
                    drawLine(
                        color = tint,
                        start = Offset(w * cx, baseline),
                        end = Offset(w * cx, baseline - h * heightFraction),
                        strokeWidth = w * 0.13f,
                        cap = StrokeCap.Round,
                    )
                }
            }

            LedgerIcon.WALLET -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.14f, h * 0.26f),
                    size = Size(w * 0.72f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.12f),
                    style = Stroke(width = stroke),
                )
                drawCircle(
                    color = tint,
                    radius = w * 0.07f,
                    center = Offset(w * 0.70f, h * 0.50f),
                )
            }

            LedgerIcon.PERSON -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.16f,
                    center = Offset(w * 0.5f, h * 0.33f),
                )
                drawArc(
                    color = tint,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.52f),
                    size = Size(w * 0.64f, h * 0.56f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            LedgerIcon.PLUS -> {
                drawLine(
                    color = tint,
                    start = Offset(w * 0.5f, h * 0.24f),
                    end = Offset(w * 0.5f, h * 0.76f),
                    strokeWidth = stroke * 1.25f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(w * 0.24f, h * 0.5f),
                    end = Offset(w * 0.76f, h * 0.5f),
                    strokeWidth = stroke * 1.25f,
                    cap = StrokeCap.Round,
                )
            }

            LedgerIcon.SETTINGS -> {
                // Three sliders: recognisable as "settings" without a gear's detail.
                val rows = listOf(0.30f to 0.66f, 0.50f to 0.36f, 0.70f to 0.58f)
                rows.forEach { (cy, knobX) ->
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.20f, h * cy),
                        end = Offset(w * 0.80f, h * cy),
                        strokeWidth = stroke * 0.85f,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(
                        color = tint,
                        radius = w * 0.085f,
                        center = Offset(w * knobX, h * cy),
                    )
                }
            }
        }
    }
}
