package app.modem73.core

import org.json.JSONArray
import org.json.JSONObject

data class HeardStation(val mode: String, val callsign: String, val snr: Float, val ageS: Long)

data class EngineStatus(
    val running: Boolean = false,
    val error: String = "",
    val channelState: String = "idle",
    val transmitting: Boolean = false,
    val receiving: Boolean = false,
    val pttOn: Boolean = false,
    val dcdActive: Boolean = false,
    val carrierDb: Float = -100f,
    val thresholdDb: Float = -30f,
    val lastSnr: Float = 0f,
    val lastBerPct: Float = -1f,
    val rxFrames: Int = 0,
    val txFrames: Int = 0,
    val rxErrors: Int = 0,
    val syncCount: Int = 0,
    val clients: Int = 0,
    val txQueue: Int = 0,
    val population: Int = 0,
    val occupancyPct: Int = 0,
    val channelOccupancy: Float = 0f,
    val csmaEnabled: Boolean = true,
    val csmaMode: Int = 0,
    val csmaPhase: Int = 0,
    val csmaWaitMs: Int = 0,
    val csmaWaitNeedMs: Int = 0,
    val csmaRank: Int = -1,
    val csmaRankN: Int = 0,
    val csmaWindowMs: Int = 0,
    val csmaQuietMs: Int = 0,
    val slotMs: Int = 500,
    val csmaCw: Int = 8,
    val audioOk: Boolean = false,
    val rigctlConnected: Boolean = false,
    val pttFail: Boolean = false,
    val pttType: Int = 0,
    val totalTxTime: Float = 0f,
    val payloadBytes: Int = 0,
    val mtuBytes: Int = 0,
    val bitrateBps: Int = 0,
    val airtimeS: Float = 0f,
    val netBpsEstimate: Int = 0,
    val uptimeS: Long = 0,
    val modemType: Int = 0,
    val modeName: String = "",
    val callsign: String = "",
    val levelHistory: List<Float> = emptyList(),
    val levelDcd: List<Boolean> = emptyList(),
    val levelTone: List<Boolean> = emptyList(),
    val snrHistory: List<Float> = emptyList(),
    val heard: HeardStation? = null,
    val rigFreqHz: Long = 0,
    val rigMode: String = "",
    val rigPower: Float = -1f,
    val rigTuner: Int = -1,
    val rigTunerSupported: Int = -1,
    val rigDataValid: Boolean = false,
    val rigAgeS: Float = -1f,
    val rigMeters: List<Float?> = emptyList(),
    val alcTuning: Boolean = false,
    val swrWarn: Float = 0f,
    val txDrive: Float = 1f
) {
    companion object {
        private fun floats(a: JSONArray?): List<Float> {
            if (a == null) return emptyList()
            return List(a.length()) { a.optDouble(it, 0.0).toFloat() }
        }
        private fun bools(a: JSONArray?): List<Boolean> {
            if (a == null) return emptyList()
            return List(a.length()) { a.optInt(it, 0) != 0 }
        }
        private fun optFloats(a: JSONArray?): List<Float?> {
            if (a == null) return emptyList()
            return List(a.length()) { if (a.isNull(it)) null else a.optDouble(it).toFloat() }
        }

        fun parse(raw: String): EngineStatus {
            val j = JSONObject(raw)
            val heardObj = j.optJSONObject("heard")
            return EngineStatus(
                running = j.optBoolean("running", false),
                error = j.optString("error", ""),
                channelState = j.optString("channel_state", "idle"),
                transmitting = j.optBoolean("transmitting", false),
                receiving = j.optBoolean("receiving", false),
                pttOn = j.optBoolean("ptt_on", false),
                dcdActive = j.optBoolean("dcd_active", false),
                carrierDb = j.optDouble("carrier_db", -100.0).toFloat(),
                thresholdDb = j.optDouble("threshold_db", -30.0).toFloat(),
                lastSnr = j.optDouble("last_snr", 0.0).toFloat(),
                lastBerPct = j.optDouble("last_ber_pct", -1.0).toFloat(),
                rxFrames = j.optInt("rx_frames", 0),
                txFrames = j.optInt("tx_frames", 0),
                rxErrors = j.optInt("rx_errors", 0),
                syncCount = j.optInt("sync_count", 0),
                clients = j.optInt("clients", 0),
                txQueue = j.optInt("tx_queue", 0),
                population = j.optInt("population", 0),
                occupancyPct = j.optInt("occupancy_pct", 0),
                channelOccupancy = j.optDouble("channel_occupancy", 0.0).toFloat(),
                csmaEnabled = j.optBoolean("csma_enabled", true),
                csmaMode = j.optInt("csma_mode", 0),
                csmaPhase = j.optInt("csma_phase", 0),
                csmaWaitMs = j.optInt("csma_wait_ms", 0),
                csmaWaitNeedMs = j.optInt("csma_wait_need_ms", 0),
                csmaRank = j.optInt("csma_rank", -1),
                csmaRankN = j.optInt("csma_rank_n", 0),
                csmaWindowMs = j.optInt("csma_window_ms", 0),
                csmaQuietMs = j.optInt("csma_quiet_ms", 0),
                slotMs = j.optInt("slot_ms", 500),
                csmaCw = j.optInt("csma_cw", 8),
                audioOk = j.optBoolean("audio_ok", false),
                rigctlConnected = j.optBoolean("rigctl_connected", false),
                pttFail = j.optBoolean("ptt_fail", false),
                pttType = j.optInt("ptt_type", 0),
                totalTxTime = j.optDouble("total_tx_time", 0.0).toFloat(),
                payloadBytes = j.optInt("payload_bytes", 0),
                mtuBytes = j.optInt("mtu_bytes", 0),
                bitrateBps = j.optInt("bitrate_bps", 0),
                airtimeS = j.optDouble("airtime_s", 0.0).toFloat(),
                netBpsEstimate = j.optInt("net_bps_estimate", 0),
                uptimeS = j.optLong("uptime_s", 0),
                modemType = j.optInt("modem_type", 0),
                modeName = j.optString("mode_name", ""),
                callsign = j.optString("callsign", ""),
                levelHistory = floats(j.optJSONArray("level_history")),
                levelDcd = bools(j.optJSONArray("level_dcd")),
                levelTone = bools(j.optJSONArray("level_tone")),
                snrHistory = floats(j.optJSONArray("snr_history")),
                heard = heardObj?.let {
                    HeardStation(it.optString("mode", ""), it.optString("callsign", ""), it.optDouble("snr", 0.0).toFloat(), it.optLong("age_s", 0))
                },
                rigFreqHz = j.optLong("rig_freq_hz", 0),
                rigMode = j.optString("rig_mode", ""),
                rigPower = j.optDouble("rig_power", -1.0).toFloat(),
                rigTuner = j.optInt("rig_tuner", -1),
                rigTunerSupported = j.optInt("rig_tuner_supported", -1),
                rigDataValid = j.optBoolean("rig_data_valid", false),
                rigAgeS = j.optDouble("rig_age_s", -1.0).toFloat(),
                rigMeters = optFloats(j.optJSONArray("rig_meters")),
                alcTuning = j.optBoolean("alc_tuning", false),
                swrWarn = j.optDouble("swr_warn", 0.0).toFloat(),
                txDrive = j.optDouble("tx_drive", 1.0).toFloat()
            )
        }
    }
}

data class EnginePacket(val tx: Boolean, val bytes: Int, val snr: Float, val ber: Float, val ageS: Long, val mode: String, val callsign: String)
data class EngineMessage(val time: String, val from: String, val text: String, val outgoing: Boolean)

data class EngineSnapshot(
    val packets: List<EnginePacket> = emptyList(),
    val messages: List<EngineMessage> = emptyList(),
    val log: List<String> = emptyList()
) {
    companion object {
        fun parse(raw: String): EngineSnapshot {
            val j = JSONObject(raw)
            val pk = j.optJSONArray("packets") ?: JSONArray()
            val ms = j.optJSONArray("messages") ?: JSONArray()
            val lg = j.optJSONArray("log") ?: JSONArray()
            return EngineSnapshot(
                packets = List(pk.length()) { i ->
                    val p = pk.getJSONObject(i)
                    EnginePacket(p.optBoolean("tx"), p.optInt("bytes"), p.optDouble("snr", 0.0).toFloat(), p.optDouble("ber", -1.0).toFloat(), p.optLong("age_s", 0), p.optString("mode", ""), p.optString("callsign", ""))
                },
                messages = List(ms.length()) { i ->
                    val m = ms.getJSONObject(i)
                    EngineMessage(m.optString("time", ""), m.optString("from", ""), m.optString("text", ""), m.optBoolean("outgoing"))
                },
                log = List(lg.length()) { i -> lg.optString(i, "") }
            )
        }
    }
}
