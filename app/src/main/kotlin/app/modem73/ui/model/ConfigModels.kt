package app.modem73.ui.model

enum class ModemType(val label: String) { OFDM("OFDM"), MFSK("MFSK"), ROBUST("ROBUST") }

enum class CsmaMode(val label: String, val hint: String) {
    THRESHOLD("THRESHOLD", "busy = any audio over Threshold"),
    SYNC("SYNC", "busy = a real modem signal only"),
    RANKED("RANKED", "SYNC plus stations take turns")
}

enum class PttType(val label: String, val description: String, val available: Boolean) {
    NONE("NONE", "PTT disabled (over the air)", true),
    VOX("VOX", "Tone-keyed VOX", true),
    SERIAL("SERIAL", "USB serial DTR/RTS (AIOC, FTDI, CP210x, CH34x)", true),
    HAMLIB("HAMLIB", "Hamlib CAT PTT via USB serial or network rig", true),
    RIGCTL("RIGCTL", "Hamlib rigctld (network)", false),
    CM108("CM108", "USB HID GPIO", false)
}

data class ModemInfo(val payloadBytes: Int, val bitrateText: String, val frameSeconds: Float, val txTimeText: String, val bandText: String, val netText: String? = null)

data class ConfigUiState(
    val callsign: String,
    val modemType: ModemType,
    val modulation: String,
    val codeRate: String,
    val frameSize: String,
    val postamble: Boolean,
    val mfskMode: String,
    val robustMode: String,
    val robustFrame: String,
    val modemInfo: ModemInfo,
    val csmaEnabled: Boolean,
    val csmaMode: CsmaMode,
    val thresholdDb: Float,
    val liveLevelDb: Float,
    val band: String,
    val preset: String,
    val advancedOpen: Boolean,
    val quietText: String,
    val windowText: String,
    val leadTone: Boolean,
    val replyOffsetText: String,
    val burstText: String,
    val fastFloor: Boolean,
    val presenceIntervalS: Int,
    val fragmentation: Boolean,
    val txBlanking: Boolean,
    val rxOfdm: Boolean,
    val rxRobust: Boolean,
    val rxMfsk: Boolean,
    val audioInput: String,
    val audioOutput: String,
    val audioOk: Boolean,
    val pttFail: Boolean,
    val txLevelPct: Int,
    val pttType: PttType,
    val voxToneHz: Int,
    val voxLeadMs: Int,
    val voxTailMs: Int,
    val serialDevice: String,
    val hamlibRig: String,
    val hamlibDevice: String,
    val hamlibBaud: Int,
    val serialLine: String,
    val serialInvert: String,
    val txDelayMs: Int,
    val kissPort: Int,
    val controlPort: Int,
    val presetName: String?
)

object ConfigOptions {
    val modulations = listOf("BPSK", "QPSK", "8PSK", "QAM16", "QAM64", "QAM256", "QAM1024", "QAM4096")
    val codeRates = listOf("1/2", "2/3", "3/4", "5/6", "1/4", "1/2x2", "1/4x2")
    val frameSizes = listOf("SHORT", "NORMAL", "LONG", "MICRO")
    val mfskModes = listOf("MFSK-8", "MFSK-16", "MFSK-32", "MFSK-32R")
    val robustModes = listOf("RDM-1200", "RDM-800", "RDM-600", "RDM-300", "RDMN-300", "RDMN-150", "RDM-QB")
    val robustFrames = listOf("510 B", "170 B (short)", "30 B (micro)")
    val bands = listOf("HF", "VHF/UHF")
    val csmaPresets = listOf("BENCH", "RELAXED", "MODERATE", "BUSY", "CUSTOM")
    val serialLines = listOf("DTR", "RTS", "BOTH")
    val hamlibBauds = listOf(4800, 9600, 19200, 38400, 57600, 115200)
    val serialInverts = listOf("NORMAL", "INV DTR", "INV RTS", "INV BOTH")
}
