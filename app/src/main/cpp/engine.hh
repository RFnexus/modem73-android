#pragma once

#include <atomic>
#include <deque>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

class Modem73Engine {
public:
    static Modem73Engine& instance();

    bool start(const std::string& config_json, const std::string& home_dir, std::string& error);
    void stop();
    bool running() const { return running_.load(); }
    std::string last_error();

    std::string status_json();
    std::string snapshot_json();
    std::string config_json();

    bool set_config_json(const std::string& json, std::string& error);
    void queue_data(const std::vector<uint8_t>& data, int oper_mode);
    void send_chat(const std::string& text);

    std::string rigctl(const std::string& cmd);
    void set_rig_poll(bool on) { rig_poll_ui_ = on; }
    void rig_refresh();
    float alc_tune();
    std::vector<float> take_waterfall(int& bins);
    void reset_stats();
    
    std::string audio_devices_json();
    std::string debug_record(int seconds);

    std::function<bool(bool)> ptt_hook;
    std::atomic<bool> rig_poll_ui_{false};
    int64_t last_ptt_ms_ = 0;

private:
    Modem73Engine() = default;
    void tick_loop();
    void update_waterfall();

    struct Impl;
    std::unique_ptr<Impl> impl_;
    std::atomic<bool> running_{false};
    std::mutex error_mutex_;
    std::string error_;
    std::thread tnc_thread_;
    std::thread tick_thread_;
    std::atomic<bool> tick_running_{false};
    std::mutex wf_mutex_;
    std::deque<std::vector<float>> wf_pending_;
    std::mutex lifecycle_mutex_;
    std::mutex rec_mutex_;
    std::vector<float> rec_buf_;
    size_t rec_target_ = 0;
    std::string rec_path_;
    std::atomic<bool> rec_active_{false};
    void write_recording();
};
