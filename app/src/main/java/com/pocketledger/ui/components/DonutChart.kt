package com.pocketledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One wedge of a [DonutChart]. */
data class ChartSlice(
    val label: String,
    val value: Long,
    val color: Color,
)

/**
 * Donut chart drawn with Canvas.
 *
 * Hand-drawn rather than a charting library: the app needs exactly one donut, and
 * `drawArc` covers it in a few lines without taking on a dependency whose API and
 * Compose version must both be tracked.
 *
 * A small gap is left between wedges so adjacent colours never blur together, and
 * a wedge too thin to show a gap is drawn solid instead of disappearing.
 */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier,
    diameter: Dp = 180.dp,
    thickness: Dp = 24.dp,
    /** Optional composable drawn in the hole, e.g. the total. */
    center: (@Composable () -> Unit)? = null,
) {
    val visible = slices.filter { it.value > 0L }
    val total = visible.sumOf { it.value }

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            if (total <= 0L) {
                // An empty ring still reads as "a chart with no data" rather than a hole.
                drawArc(
                    color = Color.Gray.copy(alpha = 0.18f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(thickness.toPx() / 2f, thickness.toPx() / 2f),
                    size = Size(
                        size.width - thickness.toPx(),
                        size.height - thickness.toPx(),
                    ),
                    style = Stroke(width = thickness.toPx()),
                )
                return@Canvas
            }

            val gapDegrees = 1.6f
            var startAngle = -90f          // begin at 12 o'clock
            visible.forEach { slice ->
                val sweep = 360f * (slice.value.toFloat() / total.toFloat())
                // Only carve a gap out of wedges wide enough to survive it.
                val drawnSweep = if (sweep > gapDegrees * 2f) sweep - gapDegrees else sweep
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = drawnSweep,
                    useCenter = false,
                    topLeft = Offset(thickness.toPx() / 2f, thickness.toPx() / 2f),
                    size = Size(
                        size.width - thickness.toPx(),
                        size.height - thickness.toPx(),
                    ),
                    style = Stroke(width = thickness.toPx(), cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }

        if (center != null) {
            Box(
                modifier = Modifier.size(diameter - thickness * 2),
                contentAlignment = Alignment.Center,
            ) {
                center()
            }
        }
    }
}
