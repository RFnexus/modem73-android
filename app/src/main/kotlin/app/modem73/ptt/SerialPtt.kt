package app.modem73.ptt

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

class SerialPtt(private val context: Context) {
    data class DeviceInfo(val key: String, val label: String, val hasPermission: Boolean)

    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null
    private var pendingKey: String? = null
    private var line = 1
    private var invertDtr = false
    private var invertRts = false
    private var receiverRegistered = false

    @Volatile
    var lastError: String = ""

    var onAttached: (() -> Unit)? = null
    var onDetached: (() -> Unit)? = null
    var onPermission: ((String, Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> { close(); onDetached?.invoke(); return }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> { onAttached?.invoke(); return }
            }
            if (intent.action != ACTION_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val key = pendingKey ?: return
            val cb = onPermission
            if (cb != null) cb(key, granted)
            else if (granted) open(key, line, invertDtr, invertRts)
            if (!granted) lastError = "USB permission denied"
        }
    }

    private fun keyOf(d: UsbDevice): String {
        val serial = runCatching { if (manager.hasPermission(d)) d.serialNumber else null }.getOrNull()
        return String.format("%04x:%04x:%s", d.vendorId, d.productId, serial ?: d.deviceName)
    }

    private fun drivers(): List<UsbSerialDriver> = runCatching { UsbSerialProber.getDefaultProber().findAllDrivers(manager) }.getOrDefault(emptyList())

    fun listDevices(): List<DeviceInfo> = drivers().map { drv ->
        val d = drv.device
        val name = d.productName ?: drv.javaClass.simpleName.removeSuffix("SerialDriver")
        DeviceInfo(keyOf(d), String.format("%s %04x:%04x", name, d.vendorId, d.productId), manager.hasPermission(d))
    }

    fun watchUsb() = ensureReceiver()

    private fun ensureReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(ACTION_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    fun requestPermission(key: String) {
        val drv = drivers().firstOrNull { keyOf(it.device) == key } ?: return
        ensureReceiver()
        pendingKey = key
        val flags = PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
        manager.requestPermission(drv.device, pi)
    }

    @Synchronized
    fun open(key: String, pttLine: Int, invDtr: Boolean, invRts: Boolean): Boolean {
        close()
        line = pttLine
        invertDtr = invDtr
        invertRts = invRts
        val all = drivers()
        val drv = if (key.isEmpty()) {
            all.firstOrNull()
        } else {
            all.firstOrNull { keyOf(it.device) == key }
                ?: all.firstOrNull { key.startsWith(String.format("%04x:%04x", it.device.vendorId, it.device.productId)) }
        }
        if (drv == null) {
            lastError = "no USB serial device"
            Log.w(TAG, "serial PTT: no device matching $key among ${all.size}")
            return false
        }
        if (!manager.hasPermission(drv.device)) {
            requestPermission(keyOf(drv.device))
            lastError = "waiting for USB permission"
            Log.i(TAG, "serial PTT: requesting USB permission")
            return false
        }
        return try {
            val conn = manager.openDevice(drv.device) ?: throw IllegalStateException("openDevice failed")
            val p = drv.ports[0]
            p.open(conn)
            p.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = p
            applyLines(false)
            lastError = ""
            Log.i(TAG, "serial PTT open on ${drv.device.deviceName}")
            true
        } catch (e: Exception) {
            lastError = e.message ?: "open failed"
            Log.w(TAG, "serial PTT open failed", e)
            port = null
            false
        }
    }

    private var rawThread: Thread? = null

    @Synchronized
    fun openRaw(key: String, baud: Int, portIndex: Int, onData: (ByteArray) -> Unit): Boolean {
        close()
        val all = drivers()
        val drv = all.firstOrNull { keyOf(it.device) == key }
            ?: all.firstOrNull { key.startsWith(String.format("%04x:%04x", it.device.vendorId, it.device.productId)) }
        if (drv == null) {
            lastError = "no USB serial device"
            return false
        }
        if (!manager.hasPermission(drv.device)) {
            requestPermission(keyOf(drv.device))
            lastError = "waiting for USB permission"
            return false
        }
        return try {
            val conn = manager.openDevice(drv.device) ?: throw IllegalStateException("openDevice failed")
            val p = drv.ports[portIndex.coerceIn(0, drv.ports.size - 1)]
            p.open(conn)
            p.setParameters(if (baud > 0) baud else 9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            runCatching { p.dtr = true; p.rts = true }
            port = p
            var logged = 0
            val t = Thread {
                val buf = ByteArray(1024)
                while (!Thread.currentThread().isInterrupted) {
                    val n = try { p.read(buf, 50) } catch (e: Exception) { break }
                    if (n > 0) {
                        if (logged < 8) { logged++; Log.i(TAG, "rig -> " + buf.copyOf(n).joinToString(" ") { "%02x".format(it) }) }
                        onData(buf.copyOf(n))
                    }
                }
            }
            t.isDaemon = true
            t.start()
            rawThread = t
            lastError = ""
            Log.i(TAG, "serial raw open on ${drv.device.deviceName} port $portIndex/${drv.ports.size} at $baud")
            true
        } catch (e: Exception) {
            lastError = e.message ?: "open failed"
            port = null
            false
        }
    }

    private var wlogged = 0

    fun writeRaw(data: ByteArray) {
        val p = port ?: return
        if (wlogged < 8) { wlogged++; Log.i(TAG, "rig <- " + data.joinToString(" ") { "%02x".format(it) }) }
        try { p.write(data, 200) } catch (e: Exception) { lastError = e.message ?: "write failed" }
    }

    private fun applyLines(on: Boolean) {
        val p = port ?: return
        val useDtr = line == 0 || line == 2
        val useRts = line == 1 || line == 2
        if (useDtr) p.dtr = on xor invertDtr else p.dtr = invertDtr
        if (useRts) p.rts = on xor invertRts else p.rts = invertRts
    }

    @Synchronized
    fun setPtt(on: Boolean): Boolean {
        if (rawThread != null) return false
        val p = port ?: return false
        return try {
            applyLines(on)
            true
        } catch (e: Exception) {
            lastError = e.message ?: "ptt failed"
            Log.w(TAG, "serial PTT set failed", e)
            false
        }
    }

    @Synchronized
    fun close() {
        rawThread?.interrupt()
        rawThread = null
        try {
            port?.let {
                applyLines(false)
                it.close()
            }
        } catch (_: Exception) {
        }
        port = null
    }

    val isOpen: Boolean get() = port != null

    companion object {
        private const val TAG = "modem73"
        const val ACTION_PERMISSION = "app.modem73.USB_PERMISSION"
    }
}
