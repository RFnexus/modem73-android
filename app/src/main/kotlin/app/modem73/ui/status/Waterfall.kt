package app.modem73.ui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class WaterfallSpec(
    val minHz: Int = 0,
    val maxHz: Int = 3000,
    val centerHz: Int = 1500,
    val bandwidthHz: Int = 2400
)

@Composable
fun Waterfall(
    rows: List<FloatArray>,
    palette: List<Color>,
    spec: WaterfallSpec,
    markerColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            if (rows.isEmpty() || palette.isEmpty()) return@Canvas
            val bins = rows.first().size
            val cellW = size.width / bins
            val cellH = size.height / rows.size
            rows.forEachIndexed { r, row ->
                val y = r * cellH
                for (b in 0 until bins) {
                    val v = row[b].coerceIn(0f, 0.999f)
                    val idx = (v * palette.size).toInt()
                    drawRect(
                        color = palette[idx],
                        topLeft = Offset(b * cellW, y),
                        size = Size(cellW + 0.5f, cellH + 0.5f)
                    )
                }
            }
            val span = (spec.maxHz - spec.minHz).toFloat()
            fun xOf(hz: Int) = (hz - spec.minHz) / span * size.width
            val lo = spec.centerHz - spec.bandwidthHz / 2
            val hi = spec.centerHz + spec.bandwidthHz / 2
            drawLine(markerColor, Offset(xOf(lo), 0f), Offset(xOf(lo), size.height), strokeWidth = 2f)
            drawLine(markerColor, Offset(xOf(hi), 0f), Offset(xOf(hi), size.height), strokeWidth = 2f)
            drawLine(markerColor.copy(alpha = 0.6f), Offset(xOf(spec.centerHz), 0f), Offset(xOf(spec.centerHz), size.height), strokeWidth = 1f)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        ) {
            val ticks = 6
            for (i in 0..ticks) {
                val hz = spec.minHz + ((spec.maxHz - spec.minHz) * i / ticks.toFloat()).roundToInt()
                Text(
                    text = if (hz >= 1000) String.format("%.1fk", hz / 1000f) else "$hz",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
