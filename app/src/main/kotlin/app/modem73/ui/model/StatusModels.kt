package app.modem73.ui.model

data class PacketEntry(
    val age: String,
    val tx: Boolean,
    val bytes: Int,
    val mode: String,
    val snrDb: Float?,
    val berPct: Float?,
    val callsign: String?
)

data class HeardEntry(val mode: String, val callsign: String, val snrDb: Float, val age: String)

data class ActivitySample(val levelDb: Float, val dcd: Boolean, val tone: Boolean)

data class StatusUiState(
    val callsign: String,
    val modeSummary: String,
    val clock: String,
    val transmitting: Boolean,
    val pttOn: Boolean,
    val pttFail: Boolean,
    val swrAlarm: Float?,
    val carrierDb: Float,
    val thresholdDb: Float,
    val lastSnrDb: Float,
    val snrHistory: List<Float>,
    val berPct: Float,
    val robustModem: Boolean,
    val heard: HeardEntry?,
    val csmaEnabled: Boolean,
    val csmaModeLabel: String,
    val csmaBusy: Boolean,
    val csmaSync: Boolean,
    val occupancyPct: Int,
    val occHistory: List<Float>,
    val quietText: String,
    val windowText: String,
    val slotMs: Int,
    val csmaPhaseText: String,
    val csmaPhaseKind: CsmaPhaseKind,
    val txQueued: Int,
    val txQueueEtaS: Int,
    val rigText: String?,
    val activity: List<ActivitySample>,
    val rxFrames: Int,
    val txFrames: Int,
    val rxErrors: Int,
    val syncCount: Int,
    val clients: Int,
    val queue: Int,
    val packets: List<PacketEntry>
)

enum class CsmaPhaseKind { IDLE, SENDING, DEFERRING, QUIET, CONTENDING }
