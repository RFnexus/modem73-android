package app.modem73.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TncConfig(
    val callsign: String = "N0CALL",
    val modemType: Int = 0,
    val mfskMode: Int = 1,
    val robustMode: Int = 0,
    val modulation: String = "QPSK",
    val codeRate: String = "1/2",
    val frameSize: Int = 1,
    val postamble: Boolean = false,
    val csmaEnabled: Boolean = true,
    val csmaSyncOnly: Boolean = false,
    val csmaFastFloor: Boolean = true,
    val csmaRanked: Boolean = false,
    val beaconIntervalS: Int = 45,
    val csmaBand: Int = 0,
    val carrierThresholdDb: Float = -30f,
    val pPersistence: Int = 128,
    val slotTimeMs: Int = 500,
    val csmaQuietMs: Int = 0,
    val csmaCw: Int = 8,
    val csmaResponderDither: Int = 250,
    val csmaBurst: Int = 2,
    val txLeadTone: Boolean = true,
    val txDrive: Float = 1.0f,
    val txBlankingEnabled: Boolean = true,
    val fragmentationEnabled: Boolean = false,
    val mfskRxEnabled: Boolean = false,
    val ofdmRxEnabled: Boolean = true,
    val robustRxEnabled: Boolean = true,
    val audioInputDevice: String = "default",
    val audioOutputDevice: String = "default",
    val pttType: Int = 0,
    val rigctlHost: String = "localhost",
    val rigctlPort: Int = 4532,
    val voxToneFreq: Int = 1200,
    val voxLeadMs: Int = 150,
    val voxTailMs: Int = 100,
    val comPort: String = "",
    val comPttLine: Int = 0,
    val comInvertDtr: Boolean = false,
    val comInvertRts: Boolean = false,
    val hamlibModel: Int = 0,
    val hamlibDevice: String = "",
    val hamlibBaud: Int = 0,
    val hamlibPort: Int = 0,
    val pttDelayMs: Int = 50,
    val pttTailMs: Int = 50,
    val txDelayMs: Int = 500,
    val port: Int = 8001,
    val controlPort: Int = 8073,
    val lanMode: Boolean = false
) {
    val csmaMode: Int get() = if (!csmaSyncOnly) 0 else if (csmaRanked) 2 else 1

    fun withCsmaMode(mode: Int): TncConfig = copy(csmaSyncOnly = mode >= 1, csmaRanked = mode == 2)

    fun toJson(): JSONObject {
        val j = JSONObject()
        j.put("callsign", callsign)
        j.put("modem_type", modemType)
        j.put("mfsk_mode", mfskMode)
        j.put("robust_mode", robustMode)
        j.put("modulation", modulation)
        j.put("code_rate", codeRate)
        j.put("frame_size", frameSize)
        j.put("postamble", postamble)
        j.put("csma_enabled", csmaEnabled)
        j.put("csma_sync_only", csmaSyncOnly)
        j.put("csma_fast_floor", csmaFastFloor)
        j.put("csma_ranked", csmaRanked)
        j.put("beacon_interval_s", beaconIntervalS)
        j.put("csma_band", csmaBand)
        j.put("carrier_threshold_db", carrierThresholdDb.toDouble())
        j.put("p_persistence", pPersistence)
        j.put("slot_time_ms", slotTimeMs)
        j.put("csma_quiet_ms", csmaQuietMs)
        j.put("csma_cw", csmaCw)
        j.put("csma_responder_dither", csmaResponderDither)
        j.put("csma_burst", csmaBurst)
        j.put("tx_lead_tone", txLeadTone)
        j.put("tx_drive", txDrive.toDouble())
        j.put("tx_blanking_enabled", txBlankingEnabled || csmaEnabled)
        j.put("fragmentation_enabled", fragmentationEnabled)
        j.put("mfsk_rx_enabled", mfskRxEnabled)
        j.put("ofdm_rx_enabled", ofdmRxEnabled)
        j.put("robust_rx_enabled", robustRxEnabled)
        j.put("audio_input_device", audioInputDevice)
        j.put("audio_output_device", audioOutputDevice)
        j.put("ptt_type", pttType)
        j.put("rigctl_host", rigctlHost)
        j.put("rigctl_port", rigctlPort)
        j.put("vox_tone_freq", voxToneFreq)
        j.put("vox_lead_ms", voxLeadMs)
        j.put("vox_tail_ms", voxTailMs)
        j.put("com_port", comPort)
        j.put("com_ptt_line", comPttLine)
        j.put("com_invert_dtr", comInvertDtr)
        j.put("com_invert_rts", comInvertRts)
        j.put("hamlib_model", hamlibModel)
        j.put("hamlib_device", hamlibDevice)
        j.put("hamlib_baud", hamlibBaud)
        j.put("hamlib_port", hamlibPort)
        j.put("ptt_delay_ms", pttDelayMs)
        j.put("ptt_tail_ms", pttTailMs)
        j.put("tx_delay_ms", txDelayMs)
        j.put("port", port)
        j.put("control_port", controlPort)
        j.put("bind_address", if (lanMode) "0.0.0.0" else "127.0.0.1")
        j.put("control_bind_address", if (lanMode) "0.0.0.0" else "127.0.0.1")
        j.put("perf_log", false)
        return j
    }

    companion object {
        fun fromJson(j: JSONObject): TncConfig {
            val d = TncConfig()
            return TncConfig(
                callsign = j.optString("callsign", d.callsign),
                modemType = j.optInt("modem_type", d.modemType),
                mfskMode = j.optInt("mfsk_mode", d.mfskMode),
                robustMode = j.optInt("robust_mode", d.robustMode),
                modulation = j.optString("modulation", d.modulation),
                codeRate = j.optString("code_rate", d.codeRate),
                frameSize = j.optInt("frame_size", d.frameSize),
                postamble = j.optBoolean("postamble", d.postamble),
                csmaEnabled = j.optBoolean("csma_enabled", d.csmaEnabled),
                csmaSyncOnly = j.optBoolean("csma_sync_only", d.csmaSyncOnly),
                csmaFastFloor = j.optBoolean("csma_fast_floor", d.csmaFastFloor),
                csmaRanked = j.optBoolean("csma_ranked", d.csmaRanked),
                beaconIntervalS = j.optInt("beacon_interval_s", d.beaconIntervalS),
                csmaBand = j.optInt("csma_band", d.csmaBand),
                carrierThresholdDb = j.optDouble("carrier_threshold_db", d.carrierThresholdDb.toDouble()).toFloat(),
                pPersistence = j.optInt("p_persistence", d.pPersistence),
                slotTimeMs = j.optInt("slot_time_ms", d.slotTimeMs),
                csmaQuietMs = j.optInt("csma_quiet_ms", d.csmaQuietMs),
                csmaCw = j.optInt("csma_cw", d.csmaCw),
                csmaResponderDither = j.optInt("csma_responder_dither", d.csmaResponderDither),
                csmaBurst = j.optInt("csma_burst", d.csmaBurst),
                txLeadTone = j.optBoolean("tx_lead_tone", d.txLeadTone),
                txDrive = j.optDouble("tx_drive", d.txDrive.toDouble()).toFloat(),
                txBlankingEnabled = j.optBoolean("tx_blanking_enabled", d.txBlankingEnabled),
                fragmentationEnabled = j.optBoolean("fragmentation_enabled", d.fragmentationEnabled),
                mfskRxEnabled = j.optBoolean("mfsk_rx_enabled", d.mfskRxEnabled),
                ofdmRxEnabled = j.optBoolean("ofdm_rx_enabled", d.ofdmRxEnabled),
                robustRxEnabled = j.optBoolean("robust_rx_enabled", d.robustRxEnabled),
                audioInputDevice = j.optString("audio_input_device", d.audioInputDevice),
                audioOutputDevice = j.optString("audio_output_device", d.audioOutputDevice),
                pttType = j.optInt("ptt_type", d.pttType),
                rigctlHost = j.optString("rigctl_host", d.rigctlHost),
                rigctlPort = j.optInt("rigctl_port", d.rigctlPort),
                voxToneFreq = j.optInt("vox_tone_freq", d.voxToneFreq),
                voxLeadMs = j.optInt("vox_lead_ms", d.voxLeadMs),
                voxTailMs = j.optInt("vox_tail_ms", d.voxTailMs),
                comPort = j.optString("com_port", d.comPort),
                comPttLine = j.optInt("com_ptt_line", d.comPttLine),
                comInvertDtr = j.optBoolean("com_invert_dtr", d.comInvertDtr),
                comInvertRts = j.optBoolean("com_invert_rts", d.comInvertRts),
                hamlibModel = j.optInt("hamlib_model", d.hamlibModel),
                hamlibDevice = j.optString("hamlib_device", d.hamlibDevice),
                hamlibBaud = j.optInt("hamlib_baud", d.hamlibBaud),
                hamlibPort = j.optInt("hamlib_port", d.hamlibPort),
                pttDelayMs = j.optInt("ptt_delay_ms", d.pttDelayMs),
                pttTailMs = j.optInt("ptt_tail_ms", d.pttTailMs),
                txDelayMs = j.optInt("tx_delay_ms", d.txDelayMs),
                port = j.optInt("port", d.port),
                controlPort = j.optInt("control_port", d.controlPort),
                lanMode = j.optString("bind_address", "127.0.0.1") == "0.0.0.0"
            )
        }
    }
}

object CsmaPresets {
    data class Preset(val name: String, val cw: Int, val slotMs: Int, val burst: Int, val ditherMs: Int)

    val hf = listOf(
        Preset("BENCH", 3, 500, 3, 0),
        Preset("RELAXED", 8, 500, 3, 300),
        Preset("MODERATE", 12, 500, 2, 800),
        Preset("BUSY", 16, 500, 2, 1500)
    )
    val vhf = listOf(
        Preset("BENCH", 2, 200, 4, 0),
        Preset("RELAXED", 4, 200, 4, 200),
        Preset("MODERATE", 6, 200, 3, 300),
        Preset("BUSY", 10, 200, 2, 500)
    )
    val names = listOf("BENCH", "RELAXED", "MODERATE", "BUSY", "CUSTOM")

    fun forBand(band: Int) = if (band == 0) hf else vhf

    fun current(c: TncConfig): String {
        val p = forBand(c.csmaBand).firstOrNull {
            it.cw == c.csmaCw && it.slotMs == c.slotTimeMs && it.burst == c.csmaBurst && it.ditherMs == c.csmaResponderDither
        }
        return p?.name ?: "CUSTOM"
    }

    fun apply(c: TncConfig, name: String): TncConfig {
        val p = forBand(c.csmaBand).firstOrNull { it.name == name } ?: return c
        return c.copy(csmaQuietMs = 0, csmaCw = p.cw, slotTimeMs = p.slotMs, csmaBurst = p.burst, csmaResponderDither = p.ditherMs, txLeadTone = true)
    }
}

data class FreqPreset(val hz: Long, val label: String)

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("modem73", Context.MODE_PRIVATE)

    fun loadPresets(): List<FreqPreset> {
        val raw = prefs.getString("freq_presets", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            List(a.length()) { i -> a.getJSONObject(i).let { FreqPreset(it.getLong("hz"), it.getString("label")) } }
        }.getOrDefault(emptyList())
    }

    fun savePresets(list: List<FreqPreset>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("hz", it.hz).put("label", it.label)) }
        prefs.edit().putString("freq_presets", a.toString()).apply()
    }

    fun load(): TncConfig {
        val raw = prefs.getString("config_json", null) ?: return TncConfig()
        return runCatching { TncConfig.fromJson(JSONObject(raw)) }.getOrDefault(TncConfig())
    }

    fun save(config: TncConfig) {
        prefs.edit().putString("config_json", config.toJson().toString()).apply()
    }
}
