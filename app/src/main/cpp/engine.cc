#include "engine.hh"

#include <android/log.h>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/prctl.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <complex>
#include <cstdlib>

#include "kiss_tnc_impl.hh"
#include "tnc_json.hh"
#include "core_smoke.hh"

#define LOG_TAG "modem73-engine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

int64_t now_ms() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

void start_stdio_redirect() {
    static bool started = false;
    if (started) return;
    started = true;
    int fds[2];
    if (pipe(fds) != 0) return;
    dup2(fds[1], STDOUT_FILENO);
    dup2(fds[1], STDERR_FILENO);
    setvbuf(stdout, nullptr, _IOLBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    int rd = fds[0];
    std::thread([rd]() {
        prctl(PR_SET_NAME, "m73-stdio", 0, 0, 0);
        std::string line;
        char buf[512];
        for (;;) {
            ssize_t n = read(rd, buf, sizeof buf);
            if (n <= 0) break;
            for (ssize_t i = 0; i < n; ++i) {
                if (buf[i] == '\n') {
                    if (!line.empty()) __android_log_write(ANDROID_LOG_INFO, "modem73-core", line.c_str());
                    line.clear();
                } else {
                    line.push_back(buf[i]);
                }
            }
        }
    }).detach();
}

std::string json_dump(cJSON* j) {
    char* s = cJSON_PrintUnformatted(j);
    std::string out = s ? s : "{}";
    if (s) cJSON_free(s);
    cJSON_Delete(j);
    return out;
}

}  // namespace

struct Modem73Engine::Impl {
    TNCConfig config;
    TNCUIState ui;
    std::unique_ptr<KISSTNC> tnc;
    std::unique_ptr<ControlPort> ctrl;
    int64_t started_ms = 0;
    static constexpr int WF_FFT = 256;
    static constexpr int WF_BINS = WF_FFT / 2;
    static constexpr int WF_ROW_MS = 100;
    DSP::RealToHalfComplexTransform<WF_FFT, std::complex<float>> wf_fft;
    uint32_t wf_seen = 0;
    int64_t wf_row_ms = 0;
    float wf_floor_db = -60.0f;
    bool wf_have_floor = false;
    int64_t last_level_ms = 0;
    int64_t last_health_ms = 0;
};

Modem73Engine& Modem73Engine::instance() {
    static Modem73Engine e;
    return e;
}

std::string Modem73Engine::last_error() {
    std::lock_guard<std::mutex> lock(error_mutex_);
    return error_;
}

bool Modem73Engine::start(const std::string& config_json, const std::string& home_dir, std::string& error) {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);

    if (running_.load()) {

        error = "already running";
        return false;

    }

    start_stdio_redirect();
    if (!home_dir.empty()) {

        setenv("HOME", home_dir.c_str(), 1);
        mkdir((home_dir + "/.config").c_str(), 0700);
        mkdir((home_dir + "/.config/modem73").c_str(), 0700);

    }

    impl_ = std::make_unique<Impl>();

    impl_->config.perf_log = false;

    cJSON* j = cJSON_Parse(config_json.c_str());

    if (!j) {

        error = "config json parse error";
        impl_.reset();
        return false;

    }


    apply_config_json(j, impl_->config);
    cJSON_Delete(j);
    if (impl_->config.csma_enabled) impl_->config.tx_blanking_enabled = true;

    TNCUIState& ui = impl_->ui;
    ui.callsign = impl_->config.callsign;
    ui.modem_type_index = impl_->config.modem_type;
    ui.mfsk_mode_index = impl_->config.mfsk_mode;
    ui.robust_mode_index = impl_->config.robust_mode;
    for (size_t i = 0; i < MODULATION_OPTIONS.size(); i++)
        if (MODULATION_OPTIONS[i] == impl_->config.modulation) ui.modulation_index = (int)i;
    for (size_t i = 0; i < CODE_RATE_OPTIONS.size(); i++)
        if (CODE_RATE_OPTIONS[i] == impl_->config.code_rate) ui.code_rate_index = (int)i;
    ui.frame_size = impl_->config.frame_size;

    ui.fragmentation_enabled = impl_->config.fragmentation_enabled;

    ui.tx_blanking_enabled = impl_->config.tx_blanking_enabled;

    ui.csma_enabled = impl_->config.csma_enabled;

    ui.csma_sync_only = impl_->config.csma_sync_only;

    ui.csma_ranked = impl_->config.csma_ranked;

    ui.csma_quiet_ms = impl_->config.csma_quiet_ms;

    ui.csma_cw = impl_->config.csma_cw;

    ui.slot_time_ms = impl_->config.slot_time_ms;

    ui.carrier_threshold_db = impl_->config.carrier_threshold_db;

    ui.tx_drive = impl_->config.tx_drive;

    ui.ptt_type_index = ptt_type_index(impl_->config.ptt_type);

    ui.scope_active = true;



    ui.update_modem_info();




    g_use_ui = true;
    g_verbose = true;
    g_running = true;
    g_fatal_error.clear();

    g_ui_state = &ui;
    {
        std::lock_guard<std::mutex> el(error_mutex_);
        error_.clear();
    }

    try {
        impl_->tnc = std::make_unique<KISSTNC>(impl_->config);
    } catch (const std::exception& e) {
        error = e.what();
        g_ui_state = nullptr;
        impl_.reset();
        return false;
    }
    KISSTNC& tnc = *impl_->tnc;
    tnc.rx_tap = [this](const float* samples, int n) {
        if (!rec_active_.load()) return;
        std::lock_guard<std::mutex> lock(rec_mutex_);
        rec_buf_.insert(rec_buf_.end(), samples, samples + n);
        if (rec_buf_.size() >= rec_target_) {
            rec_active_ = false;
            write_recording();
        }
    };
    tnc.external_ptt = [this](bool on) -> bool {
        if (ptt_hook) return ptt_hook(on);
        return false;
    };
    ui.perf_logger = &tnc.perf_log_;

    ui.on_send_data = [&tnc](const std::vector<uint8_t>& data) { tnc.queue_data(data); };

    ui.on_reconnect_audio = [&tnc]() -> bool { return tnc.reconnect_audio(); };
    ui.on_get_audio_level = [&tnc]() -> float { return tnc.get_audio_level(); };
    ui.on_alc_tune = [&tnc]() -> float { return tnc.alc_auto_tune(); };
    ui.on_rigctl_command = [&tnc](const std::string& cmd) -> std::string { return tnc.rigctl_command(cmd); };

    if (impl_->config.control_port > 0) {
        ControlPort::TNCInterface iface;
        iface.get_status = [this, &tnc]() -> cJSON* {
            cJSON* j = control_status_json(tnc);
            TNCConfig c = tnc.get_config();
            cJSON_AddNumberToObject(j, "net_bps_estimate",
                net_bps_estimate(c.csma_enabled, c.csma_quiet_ms, c.csma_cw,
                                 c.slot_time_ms, c.csma_burst, c.tx_lead_tone,
                                 c.tx_delay_ms, impl_->ui.airtime_seconds,
                                 impl_->ui.mtu_bytes));
            return j;
        };
        iface.get_config = [&tnc]() -> cJSON* { return control_config_json(tnc); };
        iface.set_config = [this, &tnc](cJSON* params) -> bool {
            TNCConfig nc = tnc.get_config();
            apply_config_json(params, nc);
            auto rejected = tnc.update_config(nc);
            TNCConfig applied = tnc.get_config();
            impl_->config = applied;
            impl_->ui.callsign = applied.callsign;
            impl_->ui.modem_type_index = applied.modem_type;
            impl_->ui.mfsk_mode_index = applied.mfsk_mode;
            impl_->ui.robust_mode_index = applied.robust_mode;
            impl_->ui.frame_size = applied.frame_size;
            impl_->ui.csma_enabled = applied.csma_enabled;
            impl_->ui.csma_sync_only = applied.csma_sync_only;
            impl_->ui.csma_ranked = applied.csma_ranked;
            impl_->ui.tx_drive = applied.tx_drive;
            impl_->ui.update_modem_info();
            return rejected.empty();
        };
        iface.send_beacon = [&tnc]() -> bool { return tnc.queue_beacon(); };
        iface.rigctl_command = [&tnc](const std::string& cmd) -> std::string { return tnc.rigctl_command(cmd); };
        iface.tx_data = [&tnc](const std::vector<uint8_t>& data, int oper_mode) -> bool {
            tnc.queue_data_ex(data, oper_mode);
            return true;
        };
        iface.rx_frame_history = [&tnc](size_t limit, uint64_t since_seq) {
            return tnc.rx_frame_history(limit, since_seq);
        };
        impl_->ctrl = std::make_unique<ControlPort>(impl_->config.control_port, impl_->config.control_bind_address, iface);
        impl_->ctrl->start();
        ControlPort* ctrl = impl_->ctrl.get();
        tnc.rx_frame_callback = [ctrl](const RxFrameInfo& info) {
            ctrl->notify_rx_frame(info);
        };
    }

    impl_->started_ms = now_ms();
    running_ = true;
    tnc_thread_ = std::thread([this]() {
        prctl(PR_SET_NAME, "m73-tnc", 0, 0, 0);
        try {
            impl_->tnc->run();
        } catch (const std::exception& e) {
            impl_->tnc->unkey();
            ui_log(std::string("FATAL: ") + e.what());
            std::lock_guard<std::mutex> el(error_mutex_);
            error_ = e.what();
            g_running = false;
        }
        running_ = false;
    });
    tick_running_ = true;
    tick_thread_ = std::thread([this]() {
        prctl(PR_SET_NAME, "m73-tick", 0, 0, 0);
        tick_loop();
    });
    LOGI("engine started (kiss %d, control %d)", impl_->config.port, impl_->config.control_port);
    return true;
}

void Modem73Engine::stop() {
    std::lock_guard<std::mutex> lock(lifecycle_mutex_);
    if (!impl_) return;
    for (int i = 0; i < 100 && impl_->ui.alc_tune_running.load(); ++i)
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    g_running = false;
    tick_running_ = false;
    if (tick_thread_.joinable()) tick_thread_.join();
    if (tnc_thread_.joinable()) tnc_thread_.join();
    if (impl_->ctrl) impl_->ctrl->stop();
    running_ = false;
    g_ui_state = nullptr;
    impl_.reset();
    {
        std::lock_guard<std::mutex> wl(wf_mutex_);
        wf_pending_.clear();
    }
    LOGI("engine stopped");
}

void Modem73Engine::tick_loop() {
    while (tick_running_.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(50));
        if (!impl_ || !impl_->tnc) continue;
        int64_t now = now_ms();
        if (now - impl_->last_level_ms >= 100) {
            impl_->last_level_ms = now;
            float db = impl_->tnc->get_audio_level();
            int64_t sig = impl_->ui.wf_sig_ms.load();
            impl_->ui.update_level(db, impl_->ui.dcd_active.load(), sig != 0 && now - sig < 450);
        }
        if (now - impl_->last_health_ms >= 500) {
            impl_->last_health_ms = now;
            impl_->ui.rigctl_connected = impl_->tnc->is_rigctl_connected();
            impl_->ui.audio_connected = impl_->tnc->is_audio_healthy();
            if (impl_->ui.ptt_on.load() || impl_->ui.transmitting.load()) last_ptt_ms_ = now;
            bool after_tx = last_ptt_ms_ > 0 && now - last_ptt_ms_ < 3000;
            if (impl_->ui.rigctl_connected.load() && (rig_poll_ui_.load() || after_tx))
                impl_->ui.poll_rig();
        }
        update_waterfall();
    }
}

void Modem73Engine::update_waterfall() {
    Impl& im = *impl_;
    int64_t now = now_ms();
    if (now - im.wf_row_ms < Impl::WF_ROW_MS) return;
    static constexpr int SEGS = 6;
    static constexpr int HOP = Impl::WF_FFT / 2;
    static constexpr int SPAN = Impl::WF_FFT + HOP * (SEGS - 1);
    float tdom[SPAN];
    {
        std::lock_guard<std::mutex> lock(im.ui.wf_mutex);
        if (im.ui.wf_written == im.wf_seen) return;
        im.wf_seen = im.ui.wf_written;
        int p = im.ui.wf_wpos;
        for (int i = 0; i < SPAN; i++)
            tdom[i] = im.ui.wf_ring[(p + TNCUIState::WF_RING - SPAN + i) % TNCUIState::WF_RING];
    }
    im.wf_row_ms = now;
    static const auto window = [] {
        std::array<float, Impl::WF_FFT> w{};
        for (int i = 0; i < Impl::WF_FFT; i++)
            w[i] = 0.5f - 0.5f * std::cos(2.0f * (float)M_PI * i / Impl::WF_FFT);
        return w;
    }();
    float pwr[Impl::WF_BINS] = {};
    float seg[Impl::WF_FFT];
    std::complex<float> fdom[Impl::WF_FFT / 2 + 1];
    for (int s = 0; s < SEGS; s++) {
        const float* src = tdom + s * HOP;
        for (int i = 0; i < Impl::WF_FFT; i++) seg[i] = src[i] * window[i];
        im.wf_fft(fdom, seg);
        for (int i = 0; i < Impl::WF_BINS; i++) pwr[i] += std::norm(fdom[i]);
    }
    float db[Impl::WF_BINS];
    for (int i = 0; i < Impl::WF_BINS; i++) {
        float p3 = 2.0f * pwr[i] + pwr[i > 0 ? i - 1 : 0] + pwr[i < Impl::WF_BINS - 1 ? i + 1 : i];
        db[i] = 10.0f * std::log10(p3 + 1e-12f);
    }
    float sorted[Impl::WF_BINS];
    std::copy(db, db + Impl::WF_BINS, sorted);
    std::nth_element(sorted, sorted + Impl::WF_BINS / 2, sorted + Impl::WF_BINS);
    float med = sorted[Impl::WF_BINS / 2];
    if (med < -105.0f) return;
    if (!im.wf_have_floor) {
        im.wf_floor_db = med;
        im.wf_have_floor = true;
    } else if (med < im.wf_floor_db) {
        im.wf_floor_db += (med - im.wf_floor_db) * 0.5f;
    } else {
        im.wf_floor_db += (med - im.wf_floor_db) * 0.02f;
    }
    std::vector<float> row(Impl::WF_BINS);
    for (int i = 0; i < Impl::WF_BINS; i++) {
        float v = (db[i] - im.wf_floor_db + 6.0f) / 50.0f;
        row[i] = std::max(0.0f, std::min(1.0f, v));
    }
    std::lock_guard<std::mutex> lock(wf_mutex_);
    wf_pending_.push_back(std::move(row));
    while (wf_pending_.size() > 64) wf_pending_.pop_front();
}

std::vector<float> Modem73Engine::take_waterfall(int& bins) {
    bins = Impl::WF_BINS;
    std::lock_guard<std::mutex> lock(wf_mutex_);
    std::vector<float> out;
    out.reserve(wf_pending_.size() * Impl::WF_BINS);
    for (auto& r : wf_pending_) out.insert(out.end(), r.begin(), r.end());
    wf_pending_.clear();
    return out;
}

std::string Modem73Engine::status_json() {
    cJSON* j = cJSON_CreateObject();
    cJSON_AddBoolToObject(j, "running", running_.load());
    if (!impl_ || !impl_->tnc) {
        std::lock_guard<std::mutex> el(error_mutex_);
        cJSON_AddStringToObject(j, "error", error_.c_str());
        return json_dump(j);
    }
    KISSTNC& tnc = *impl_->tnc;
    TNCUIState& ui = impl_->ui;
    TNCConfig cfg = tnc.get_config();
    auto stats = tnc.get_decoder_stats();
    const char* state = "idle";
    if (ui.transmitting.load()) state = "tx";
    else if (ui.receiving.load()) state = "rx";
    cJSON_AddStringToObject(j, "channel_state", state);
    cJSON_AddBoolToObject(j, "transmitting", ui.transmitting.load());
    cJSON_AddBoolToObject(j, "receiving", ui.receiving.load());
    cJSON_AddBoolToObject(j, "ptt_on", ui.ptt_on.load());
    cJSON_AddBoolToObject(j, "ptt_fail", tnc.ptt_failed());
    cJSON_AddBoolToObject(j, "dcd_active", ui.dcd_active.load());
    cJSON_AddNumberToObject(j, "carrier_db", ui.carrier_level_db.load());
    cJSON_AddNumberToObject(j, "threshold_db", cfg.carrier_threshold_db);
    cJSON_AddNumberToObject(j, "last_snr", ui.last_rx_snr.load());
    cJSON_AddNumberToObject(j, "last_ber_pct", ui.last_rx_ber.load());
    cJSON_AddNumberToObject(j, "ber_ema", stats.ber_ema);
    cJSON_AddNumberToObject(j, "rx_frames", ui.rx_frame_count.load());
    cJSON_AddNumberToObject(j, "tx_frames", ui.tx_frame_count.load());
    cJSON_AddNumberToObject(j, "rx_errors", ui.rx_error_count.load());
    cJSON_AddNumberToObject(j, "sync_count", ui.sync_count.load());
    cJSON_AddNumberToObject(j, "preamble_errors", ui.preamble_errors.load());
    cJSON_AddNumberToObject(j, "symbol_errors", ui.symbol_errors.load());
    cJSON_AddNumberToObject(j, "crc_errors", ui.crc_errors.load());
    cJSON_AddNumberToObject(j, "clients", ui.client_count.load());
    cJSON_AddNumberToObject(j, "tx_queue", ui.tx_queue_size.load());
    cJSON_AddNumberToObject(j, "population", tnc.channel_population());
    cJSON_AddNumberToObject(j, "occupancy_pct", tnc.channel_occupancy());
    cJSON_AddNumberToObject(j, "channel_occupancy", ui.channel_occupancy.load());
    cJSON_AddBoolToObject(j, "csma_enabled", cfg.csma_enabled);
    cJSON_AddNumberToObject(j, "csma_mode", !cfg.csma_sync_only ? 0 : cfg.csma_ranked ? 2 : 1);
    cJSON_AddNumberToObject(j, "csma_phase", ui.csma_phase.load());
    cJSON_AddNumberToObject(j, "csma_wait_ms", ui.csma_wait_ms.load());
    cJSON_AddNumberToObject(j, "csma_wait_need_ms", ui.csma_wait_need.load());
    cJSON_AddNumberToObject(j, "csma_rank", ui.csma_rank.load());
    cJSON_AddNumberToObject(j, "csma_rank_n", ui.csma_rank_n.load());
    cJSON_AddNumberToObject(j, "csma_window_ms", ui.csma_window_ms.load());
    cJSON_AddNumberToObject(j, "csma_quiet_ms", cfg.csma_quiet_ms);
    cJSON_AddNumberToObject(j, "slot_ms", cfg.slot_time_ms);
    cJSON_AddNumberToObject(j, "csma_cw", cfg.csma_cw);
    cJSON_AddBoolToObject(j, "audio_ok", ui.audio_connected.load());
    cJSON_AddBoolToObject(j, "rigctl_connected", ui.rigctl_connected.load());
    cJSON_AddNumberToObject(j, "rig_freq_hz", (double)ui.rig_freq_hz.load());
    cJSON_AddStringToObject(j, "rig_mode", ui.get_rig_mode().c_str());
    cJSON_AddNumberToObject(j, "rig_power", ui.rig_power_level.load());
    cJSON_AddNumberToObject(j, "rig_tuner", ui.rig_tuner_on.load());
    cJSON_AddNumberToObject(j, "rig_tuner_supported", ui.rig_tuner_supported.load());
    cJSON_AddBoolToObject(j, "rig_data_valid", ui.rig_data_valid.load());
    {
        int64_t last = ui.rig_last_update_ms.load();
        cJSON_AddNumberToObject(j, "rig_age_s", last > 0 ? (double)(now_ms() - last) / 1000.0 : -1.0);
        cJSON* meters = cJSON_CreateArray();
        for (int i = 0; i < RIG_METER_COUNT; i++) {
            float v = ui.rig_meter_values[i].load();
            cJSON_AddItemToArray(meters, std::isnan(v) ? cJSON_CreateNull() : cJSON_CreateNumber(v));
        }
        cJSON_AddItemToObject(j, "rig_meters", meters);
    }
    cJSON_AddBoolToObject(j, "alc_tuning", ui.alc_tune_running.load());
    cJSON_AddNumberToObject(j, "swr_warn", ui.swr_warn_value.load());
    cJSON_AddNumberToObject(j, "tx_drive", ui.tx_drive.load());
    cJSON_AddNumberToObject(j, "ptt_type", ptt_type_index(cfg.ptt_type));
    cJSON_AddNumberToObject(j, "total_tx_time", ui.total_tx_time.load());
    cJSON_AddNumberToObject(j, "payload_bytes", tnc.get_payload_size());
    cJSON_AddNumberToObject(j, "mtu_bytes", ui.mtu_bytes);
    cJSON_AddNumberToObject(j, "bitrate_bps", ui.bitrate_bps);
    cJSON_AddNumberToObject(j, "airtime_s", ui.airtime_seconds);
    cJSON_AddNumberToObject(j, "net_bps_estimate",
        net_bps_estimate(cfg.csma_enabled, cfg.csma_quiet_ms, cfg.csma_cw,
                         cfg.slot_time_ms, cfg.csma_burst, cfg.tx_lead_tone,
                         cfg.tx_delay_ms, ui.airtime_seconds, ui.mtu_bytes));
    cJSON_AddNumberToObject(j, "uptime_s", (double)((now_ms() - impl_->started_ms) / 1000));
    cJSON_AddNumberToObject(j, "modem_type", cfg.modem_type);
    std::string mode_name;
    if (cfg.modem_type == 1) mode_name = MFSK_MODE_NAMES[cfg.mfsk_mode < 4 ? cfg.mfsk_mode : 0];
    else if (cfg.modem_type == 2) mode_name = ROBUST_MODE_NAMES[cfg.robust_mode >= 0 && cfg.robust_mode < ROBUST_MODE_COUNT ? cfg.robust_mode : 0];
    else mode_name = cfg.modulation + " " + cfg.code_rate + " " + ModemConfig::frame_size_name(cfg.frame_size);
    cJSON_AddStringToObject(j, "mode_name", mode_name.c_str());
    cJSON_AddStringToObject(j, "callsign", cfg.callsign.c_str());
    {
        cJSON* lv = cJSON_CreateArray();
        cJSON* ld = cJSON_CreateArray();
        cJSON* lt = cJSON_CreateArray();
        auto hist = ui.get_level_history();
        auto dcd = ui.get_level_dcd_history();
        auto tone = ui.get_level_tone_history();
        for (size_t i = 0; i < hist.size(); i++) {
            cJSON_AddItemToArray(lv, cJSON_CreateNumber(hist[i]));
            cJSON_AddItemToArray(ld, cJSON_CreateNumber(i < dcd.size() && dcd[i] ? 1 : 0));
            cJSON_AddItemToArray(lt, cJSON_CreateNumber(i < tone.size() && tone[i] ? 1 : 0));
        }
        cJSON_AddItemToObject(j, "level_history", lv);
        cJSON_AddItemToObject(j, "level_dcd", ld);
        cJSON_AddItemToObject(j, "level_tone", lt);
        cJSON* sn = cJSON_CreateArray();
        for (float v : ui.get_snr_history()) cJSON_AddItemToArray(sn, cJSON_CreateNumber(v));
        cJSON_AddItemToObject(j, "snr_history", sn);
    }
    {
        auto pk = ui.get_recent_packets();
        auto now = std::chrono::steady_clock::now();
        for (auto it = pk.rbegin(); it != pk.rend(); ++it) {
            if (it->is_tx) continue;
            cJSON* h = cJSON_CreateObject();
            cJSON_AddStringToObject(h, "mode", it->mode.c_str());
            cJSON_AddStringToObject(h, "callsign", it->callsign.c_str());
            cJSON_AddNumberToObject(h, "snr", it->snr);
            cJSON_AddNumberToObject(h, "age_s", (double)std::chrono::duration_cast<std::chrono::seconds>(now - it->timestamp).count());
            cJSON_AddItemToObject(j, "heard", h);
            break;
        }
    }
    return json_dump(j);
}

std::string Modem73Engine::snapshot_json() {
    cJSON* j = cJSON_CreateObject();
    if (!impl_) return json_dump(j);
    TNCUIState& ui = impl_->ui;
    auto now = std::chrono::steady_clock::now();
    cJSON* pk = cJSON_CreateArray();
    auto packets = ui.get_recent_packets();
    for (auto it = packets.rbegin(); it != packets.rend(); ++it) {
        cJSON* p = cJSON_CreateObject();
        cJSON_AddBoolToObject(p, "tx", it->is_tx);
        cJSON_AddNumberToObject(p, "bytes", it->size);
        cJSON_AddNumberToObject(p, "snr", it->snr);
        cJSON_AddNumberToObject(p, "ber", it->ber);
        cJSON_AddNumberToObject(p, "age_s", (double)std::chrono::duration_cast<std::chrono::seconds>(now - it->timestamp).count());
        cJSON_AddStringToObject(p, "mode", it->mode.c_str());
        cJSON_AddStringToObject(p, "callsign", it->callsign.c_str());
        cJSON_AddItemToArray(pk, p);
    }
    cJSON_AddItemToObject(j, "packets", pk);
    cJSON* ms = cJSON_CreateArray();
    for (auto& m : ui.get_messages()) {
        cJSON* p = cJSON_CreateObject();
        cJSON_AddStringToObject(p, "time", m.time.c_str());
        cJSON_AddStringToObject(p, "from", m.from.c_str());
        cJSON_AddStringToObject(p, "text", m.text.c_str());
        cJSON_AddBoolToObject(p, "outgoing", m.outgoing);
        cJSON_AddItemToArray(ms, p);
    }
    cJSON_AddItemToObject(j, "messages", ms);
    cJSON* lg = cJSON_CreateArray();
    auto logs = ui.get_log();
    size_t start = logs.size() > 120 ? logs.size() - 120 : 0;
    for (size_t i = start; i < logs.size(); i++) cJSON_AddItemToArray(lg, cJSON_CreateString(logs[i].c_str()));
    cJSON_AddItemToObject(j, "log", lg);
    return json_dump(j);
}

std::string Modem73Engine::config_json() {
    if (!impl_ || !impl_->tnc) return "{}";
    return json_dump(control_config_json(*impl_->tnc));
}

bool Modem73Engine::set_config_json(const std::string& json, std::string& error) {
    if (!impl_ || !impl_->tnc) {
        error = "not running";
        return false;
    }
    cJSON* p = cJSON_Parse(json.c_str());
    if (!p) {
        error = "json parse error";
        return false;
    }
    KISSTNC& tnc = *impl_->tnc;
    TNCConfig nc = tnc.get_config();
    apply_config_json(p, nc);
    cJSON_Delete(p);
    auto rejected = tnc.update_config(nc);
    TNCConfig applied = tnc.get_config();
    impl_->config = applied;
    TNCUIState& ui = impl_->ui;
    ui.callsign = applied.callsign;
    ui.modem_type_index = applied.modem_type;
    ui.mfsk_mode_index = applied.mfsk_mode;
    ui.robust_mode_index = applied.robust_mode;
    for (size_t i = 0; i < MODULATION_OPTIONS.size(); i++)
        if (MODULATION_OPTIONS[i] == applied.modulation) ui.modulation_index = (int)i;
    for (size_t i = 0; i < CODE_RATE_OPTIONS.size(); i++)
        if (CODE_RATE_OPTIONS[i] == applied.code_rate) ui.code_rate_index = (int)i;
    ui.frame_size = applied.frame_size;
    ui.fragmentation_enabled = applied.fragmentation_enabled;
    ui.csma_enabled = applied.csma_enabled;
    ui.csma_sync_only = applied.csma_sync_only;
    ui.csma_ranked = applied.csma_ranked;
    ui.tx_drive = applied.tx_drive;
    ui.update_modem_info();
    if (impl_->ctrl) impl_->ctrl->notify_config_changed();
    if (!rejected.empty()) {
        error.clear();
        for (auto& r : rejected) {
            if (!error.empty()) error += ", ";
            error += r;
        }
        return false;
    }
    return true;
}

void Modem73Engine::queue_data(const std::vector<uint8_t>& data, int oper_mode) {
    if (!impl_ || !impl_->tnc) return;
    if (oper_mode >= 0) impl_->tnc->queue_data_ex(data, oper_mode);
    else impl_->tnc->queue_data(data);
}

void Modem73Engine::send_chat(const std::string& text) {
    if (!impl_ || !impl_->tnc) return;
    std::string call = impl_->tnc->get_config().callsign;
    std::string payload = "M73:" + call + ":" + text;
    std::vector<uint8_t> bytes(payload.begin(), payload.end());
    impl_->tnc->queue_data(bytes);
    impl_->ui.add_message(call, text, true);
}

std::string Modem73Engine::rigctl(const std::string& cmd) {
    if (!impl_ || !impl_->tnc) return "";
    return impl_->tnc->rigctl_command(cmd);
}

void Modem73Engine::rig_refresh() {
    if (!impl_) return;
    impl_->ui.rig_refresh_requested = true;
}

float Modem73Engine::alc_tune() {
    if (!impl_ || !impl_->tnc || !impl_->ui.on_alc_tune) return -1.0f;
    if (!running_.load() || impl_->ui.alc_tune_running.exchange(true)) return -1.0f;
    float r = impl_->ui.on_alc_tune();
    if (r > 0 && impl_) impl_->ui.tx_drive = r;
    if (impl_) impl_->ui.alc_tune_running = false;
    return r;
}

void Modem73Engine::reset_stats() {
    if (!impl_) return;
    impl_->ui.stats_reset_requested = true;
}

std::string Modem73Engine::debug_record(int seconds) {
    if (!impl_ || !impl_->tnc) return "";
    std::lock_guard<std::mutex> lock(rec_mutex_);
    const char* home = getenv("HOME");
    rec_path_ = std::string(home ? home : "/data/local/tmp") + "/rx_capture.wav";
    rec_buf_.clear();
    rec_target_ = (size_t)std::max(1, seconds) * 48000;
    rec_buf_.reserve(rec_target_ + 4096);
    rec_active_ = true;
    return rec_path_;
}

void Modem73Engine::write_recording() {
    FILE* f = fopen(rec_path_.c_str(), "wb");
    if (!f) return;
    uint32_t n = (uint32_t)rec_buf_.size();
    uint32_t data_bytes = n * 2;
    uint32_t rate = 48000;
    uint16_t ch = 1, bits = 16, block = 2;
    uint32_t byte_rate = rate * block;
    uint32_t riff = 36 + data_bytes;
    uint32_t fmt_len = 16;
    uint16_t fmt_tag = 1;
    fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); fwrite(&fmt_len, 4, 1, f); fwrite(&fmt_tag, 2, 1, f); fwrite(&ch, 2, 1, f);
    fwrite(&rate, 4, 1, f); fwrite(&byte_rate, 4, 1, f); fwrite(&block, 2, 1, f); fwrite(&bits, 2, 1, f);
    fwrite("data", 1, 4, f); fwrite(&data_bytes, 4, 1, f);
    std::vector<int16_t> pcm(n);
    for (uint32_t i = 0; i < n; i++) {
        float v = std::max(-1.0f, std::min(1.0f, rec_buf_[i]));
        pcm[i] = (int16_t)(v * 32767.0f);
    }
    fwrite(pcm.data(), 2, n, f);
    fclose(f);
    LOGI("rx capture written: %s (%u samples)", rec_path_.c_str(), n);
}

std::string Modem73Engine::audio_devices_json() {
    cJSON* j = cJSON_CreateObject();
    cJSON* in = cJSON_CreateArray();
    cJSON* out = cJSON_CreateArray();
    try {
        for (auto& d : MiniAudio::list_capture_devices()) cJSON_AddItemToArray(in, cJSON_CreateString(d.first.c_str()));
        for (auto& d : MiniAudio::list_playback_devices()) cJSON_AddItemToArray(out, cJSON_CreateString(d.first.c_str()));
    } catch (...) {
    }
    cJSON_AddItemToObject(j, "input", in);
    cJSON_AddItemToObject(j, "output", out);
    return json_dump(j);
}


std::string modem73_build_info() {
    std::string s;
#if defined(__aarch64__)
    s += "arm64-v8a";
#elif defined(__x86_64__)
    s += "x86_64";
#elif defined(__arm__)
    s += "armeabi-v7a";
#elif defined(__i386__)
    s += "x86";
#else
    s += "unknown-abi";
#endif
    s += " · clang " + std::to_string(__clang_major__) + "." + std::to_string(__clang_minor__) +
         "." + std::to_string(__clang_patchlevel__);
#if defined(__AVX2__)
    s += " · AVX2";
#elif defined(__SSE4_1__)
    s += " · SSE4.1";
#elif defined(__ARM_NEON)
    s += " · NEON";
#else
    s += " · scalar";
#endif
    return s;
}

std::string modem73_loopback_selftest() {
    using clock = std::chrono::steady_clock;
    char buf[256];

    const int mode = ModemConfig::encode_mode("QPSK", "1/2", /*frame_size=*/0 /*short*/);
    if (mode < 0) return "FAIL: encode_mode rejected QPSK 1/2 short";
    const int64_t call = ModemConfig::encode_callsign("N0CALL");

    auto enc = std::make_unique<Encoder48k>();
    auto dec = std::make_unique<Decoder48k>();

    const int payload = enc->get_payload_size(mode);
    if (payload <= 0) return "FAIL: get_payload_size returned " + std::to_string(payload);
    std::vector<uint8_t> data(payload);
    for (int i = 0; i < payload; ++i) data[i] = static_cast<uint8_t>(i * 13 + 7);

    auto t0 = clock::now();
    std::vector<float> audio = enc->encode(data.data(), data.size(), /*carrier Hz*/1500, call, mode);
    auto t1 = clock::now();
    if (audio.empty()) return "FAIL: encode() produced no samples";

    // half a second of silence, the frame, one second of silence (48 kHz)
    std::vector<float> stream(24000, 0.0f);
    stream.insert(stream.end(), audio.begin(), audio.end());
    stream.resize(stream.size() + 48000, 0.0f);

    bool ok = false;
    size_t got_len = 0;
    const size_t chunk = 1024;
    for (size_t p = 0; p < stream.size() && !ok; p += chunk) {
        size_t n = std::min(chunk, stream.size() - p);
        dec->process(stream.data() + p, n, [&](const uint8_t* d, size_t len) {
            got_len = len;
            ok = len >= static_cast<size_t>(payload) && std::memcmp(d, data.data(), payload) == 0;
        });
    }
    auto t2 = clock::now();

    const double enc_ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
    const double dec_ms = std::chrono::duration<double, std::milli>(t2 - t1).count();
    const double audio_ms = 1000.0 * audio.size() / 48000.0;
    const double stream_ms = 1000.0 * stream.size() / 48000.0;

    std::snprintf(buf, sizeof buf,
                  "%s: QPSK 1/2 short, %d B payload, %zu samples (%.0f ms of audio); "
                  "encode %.1f ms, decode %.1f ms for %.0f ms of stream (%.1fx realtime)"
                  "%s SNR %.1f dB",
                  ok ? "OK" : "FAIL", payload, audio.size(), audio_ms, enc_ms, dec_ms, stream_ms,
                  stream_ms / std::max(dec_ms, 0.001),
                  ok ? "," : (got_len ? ", payload mismatch," : ", no frame decoded,"),
                  static_cast<double>(dec->get_last_snr()));
    return buf;
}
