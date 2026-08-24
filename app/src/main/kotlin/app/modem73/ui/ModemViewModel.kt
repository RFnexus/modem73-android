package app.modem73.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.modem73.core.CsmaPresets
import app.modem73.core.EngineSnapshot
import app.modem73.core.EngineStatus
import app.modem73.core.FreqPreset
import app.modem73.core.ModemController
import app.modem73.core.TncConfig
import app.modem73.ui.model.ActivitySample
import app.modem73.ui.model.ChatDirection
import app.modem73.ui.model.ChatLine
import app.modem73.ui.model.ConfigOptions
import app.modem73.ui.model.ConfigUiState
import app.modem73.ui.model.CsmaMode
import app.modem73.ui.model.CsmaPhaseKind
import app.modem73.ui.model.HeardEntry
import app.modem73.ui.model.ModemInfo
import app.modem73.ui.model.ModemType
import app.modem73.ui.model.PacketEntry
import app.modem73.ui.model.PttType
import app.modem73.ui.model.RigOptions
import app.modem73.ui.model.RigUiState
import app.modem73.ui.model.StatusUiState
import app.modem73.ui.model.UtilsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ModemViewModel : ViewModel() {
    private val c = ModemController

    val running: StateFlow<Boolean> = c.running
    val error: StateFlow<String?> = c.error
    val waterfall: StateFlow<List<FloatArray>> = c.waterfall
    val config: StateFlow<TncConfig> = c.config

    val advancedOpen = MutableStateFlow(false)
    val chatDraft = MutableStateFlow("")
    val randomSize = MutableStateFlow(0)
    val rigStepIndex = MutableStateFlow(2)
    private val rigNote = MutableStateFlow<String?>(null)
    private var rigNoteJob: Job? = null

    val rigTab: StateFlow<Boolean> = c.config.map { it.pttType == 1 || it.pttType == 5 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.config.value.let { it.pttType == 1 || it.pttType == 5 })

    val rigUi: StateFlow<RigUiState> = combine(c.status, c.config, c.presets, rigStepIndex, rigNote) { st, cfg, presets, step, note ->
        mapRig(st, cfg, presets, step, note)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), mapRig(EngineStatus(), c.config.value, emptyList(), 2, null))

    val statusUi: StateFlow<StatusUiState> = combine(c.status, c.snapshot, c.occHistory, c.config, c.running) { st, sn, occ, cfg, run ->
        mapStatus(st, sn, occ, cfg, run)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), mapStatus(EngineStatus(), EngineSnapshot(), emptyList(), c.config.value, false))

    val configUi: StateFlow<ConfigUiState> = combine(c.config, c.status, c.serialDevices, advancedOpen) { cfg, st, devs, adv ->
        mapConfig(cfg, st, devs.firstOrNull { it.key == cfg.comPort }?.label ?: cfg.comPort.ifEmpty { if (devs.isEmpty()) "(no USB serial device)" else devs.first().label }, adv)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), mapConfig(c.config.value, EngineStatus(), "", false))


    val utilsUi: StateFlow<UtilsUiState> = combine(c.snapshot, chatDraft, randomSize, c.status) { sn, draft, size, st ->
        val mtu = if (st.mtuBytes > 0) st.mtuBytes else 512
        val sizes = listOf(32, 128, 256, 512, 1024).filter { it <= maxOf(mtu, 32) }.ifEmpty { listOf(32) }
        val sel = if (size in sizes) size else sizes.last()
        UtilsUiState(
            chatLines = sn.messages.map { m ->
                ChatLine(if (m.outgoing) ChatDirection.SENT else ChatDirection.RECEIVED, m.from, m.text, m.time, null)
            },
            draft = draft,
            randomSizes = sizes,
            selectedRandomSize = sel,
            maxPayloadBytes = mtu,
            logLines = sn.log.takeLast(40)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UtilsUiState(emptyList(), "", listOf(32, 128, 256, 512), 512, 512, emptyList()))

    fun update(transform: (TncConfig) -> TncConfig) = c.updateConfig(transform)

    fun setCsmaMode(mode: Int) = update { it.withCsmaMode(mode) }
    fun setPreset(name: String) = update { CsmaPresets.apply(it, name) }
    fun toggleAdvanced() { advancedOpen.value = !advancedOpen.value }
    fun setChatDraft(s: String) { chatDraft.value = s }
    fun sendChat() {
        val text = chatDraft.value.trim()
        if (text.isEmpty()) return
        c.sendChat(text)
        chatDraft.value = ""
    }
    fun setRandomSize(n: Int) { randomSize.value = n }
    fun sendRandom() = c.sendRandom(utilsUi.value.selectedRandomSize)
    fun sendTestPattern() = c.sendTestPattern(utilsUi.value.selectedRandomSize)
    fun sendPing() = c.sendPing()
    fun resetStats() = c.resetStats()
    fun refreshSerialDevices() = c.refreshSerialDevices()
    fun serialDevices() = c.serialDevices.value
    fun rigModels() = c.rigModels.value
    fun requestSerialPermission(key: String) = c.serialPtt.requestPermission(key)
    fun setRigPoll(on: Boolean) = c.setRigPoll(on)

    private fun note(msg: String) {
        rigNote.value = msg
        rigNoteJob?.cancel()
        rigNoteJob = viewModelScope.launch { delay(5000); rigNote.value = null }
    }

    private fun rig(cmd: String, okMsg: String, failMsg: String, onOk: () -> Unit = {}) {
        viewModelScope.launch {
            if (c.rigCommand(cmd)) { onOk(); note(okMsg) } else note("(!) $failMsg")
        }
    }

    fun setRigFreqHz(hz: Long) {
        if (hz <= 0) return
        rig("+F $hz", "Rig: freq set to " + RigOptions.formatFreq(hz), "Rig: set freq failed")
    }
    fun setRigFreqText(text: String) {
        val hz = RigOptions.parseFreq(text)
        if (hz == null || hz <= 0) note("(!) Rig: bad frequency '$text'") else setRigFreqHz(hz)
    }
    fun stepRigFreq(delta: Int) {
        val f = c.status.value.rigFreqHz
        if (f <= 0) { note("(!) Rig: no frequency data yet"); return }
        setRigFreqHz(maxOf(0L, f + delta * RigOptions.stepsHz[rigStepIndex.value]))
    }
    fun setRigStep(i: Int) { rigStepIndex.value = i.coerceIn(0, RigOptions.stepsHz.size - 1) }
    fun setRigMode(mode: String) = rig("+M $mode 0", "Rig: mode set to $mode", "Rig: set mode $mode failed")
    fun stepRigPower(delta: Int) {
        val p = c.status.value.rigPower
        if (p < 0f) { note("(!) Rig: no power data yet"); return }
        val np = (p + delta * 0.05f).coerceIn(0f, 1f)
        rig(String.format(Locale.US, "+L RFPOWER %.2f", np), "Rig: RF power ${(np * 100 + 0.5f).toInt()}%", "Rig: set power failed")
    }
    fun stepTxDrive(delta: Int) = update { it.copy(txDrive = (it.txDrive + delta * 0.05f).coerceIn(0.05f, 1f)) }
    fun alcTune() { note("ALC tune: starting..."); c.alcTune() }
    fun toggleRigTuner() {
        val nv = if (c.status.value.rigTuner == 1) 0 else 1
        rig("+U TUNER $nv", "Rig: tuner " + (if (nv == 1) "ON" else "OFF"), "Rig: tuner toggle failed")
    }
    fun startRigTune() = rig("+G TUNE", "Rig: tune cycle started", "Rig: tune failed (not supported?)")
    fun goRigPreset(i: Int) {
        val p = c.presets.value.getOrNull(i) ?: return
        setRigFreqHz(p.hz)
    }
    fun saveRigPreset(label: String) {
        val l = label.trim()
        val hz = c.status.value.rigFreqHz
        if (l.isEmpty()) { note("(!) Rig: preset not saved, label was empty"); return }
        if (hz <= 0) { note("(!) Rig: no frequency read from rig yet"); return }
        if (c.presets.value.size >= ModemController.MAX_FREQ_PRESETS) { note("(!) Rig: maximum frequency presets reached"); return }
        c.setPresets(c.presets.value + FreqPreset(hz, l.take(20)))
        note("Rig: preset saved, $l " + RigOptions.formatFreq(hz))
    }
    fun deleteRigPreset(i: Int) {
        val p = c.presets.value.getOrNull(i) ?: return
        c.setPresets(c.presets.value.filterIndexed { idx, _ -> idx != i })
        note("Rig: preset deleted, " + p.label)
    }

    private fun mapRig(st: EngineStatus, cfg: TncConfig, presets: List<FreqPreset>, step: Int, note: String?): RigUiState {
        val backend = if (cfg.pttType == 1) "rigctld ${cfg.rigctlHost}:${cfg.rigctlPort}"
        else "hamlib " + (c.rigModels.value.firstOrNull { it.id == cfg.hamlibModel }?.let { "${it.mfg} ${it.model}" } ?: "model ${cfg.hamlibModel}")
        return RigUiState(
            backendText = backend,
            connected = st.rigctlConnected,
            freqHz = st.rigFreqHz,
            freqText = RigOptions.formatFreq(st.rigFreqHz),
            stepIndex = step,
            presets = presets,
            mode = st.rigMode,
            powerPct = if (st.rigPower >= 0f) (st.rigPower * 100f + 0.5f).toInt() else -1,
            drivePct = (cfg.txDrive * 100f + 0.5f).toInt(),
            alcTuning = st.alcTuning,
            tunerSupported = st.rigTunerSupported,
            tunerOn = st.rigTuner,
            meters = RigOptions.meters(st.rigMeters),
            ageText = if (st.rigAgeS >= 0f) String.format("%.0fs ago", st.rigAgeS) else null,
            pttOn = st.pttOn,
            swrWarn = st.swrWarn,
            note = note
        )
    }

    private fun clock(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun mapStatus(st: EngineStatus, sn: EngineSnapshot, occ: List<Float>, cfg: TncConfig, running: Boolean): StatusUiState {
        val ranked = cfg.csmaMode == 2
        val quietText = when {
            ranked && st.csmaRank >= 0 -> "1000 ms (ranked)"
            cfg.csmaQuietMs > 0 -> "${cfg.csmaQuietMs} ms"
            else -> {
                var q = (st.airtimeS * 1000f).toInt() / 4
                if (q < 300) q = 300
                if (q > 3500) q = 3500
                "auto ~$q ms"
            }
        }
        val slot = maxOf(1, cfg.slotTimeMs)
        val windowText = when {
            ranked && st.csmaRank >= 0 && st.csmaRankN > 0 -> "${st.csmaRankN} turns (${st.csmaWindowMs} ms)"
            st.csmaWindowMs > 0 -> "${st.csmaWindowMs / slot} slots (${st.csmaWindowMs} ms)"
            else -> "${cfg.csmaCw} slots"
        }
        val phaseKind: CsmaPhaseKind
        val phaseText: String
        when {
            st.transmitting -> { phaseKind = CsmaPhaseKind.SENDING; phaseText = "sending" }
            st.csmaPhase == 1 -> { phaseKind = CsmaPhaseKind.DEFERRING; phaseText = "deferring (RX)" }
            st.csmaPhase == 2 -> { phaseKind = CsmaPhaseKind.QUIET; phaseText = "quiet ${st.csmaWaitMs}/${st.csmaWaitNeedMs} ms" }
            st.csmaPhase == 3 -> {
                phaseKind = CsmaPhaseKind.CONTENDING
                phaseText = when {
                    st.csmaRank >= st.csmaRankN && st.csmaRank >= 0 -> "yielding ${st.csmaWaitMs} ms"
                    st.csmaRank >= 0 -> "turn ${st.csmaRank + 1}/${st.csmaRankN} in ${st.csmaWaitMs} ms"
                    else -> "contending ${st.csmaWaitMs} ms"
                }
            }
            else -> { phaseKind = CsmaPhaseKind.IDLE; phaseText = if (running) "idle" else "stopped" }
        }
        val eta = if (st.txQueue > 0) (st.txQueue * (st.airtimeS + 1.5f)).toInt() else 0
        val activity = st.levelHistory.mapIndexed { i, db ->
            ActivitySample(db, st.levelDcd.getOrElse(i) { false }, st.levelTone.getOrElse(i) { false })
        }
        val modeSummary = when (cfg.modemType) {
            1 -> "MFSK " + ConfigOptions.mfskModes.getOrElse(cfg.mfskMode) { "" }
            2 -> "ROBUST " + ConfigOptions.robustModes.getOrElse(cfg.robustMode) { "" }
            else -> "OFDM ${cfg.modulation} ${cfg.codeRate} ${ConfigOptions.frameSizes.getOrElse(cfg.frameSize) { "" }}"
        }
        return StatusUiState(
            callsign = cfg.callsign,
            modeSummary = modeSummary,
            clock = clock(),
            transmitting = st.transmitting,
            pttOn = st.pttOn,
            pttFail = st.pttFail,
            swrAlarm = null,
            carrierDb = st.carrierDb,
            thresholdDb = cfg.carrierThresholdDb,
            lastSnrDb = st.lastSnr,
            snrHistory = st.snrHistory,
            berPct = st.lastBerPct,
            robustModem = cfg.modemType == 2,
            heard = st.heard?.let { HeardEntry(it.mode, it.callsign, it.snr, ageText(it.ageS)) },
            csmaEnabled = cfg.csmaEnabled,
            csmaModeLabel = if (cfg.csmaEnabled) CsmaMode.entries.getOrElse(cfg.csmaMode) { CsmaMode.THRESHOLD }.label else "OFF",
            csmaBusy = st.dcdActive,
            csmaSync = cfg.csmaSyncOnly,
            occupancyPct = (st.channelOccupancy * 100f).toInt(),
            occHistory = occ,
            quietText = quietText,
            windowText = windowText,
            slotMs = cfg.slotTimeMs,
            csmaPhaseText = phaseText,
            csmaPhaseKind = phaseKind,
            txQueued = st.txQueue,
            txQueueEtaS = eta,
            rigText = if ((cfg.pttType == 1 || cfg.pttType == 5) && st.rigctlConnected) (if (st.rigFreqHz > 0) RigOptions.formatFreq(st.rigFreqHz) + (if (st.rigMode.isNotEmpty()) " " + st.rigMode else "") else "connected") else null,
            activity = activity,
            rxFrames = st.rxFrames,
            txFrames = st.txFrames,
            rxErrors = st.rxErrors,
            syncCount = st.syncCount,
            clients = st.clients,
            queue = st.txQueue,
            packets = sn.packets.map { p ->
                PacketEntry(ageText(p.ageS), p.tx, p.bytes, p.mode, if (p.tx) null else p.snr, if (p.ber >= 0f) p.ber else null, p.callsign.ifEmpty { null })
            }
        )
    }

    private fun ageText(s: Long): String = when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> "${s / 3600}h"
    }

    private fun mapConfig(cfg: TncConfig, st: EngineStatus, serialLabel: String, adv: Boolean): ConfigUiState {
        val modemType = ModemType.entries.getOrElse(cfg.modemType) { ModemType.OFDM }
        val info = ModemInfo(
            payloadBytes = if (st.payloadBytes > 0) st.payloadBytes else st.mtuBytes,
            bitrateText = if (st.bitrateBps >= 1000) String.format("%.1f kb/s", st.bitrateBps / 1000f) else "${st.bitrateBps} b/s",
            frameSeconds = st.airtimeS,
            txTimeText = if (st.totalTxTime >= 60f) String.format("%.1fm", st.totalTxTime / 60f) else String.format("%.0fs", st.totalTxTime),
            bandText = if (cfg.csmaBand == 0) "HF" else "VHF/UHF",
            netText = if (cfg.csmaEnabled && st.netBpsEstimate > 0) "~${st.netBpsEstimate} b/s" else null
        )
        val pttType = when (cfg.pttType) {
            1 -> PttType.RIGCTL
            2 -> PttType.VOX
            3 -> PttType.SERIAL
            4 -> PttType.CM108
            5 -> PttType.HAMLIB
            else -> PttType.NONE
        }
        return ConfigUiState(
            callsign = cfg.callsign,
            modemType = modemType,
            modulation = cfg.modulation,
            codeRate = cfg.codeRate,
            frameSize = ConfigOptions.frameSizes.getOrElse(cfg.frameSize) { "NORMAL" },
            postamble = cfg.postamble,
            mfskMode = ConfigOptions.mfskModes.getOrElse(cfg.mfskMode) { "MFSK-16" },
            robustMode = ConfigOptions.robustModes.getOrElse(robustBaseIndex(cfg.robustMode)) { "RDM-1200" },
            robustFrame = ConfigOptions.robustFrames.getOrElse(robustMtuIndex(cfg.robustMode)) { "510 B" },
            modemInfo = info,
            csmaEnabled = cfg.csmaEnabled,
            csmaMode = CsmaMode.entries.getOrElse(cfg.csmaMode) { CsmaMode.THRESHOLD },
            thresholdDb = cfg.carrierThresholdDb,
            liveLevelDb = st.carrierDb,
            band = ConfigOptions.bands.getOrElse(cfg.csmaBand) { "HF" },
            preset = CsmaPresets.current(cfg),
            advancedOpen = adv,
            quietText = if (cfg.csmaQuietMs > 0) "${cfg.csmaQuietMs} ms" else "AUTO",
            windowText = "${cfg.csmaCw} x ${cfg.slotTimeMs}ms",
            leadTone = cfg.txLeadTone,
            replyOffsetText = if (cfg.csmaResponderDither > 0) "${cfg.csmaResponderDither} ms" else "OFF",
            burstText = if (cfg.csmaBurst > 1) "${cfg.csmaBurst} pkts" else "OFF",
            fastFloor = cfg.csmaFastFloor,
            presenceIntervalS = cfg.beaconIntervalS,
            fragmentation = cfg.fragmentationEnabled,
            txBlanking = cfg.txBlankingEnabled || cfg.csmaEnabled,
            rxOfdm = cfg.ofdmRxEnabled,
            rxRobust = cfg.robustRxEnabled,
            rxMfsk = cfg.mfskRxEnabled,
            audioInput = cfg.audioInputDevice,
            audioOutput = cfg.audioOutputDevice,
            audioOk = st.audioOk,
            pttFail = st.pttFail,
            txLevelPct = (cfg.txDrive * 100f + 0.5f).toInt(),
            pttType = pttType,
            voxToneHz = cfg.voxToneFreq,
            voxLeadMs = cfg.voxLeadMs,
            voxTailMs = cfg.voxTailMs,
            serialDevice = serialLabel,
            hamlibRig = c.rigModels.value.firstOrNull { it.id == cfg.hamlibModel }?.let { "${it.mfg} ${it.model}" } ?: (if (cfg.hamlibModel > 0) "model ${cfg.hamlibModel}" else "none"),
            hamlibDevice = when {
                cfg.hamlibDevice.isEmpty() -> "none"
                cfg.hamlibDevice.contains(':') -> cfg.hamlibDevice
                else -> c.serialDevices.value.firstOrNull { it.key == cfg.hamlibDevice }?.label ?: cfg.hamlibDevice
            },
            hamlibBaud = cfg.hamlibBaud,
            serialLine = ConfigOptions.serialLines.getOrElse(cfg.comPttLine) { "RTS" },
            serialInvert = when {
                cfg.comInvertDtr && cfg.comInvertRts -> "INV BOTH"
                cfg.comInvertRts -> "INV RTS"
                cfg.comInvertDtr -> "INV DTR"
                else -> "NORMAL"
            },
            txDelayMs = cfg.txDelayMs,
            kissPort = cfg.port,
            controlPort = cfg.controlPort,
            presetName = null
        )
    }

    companion object {
        val robustModeIndices = listOf(0, 10, 1, 2, 3, 4, 12)
        val robustShortIndices = listOf(5, 11, 6, 7, 8, 9, 12)

        fun robustBaseIndex(mode: Int): Int {
            robustModeIndices.indexOf(mode).let { if (it >= 0) return it }
            robustShortIndices.indexOf(mode).let { if (it >= 0) return it }
            return 0
        }

        fun robustMtuIndex(mode: Int): Int = when {
            mode == 12 -> 2
            mode in 5..9 || mode == 11 -> 1
            else -> 0
        }

        fun robustModeOf(baseIndex: Int, short: Boolean): Int {
            if (baseIndex == 6) return 12
            return if (short) robustShortIndices[baseIndex] else robustModeIndices[baseIndex]
        }
    }
}
