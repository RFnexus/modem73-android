#pragma once

#include <string>
#include <vector>
#include "kiss_tnc_impl.hh"
#include "cJSON.h"

inline int ptt_type_index(PTTType t) {
    return static_cast<int>(t);
}

inline void apply_config_json(cJSON* p, TNCConfig& c) {
    cJSON* it;
    auto num = [&](const char* k, int& dst, int lo, int hi) {
        if ((it = cJSON_GetObjectItemCaseSensitive(p, k)) && cJSON_IsNumber(it)) {
            int v = it->valueint;
            if (v < lo) v = lo;
            if (v > hi) v = hi;
            dst = v;
        }
    };
    auto fnum = [&](const char* k, float& dst, float lo, float hi) {
        if ((it = cJSON_GetObjectItemCaseSensitive(p, k)) && cJSON_IsNumber(it)) {
            float v = (float)it->valuedouble;
            if (v < lo) v = lo;
            if (v > hi) v = hi;
            dst = v;
        }
    };
    auto boolean = [&](const char* k, bool& dst) {
        if ((it = cJSON_GetObjectItemCaseSensitive(p, k)) && cJSON_IsBool(it)) dst = cJSON_IsTrue(it);
    };
    auto str = [&](const char* k, std::string& dst) {
        if ((it = cJSON_GetObjectItemCaseSensitive(p, k)) && cJSON_IsString(it)) dst = it->valuestring;
    };
    if ((it = cJSON_GetObjectItemCaseSensitive(p, "callsign")) && cJSON_IsString(it) && ModemConfig::valid_callsign(it->valuestring)) {
        c.callsign = it->valuestring;
    }
    num("modem_type", c.modem_type, 0, 2);
    num("mfsk_mode", c.mfsk_mode, 0, 3);
    num("robust_mode", c.robust_mode, 0, ROBUST_MODE_COUNT - 1);
    str("modulation", c.modulation);
    str("code_rate", c.code_rate);
    num("frame_size", c.frame_size, 0, 3);
    boolean("postamble", c.postamble);
    boolean("csma_enabled", c.csma_enabled);
    boolean("csma_sync_only", c.csma_sync_only);
    boolean("csma_fast_floor", c.csma_fast_floor);
    boolean("csma_ranked", c.csma_ranked);
    num("beacon_interval_s", c.beacon_interval_s, 45, 90);
    num("csma_band", c.csma_band, 0, 1);
    fnum("carrier_threshold_db", c.carrier_threshold_db, -80.0f, 0.0f);
    num("p_persistence", c.p_persistence, 0, 255);
    num("slot_time_ms", c.slot_time_ms, 50, 5000);
    num("csma_quiet_ms", c.csma_quiet_ms, 0, 10000);
    num("csma_cw", c.csma_cw, 2, 32);
    num("csma_responder_dither", c.csma_responder_dither, 0, 3000);
    num("csma_burst", c.csma_burst, 1, 4);
    boolean("tx_lead_tone", c.tx_lead_tone);
    fnum("tx_drive", c.tx_drive, 0.05f, 1.0f);
    boolean("tx_blanking_enabled", c.tx_blanking_enabled);
    boolean("fragmentation_enabled", c.fragmentation_enabled);
    boolean("mfsk_rx_enabled", c.mfsk_rx_enabled);
    boolean("ofdm_rx_enabled", c.ofdm_rx_enabled);
    boolean("robust_rx_enabled", c.robust_rx_enabled);
    str("audio_input_device", c.audio_input_device);
    str("audio_output_device", c.audio_output_device);
    int ptt = ptt_type_index(c.ptt_type);
    num("ptt_type", ptt, 0, (int)PTT_TYPE_OPTIONS.size() - 1);
    c.ptt_type = static_cast<PTTType>(ptt);
    str("rigctl_host", c.rigctl_host);
    num("hamlib_model", c.hamlib_model, 0, 100000);
    str("hamlib_device", c.hamlib_device);
    num("hamlib_baud", c.hamlib_baud, 0, 1000000);
    num("rigctl_port", c.rigctl_port, 1, 65535);
    num("vox_tone_freq", c.vox_tone_freq, 300, 2500);
    num("vox_lead_ms", c.vox_lead_ms, 50, 2000);
    num("vox_tail_ms", c.vox_tail_ms, 50, 2000);
    str("com_port", c.com_port);
    num("com_ptt_line", c.com_ptt_line, 0, 2);
    boolean("com_invert_dtr", c.com_invert_dtr);
    boolean("com_invert_rts", c.com_invert_rts);
    num("ptt_delay_ms", c.ptt_delay_ms, 0, 2000);
    num("ptt_tail_ms", c.ptt_tail_ms, 0, 2000);
    num("tx_delay_ms", c.tx_delay_ms, 250, 2500);
    num("port", c.port, 1024, 65535);
    num("control_port", c.control_port, 0, 65535);
    str("bind_address", c.bind_address);
    str("control_bind_address", c.control_bind_address);
    boolean("perf_log", c.perf_log);
    if (c.csma_enabled) c.tx_blanking_enabled = true;
}

inline cJSON* config_to_json(const TNCConfig& c) {
    cJSON* j = cJSON_CreateObject();
    cJSON_AddStringToObject(j, "callsign", c.callsign.c_str());
    cJSON_AddNumberToObject(j, "modem_type", c.modem_type);
    cJSON_AddNumberToObject(j, "mfsk_mode", c.mfsk_mode);
    cJSON_AddNumberToObject(j, "robust_mode", c.robust_mode);
    cJSON_AddStringToObject(j, "modulation", c.modulation.c_str());
    cJSON_AddStringToObject(j, "code_rate", c.code_rate.c_str());
    cJSON_AddNumberToObject(j, "frame_size", c.frame_size);
    cJSON_AddBoolToObject(j, "postamble", c.postamble);
    cJSON_AddNumberToObject(j, "center_freq", c.center_freq);
    cJSON_AddBoolToObject(j, "csma_enabled", c.csma_enabled);
    cJSON_AddBoolToObject(j, "csma_sync_only", c.csma_sync_only);
    cJSON_AddBoolToObject(j, "csma_fast_floor", c.csma_fast_floor);
    cJSON_AddBoolToObject(j, "csma_ranked", c.csma_ranked);
    cJSON_AddNumberToObject(j, "beacon_interval_s", c.beacon_interval_s);
    cJSON_AddNumberToObject(j, "csma_band", c.csma_band);
    cJSON_AddNumberToObject(j, "carrier_threshold_db", c.carrier_threshold_db);
    cJSON_AddNumberToObject(j, "p_persistence", c.p_persistence);
    cJSON_AddNumberToObject(j, "slot_time_ms", c.slot_time_ms);
    cJSON_AddNumberToObject(j, "csma_quiet_ms", c.csma_quiet_ms);
    cJSON_AddNumberToObject(j, "csma_cw", c.csma_cw);
    cJSON_AddNumberToObject(j, "csma_responder_dither", c.csma_responder_dither);
    cJSON_AddNumberToObject(j, "csma_burst", c.csma_burst);
    cJSON_AddBoolToObject(j, "tx_lead_tone", c.tx_lead_tone);
    cJSON_AddNumberToObject(j, "tx_drive", c.tx_drive);
    cJSON_AddBoolToObject(j, "tx_blanking_enabled", c.tx_blanking_enabled);
    cJSON_AddBoolToObject(j, "fragmentation_enabled", c.fragmentation_enabled);
    cJSON_AddBoolToObject(j, "mfsk_rx_enabled", c.mfsk_rx_enabled);
    cJSON_AddBoolToObject(j, "ofdm_rx_enabled", c.ofdm_rx_enabled);
    cJSON_AddBoolToObject(j, "robust_rx_enabled", c.robust_rx_enabled);
    cJSON_AddStringToObject(j, "audio_input_device", c.audio_input_device.c_str());
    cJSON_AddStringToObject(j, "audio_output_device", c.audio_output_device.c_str());
    cJSON_AddNumberToObject(j, "ptt_type", ptt_type_index(c.ptt_type));
    cJSON_AddStringToObject(j, "rigctl_host", c.rigctl_host.c_str());
    cJSON_AddNumberToObject(j, "hamlib_model", c.hamlib_model);
    cJSON_AddStringToObject(j, "hamlib_device", c.hamlib_device.c_str());
    cJSON_AddNumberToObject(j, "hamlib_baud", c.hamlib_baud);
    cJSON_AddNumberToObject(j, "rigctl_port", c.rigctl_port);
    cJSON_AddNumberToObject(j, "vox_tone_freq", c.vox_tone_freq);
    cJSON_AddNumberToObject(j, "vox_lead_ms", c.vox_lead_ms);
    cJSON_AddNumberToObject(j, "vox_tail_ms", c.vox_tail_ms);
    cJSON_AddStringToObject(j, "com_port", c.com_port.c_str());
    cJSON_AddNumberToObject(j, "com_ptt_line", c.com_ptt_line);
    cJSON_AddBoolToObject(j, "com_invert_dtr", c.com_invert_dtr);
    cJSON_AddBoolToObject(j, "com_invert_rts", c.com_invert_rts);
    cJSON_AddNumberToObject(j, "ptt_delay_ms", c.ptt_delay_ms);
    cJSON_AddNumberToObject(j, "ptt_tail_ms", c.ptt_tail_ms);
    cJSON_AddNumberToObject(j, "tx_delay_ms", c.tx_delay_ms);
    cJSON_AddNumberToObject(j, "port", c.port);
    cJSON_AddNumberToObject(j, "control_port", c.control_port);
    cJSON_AddStringToObject(j, "bind_address", c.bind_address.c_str());
    cJSON_AddStringToObject(j, "control_bind_address", c.control_bind_address.c_str());
    cJSON_AddBoolToObject(j, "perf_log", c.perf_log);
    return j;
}

inline cJSON* control_status_json(KISSTNC& tnc) {
    cJSON* j = cJSON_CreateObject();
    auto stats = tnc.get_decoder_stats();
    const char* state = "idle";
    if (tnc.is_transmitting()) state = "tx";
    else if (tnc.is_receiving()) state = "rx";
    cJSON_AddStringToObject(j, "channel_state", state);
    cJSON_AddBoolToObject(j, "ptt_on", tnc.is_transmitting());
    cJSON_AddNumberToObject(j, "tx_queue", (double)tnc.tx_queue_depth());
    cJSON_AddNumberToObject(j, "rx_frame_count", stats.sync_count - stats.preamble_errors - stats.crc_errors);
    cJSON_AddNumberToObject(j, "tx_frame_count", 0);
    cJSON_AddNumberToObject(j, "rx_error_count", stats.preamble_errors + stats.crc_errors);
    cJSON_AddNumberToObject(j, "sync_count", stats.sync_count);
    cJSON_AddNumberToObject(j, "preamble_errors", stats.preamble_errors);
    cJSON_AddNumberToObject(j, "symbol_errors", stats.symbol_errors);
    cJSON_AddNumberToObject(j, "erased_symbols", stats.erased_symbols);
    cJSON_AddNumberToObject(j, "crc_errors", stats.crc_errors);
    cJSON_AddNumberToObject(j, "last_snr", stats.last_snr);
    cJSON_AddNumberToObject(j, "last_ber", stats.last_ber);
    cJSON_AddNumberToObject(j, "ber_ema", stats.ber_ema);
    cJSON_AddNumberToObject(j, "client_count", tnc.get_client_count());
    cJSON_AddBoolToObject(j, "rigctl_connected", tnc.is_rigctl_connected());
    cJSON_AddBoolToObject(j, "audio_connected", tnc.is_audio_healthy());
    cJSON_AddNumberToObject(j, "population", tnc.channel_population());
    cJSON_AddNumberToObject(j, "occupancy_pct", tnc.channel_occupancy());
    return j;
}

inline cJSON* control_config_json(KISSTNC& tnc) {
    TNCConfig cfg = tnc.get_config();
    cJSON* j = config_to_json(cfg);
    if (cfg.modem_type == 1) {
        cJSON_ReplaceItemInObject(j, "modulation", cJSON_CreateString(MFSK_MODE_NAMES[cfg.mfsk_mode < 4 ? cfg.mfsk_mode : 0]));
    } else if (cfg.modem_type == 2) {
        cJSON_ReplaceItemInObject(j, "modulation", cJSON_CreateString(ROBUST_MODE_NAMES[cfg.robust_mode >= 0 && cfg.robust_mode < ROBUST_MODE_COUNT ? cfg.robust_mode : 0]));
    }
    cJSON_AddBoolToObject(j, "short_frame", cfg.frame_size == 0);
    cJSON_AddNumberToObject(j, "payload_size", tnc.get_payload_size());
    return j;
}
