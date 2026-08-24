package app.modem73.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.core.app.NotificationCompat
import app.modem73.MainActivity
import app.modem73.core.ModemController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModemService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchJob: Job? = null
    private var runJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                watchJob?.cancel()
                runJob?.cancel()
                scope.launch {
                    withContext(Dispatchers.Default) { ModemController.stopEngine() }
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                val restart = intent?.action == ACTION_RESTART
                watchJob?.cancel()
                runJob?.cancel()
                ModemController.init(this)
                createChannel()
                try {
                    startForegroundCompat(buildNotification(if (restart) "Restarting" else "Starting"))
                } catch (e: Exception) {
                    Log.w("modem73", "foreground start refused", e)
                    stopSelf()
                    return START_NOT_STICKY
                }
                scope.launch {
                    val ok = withContext(Dispatchers.Default) {
                        if (restart) ModemController.stopEngine()
                        ModemController.startEngine()
                    }
                    if (!ok) {
                        updateNotification("Failed: ${ModemController.error.value ?: "unknown"}")
                        releaseWakeLock()
                        releaseWifiLock()
                        abandonFocus()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return@launch
                    }
                    acquireWakeLock()
                    acquireWifiLock()
                    requestFocus()
                    watchJobs()
                }
                return START_STICKY
            }
        }
    }

    private fun watchJobs() {
                watchJob = scope.launch {
                    ModemController.status.map { st ->
                        buildString {
                            append(st.modeName)
                            append("  RX ").append(st.rxFrames)
                            append("  TX ").append(st.txFrames)
                            if (st.clients > 0) append("  ").append(st.clients).append(" client")
                        }
                    }.distinctUntilChanged().collectLatest { text ->
                        if (ModemController.running.value) updateNotification(text)
                    }
                }
                runJob = scope.launch {
                    ModemController.running.collectLatest { running ->
                        if (!running) {
                            ModemController.consumeDeath()?.let { postStoppedNotification(it) }
                            releaseWakeLock()
                            releaseWifiLock()
                            abandonFocus()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
    }

    override fun onDestroy() {
        watchJob?.cancel()
        runJob?.cancel()
        releaseWakeLock()
        releaseWifiLock()
        abandonFocus()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "modem73:engine").also { it.acquire() }
    }

    private fun acquireWifiLock() {
        if (wifiLock != null || !ModemController.config.value.lanMode) return
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "modem73:lan").also { it.acquire() }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun requestFocus() {
        if (focusRequest != null) return
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) Log.w("modem73", "audio focus lost")
            }
            .build()
        am.requestAudioFocus(req)
        focusRequest = req
    }

    private fun abandonFocus() {
        focusRequest?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            val cfg = ModemController.config.value
            val usbGranted = cfg.pttType == 3 && ModemController.serialDevices.value.any { it.hasPermission && (cfg.comPort.isEmpty() || it.key == cfg.comPort) }
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (usbGranted) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            startForeground(NOTIF_ID, n, types)
        } else if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Modem", NotificationManager.IMPORTANCE_LOW))
        }
        if (nm.getNotificationChannel(ALERT_CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Modem alerts", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun postStoppedNotification(reason: String) {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val restart = PendingIntent.getService(this, 3, Intent(this, ModemService::class.java).setAction(ACTION_RESTART), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("MODEM73 stopped")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setContentIntent(open)
            .addAction(0, "Restart", restart)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(STOPPED_NOTIF_ID, n)
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, ModemService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val restart = PendingIntent.getService(this, 2, Intent(this, ModemService::class.java).setAction(ACTION_RESTART), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("MODEM73 running")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .addAction(0, "Restart", restart)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL = "modem73_engine"
        const val ALERT_CHANNEL = "modem73_alerts"
        const val NOTIF_ID = 73
        const val STOPPED_NOTIF_ID = 74
        const val ACTION_STOP = "app.modem73.STOP"
        const val ACTION_RESTART = "app.modem73.RESTART"

        fun start(context: Context) {
            val i = Intent(context, ModemService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ModemService::class.java).setAction(ACTION_STOP))
        }

        fun restart(context: Context) {
            context.startService(Intent(context, ModemService::class.java).setAction(ACTION_RESTART))
        }
    }
}
