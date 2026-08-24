package app.modem73.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.modem73.ui.components.KeyValueRow
import app.modem73.ui.components.Panel
import app.modem73.ui.components.SectionTitle
import app.modem73.ui.model.CsmaPhaseKind
import app.modem73.ui.model.PacketEntry
import app.modem73.ui.model.StatusUiState
import app.modem73.ui.theme.Modem73Colors

@Composable
fun StatusScreen(
    state: StatusUiState,
    waterfall: List<FloatArray>,
    running: Boolean,
    error: String?,
    micDenied: Boolean,
    onStartStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            StationStrip(state, running, onStartStop)
            if (micDenied && !running) {
                Text(
                    text = "NO AUDIO: mic permission denied, tap to grant",
                    color = Modem73Colors.warn,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable { onStartStop() }
                )
            }
            if (error != null) {
                Text(
                    text = error,
                    color = Modem73Colors.tx,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            SignalPanel(state)
            CsmaPanel(state)
            ActivityPanel(state)
            StatsPanel(state)
            RecentPanel(state.packets, state.rxFrames)
            Spacer(Modifier.height(16.dp))
        }
        WaterfallDock(waterfall)
    }
}

@Composable
private fun StationStrip(state: StatusUiState, running: Boolean, onStartStop: () -> Unit) {
    val tx = Modem73Colors.tx
    val rx = Modem73Colors.rx
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(if (running) (if (state.transmitting) tx else rx) else MaterialTheme.colorScheme.onSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (!running) "OFF" else if (state.transmitting) "TX" else "RX",
                color = Modem73Colors.onAccent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (state.swrAlarm != null) {
            Spacer(Modifier.width(6.dp))
            Chip(String.format("!SWR %.1f", state.swrAlarm), tx)
        }
        if (state.pttFail) {
            Spacer(Modifier.width(6.dp))
            Chip("!PTT", tx)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.callsign,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = state.modeSummary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = state.clock,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleSmall
            )
            Row {
                Counter("${state.rxFrames}", "v", rx)
                Spacer(Modifier.width(6.dp))
                Counter("${state.txFrames}", "^", tx)
                Spacer(Modifier.width(6.dp))
                Counter("${state.clients}", "c", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = Modem73Colors.onAccent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Counter(value: String, suffix: String, color: Color) {
    Row {
        Text(value, color = color, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Text(suffix, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LabeledChart(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(104.dp)
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun SignalPanel(state: StatusUiState) {
    val rx = Modem73Colors.rx
    val warn = Modem73Colors.warn
    val info = Modem73Colors.info
    val tx = Modem73Colors.tx
    SectionTitle("SIGNAL")
    Panel {
        KeyValueRow(
            "Carrier",
            String.format("%.1f dB", state.carrierDb),
            valueColor = if (state.carrierDb > state.thresholdDb) info else rx
        )
        LabeledChart("Level") { LevelMeter(state.carrierDb, state.thresholdDb) }
        KeyValueRow("Threshold", String.format("%.0f dB", state.thresholdDb))
        KeyValueRow(
            "Last SNR",
            String.format("%.1f dB", state.lastSnrDb),
            valueColor = when {
                state.lastSnrDb > 10f -> rx
                state.lastSnrDb > 5f -> warn
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        LabeledChart("SNR Hist") { SnrSparkline(state.snrHistory) }
        val berColor = if (state.robustModem) {
            if (state.berPct >= 18f) tx else MaterialTheme.colorScheme.onSurface
        } else when {
            state.berPct < 3f -> rx
            state.berPct < 13f -> warn
            else -> tx
        }
        KeyValueRow("BER", if (state.berPct < 0f) "---" else String.format("%.2f%%", state.berPct), valueColor = berColor)
        val heard = state.heard
        KeyValueRow(
            "Heard",
            if (heard == null) "---" else String.format("%s  %s %.0fdB  %s", heard.mode, heard.callsign, heard.snrDb, heard.age)
        )
    }
}

@Composable
private fun CsmaPanel(state: StatusUiState) {
    val rx = Modem73Colors.rx
    val warn = Modem73Colors.warn
    val info = Modem73Colors.info
    val tx = Modem73Colors.tx
    SectionTitle("CSMA")
    Panel {
        val statusText = buildString {
            append(if (state.csmaEnabled) "ON" else "OFF")
            if (state.csmaBusy) append("  BUSY")
            if (state.csmaSync) append(" (sync)")
        }
        KeyValueRow("Status", statusText, valueColor = if (!state.csmaEnabled || state.csmaBusy) warn else rx)
        KeyValueRow(
            "Occupancy",
            "${state.occupancyPct}% (30s)",
            valueColor = when {
                state.occupancyPct >= 70 -> tx
                state.occupancyPct >= 30 -> warn
                else -> rx
            }
        )
        LabeledChart("Occ Hist") { OccSparkline(state.occHistory) }
        KeyValueRow("Quiet", state.quietText)
        KeyValueRow("Window", state.windowText)
        KeyValueRow("Slot", "${state.slotMs} ms")
        val phaseColor = when (state.csmaPhaseKind) {
            CsmaPhaseKind.SENDING -> tx
            CsmaPhaseKind.DEFERRING, CsmaPhaseKind.QUIET -> warn
            CsmaPhaseKind.CONTENDING -> info
            CsmaPhaseKind.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val queueSuffix = if (state.txQueued > 0) "  ${state.txQueued} queued ~${state.txQueueEtaS}s" else ""
        KeyValueRow("TX", state.csmaPhaseText + queueSuffix, valueColor = phaseColor)
        KeyValueRow("Rig", state.rigText ?: "---")
    }
}

@Composable
private fun ActivityPanel(state: StatusUiState) {
    SectionTitle("ACTIVITY", trailing = "60 s")
    Panel {
        ActivityGraph(state.activity, state.thresholdDb, modifier = Modifier.padding(vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 2.dp)) {
            LegendDot("below", Modem73Colors.rx)
            LegendDot("above", Modem73Colors.info)
            LegendDot("DCD", Modem73Colors.special)
            LegendDot("tone", Modem73Colors.tx)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(10.dp).height(10.dp).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatsPanel(state: StatusUiState) {
    val rx = Modem73Colors.rx
    val tx = Modem73Colors.tx
    val warn = Modem73Colors.warn
    val info = Modem73Colors.info
    SectionTitle("STATS")
    Panel {
        Row(modifier = Modifier.padding(vertical = 6.dp)) {
            Stat("RX", "${state.rxFrames}", rx, Modifier.weight(0.8f))
            Stat("TX", "${state.txFrames}", tx, Modifier.weight(0.8f))
            val pct = if (state.syncCount > 0) 100 * state.rxErrors / state.syncCount else 0
            Stat("Err", "${state.rxErrors}/${state.syncCount} ($pct%)", when {
                state.rxErrors == 0 -> rx
                pct < 20 -> warn
                else -> tx
            }, Modifier.weight(1.9f))
            Stat("Clients", "${state.clients}", if (state.clients > 0) info else MaterialTheme.colorScheme.onSurface, Modifier.weight(1.1f))
            Stat("Queue", "${state.queue}", MaterialTheme.colorScheme.onSurface, Modifier.weight(0.9f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RecentPanel(packets: List<PacketEntry>, rxFrames: Int) {
    val rx = Modem73Colors.rx
    val tx = Modem73Colors.tx
    val info = Modem73Colors.info
    val warn = Modem73Colors.warn
    var lastRx by remember { mutableIntStateOf(-1) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(rxFrames) {
        if (lastRx in 0 until rxFrames) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(900))
        }
        lastRx = rxFrames
    }
    SectionTitle("RECENT")
    Panel {
        if (packets.isEmpty()) {
            Text(
                "Waiting for packets...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        packets.forEachIndexed { i, p ->
            val bg = if (i == 0 && !p.tx && flash.value > 0f) rx.copy(alpha = 0.3f * flash.value) else Color.Transparent
            Row(modifier = Modifier.fillMaxWidth().background(bg).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.tx) "TX" else "RX", color = if (p.tx) tx else rx, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                Text(String.format("%4dB", p.bytes), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.width(54.dp))
                Text(p.age, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
                Text(p.mode, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (p.snrDb != null) {
                    Text(String.format("%.0fdB", p.snrDb), color = info, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
                }
                if (p.berPct != null) {
                    val c = when {
                        p.berPct < 3f -> rx
                        p.berPct < 13f -> warn
                        else -> tx
                    }
                    Text(String.format("%.1f%%", p.berPct), color = c, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun WaterfallDock(rows: List<FloatArray>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "WATERFALL",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                "0-3 kHz  center 1500  bw 2400",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(6.dp))
        Waterfall(
            rows = rows,
            palette = Modem73Colors.waterfall,
            spec = WaterfallSpec(),
            markerColor = Modem73Colors.waterfallMarker,
            height = 150.dp
        )
    }
}
