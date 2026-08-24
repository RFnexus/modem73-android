package app.modem73.core

import android.content.Context
import android.util.Log
import app.modem73.NativeCore
import app.modem73.ptt.SerialPtt
import app.modem73.service.ModemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

object ModemController {
    private const val TAG = "modem73"
    private const val WF_ROWS = 64

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private lateinit var store: ConfigStore
    private lateinit var appContext: Context
    private var initialized = false

    private val _config = MutableStateFlow(TncConfig())
    val config: StateFlow<TncConfig> = _config.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _uiVisible = MutableStateFlow(false)

    fun setUiVisible(visible: Boolean) {
        _uiVisible.value = visible
    }

    private val _micDenied = MutableStateFlow(false)
    val micDenied: StateFlow<Boolean> = _micDenied.asStateFlow()

    fun setMicDenied(denied: Boolean) {
        _micDenied.value = denied
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _died = MutableStateFlow<String?>(null)

    fun consumeDeath(): String? {
        val d = _died.value
        _died.value = null
        return d
    }

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _snapshot = MutableStateFlow(EngineSnapshot())
    val snapshot: StateFlow<EngineSnapshot> = _snapshot.asStateFlow()

    private val _waterfall = MutableStateFlow<List<FloatArray>>(emptyList())
    val waterfall: StateFlow<List<FloatArray>> = _waterfall.asStateFlow()

    private val _occHistory = MutableStateFlow<List<Float>>(emptyList())
    val occHistory: StateFlow<List<Float>> = _occHistory.asStateFlow()

    private val _serialDevices = MutableStateFlow<List<SerialPtt.DeviceInfo>>(emptyList())
    val serialDevices: StateFlow<List<SerialPtt.DeviceInfo>> = _serialDevices.asStateFlow()

    val serialPtt: SerialPtt by lazy { SerialPtt(appContext) }

    data class RigModel(val id: Int, val mfg: String, val model: String)
    private val _rigModels = MutableStateFlow<List<RigModel>>(emptyList())
    val rigModels: StateFlow<List<RigModel>> = _rigModels.asStateFlow()

    private val _presets = MutableStateFlow<List<FreqPreset>>(emptyList())
    val presets: StateFlow<List<FreqPreset>> = _presets.asStateFlow()
    const val MAX_FREQ_PRESETS = 12

    fun setPresets(list: List<FreqPreset>) {
        _presets.value = list.take(MAX_FREQ_PRESETS)
        store.savePresets(_presets.value)
    }

    private var rigPollWanted = false

    fun setRigPoll(on: Boolean) {
        rigPollWanted = on
        if (_running.value) NativeCore.setRigPoll(on)
    }

    suspend fun rigCommand(cmd: String): Boolean {
        if (!_running.value) return false
        return withContext(Dispatchers.IO) {
            val ok = NativeCore.rigctlCommand(cmd).contains("RPRT 0")
            NativeCore.rigRefresh()
            ok
        }
    }

    fun alcTune() {
        if (!_running.value || _status.value.alcTuning) return
        scope.launch {
            val r = NativeCore.alcTune()
            if (r > 0f) updateConfig { it.copy(txDrive = r) }
        }
    }

    private fun engineConfigJson(cfg: TncConfig): String {
        val j = cfg.toJson()
        val usbKey = Regex("^[0-9a-f]{4}:[0-9a-f]{4}:").containsMatchIn(cfg.hamlibDevice)
        if (cfg.pttType == 5 && usbKey) {
            val slave = NativeCore.ptyCreate()
            if (slave.isNotEmpty()) {
                NativeCore.rigWriteHandler = { serialPtt.writeRaw(it) }
                serialPtt.openRaw(cfg.hamlibDevice, cfg.hamlibBaud, cfg.hamlibPort) { NativeCore.ptyPush(it) }
                j.put("hamlib_device", slave)
            }
        }
        return j.toString()
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        store = ConfigStore(appContext)
        _config.value = store.load()
        _presets.value = store.loadPresets()
        NativeCore.pttHandler = { on -> serialPtt.setPtt(on) }
        serialPtt.onAttached = { onUsbAttached() }
        serialPtt.onDetached = { onUsbDetached() }
        serialPtt.onPermission = { key, granted ->
            if (granted) {
                val cfg = _config.value
                if (_running.value && cfg.pttType == 3) {
                    serialPtt.open(key, cfg.comPttLine, cfg.comInvertDtr, cfg.comInvertRts)
                }
                maybeRestartForRig()
            } else {
                Log.w(TAG, "USB permission denied for $key")
            }
        }
        scope.launch {
            val rigs = runCatching { NativeCore.hamlibRigList() }.getOrDefault("")
            val parsed = rigs.lines().filter { it.isNotBlank() }.mapNotNull { line ->
                val f = line.split("|")
                if (f.size >= 3) RigModel(f[0].toIntOrNull() ?: return@mapNotNull null, f[1], f[2]) else null
            }.sortedWith(compareBy({ it.mfg.lowercase() }, { it.model.lowercase() }))
            _rigModels.value = parsed
            Log.i(TAG, "hamlib rig models available: " + parsed.size)
        }
        serialPtt.watchUsb()
        refreshSerialDevices()
    }

    fun onUsbAttached() {
        refreshSerialDevices()
        val cfg = _config.value
        if (_running.value && cfg.pttType == 3) {
            serialPtt.open(cfg.comPort, cfg.comPttLine, cfg.comInvertDtr, cfg.comInvertRts)
        }
        maybeRestartForRig()
    }

    private var lastRigRestartMs = 0L

    private fun maybeRestartForRig() {
        val cfg = _config.value
        if (!_running.value || cfg.pttType != 5) return
        if (!Regex("^[0-9a-f]{4}:[0-9a-f]{4}:").containsMatchIn(cfg.hamlibDevice)) return
        val present = serialPtt.listDevices().any { it.key.take(9) == cfg.hamlibDevice.take(9) }
        if (!present) return
        val now = System.currentTimeMillis()
        if (now - lastRigRestartMs < 8000) return
        lastRigRestartMs = now
        Log.i(TAG, "rig USB attached, restarting engine to reopen hamlib")
        ModemService.restart(appContext)
    }

    fun onUsbDetached() {
        refreshSerialDevices()
        if (_running.value && _config.value.pttType == 5) {
            Log.w(TAG, "USB detached, closing rig bridge")
            NativeCore.ptyClose()
        }
    }

    fun refreshSerialDevices() {
        if (!initialized) return
        _serialDevices.value = serialPtt.listDevices()
    }

    fun updateConfig(transform: (TncConfig) -> TncConfig) {
        val prev = _config.value
        val next = transform(prev)
        _config.value = next
        store.save(next)
        if (_running.value) {
            val serialChanged = next.pttType != prev.pttType ||
                next.comPort != prev.comPort ||
                next.comPttLine != prev.comPttLine ||
                next.comInvertDtr != prev.comInvertDtr ||
                next.comInvertRts != prev.comInvertRts
            if (serialChanged) {
                if (next.pttType == 3) {
                    serialPtt.open(next.comPort, next.comPttLine, next.comInvertDtr, next.comInvertRts)
                } else {
                    serialPtt.close()
                }
            }
            scope.launch {
                val err = NativeCore.setConfigJson(next.toJson().toString())
                if (err.isNotEmpty()) Log.w(TAG, "setConfig rejected: $err")
            }
        }
    }

    fun startEngine(): Boolean {
        if (_running.value) return true
        val cfg = _config.value
        _error.value = null
        if (cfg.pttType == 3) {
            serialPtt.open(cfg.comPort, cfg.comPttLine, cfg.comInvertDtr, cfg.comInvertRts)
        }
        _died.value = null
        val ok = NativeCore.engineStart(engineConfigJson(cfg), appContext.filesDir.absolutePath)
        if (!ok) {
            _error.value = NativeCore.lastError().ifEmpty { "engine start failed" }
            serialPtt.close()
            return false
        }
        _running.value = true
        _waterfall.value = emptyList()
        NativeCore.setRigPoll(rigPollWanted)
        startPolling()
        return true
    }

    fun stopEngine() {
        pollJob?.cancel()
        pollJob = null
        NativeCore.engineStop()
        NativeCore.ptyClose()
        serialPtt.close()
        _running.value = false
        _status.value = EngineStatus()
    }

    fun sendChat(text: String) {
        if (!_running.value || text.isBlank()) return
        scope.launch { NativeCore.sendChat(text.trim()) }
    }

    fun sendRandom(bytes: Int) {
        if (!_running.value) return
        val payload = ByteArray(bytes) { Random.nextInt(256).toByte() }
        scope.launch { NativeCore.queueData(payload, -1) }
    }

    fun sendTestPattern(bytes: Int) {
        if (!_running.value) return
        val payload = ByteArray(bytes) { 0x55 }
        scope.launch { NativeCore.queueData(payload, -1) }
    }

    fun sendPing() {
        if (!_running.value) return
        val payload = "PING:${_config.value.callsign}".toByteArray()
        scope.launch { NativeCore.queueData(payload, -1) }
    }

    fun resetStats() {
        if (_running.value) scope.launch { NativeCore.resetStats() }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var tick = 0L
            var lastOccMs = 0L
            val occ = ArrayDeque<Float>()
            val rows = ArrayDeque<FloatArray>()
            while (isActive) {
                if (!_uiVisible.value) {
                    if (!NativeCore.engineRunning()) {
                        val err = NativeCore.lastError()
                        withContext(Dispatchers.Main) {
                            _died.value = err.ifEmpty { "engine stopped unexpectedly" }
                            _running.value = false
                            if (err.isNotEmpty()) _error.value = err
                            _status.value = EngineStatus(error = err)
                        }
                        serialPtt.close()
                        break
                    }
                    delay(2000)
                    continue
                }
                val alive = NativeCore.engineRunning()
                if (!alive) {
                    val err = NativeCore.lastError()
                    withContext(Dispatchers.Main) {
                        _died.value = err.ifEmpty { "engine stopped unexpectedly" }
                        _running.value = false
                        if (err.isNotEmpty()) _error.value = err
                        _status.value = EngineStatus(error = err)
                    }
                    serialPtt.close()
                    break
                }
                val st = runCatching { EngineStatus.parse(NativeCore.statusJson()) }.getOrNull()
                if (st != null) _status.value = st
                val wf = NativeCore.takeWaterfall()
                val bins = 128
                if (wf.size >= bins) {
                    var off = 0
                    while (off + bins <= wf.size) {
                        rows.addFirst(wf.copyOfRange(off, off + bins))
                        off += bins
                    }
                    while (rows.size > WF_ROWS) rows.removeLast()
                    _waterfall.value = rows.toList()
                }
                val now = System.currentTimeMillis()
                if (st != null && now - lastOccMs >= 5000) {
                    lastOccMs = now
                    occ.addLast(st.channelOccupancy)
                    while (occ.size > 60) occ.removeFirst()
                    _occHistory.value = occ.toList()
                }
                if (tick % 5 == 0L) {
                    val sn = runCatching { EngineSnapshot.parse(NativeCore.snapshotJson()) }.getOrNull()
                    if (sn != null) _snapshot.value = sn
                }
                tick++
                delay(100)
            }
        }
    }
}
