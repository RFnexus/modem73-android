package app.modem73.ui.status

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.modem73.ui.model.ActivitySample
import app.modem73.ui.theme.Modem73Colors

@Composable
fun SnrSparkline(values: List<Float>, modifier: Modifier = Modifier, height: Dp = 36.dp, maxDb: Float = 30f) {
    val rx = Modem73Colors.rx
    val warn = Modem73Colors.warn
    val info = Modem73Colors.info
    val tx = Modem73Colors.tx
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val gap = 2f
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val f = (v / maxDb).coerceIn(0.04f, 1f)
            val h = size.height * f
            val color = when {
                v > 15f -> rx
                v > 8f -> warn
                v > 3f -> info
                else -> tx
            }
            drawRect(color, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}

@Composable
fun OccSparkline(values: List<Float>, modifier: Modifier = Modifier, height: Dp = 28.dp) {
    val rx = Modem73Colors.rx
    val warn = Modem73Colors.warn
    val tx = Modem73Colors.tx
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        val gap = 1.5f
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val f = v.coerceIn(0.04f, 1f)
            val h = size.height * f
            val color = when {
                v >= 0.7f -> tx
                v >= 0.3f -> warn
                else -> rx
            }
            drawRect(color, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
    }
}

@Composable
fun ActivityGraph(samples: List<ActivitySample>, thresholdDb: Float, modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val rx = Modem73Colors.rx
    val info = Modem73Colors.info
    val special = Modem73Colors.special
    val tx = Modem73Colors.tx
    val warn = Modem73Colors.warn
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (samples.isEmpty()) return@Canvas
        val gap = 1.5f
        val w = (size.width - gap * (samples.size - 1)) / samples.size
        fun frac(db: Float) = ((db + 80f) / 80f).coerceIn(0.03f, 1f)
        samples.forEachIndexed { i, s ->
            val h = size.height * frac(s.levelDb)
            val color = when {
                s.tone -> tx
                s.dcd -> special
                s.levelDb > thresholdDb -> info
                else -> rx
            }
            drawRect(color, Offset(i * (w + gap), size.height - h), Size(w, h))
        }
        val ty = size.height * (1f - frac(thresholdDb))
        drawLine(
            color = warn.copy(alpha = 0.8f),
            start = Offset(0f, ty),
            end = Offset(size.width, ty),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        )
    }
}

@Composable
fun LevelMeter(levelDb: Float, thresholdDb: Float, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val rx = Modem73Colors.rx
    val warn = Modem73Colors.warn
    val info = Modem73Colors.info
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val cells = 24
        val gap = 2f
        val w = (size.width - gap * (cells - 1)) / cells
        val lit = ((levelDb + 80f) / 80f * cells).toInt().coerceIn(0, cells)
        val thr = ((thresholdDb + 80f) / 80f * cells).toInt().coerceIn(0, cells - 1)
        for (i in 0 until cells) {
            if (i < lit) {
                val color: Color = when {
                    i > thr -> info
                    i > cells * 2 / 3 -> warn
                    else -> rx
                }
                drawRect(color, Offset(i * (w + gap), 0f), Size(w, size.height))
            }
        }
        val x = thr * (w + gap) + w
        drawLine(marker, Offset(x, -2f), Offset(x, size.height + 2f), strokeWidth = 2f)
    }
}
