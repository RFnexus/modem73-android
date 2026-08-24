package app.modem73

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.modem73.core.ModemController
import app.modem73.service.ModemService
import app.modem73.ui.Modem73App

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            ModemController.setMicDenied(false)
            ModemService.start(this)
        } else if (grants.containsKey(Manifest.permission.RECORD_AUDIO)) {
            ModemController.setMicDenied(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ModemController.init(this)
        enableEdgeToEdge()
        setContent {
            Modem73App(onStartStop = { toggleEngine() })
        }
        handleDebugIntent(intent)
        if (savedInstanceState == null && !ModemController.running.value) {
            startEngineWithPermissions()
            requestBatteryExemption()
        }
    }

    override fun onStart() {
        super.onStart()
        ModemController.setUiVisible(true)
    }

    override fun onStop() {
        super.onStop()
        ModemController.setUiVisible(false)
    }

    override fun onResume() {
        super.onResume()
        ModemController.refreshSerialDevices()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            ModemController.setMicDenied(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        if (intent?.action == android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            ModemController.onUsbAttached()
        }
        if (intent?.action == "app.modem73.DEBUG_RECORD" && ModemController.running.value) {
            val secs = intent.getIntExtra("seconds", 10)
            android.util.Log.i("modem73", "debug record -> " + NativeCore.debugRecord(secs))
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        val prefs = getSharedPreferences("modem73", MODE_PRIVATE)
        if (prefs.getBoolean("battery_prompted", false)) return
        prefs.edit().putBoolean("battery_prompted", true).apply()
        runCatching {
            startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(android.net.Uri.parse("package:$packageName"))
            )
        }
    }

    private fun toggleEngine() {
        if (ModemController.running.value) {
            ModemService.stop(this)
            return
        }
        startEngineWithPermissions()
    }

    private fun startEngineWithPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.contains(Manifest.permission.RECORD_AUDIO) &&
            ModemController.micDenied.value &&
            !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
        ) {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null)
                )
            )
            return
        }
        if (needed.isEmpty()) {
            ModemService.start(this)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
