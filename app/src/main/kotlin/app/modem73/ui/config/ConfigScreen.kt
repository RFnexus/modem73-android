package app.modem73.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.app.Activity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import app.modem73.service.ModemService
import app.modem73.core.CsmaPresets
import app.modem73.core.TncConfig
import app.modem73.ui.ModemViewModel
import app.modem73.ui.components.ChoiceChips
import app.modem73.ui.components.KeyValueRow
import app.modem73.ui.components.Panel
import app.modem73.ui.components.PickerDialog
import app.modem73.ui.components.SectionTitle
import app.modem73.ui.components.SelectorRow
import app.modem73.ui.components.StepperRow
import app.modem73.ui.components.TextInputDialog
import app.modem73.ui.components.ToggleRow
import app.modem73.ui.model.ConfigOptions
import app.modem73.ui.model.ConfigUiState
import app.modem73.ui.model.CsmaMode
import app.modem73.ui.model.ModemType
import app.modem73.ui.model.PttType
import app.modem73.ui.status.LevelMeter
import app.modem73.ui.theme.Modem73Colors

private sealed class Dialog {
    data object None : Dialog()
    data object Callsign : Dialog()
    data object Modulation : Dialog()
    data object CodeRate : Dialog()
    data object FrameSize : Dialog()
    data object MfskMode : Dialog()
    data object RobustMode : Dialog()
    data object RobustFrame : Dialog()
    data object Band : Dialog()
    data object Preset : Dialog()
    data object SerialDevice : Dialog()
    data object HamlibRig : Dialog()
    data object HamlibDevice : Dialog()
    data object HamlibNet : Dialog()
    data object KissPort : Dialog()
    data object ControlPort : Dialog()
}

private fun deviceIp(): String? = runCatching {
    java.net.NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is java.net.Inet4Address && !it.isLinkLocalAddress }
        ?.hostAddress
}.getOrNull()

@Composable
private fun AppControlRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<String?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton("Quit", Icons.Default.Close, Modem73Colors.tx, Modifier.weight(1f)) { confirm = "quit" }
        ActionButton("Restart", Icons.Default.Refresh, Modem73Colors.info, Modifier.weight(1f)) { confirm = "restart" }
    }
    confirm?.let { which ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text(if (which == "quit") "Quit modem73?" else "Restart modem73?", fontWeight = FontWeight.Bold) },
            text = { Text(if (which == "quit") "Stops the modem and closes the app." else "Stops and starts the modem engine.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (which == "quit") {
                        ModemService.stop(context)
                        scope.launch {
                            kotlinx.coroutines.withTimeoutOrNull(2000) {
                                app.modem73.core.ModemController.running.first { !it }
                            }
                            (context as? Activity)?.finishAndRemoveTask()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        }
                    } else {
                        ModemService.restart(context)
                    }
                }) {
                    Text(if (which == "quit") "Quit" else "Restart", color = if (which == "quit") Modem73Colors.tx else Modem73Colors.info, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .background(color)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Modem73Colors.onAccent, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Modem73Colors.onAccent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ConfigScreen(state: ConfigUiState, vm: ModemViewModel, modifier: Modifier = Modifier) {
    var dialog by remember { mutableStateOf<Dialog>(Dialog.None) }
    val cfg = vm.config.value

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Quit and Restart are at the bottom",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        ModemSection(state, vm) { dialog = it }
        CsmaSection(state, vm) { dialog = it }
        SectionTitle("FRAGMENTATION")
        Panel {
            ToggleRow("Enabled", state.fragmentation) { v -> vm.update { it.copy(fragmentationEnabled = v) } }
        }
        if (!state.csmaEnabled) {
            SectionTitle("TX BLANKING")
            Panel {
                ToggleRow("Enabled", state.txBlanking) { v -> vm.update { it.copy(txBlankingEnabled = v) } }
            }
        }
        SectionTitle("RX DECODERS")
        Panel {
            ToggleRow("OFDM", state.rxOfdm, hint = if (state.modemType == ModemType.OFDM) "still on: current TX mode" else null) { v -> vm.update { it.copy(ofdmRxEnabled = v) } }
            ToggleRow("ROBUST", state.rxRobust, hint = if (state.modemType == ModemType.ROBUST) "still on: current TX mode" else null) { v -> vm.update { it.copy(robustRxEnabled = v) } }
            ToggleRow("MFSK", state.rxMfsk, hint = if (state.modemType == ModemType.MFSK) "still on: current TX mode" else null) { v -> vm.update { it.copy(mfskRxEnabled = v) } }
        }
        AudioPttSection(state, vm) { dialog = it }
        SectionTitle("NETWORK", trailing = "(restart)")
        Panel {
            SelectorRow("KISS Port", "${state.kissPort}") { dialog = Dialog.KissPort }
            SelectorRow("Control Port", "${state.controlPort}") { dialog = Dialog.ControlPort }
            ToggleRow("LAN access", vm.config.value.lanMode) { v -> vm.update { it.copy(lanMode = v) } }
            if (vm.config.value.lanMode) {
                val ip = remember(vm.config.value.lanMode) { deviceIp() }
                KeyValueRow("Device IP", ip ?: "no network", valueColor = Modem73Colors.info)
            }
        }
        AppControlRow()
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        Dialog.None -> {}
        Dialog.Callsign -> TextInputDialog("Callsign", cfg.callsign, onConfirm = { s ->
            val v = s.trim().uppercase().filter { it.isLetterOrDigit() || it == '/' }.take(9)
            if (v.isNotEmpty()) vm.update { it.copy(callsign = v) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None }, uppercase = true)
        Dialog.Modulation -> PickerDialog("Modulation", ConfigOptions.modulations, ConfigOptions.modulations.indexOf(cfg.modulation), onPick = { i ->
            vm.update { it.copy(modulation = ConfigOptions.modulations[i], frameSize = if (it.frameSize == 3 && (ConfigOptions.modulations[i] != "QPSK" || it.codeRate != "1/2")) 1 else it.frameSize) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.CodeRate -> PickerDialog("Code Rate", ConfigOptions.codeRates, ConfigOptions.codeRates.indexOf(cfg.codeRate), onPick = { i ->
            vm.update { it.copy(codeRate = ConfigOptions.codeRates[i], frameSize = if (it.frameSize == 3 && (it.modulation != "QPSK" || ConfigOptions.codeRates[i] != "1/2")) 1 else it.frameSize) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.FrameSize -> {
            val micro = cfg.modulation == "QPSK" && cfg.codeRate == "1/2"
            val opts = if (micro) ConfigOptions.frameSizes else ConfigOptions.frameSizes.take(3)
            PickerDialog("Frame Size", opts, cfg.frameSize.coerceIn(0, opts.size - 1), onPick = { i ->
                vm.update { it.copy(frameSize = i) }
                dialog = Dialog.None
            }, onDismiss = { dialog = Dialog.None })
        }
        Dialog.MfskMode -> PickerDialog("MFSK Mode", ConfigOptions.mfskModes, cfg.mfskMode, onPick = { i ->
            vm.update { it.copy(mfskMode = i) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.RobustMode -> PickerDialog("RDM Mode", ConfigOptions.robustModes, ModemViewModel.robustBaseIndex(cfg.robustMode), onPick = { i ->
            vm.update { it.copy(robustMode = ModemViewModel.robustModeOf(i, ModemViewModel.robustMtuIndex(it.robustMode) == 1)) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.RobustFrame -> PickerDialog("Frame", ConfigOptions.robustFrames.take(2), ModemViewModel.robustMtuIndex(cfg.robustMode).coerceAtMost(1), onPick = { i ->
            vm.update { it.copy(robustMode = ModemViewModel.robustModeOf(ModemViewModel.robustBaseIndex(it.robustMode), i == 1)) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.Band -> PickerDialog("Band", ConfigOptions.bands, cfg.csmaBand, onPick = { i ->
            vm.update { c -> CsmaPresets.apply(c.copy(csmaBand = i), CsmaPresets.current(c).takeIf { it != "CUSTOM" } ?: "MODERATE") }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.Preset -> PickerDialog("Preset", CsmaPresets.names.take(4), CsmaPresets.names.indexOf(CsmaPresets.current(cfg)).coerceAtLeast(0), onPick = { i ->
            vm.setPreset(CsmaPresets.names[i])
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None }, subtitles = CsmaPresets.forBand(cfg.csmaBand).map { "window ${it.cw} x ${it.slotMs} ms, burst ${it.burst}, dither ${it.ditherMs} ms" })
        Dialog.HamlibRig -> {
            val rigs = vm.rigModels()
            PickerDialog("Rig model", rigs.map { "${it.mfg} ${it.model}" }, rigs.indexOfFirst { it.id == cfg.hamlibModel },
                onPick = { i -> vm.update { it.copy(hamlibModel = rigs[i].id) }; dialog = Dialog.None },
                onDismiss = { dialog = Dialog.None },
                subtitles = rigs.map { "#${it.id}" },
                searchable = true)
        }
        Dialog.HamlibDevice -> {
            val devs = vm.serialDevices()
            val labels = devs.map { it.label } + "Network rig (host:port)" + "Rescan"
            PickerDialog("Rig connection", labels, devs.indexOfFirst { it.key == cfg.hamlibDevice }, onPick = { i ->
                when (i) {
                    devs.size -> dialog = Dialog.HamlibNet
                    devs.size + 1 -> vm.refreshSerialDevices()
                    else -> {
                        val d = devs[i]
                        vm.update { it.copy(hamlibDevice = d.key) }
                        if (!d.hasPermission) vm.requestSerialPermission(d.key)
                        dialog = Dialog.None
                    }
                }
            }, onDismiss = { dialog = Dialog.None })
        }
        Dialog.HamlibNet -> TextInputDialog("Network rig host:port", if (cfg.hamlibDevice.contains(':')) cfg.hamlibDevice else "", onConfirm = { s ->
            val v = s.trim()
            if (v.contains(':')) vm.update { it.copy(hamlibDevice = v) }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None })
        Dialog.SerialDevice -> {
            val devs = vm.serialDevices()
            val labels = devs.map { it.label } + "Rescan"
            PickerDialog("USB serial device", labels, devs.indexOfFirst { it.key == cfg.comPort }, onPick = { i ->
                if (i == devs.size) {
                    vm.refreshSerialDevices()
                } else {
                    val d = devs[i]
                    vm.update { it.copy(comPort = d.key) }
                    if (!d.hasPermission) vm.requestSerialPermission(d.key)
                    dialog = Dialog.None
                }
            }, onDismiss = { dialog = Dialog.None }, subtitles = devs.map { if (it.hasPermission) "permission granted" else "tap to request permission" } + "look for newly attached adapters")
        }
        Dialog.KissPort -> TextInputDialog("KISS Port", "${cfg.port}", onConfirm = { s ->
            s.toIntOrNull()?.let { p -> if (p in 1024..65535) vm.update { it.copy(port = p) } }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None }, numeric = true)
        Dialog.ControlPort -> TextInputDialog("Control Port", "${cfg.controlPort}", onConfirm = { s ->
            s.toIntOrNull()?.let { p -> if (p in 1024..65535) vm.update { it.copy(controlPort = p) } }
            dialog = Dialog.None
        }, onDismiss = { dialog = Dialog.None }, numeric = true)
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun ModemSection(state: ConfigUiState, vm: ModemViewModel, open: (Dialog) -> Unit) {
    SectionTitle("MODEM")
    Panel {
        SelectorRow("Callsign", state.callsign) { open(Dialog.Callsign) }
        FieldLabel("Modem")
        ChoiceChips(ModemType.entries.map { it.label }, state.modemType.ordinal) { i -> vm.update { it.copy(modemType = i) } }
        when (state.modemType) {
            ModemType.OFDM -> {
                SelectorRow("Modulation", state.modulation) { open(Dialog.Modulation) }
                SelectorRow("Code Rate", state.codeRate) { open(Dialog.CodeRate) }
                SelectorRow("Frame Size", state.frameSize) { open(Dialog.FrameSize) }
                ToggleRow("Postamble", state.postamble) { v -> vm.update { it.copy(postamble = v) } }
            }
            ModemType.MFSK -> SelectorRow("MFSK Mode", state.mfskMode) { open(Dialog.MfskMode) }
            ModemType.ROBUST -> {
                SelectorRow("RDM Mode", state.robustMode) { open(Dialog.RobustMode) }
                SelectorRow("Frame", state.robustFrame, enabled = state.robustMode != "RDM-QB") { open(Dialog.RobustFrame) }
            }
        }
    }
    SectionTitle("MODEM INFO")
    Panel {
        val info = state.modemInfo
        Row(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoStat("Payload", "${info.payloadBytes} B", Modifier.weight(1f))
            InfoStat("Rate", info.bitrateText, Modifier.weight(1.1f))
            InfoStat("Frame", String.format("%.2fs", info.frameSeconds), Modifier.weight(0.9f))
            InfoStat("TX", info.txTimeText, Modifier.weight(0.6f))
            if (info.netText != null) {
                InfoStat("Net", info.netText, Modifier.weight(1.2f))
            }
        }
    }
}

@Composable
private fun InfoStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CsmaSection(state: ConfigUiState, vm: ModemViewModel, open: (Dialog) -> Unit) {
    val mode = state.csmaMode
    SectionTitle("CSMA")
    Panel {
        ToggleRow("Enabled", state.csmaEnabled) { v -> vm.update { it.copy(csmaEnabled = v, txBlankingEnabled = if (v) true else it.txBlankingEnabled) } }
        FieldLabel("Mode")
        ChoiceChips(CsmaMode.entries.map { it.label }, mode.ordinal) { i -> vm.setCsmaMode(i) }
        Text(mode.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        if (mode == CsmaMode.THRESHOLD) {
            StepperRow("Threshold", String.format("%.0f dB", state.thresholdDb)) { d -> vm.update { it.copy(carrierThresholdDb = (it.carrierThresholdDb + 2f * d).coerceIn(-80f, 0f)) } }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Level", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(104.dp))
                LevelMeter(state.liveLevelDb, state.thresholdDb, modifier = Modifier.weight(1f))
            }
        }
        if (mode != CsmaMode.RANKED) {
            SelectorRow("Band", state.band) { open(Dialog.Band) }
            SelectorRow("Preset", state.preset) { open(Dialog.Preset) }
        }
        LinkRow(if (state.advancedOpen) "[-] Advanced" else "[+] Advanced") { vm.toggleAdvanced() }
        if (state.advancedOpen) {
            if (mode != CsmaMode.RANKED) {
                StepperRow("Quiet", state.quietText) { d -> vm.update { it.copy(csmaQuietMs = (it.csmaQuietMs + 250 * d).coerceIn(0, 10000)) } }
            }
            if (mode == CsmaMode.THRESHOLD) {
                StepperRow("Window", state.windowText) { d -> vm.update { it.copy(csmaCw = (it.csmaCw + d).coerceIn(2, 32)) } }
            }
            if (mode != CsmaMode.RANKED) {
                ToggleRow("Lead Tone", state.leadTone) { v -> vm.update { it.copy(txLeadTone = v) } }
                StepperRow("Reply Offset", state.replyOffsetText) { d -> vm.update { it.copy(csmaResponderDither = (it.csmaResponderDither + 100 * d).coerceIn(0, 3000)) } }
            }
            StepperRow("Burst", state.burstText) { d -> vm.update { it.copy(csmaBurst = (it.csmaBurst + d).coerceIn(1, 4)) } }
            if (mode == CsmaMode.SYNC) {
                ToggleRow("Fast Floor", state.fastFloor) { v -> vm.update { it.copy(csmaFastFloor = v) } }
            }
            if (mode == CsmaMode.RANKED) {
                StepperRow("Presence Ivl", "${state.presenceIntervalS} s") { d -> vm.update { it.copy(beaconIntervalS = (it.beaconIntervalS + 15 * d).coerceIn(45, 90)) } }
            }
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun AudioPttSection(state: ConfigUiState, vm: ModemViewModel, open: (Dialog) -> Unit) {
    SectionTitle("AUDIO / PTT", trailing = "(restart)")
    Panel {
        KeyValueRow("Audio", if (state.audioOk) "OK" else "DISCONNECTED", valueColor = if (state.audioOk) Modem73Colors.rx else Modem73Colors.tx)
        Text(
            text = "Audio goes through the USB OTG sound device when one is attached, otherwise the phone mic and speaker",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        if (state.pttType != PttType.RIGCTL) {
            StepperRow("TX Level", "${state.txLevelPct}%") { d -> vm.update { it.copy(txDrive = ((it.txDrive * 100f + 0.5f).toInt() + 5 * d).coerceIn(5, 100) / 100f) } }
        }
    }
    SectionTitle("PTT", color = if (state.pttFail) Modem73Colors.tx else null)
    if (state.pttFail) {
        Text(
            text = "Key failed: radio is not transmitting",
            color = Modem73Colors.tx,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    Panel {
        Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PttType.entries.forEach { type ->
                PttOption(type, selected = type == state.pttType) {
                    val idx = when (type) {
                        PttType.NONE -> 0
                        PttType.RIGCTL -> 1
                        PttType.VOX -> 2
                        PttType.SERIAL -> 3
                        PttType.CM108 -> 4
                        PttType.HAMLIB -> 5
                    }
                    vm.update { it.copy(pttType = idx) }
                    if (type == PttType.SERIAL) vm.refreshSerialDevices()
                }
            }
        }
        when (state.pttType) {
            PttType.VOX -> {
                StepperRow("VOX Tone", "${state.voxToneHz} Hz") { d -> vm.update { it.copy(voxToneFreq = (it.voxToneFreq + 100 * d).coerceIn(300, 2500)) } }
                StepperRow("VOX Lead", "${state.voxLeadMs} ms") { d -> vm.update { it.copy(voxLeadMs = (it.voxLeadMs + 50 * d).coerceIn(50, 2000)) } }
                StepperRow("VOX Tail", "${state.voxTailMs} ms") { d -> vm.update { it.copy(voxTailMs = (it.voxTailMs + 50 * d).coerceIn(50, 2000)) } }
            }
            PttType.HAMLIB -> {
                SelectorRow("Rig", state.hamlibRig) { open(Dialog.HamlibRig) }
                SelectorRow("Device", state.hamlibDevice) { vm.refreshSerialDevices(); open(Dialog.HamlibDevice) }
                FieldLabel("Baud")
                ChoiceChips(ConfigOptions.hamlibBauds.map { it.toString() }, ConfigOptions.hamlibBauds.indexOf(state.hamlibBaud)) { i -> vm.update { it.copy(hamlibBaud = ConfigOptions.hamlibBauds[i]) } }
                FieldLabel("Serial port on device")
                ChoiceChips(listOf("PORT 1", "PORT 2"), vm.config.value.hamlibPort.coerceIn(0, 1)) { i -> vm.update { it.copy(hamlibPort = i) } }
            }
            PttType.SERIAL -> {
                SelectorRow("Device", state.serialDevice) { vm.refreshSerialDevices(); open(Dialog.SerialDevice) }
                FieldLabel("PTT Line")
                ChoiceChips(ConfigOptions.serialLines, ConfigOptions.serialLines.indexOf(state.serialLine)) { i -> vm.update { it.copy(comPttLine = i) } }
                FieldLabel("Invert")
                ChoiceChips(ConfigOptions.serialInverts, ConfigOptions.serialInverts.indexOf(state.serialInvert)) { i ->
                    vm.update { it.copy(comInvertDtr = i == 1 || i == 3, comInvertRts = i == 2 || i == 3) }
                }
            }
            else -> {}
        }
        StepperRow("TX Delay", "${state.txDelayMs} ms") { d -> vm.update { it.copy(txDelayMs = (it.txDelayMs + 50 * d).coerceIn(250, 2500)) } }
    }
}

@Composable
private fun PttOption(type: PttType, selected: Boolean, onClick: () -> Unit) {
    val fg = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        type.available -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
            .clickable(enabled = type.available) { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            type.label,
            color = fg,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(88.dp)
        )
        Text(
            type.description,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else fg,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (!type.available) {
            Text("LATER", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
    }
}
