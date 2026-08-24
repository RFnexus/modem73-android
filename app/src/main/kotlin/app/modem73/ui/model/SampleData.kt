package app.modem73.ui.model

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

object SampleData {
    fun waterfallRows(rows: Int = 56, bins: Int = 120, minHz: Int = 0, maxHz: Int = 3000): List<FloatArray> {
        val rnd = Random(73)
        val binHz = (maxHz - minHz).toFloat() / bins
        val out = ArrayList<FloatArray>(rows)
        for (r in 0 until rows) {
            val row = FloatArray(bins)
            val inFrame = r in 8..30
            val inTone = r in 31..36
            for (b in 0 until bins) {
                val hz = minHz + (b + 0.5f) * binHz
                var v = 0.04f + rnd.nextFloat() * 0.13f
                if (inFrame && hz > 300f && hz < 2700f) {
                    val edge = minOf(hz - 300f, 2700f - hz) / 200f
                    v = 0.42f + rnd.nextFloat() * 0.33f * edge.coerceIn(0.4f, 1f)
                }
                if (inTone && abs(hz - 1500f) < 60f) {
                    v = 0.95f
                }
                if (r in 44..46 && hz > 1200f && hz < 1400f) {
                    v = 0.5f + rnd.nextFloat() * 0.2f
                }
                row[b] = v
            }
            out.add(row)
        }
        return out
    }

    val status = StatusUiState(
        callsign = "N0CALL",
        modeSummary = "OFDM QPSK 1/2 NORMAL",
        clock = "19:42:07",
        transmitting = false,
        pttOn = false,
        pttFail = false,
        swrAlarm = null,
        carrierDb = -47.3f,
        thresholdDb = -30f,
        lastSnrDb = 14.2f,
        snrHistory = List(32) { i -> (12f + 6f * sin(i / 3.5f) + (i % 5) * 0.6f).coerceIn(0f, 30f) },
        berPct = 1.8f,
        robustModem = false,
        heard = HeardEntry("QPSK 1/2 N", "K7ABC", 14f, "42s"),
        csmaEnabled = true,
        csmaModeLabel = "RANKED",
        csmaBusy = false,
        csmaSync = true,
        occupancyPct = 23,
        occHistory = List(60) { i -> (0.15f + 0.35f * abs(sin(i / 7f))).coerceIn(0f, 1f) },
        quietText = "auto ~683 ms",
        windowText = "6 slots (4200 ms)",
        slotMs = 500,
        csmaPhaseText = "idle",
        csmaPhaseKind = CsmaPhaseKind.IDLE,
        txQueued = 0,
        txQueueEtaS = 0,
        rigText = null,
        activity = List(60) { i ->
            val base = -62f + 5f * sin(i / 4f)
            val frame = i in 22..38
            ActivitySample(if (frame) -28f + 3f * sin(i.toFloat()) else base, dcd = frame && i > 25, tone = i in 22..25)
        },
        rxFrames = 128,
        txFrames = 41,
        rxErrors = 3,
        syncCount = 131,
        clients = 1,
        queue = 0,
        packets = listOf(
            PacketEntry("12s", false, 512, "QPSK 1/2 N", 14.2f, 1.8f, "K7ABC"),
            PacketEntry("48s", true, 512, "QPSK 1/2 N", null, null, null),
            PacketEntry(" 1m", false, 172, "RDM-1200S", 6.1f, 4.2f, "W1XYZ"),
            PacketEntry(" 3m", false, 512, "8PSK 1/2 N", 18.9f, 0.4f, "K7ABC"),
            PacketEntry(" 5m", true, 1024, "QPSK 1/2 N", null, null, null),
            PacketEntry(" 9m", false, 29, "MFSK-16", -2.5f, 0.0f, "VE3QRP")
        )
    )

    val config = ConfigUiState(
        callsign = "N0CALL",
        modemType = ModemType.OFDM,
        modulation = "QPSK",
        codeRate = "1/2",
        frameSize = "NORMAL",
        postamble = false,
        mfskMode = "MFSK-16",
        robustMode = "RDM-1200",
        robustFrame = "510 B",
        modemInfo = ModemInfo(512, "1.5 kb/s", 2.73f, "3s", "HF  VHF/UHF"),
        csmaEnabled = true,
        csmaMode = CsmaMode.SYNC,
        thresholdDb = -30f,
        liveLevelDb = -47.3f,
        band = "HF",
        preset = "MODERATE",
        advancedOpen = false,
        quietText = "AUTO",
        windowText = "12 x 500ms",
        leadTone = true,
        replyOffsetText = "800 ms",
        burstText = "2 pkts",
        fastFloor = true,
        presenceIntervalS = 45,
        fragmentation = false,
        txBlanking = true,
        rxOfdm = true,
        rxRobust = true,
        rxMfsk = true,
        audioInput = "USB Audio Device",
        audioOutput = "USB Audio Device",
        audioOk = true,
        pttFail = false,
        txLevelPct = 60,
        pttType = PttType.SERIAL,
        voxToneHz = 1200,
        voxLeadMs = 150,
        voxTailMs = 100,
        serialDevice = "AIOC 1209:7388",
        hamlibRig = "none",
        hamlibDevice = "none",
        hamlibBaud = 0,
        serialLine = "BOTH",
        serialInvert = "INV RTS",
        txDelayMs = 500,
        kissPort = 8001,
        controlPort = 8073,
        presetName = null
    )

    val utils = UtilsUiState(
        chatLines = listOf(
            ChatLine(ChatDirection.SENT, "N0CALL", "hello from the pixel", "19:39:14"),
            ChatLine(ChatDirection.RECEIVED, "K7ABC", "copy 59 on 40m nvis, rdm-600", "19:39:51", 14.2f),
            ChatLine(ChatDirection.SENT, "N0CALL", "switching to qam16 3/4", "19:40:33"),
            ChatLine(ChatDirection.RECEIVED, "K7ABC", "still solid copy", "19:41:20", 17.6f)
        ),
        draft = "",
        randomSizes = listOf(64, 128, 256, 512, 1024),
        selectedRandomSize = 512,
        maxPayloadBytes = 512,
        logLines = emptyList()
    )
}
