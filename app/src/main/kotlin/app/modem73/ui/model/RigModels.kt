package app.modem73.ui.model

import app.modem73.core.FreqPreset

enum class MeterLevel { OK, WARN, BAD }

data class RigMeter(val label: String, val fraction: Float?, val text: String, val level: MeterLevel)

data class RigUiState(
    val backendText: String,
    val connected: Boolean,
    val freqHz: Long,
    val freqText: String,
    val stepIndex: Int,
    val presets: List<FreqPreset>,
    val mode: String,
    val powerPct: Int,
    val drivePct: Int,
    val alcTuning: Boolean,
    val tunerSupported: Int,
    val tunerOn: Int,
    val meters: List<RigMeter>,
    val ageText: String?,
    val pttOn: Boolean,
    val swrWarn: Float,
    val note: String?
)

object RigOptions {
    val modes = listOf("USB", "LSB", "CW", "CWR", "RTTY", "AM", "FM", "PKTUSB", "PKTLSB")
    val stepsHz = listOf(10L, 100L, 1_000L, 5_000L, 10_000L, 100_000L, 1_000_000L)
    val stepLabels = listOf("10", "100", "1k", "5k", "10k", "100k", "1M")
    val stepNames = listOf("10 Hz", "100 Hz", "1 kHz", "5 kHz", "10 kHz", "100 kHz", "1 MHz")
    const val SWR_WARN = 2.5f

    fun formatFreq(hz: Long): String {
        if (hz <= 0) return "---"
        return if (hz >= 1_000_000) String.format("%d.%03d.%03d", hz / 1_000_000, (hz / 1000) % 1000, hz % 1000)
        else String.format("%d.%03d", hz / 1000, hz % 1000)
    }

    fun parseFreq(text: String): Long? {
        val t = text.trim().replace(",", ".")
        if (t.isEmpty()) return null
        return if (t.contains('.')) t.toDoubleOrNull()?.let { Math.round(it * 1e6) }
        else t.toLongOrNull()?.let { it * 1000 }
    }

    fun meters(values: List<Float?>): List<RigMeter> {
        fun frac(v: Float?, min: Float, max: Float): Float? = v?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) }
        val s = values.getOrNull(0)
        val swr = values.getOrNull(1)
        val pwr = values.getOrNull(2)
        val alc = values.getOrNull(3)
        val temp = values.getOrNull(4)
        val out = mutableListOf(
            RigMeter("S-Meter", frac(s, -54f, 60f), when {
                s == null -> "---"
                s <= 0f -> String.format("S%.0f", maxOf(0f, 9f + s / 6f))
                else -> String.format("S9+%.0f", s)
            }, MeterLevel.OK),
            RigMeter("SWR", frac(swr, 1f, 5f), swr?.let { String.format("%.1f", it) } ?: "---", when {
                swr == null || swr < 1.5f -> MeterLevel.OK
                swr < SWR_WARN -> MeterLevel.WARN
                else -> MeterLevel.BAD
            }),
            RigMeter("Power", frac(pwr, 0f, 100f), pwr?.let { String.format("%.0fW", it) } ?: "---", MeterLevel.OK),
            RigMeter("ALC", frac(alc, 0f, 1f), alc?.let { String.format("%.2f", it) } ?: "---", MeterLevel.OK)
        )
        if (temp != null) {
            out += RigMeter("Temp", frac(temp, 0f, 100f), String.format("%.0fC", temp), when {
                temp < 60f -> MeterLevel.OK
                temp < 80f -> MeterLevel.WARN
                else -> MeterLevel.BAD
            })
        }
        return out
    }
}
