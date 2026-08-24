package app.modem73.ui.rig

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.modem73.ui.ModemViewModel
import app.modem73.ui.components.ChoiceChips
import app.modem73.ui.components.Panel
import app.modem73.ui.components.PickerDialog
import app.modem73.ui.components.SectionTitle
import app.modem73.ui.components.SelectorRow
import app.modem73.ui.components.StepperRow
import app.modem73.ui.components.TextInputDialog
import app.modem73.ui.components.ToggleRow
import app.modem73.ui.model.MeterLevel
import app.modem73.ui.model.RigOptions
import app.modem73.ui.model.RigUiState
import app.modem73.ui.theme.Modem73Colors

private sealed interface RigDialog {
    data object None : RigDialog
    data object Freq : RigDialog
    data object Mode : RigDialog
    data object SavePreset : RigDialog
}

@Composable
fun RigScreen(state: RigUiState, running: Boolean, vm: ModemViewModel, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<RigDialog>(RigDialog.None) }
    val live = running && state.connected

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionTitle("RIG CONTROL")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.backendText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    text = if (state.connected) "OK" else "--",
                    color = if (state.connected) Modem73Colors.ok else Modem73Colors.tx,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                if (state.pttOn) {
                    Spacer(Modifier.width(10.dp))
                    Text("TX", color = Modem73Colors.tx, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (state.note != null) {
                Text(
                    text = state.note,
                    color = if (state.note.startsWith("(!)")) Modem73Colors.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        SectionTitle("FREQUENCY")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.freqText,
                    color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = live) { dialog = RigDialog.Freq }
                )
                BigStep("−", live) { vm.stepRigFreq(-1) }
                Spacer(Modifier.width(6.dp))
                BigStep("+", live) { vm.stepRigFreq(1) }
            }
            Text("Tap the frequency to type one, e.g. 7074 (kHz) or 14.074 (MHz)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(RigOptions.stepLabels, state.stepIndex) { vm.setRigStep(it) }
            SelectorRow("Mode", state.mode.ifEmpty { "---" }, enabled = live) { dialog = RigDialog.Mode }
            StepperRow("RF Power", if (state.powerPct >= 0) "${state.powerPct}%" else "---", enabled = live) { vm.stepRigPower(it) }
            StepperRow("TX Drive", if (state.alcTuning) "TUNING..." else "${state.drivePct}%", hint = "Soundcard output level", enabled = running && !state.alcTuning) { vm.stepTxDrive(it) }
            ActionButton("ALC TUNE", live && !state.alcTuning, Modem73Colors.info) { vm.alcTune() }
        }

        SectionTitle("PRESETS", trailing = "${state.presets.size}/12")
        Panel {
            if (state.presets.isEmpty()) {
                Text("(none)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
            }
            state.presets.forEachIndexed { i, p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = live) { vm.goRigPreset(i) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(p.label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        RigOptions.formatFreq(p.hz),
                        color = if (p.hz == state.freqHz) Modem73Colors.ok else MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "×",
                        color = Modem73Colors.tx,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { vm.deleteRigPreset(i) },
                        textAlign = TextAlign.Center
                    )
                }
            }
            ActionButton("SAVE CURRENT AS PRESET", live && state.freqHz > 0 && state.presets.size < 12, MaterialTheme.colorScheme.primary) { dialog = RigDialog.SavePreset }
        }

        SectionTitle("ANTENNA TUNER")
        Panel {
            if (state.tunerSupported == 0) {
                Text("(not supported by rig)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                ToggleRow("Tuner", state.tunerOn == 1, enabled = live && state.tunerOn >= 0, hint = if (state.tunerOn < 0) "---" else null) { vm.toggleRigTuner() }
                ActionButton("START TUNE", live, Modem73Colors.warn) { vm.startRigTune() }
            }
        }

        SectionTitle("METERS", trailing = state.ageText)
        Panel {
            if (!state.connected || state.ageText == null) {
                Text("No data from rig yet...", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
            state.meters.forEach { m ->
                val color = when (m.level) {
                    MeterLevel.OK -> Modem73Colors.ok
                    MeterLevel.WARN -> Modem73Colors.warn
                    MeterLevel.BAD -> Modem73Colors.tx
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                    Text(m.label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                    MeterBar(m.fraction, color, Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        m.text,
                        color = if (m.fraction == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
            Text("SWR / Power / ALC valid during TX", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (state.swrWarn > 0f) {
                Text(
                    String.format("(!) HIGH SWR %.1f during last TX", state.swrWarn),
                    color = Modem73Colors.tx,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        RigDialog.Freq -> TextInputDialog(
            title = "Frequency (kHz or MHz)",
            initial = if (state.freqHz > 0) String.format("%.6f", state.freqHz / 1e6).trimEnd('0').trimEnd('.') else "",
            onConfirm = { dialog = RigDialog.None; vm.setRigFreqText(it) },
            onDismiss = { dialog = RigDialog.None },
            numeric = true
        )
        RigDialog.Mode -> PickerDialog(
            title = "Mode",
            options = RigOptions.modes,
            selected = RigOptions.modes.indexOf(state.mode),
            onPick = { dialog = RigDialog.None; vm.setRigMode(RigOptions.modes[it]) },
            onDismiss = { dialog = RigDialog.None }
        )
        RigDialog.SavePreset -> TextInputDialog(
            title = "Preset label for ${state.freqText}",
            initial = "",
            onConfirm = { dialog = RigDialog.None; vm.saveRigPreset(it) },
            onDismiss = { dialog = RigDialog.None },
            uppercase = true
        )
        RigDialog.None -> {}
    }
}

@Composable
private fun BigStep(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun MeterBar(fraction: Float?, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(12.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (fraction != null && fraction > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(color)
            )
        }
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        elevation = null,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Modem73Colors.onAccent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(46.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
